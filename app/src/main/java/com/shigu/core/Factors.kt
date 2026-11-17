package com.shigu.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** 筛选淘汰原因；顺序决定漏斗展示顺序。 */
enum class FilterReason(val label: String) {
    BOARD("板块/资产"),
    PRICE("价格"),
    AMOUNT("流动性"),
    ST("ST/风险警示"),
    SUSPENDED("停牌"),
    OWNER("企业性质"),
    LISTED("上市天数"),
    PE("PE 上限"),
    PB("PB 上限"),
    CAP("市值区间"),
    PROFILE("资料未同步"),
}

class Panel(
    val calendar: List<String>,
    val stocks: List<Stock>,
    val open: Array<DoubleArray>,
    val close: Array<DoubleArray>,
    val high: Array<DoubleArray>,
    val low: Array<DoubleArray>,
    val volume: Array<DoubleArray>,
    val amount: Array<DoubleArray>,
    val turnover: Array<DoubleArray>,
    val pe: Array<DoubleArray>,
    val pb: Array<DoubleArray>,
    val marketCap: Array<DoubleArray>,
    val benchmark: DoubleArray,
    val factorCache: MutableMap<Triple<Int, Int, FactorKey>, Double?> = HashMap(),
    val crossCache: MutableMap<Pair<Int, FactorKey>, DoubleArray> = HashMap(),
    val statusHistory: Map<String, List<StatusSnapshot>> = emptyMap(),
) {
    val lastIndex: Int get() = calendar.lastIndex
    private val dateIndex: Map<String, Int> = calendar.withIndex().associate { it.value to it.index }
    private val codeIndex: Map<String, Int> = stocks.withIndex().associate { it.value.code to it.index }

    fun indexOfDate(date: String): Int? = dateIndex[date]
    fun indexOfCode(code: String): Int? = codeIndex[code]

    /** 最后一个 <= date 的状态快照；没有历史时返回 null。 */
    fun statusAt(code: String, date: String): StatusSnapshot? {
        val list = statusHistory[code] ?: return null
        var lo = 0
        var hi = list.lastIndex
        var ans: StatusSnapshot? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (list[mid].date <= date) {
                ans = list[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

object PanelBuilder {
    fun build(
        stocks: List<Stock>,
        calendar: List<String>,
        barsByCode: Map<String, List<DailyBar>>,
        valuationsByCode: Map<String, List<ValuationPoint>> = emptyMap(),
        benchmarkBars: List<DailyBar> = emptyList(),
        fillCurrentSnapshot: Boolean = false,
        statusHistory: Map<String, List<StatusSnapshot>> = emptyMap(),
    ): Panel {
        val n = stocks.size
        val m = calendar.size
        fun nanArray() = Array(n) { DoubleArray(m) { Double.NaN } }
        val open = nanArray()
        val close = nanArray()
        val high = nanArray()
        val low = nanArray()
        val volume = nanArray()
        val amount = nanArray()
        val turnover = nanArray()
        val pe = nanArray()
        val pb = nanArray()
        val cap = nanArray()
        val dateToIndex = calendar.withIndex().associate { it.value to it.index }

        stocks.forEachIndexed { si, stock ->
            barsByCode[stock.code]?.forEach { b ->
                val di = dateToIndex[b.date] ?: return@forEach
                open[si][di] = b.open
                close[si][di] = b.close
                high[si][di] = b.high
                low[si][di] = b.low
                volume[si][di] = b.volume
                amount[si][di] = b.amount
                turnover[si][di] = b.turnover
            }
            valuationsByCode[stock.code]?.forEach { v ->
                val di = dateToIndex[v.date] ?: return@forEach
                if (v.pe != null && v.pe > 0) pe[si][di] = v.pe
                if (v.pb != null && v.pb > 0) pb[si][di] = v.pb
                if (v.marketCap != null && v.marketCap > 0) cap[si][di] = v.marketCap
            }
        }

        if (fillCurrentSnapshot && m > 0) {
            stocks.forEachIndexed { si, s ->
                s.pe?.takeIf { it > 0 }?.let { pe[si][m - 1] = it }
                s.pb?.takeIf { it > 0 }?.let { pb[si][m - 1] = it }
                s.marketCap?.takeIf { it > 0 }?.let { cap[si][m - 1] = it }
            }
        }

        val bench = DoubleArray(m) { Double.NaN }
        benchmarkBars.forEach { b ->
            val di = dateToIndex[b.date] ?: return@forEach
            bench[di] = b.close
        }
        return Panel(calendar, stocks, open, close, high, low, volume, amount, turnover, pe, pb, cap, bench, statusHistory = statusHistory)
    }
}

data class ScoredStock(
    val stockIndex: Int,
    val score: Double,
    val values: Map<FactorKey, Double?>,
    val contributions: Map<FactorKey, Double> = emptyMap(),
    val customValues: Map<String, Double?> = emptyMap(),
    val customContributions: Map<String, Double> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
)

data class FunnelStep(
    val label: String,
    val remaining: Int,
    val dropped: Int,
    val reasons: Map<String, Int> = emptyMap(),
)

object FactorEngine {

    // ---------------- 基础统计 ----------------

    private fun avg(a: DoubleArray, end: Int, n: Int): Double? {
        if (end < 0 || end - n + 1 < 0) return null
        var sum = 0.0
        for (k in end - n + 1..end) {
            if (a[k].isNaN()) return null
            sum += a[k]
        }
        return sum / n
    }

    private fun maxOf(a: DoubleArray, end: Int, n: Int): Double? {
        if (end < 0 || end - n + 1 < 0) return null
        var m = Double.NEGATIVE_INFINITY
        for (k in end - n + 1..end) {
            if (a[k].isNaN()) return null
            if (a[k] > m) m = a[k]
        }
        return m
    }

    private fun ret(close: DoubleArray, i: Int, n: Int): Double? {
        if (i - n < 0) return null
        val a = close[i - n]
        val b = close[i]
        if (a.isNaN() || b.isNaN() || a <= 0) return null
        return b / a - 1.0
    }

    private fun dailyVol(close: DoubleArray, i: Int, n: Int): Double? {
        if (i - n < 0) return null
        val rets = ArrayList<Double>(n)
        for (k in i - n + 1..i) {
            val a = close[k - 1]
            val b = close[k]
            if (a.isNaN() || b.isNaN() || a <= 0) return null
            rets += b / a - 1.0
        }
        val mean = rets.average()
        val varSum = rets.sumOf { (it - mean) * (it - mean) }
        return sqrt(varSum / (rets.size - 1).coerceAtLeast(1)) * sqrt(252.0)
    }

    private fun rsi(close: DoubleArray, i: Int, n: Int = 14): Double? {
        if (i - n < 0) return null
        var gain = 0.0
        var loss = 0.0
        for (k in i - n + 1..i) {
            val a = close[k - 1]
            val b = close[k]
            if (a.isNaN() || b.isNaN() || a <= 0) return null
            val d = b - a
            if (d >= 0) gain += d else loss -= d
        }
        if (gain + loss == 0.0) return 50.0
        return 100.0 * gain / (gain + loss)
    }

    private fun mfi(panel: Panel, si: Int, i: Int, n: Int = 14): Double? {
        if (i - n < 0) return null
        var positive = 0.0
        var negative = 0.0
        for (k in i - n + 1..i) {
            val h = panel.high[si][k]
            val l = panel.low[si][k]
            val c = panel.close[si][k]
            val ph = panel.high[si][k - 1]
            val pl = panel.low[si][k - 1]
            val pc = panel.close[si][k - 1]
            val v = panel.volume[si][k]
            if (h.isNaN() || l.isNaN() || c.isNaN() || ph.isNaN() || pl.isNaN() || pc.isNaN() || v.isNaN()) {
                return null
            }
            val tp = (h + l + c) / 3.0
            val prev = (ph + pl + pc) / 3.0
            val flow = tp * v
            if (tp > prev) positive += flow else if (tp < prev) negative += flow
        }
        if (positive + negative <= 1e-9) return 50.0
        if (negative <= 1e-9) return 100.0
        return 100.0 - 100.0 / (1.0 + positive / negative)
    }

    private fun bollZ(close: DoubleArray, i: Int, n: Int = 20): Double? {
        val ma = avg(close, i, n) ?: return null
        if (i - n + 1 < 0 || close[i].isNaN()) return null
        var variance = 0.0
        for (k in i - n + 1..i) {
            val d = close[k] - ma
            variance += d * d
        }
        val sd = sqrt(variance / (n - 1).coerceAtLeast(1))
        if (sd <= 1e-9) return null
        return (close[i] - ma) / sd
    }

    private fun atr(panel: Panel, si: Int, i: Int, n: Int = 20): Double? {
        if (i - n < 0) return null
        val tr = ArrayList<Double>(n)
        for (k in i - n + 1..i) {
            val h = panel.high[si][k]
            val l = panel.low[si][k]
            val pc = panel.close[si][k - 1]
            if (h.isNaN() || l.isNaN() || pc.isNaN()) return null
            tr += maxOf(h - l, abs(h - pc), abs(l - pc))
        }
        val c = panel.close[si][i]
        if (c.isNaN() || c <= 0) return null
        return tr.average() / c
    }

    private fun std(a: DoubleArray): Double? {
        val v = a.filter { !it.isNaN() }
        if (v.size < 3) return null
        val mean = v.average()
        val variance = v.sumOf { (it - mean) * (it - mean) } / (v.size - 1)
        return sqrt(variance)
    }

    private fun corr(x: DoubleArray, y: DoubleArray): Double? {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (k in x.indices) {
            if (!x[k].isNaN() && !y[k].isNaN()) {
                xs += x[k]
                ys += y[k]
            }
        }
        if (xs.size < 6) return null
        val mx = xs.average()
        val my = ys.average()
        var cov = 0.0
        var vx = 0.0
        var vy = 0.0
        for (k in xs.indices) {
            val dx = xs[k] - mx
            val dy = ys[k] - my
            cov += dx * dy
            vx += dx * dx
            vy += dy * dy
        }
        if (vx <= 1e-12 || vy <= 1e-12) return null
        return cov / sqrt(vx * vy)
    }

    private fun skew(a: DoubleArray): Double? {
        val v = a.filter { !it.isNaN() }
        if (v.size < 6) return null
        val m = v.average()
        val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        if (sd <= 1e-12) return null
        return v.sumOf { ((it - m) / sd).pow(3) } / v.size
    }

    private fun kurt(a: DoubleArray): Double? {
        val v = a.filter { !it.isNaN() }
        if (v.size < 6) return null
        val m = v.average()
        val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        if (sd <= 1e-12) return null
        return v.sumOf { ((it - m) / sd).pow(4) } / v.size - 3.0
    }

    private fun olsSlope(x: DoubleArray, y: DoubleArray, from: Int, to: Int): Pair<Double, Double>? {
        if (from < 0 || to >= x.size || to - from + 1 < 3) return null
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        val n = to - from + 1
        for (k in from..to) {
            val xv = x[k]
            val yv = y[k]
            if (xv.isNaN() || yv.isNaN()) return null
            sx += xv
            sy += yv
            sxx += xv * xv
            sxy += xv * yv
        }
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-12) return null
        val slope = (n * sxy - sx * sy) / denom
        val r = corr(x.copyOfRange(from, to + 1), y.copyOfRange(from, to + 1)) ?: 0.0
        return slope to (r * r).coerceIn(0.0, 1.0)
    }

    private data class Rsrs(val slope: Double, val r2: Double, val z: Double, val right: Double)

    private fun rsrs(panel: Panel, si: Int, i: Int): Rsrs? {
        if (i < 78) return null
        val slopes = DoubleArray(61)
        val r2s = DoubleArray(61)
        for ((k, t) in (i - 60..i).withIndex()) {
            val s = olsSlope(panel.low[si], panel.high[si], t - 17, t) ?: return null
            slopes[k] = s.first
            r2s[k] = s.second
        }
        val mean = slopes.average()
        val sd = std(slopes) ?: return null
        if (sd < 1e-9) return null
        val z = (slopes.last() - mean) / sd
        return Rsrs(slopes.last(), r2s.last(), z, z * r2s.last() * slopes.last())
    }

    private fun lltGap(close: DoubleArray, i: Int): Double? {
        if (i < 40) return null
        val start = (i - 120).coerceAtLeast(0)
        val n = i - start + 1
        val p = DoubleArray(n) { close[start + it] }
        if (p.any { it.isNaN() || it <= 0 }) return null
        val a = 2.0 / 31.0
        val llt = DoubleArray(n)
        llt[0] = p[0]
        llt[1] = p[1]
        for (k in 2 until n) {
            llt[k] = (a - a * a / 4.0) * p[k] +
                (a * a / 2.0) * p[k - 1] -
                (a - 3.0 * a * a / 4.0) * p[k - 2] +
                2.0 * (1.0 - a) * llt[k - 1] -
                (1.0 - a) * (1.0 - a) * llt[k - 2]
        }
        val last = llt[n - 1]
        return if (last > 0) close[i] / last - 1.0 else null
    }

    private fun betaAndIdio(panel: Panel, si: Int, i: Int, n: Int = 60): Pair<Double, Double>? {
        if (i - n < 0) return null
        val ri = ArrayList<Double>(n)
        val rm = ArrayList<Double>(n)
        for (k in i - n + 1..i) {
            val a = panel.close[si][k - 1]
            val b = panel.close[si][k]
            val ma = panel.benchmark[k - 1]
            val mb = panel.benchmark[k]
            if (a.isNaN() || b.isNaN() || ma.isNaN() || mb.isNaN() || a <= 0 || ma <= 0) continue
            ri += b / a - 1.0
            rm += mb / ma - 1.0
        }
        if (ri.size < 30) return null
        val mi = ri.average()
        val mm = rm.average()
        var cov = 0.0
        var varM = 0.0
        for (k in ri.indices) {
            cov += (ri[k] - mi) * (rm[k] - mm)
            varM += (rm[k] - mm) * (rm[k] - mm)
        }
        if (varM <= 1e-12) return null
        val beta = cov / varM
        val residuals = DoubleArray(ri.size) { ri[it] - beta * rm[it] }
        val sd = std(residuals) ?: return null
        return beta to sd * sqrt(252.0)
    }

    private fun seriesOf(a: DoubleArray, end: Int, n: Int): DoubleArray? {
        if (end - n + 1 < 0) return null
        return a.copyOfRange(end - n + 1, end + 1)
    }

    fun value(panel: Panel, si: Int, i: Int, key: FactorKey): Double? {
        if (i < 0 || si !in panel.stocks.indices) return null
        val cacheKey = Triple(si, i, key)
        if (panel.factorCache.containsKey(cacheKey)) return panel.factorCache[cacheKey]
        val v = computeValue(panel, si, i, key)
        panel.factorCache[cacheKey] = v
        return v
    }

    private fun computeValue(panel: Panel, si: Int, i: Int, key: FactorKey): Double? {
        val close = panel.close[si]
        val high = panel.high[si]
        val amount = panel.amount[si]
        val volume = panel.volume[si]
        val turnover = panel.turnover[si]
        return when (key) {
            FactorKey.RSRS_RIGHT -> rsrs(panel, si, i)?.right
            FactorKey.RSRS_Z -> rsrs(panel, si, i)?.z
            FactorKey.RSRS_SLOPE -> rsrs(panel, si, i)?.slope
            FactorKey.RSRS_R2 -> rsrs(panel, si, i)?.r2
            FactorKey.LLT_GAP -> lltGap(close, i)
            FactorKey.RET1 -> ret(close, i, 1)
            FactorKey.RET5 -> ret(close, i, 5)
            FactorKey.RET20 -> ret(close, i, 20)
            FactorKey.RET60 -> ret(close, i, 60)
            FactorKey.RET120 -> ret(close, i, 120)
            FactorKey.RET250 -> ret(close, i, 250)
            FactorKey.VOL20 -> dailyVol(close, i, 20)
            FactorKey.VOL60 -> dailyVol(close, i, 60)
            FactorKey.MA_TREND -> {
                val ma20 = avg(close, i, 20)
                val ma60 = avg(close, i, 60)
                if (ma20 == null || ma60 == null || ma60 == 0.0) null else ma20 / ma60 - 1.0
            }
            FactorKey.MA60_GAP -> {
                val ma60 = avg(close, i, 60)
                if (ma60 == null || ma60 == 0.0) null else close[i] / ma60 - 1.0
            }
            FactorKey.RSI14 -> rsi(close, i, 14)
            FactorKey.RSI21 -> rsi(close, i, 21)
            FactorKey.MFI14 -> mfi(panel, si, i, 14)
            FactorKey.BOLL_Z -> bollZ(close, i, 20)
            FactorKey.VOLUME_RATIO -> {
                val v5 = avg(volume, i, 5)
                val v20 = avg(volume, i, 20)
                if (v5 == null || v20 == null || v20 == 0.0) null else v5 / v20
            }
            FactorKey.VOLUME_PRICE_DIV -> {
                val rets = DoubleArray(20) { k ->
                    val t = i - 19 + k
                    if (t <= 0) Double.NaN else {
                        val a = close[t - 1]
                        val b = close[t]
                        if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
                    }
                }
                val dvol = DoubleArray(20) { k ->
                    val t = i - 19 + k
                    if (t <= 0) Double.NaN else {
                        val a = volume[t - 1]
                        val b = volume[t]
                        if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
                    }
                }
                val c = corr(rets, dvol) ?: return null
                -c
            }
            FactorKey.TURNOVER_STABILITY -> {
                val s = seriesOf(turnover, i, 20) ?: return null
                val mean = s.filter { !it.isNaN() }.average()
                val sd = std(s) ?: return null
                if (mean <= 1e-9) null else sd / mean
            }
            FactorKey.AMOUNT_STABILITY -> {
                val s = seriesOf(amount, i, 20) ?: return null
                val mean = s.filter { !it.isNaN() }.average()
                val sd = std(s) ?: return null
                if (mean <= 1e-9) null else sd / mean
            }
            FactorKey.INTERDAY_REV -> {
                val rets = DoubleArray(20) { k ->
                    val t = i - 19 + k
                    if (t <= 0) Double.NaN else {
                        val a = close[t - 1]
                        val b = close[t]
                        if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
                    }
                }
                val v = rets.filter { !it.isNaN() }
                if (v.size < 15) null else v.average()
            }
            FactorKey.INTRADAY_REV -> {
                var sum = 0.0
                var n = 0
                for (t in i - 19..i) {
                    val o = panel.open[si][t]
                    val c = close[t]
                    if (!o.isNaN() && !c.isNaN() && o > 0) {
                        sum += c / o - 1.0
                        n++
                    }
                }
                if (n < 15) null else sum / n
            }
            FactorKey.HIGH20_GAP -> {
                val h20 = maxOf(high, i, 20)
                if (h20 == null || h20 == 0.0) null else close[i] / h20 - 1.0
            }
            FactorKey.DRAWDOWN60 -> {
                val h60 = maxOf(close, i, 60)
                if (h60 == null || h60 == 0.0) null else close[i] / h60 - 1.0
            }
            FactorKey.ATR20 -> atr(panel, si, i, 20)
            FactorKey.BETA60 -> betaAndIdio(panel, si, i, 60)?.first
            FactorKey.IDIO_VOL -> betaAndIdio(panel, si, i, 60)?.second
            FactorKey.TURNOVER20 -> avg(turnover, i, 20)
            FactorKey.AMOUNT20 -> avg(amount, i, 20)
            FactorKey.AMIHUD20 -> {
                var sum = 0.0
                var n = 0
                for (t in i - 19..i) {
                    val a = if (t > 0) close[t - 1] else Double.NaN
                    val b = close[t]
                    val amt = amount[t]
                    if (!a.isNaN() && !b.isNaN() && a > 0 && amt > 0) {
                        sum += abs(b / a - 1.0) / amt
                        n++
                    }
                }
                if (n < 15) null else sum / n * 1e8
            }
            FactorKey.MAX_RET20 -> {
                var m = Double.NEGATIVE_INFINITY
                var n = 0
                for (t in i - 19..i) {
                    if (t <= 0) continue
                    val a = close[t - 1]
                    val b = close[t]
                    if (!a.isNaN() && !b.isNaN() && a > 0) {
                        m = max(m, b / a - 1.0)
                        n++
                    }
                }
                if (n < 15) null else m
            }
            FactorKey.SKEW20 -> {
                val rets = DoubleArray(20) { k ->
                    val t = i - 19 + k
                    if (t <= 0) Double.NaN else {
                        val a = close[t - 1]
                        val b = close[t]
                        if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
                    }
                }
                skew(rets)
            }
            FactorKey.KURT20 -> {
                val rets = DoubleArray(20) { k ->
                    val t = i - 19 + k
                    if (t <= 0) Double.NaN else {
                        val a = close[t - 1]
                        val b = close[t]
                        if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
                    }
                }
                kurt(rets)
            }
            FactorKey.PE -> panel.pe[si][i].takeIf { !it.isNaN() && it > 0 }
            FactorKey.PB -> panel.pb[si][i].takeIf { !it.isNaN() && it > 0 }
            FactorKey.MARKET_CAP -> panel.marketCap[si][i].takeIf { !it.isNaN() && it > 0 }
            FactorKey.LISTED_DAYS -> {
                val stock = panel.stocks[si]
                val listed = panel.statusAt(stock.code, panel.calendar[i])?.listedDate?.takeIf { it.isNotBlank() }
                    ?: stock.listedDate
                listedDaysBetween(listed, panel.calendar[i])?.toDouble()
            }
            FactorKey.ROE -> panel.stocks[si].roe
            FactorKey.GROSS_MARGIN -> panel.stocks[si].grossMargin
            FactorKey.NET_MARGIN -> panel.stocks[si].netMargin
            FactorKey.REVENUE_GROWTH -> panel.stocks[si].revenueGrowth
            FactorKey.PROFIT_GROWTH -> panel.stocks[si].profitGrowth
            FactorKey.DEBT_RATIO -> panel.stocks[si].debtRatio
            FactorKey.EPS -> panel.stocks[si].eps
            FactorKey.BPS -> panel.stocks[si].bps
            FactorKey.OCFPS -> panel.stocks[si].ocfps
            FactorKey.TEAM_COIN, FactorKey.OVERNIGHT_DIST -> null
        }
    }

    fun valueAt(panel: Panel, si: Int, i: Int, key: FactorKey): Double? {
        return if (key.crossSectional) {
            CrossSectionalEngine.values(panel, i, key)?.getOrNull(si)?.takeIf { !it.isNaN() }
        } else {
            value(panel, si, i, key)
        }
    }

    /** 与 scoreAt 完全一致的过滤顺序；漏斗和打分共用，避免口径漂移。 */
    fun filterReason(panel: Panel, si: Int, i: Int, filters: StrategyFilters): FilterReason? {
        val stock = panel.stocks[si]
        if (stock.board !in filters.boards) return FilterReason.BOARD
        val price = panel.close[si][i]
        if (price.isNaN() || price < filters.minPrice) return FilterReason.PRICE
        val amount20 = value(panel, si, i, FactorKey.AMOUNT20)
        if (amount20 == null || amount20 < filters.minAmount20) return FilterReason.AMOUNT

        val status = panel.statusAt(stock.code, panel.calendar[i])
        val stStatus = status?.stStatus ?: stock.stStatus
        val ownerType = status?.ownerType ?: stock.ownerType
        val suspended = status?.suspended ?: stock.suspended
        val listedDate = status?.listedDate?.takeIf { it.isNotBlank() } ?: stock.listedDate

        if (filters.excludeSt && stStatus != StStatus.NORMAL) return FilterReason.ST
        if (filters.stStatuses.isNotEmpty() && stStatus !in filters.stStatuses) return FilterReason.ST
        if (filters.excludeSuspended && suspended) return FilterReason.SUSPENDED
        if (filters.owners.isNotEmpty() && ownerType !in filters.owners) return FilterReason.OWNER
        if (filters.minListedDays > 0) {
            val days = listedDaysBetween(listedDate, panel.calendar[i]) ?: return FilterReason.LISTED
            if (days < filters.minListedDays) return FilterReason.LISTED
        }

        val peV = value(panel, si, i, FactorKey.PE)
        val pbV = value(panel, si, i, FactorKey.PB)
        if (filters.maxPe != null && (peV == null || peV <= 0 || peV > filters.maxPe)) return FilterReason.PE
        if (filters.maxPb != null && (pbV == null || pbV <= 0 || pbV > filters.maxPb)) return FilterReason.PB

        if (filters.minMarketCap != null || filters.maxMarketCap != null) {
            val cap = panel.marketCap[si][i].takeIf { !it.isNaN() && it > 0 }
                ?: stock.marketCap?.takeIf { i == panel.lastIndex && it > 0 }
                ?: return FilterReason.CAP
            if (filters.minMarketCap != null && cap < filters.minMarketCap) return FilterReason.CAP
            if (filters.maxMarketCap != null && cap > filters.maxMarketCap) return FilterReason.CAP
        }
        if (filters.requireProfile && stock.profileUpdatedAt <= 0) return FilterReason.PROFILE
        return null
    }

    fun scoreAt(panel: Panel, i: Int, config: StrategyConfig): List<ScoredStock> {
        if (i < 0 || panel.stocks.isEmpty()) return emptyList()
        val totalWeight = config.factors.sumOf { it.weight } + config.customFactors.sumOf { it.weight }
        if (totalWeight <= 0.0) return emptyList()

        val count = panel.stocks.size
        val raw = HashMap<FactorKey, DoubleArray>(config.factors.size)
        config.factors.forEach { fw ->
            raw[fw.key] = if (fw.key.crossSectional) {
                CrossSectionalEngine.values(panel, i, fw.key) ?: DoubleArray(count) { Double.NaN }
            } else {
                DoubleArray(count) { si -> value(panel, si, i, fw.key) ?: Double.NaN }
            }
        }
        val customRaw = HashMap<String, DoubleArray>(config.customFactors.size)
        config.customFactors.forEach { cw ->
            customRaw[cw.factor.id] = DoubleArray(count) { si ->
                CustomFactorEngine.value(panel, si, i, cw.factor) ?: Double.NaN
            }
        }

        val stats = HashMap<FactorKey, Pair<Double, Double>>()
        raw.forEach { (key, arr) ->
            val valid = arr.filter { !it.isNaN() }
            if (valid.size >= 5) {
                val mean = valid.average()
                val variance = valid.sumOf { (it - mean) * (it - mean) } / (valid.size - 1).coerceAtLeast(1)
                val sd = sqrt(variance)
                if (sd > 1e-9) stats[key] = mean to sd
            }
        }
        val customStats = HashMap<String, Pair<Double, Double>>()
        customRaw.forEach { (key, arr) ->
            val valid = arr.filter { !it.isNaN() }
            if (valid.size >= 5) {
                val mean = valid.average()
                val variance = valid.sumOf { (it - mean) * (it - mean) } / (valid.size - 1).coerceAtLeast(1)
                val sd = sqrt(variance)
                if (sd > 1e-9) customStats[key] = mean to sd
            }
        }

        // 排名打分：每个因子在截面上转成 [-1, 1] 的百分位，降低极端值影响。
        val rankScores = if (config.scoring == ScoringMode.RANK) {
            val out = HashMap<FactorKey, DoubleArray>(raw.size)
            raw.forEach { (key, arr) ->
                val valid = arr.withIndex().filter { !it.value.isNaN() }.sortedBy { it.value }
                if (valid.size >= 5) {
                    val ranked = DoubleArray(count) { Double.NaN }
                    val denom = (valid.size - 1).coerceAtLeast(1)
                    valid.forEachIndexed { rank, item ->
                        ranked[item.index] = rank.toDouble() / denom * 2.0 - 1.0
                    }
                    out[key] = ranked
                }
            }
            out
        } else {
            emptyMap()
        }
        val customRanks = if (config.scoring == ScoringMode.RANK) {
            val out = HashMap<String, DoubleArray>(customRaw.size)
            customRaw.forEach { (key, arr) ->
                val valid = arr.withIndex().filter { !it.value.isNaN() }.sortedBy { it.value }
                if (valid.size >= 5) {
                    val ranked = DoubleArray(count) { Double.NaN }
                    val denom = (valid.size - 1).coerceAtLeast(1)
                    valid.forEachIndexed { rank, item ->
                        ranked[item.index] = rank.toDouble() / denom * 2.0 - 1.0
                    }
                    out[key] = ranked
                }
            }
            out
        } else {
            emptyMap()
        }

        val result = ArrayList<ScoredStock>(count)
        for (si in 0 until count) {
            if (filterReason(panel, si, i, config.filters) != null) continue

            var score = 0.0
            var usedWeight = 0.0
            val values = LinkedHashMap<FactorKey, Double?>()
            val rawContrib = LinkedHashMap<FactorKey, Double>()
            config.factors.forEach { fw ->
                val v = raw[fw.key]?.getOrNull(si)?.takeIf { !it.isNaN() }
                values[fw.key] = v
                val direction = if (fw.key.higherIsBetter) 1.0 else -1.0
                val scoreValue = if (config.scoring == ScoringMode.RANK) {
                    rankScores[fw.key]?.getOrNull(si)?.takeIf { !it.isNaN() }
                } else {
                    val st = stats[fw.key]
                    if (v != null && st != null) {
                        ((v - st.first) / st.second).coerceIn(-2.5, 2.5)
                    } else {
                        null
                    }
                }
                if (scoreValue != null) {
                    val weighted = fw.weight * scoreValue * direction
                    score += weighted
                    rawContrib[fw.key] = weighted
                    usedWeight += fw.weight
                }
            }
            val customValues = LinkedHashMap<String, Double?>()
            val customContrib = LinkedHashMap<String, Double>()
            config.customFactors.forEach { cw ->
                val v = customRaw[cw.factor.id]?.getOrNull(si)?.takeIf { !it.isNaN() }
                customValues[cw.factor.id] = v
                val direction = if (cw.factor.higherIsBetter) 1.0 else -1.0
                val scoreValue = if (config.scoring == ScoringMode.RANK) {
                    customRanks[cw.factor.id]?.getOrNull(si)?.takeIf { !it.isNaN() }
                } else {
                    val st = customStats[cw.factor.id]
                    if (v != null && st != null) {
                        ((v - st.first) / st.second).coerceIn(-2.5, 2.5)
                    } else {
                        null
                    }
                }
                if (scoreValue != null) {
                    val weighted = cw.weight * scoreValue * direction
                    score += weighted
                    customContrib[cw.factor.id] = weighted
                    usedWeight += cw.weight
                }
            }
            if (usedWeight < totalWeight * 0.5) continue
            val denom = if (usedWeight > 0.0) usedWeight else 1.0
            result += ScoredStock(
                stockIndex = si,
                score = score / denom,
                values = values,
                contributions = rawContrib.mapValues { it.value / denom },
                customValues = customValues,
                customContributions = customContrib.mapValues { it.value / denom },
                customNames = config.customFactors.associate { it.factor.id to it.factor.name },
            )
        }
        return result.sortedByDescending { it.score }
    }
}

/**
 * 需要截面均值的因子：球队硬币、隔夜距离。
 * 这些因子不能逐股独立算，必须先在每个交易日做全市场截面统计。
 */
object CrossSectionalEngine {

    private const val W = 20

    private fun crossMean(a: DoubleArray): Double {
        val v = a.filter { !it.isNaN() }
        return if (v.isEmpty()) Double.NaN else v.average()
    }

    private fun zscore(a: DoubleArray): DoubleArray {
        val v = a.filter { !it.isNaN() }
        val out = DoubleArray(a.size) { Double.NaN }
        if (v.size < 5) return out
        val mean = v.average()
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / (v.size - 1).coerceAtLeast(1))
        if (sd < 1e-9) return out
        for (k in a.indices) {
            if (!a[k].isNaN()) out[k] = ((a[k] - mean) / sd).coerceIn(-3.0, 3.0)
        }
        return out
    }

    private fun retAt(close: DoubleArray, t: Int): Double {
        if (t <= 0) return Double.NaN
        val a = close[t - 1]
        val b = close[t]
        return if (a.isNaN() || b.isNaN() || a <= 0) Double.NaN else b / a - 1.0
    }

    fun values(panel: Panel, i: Int, key: FactorKey): DoubleArray? {
        if (i < W + 2) return null
        val cacheKey = i to key
        panel.crossCache[cacheKey]?.let { return it }
        val computed = computeValues(panel, i, key) ?: return null
        panel.crossCache[cacheKey] = computed
        return computed
    }

    private fun computeValues(panel: Panel, i: Int, key: FactorKey): DoubleArray? {
        return when (key) {
            FactorKey.TEAM_COIN -> teamCoin(panel, i)
            FactorKey.OVERNIGHT_DIST -> overnightDistance(panel, i)
            else -> null
        }
    }

    private fun overnightDistance(panel: Panel, i: Int): DoubleArray {
        val n = panel.stocks.size
        val meanOn = DoubleArray(W)
        for (k in 0 until W) {
            val t = i - W + 1 + k
            val arr = DoubleArray(n) { si ->
                val o = panel.open[si][t]
                val pc = if (t > 0) panel.close[si][t - 1] else Double.NaN
                if (o.isNaN() || pc.isNaN() || pc <= 0) Double.NaN else o / pc - 1.0
            }
            meanOn[k] = crossMean(arr)
        }
        val out = DoubleArray(n) { Double.NaN }
        for (si in 0 until n) {
            var sum = 0.0
            var c = 0
            for (k in 0 until W) {
                val t = i - W + 1 + k
                val o = panel.open[si][t]
                val pc = if (t > 0) panel.close[si][t - 1] else Double.NaN
                if (o.isNaN() || pc.isNaN() || pc <= 0 || meanOn[k].isNaN()) continue
                sum += abs(o / pc - 1.0 - meanOn[k])
                c++
            }
            if (c >= 15) out[si] = sum / c
        }
        return out
    }

    private fun teamCoin(panel: Panel, i: Int): DoubleArray {
        val n = panel.stocks.size

        // 每个交易日的截面均值：隔夜收益、换手率变化、换手距离
        val meanOn = DoubleArray(W)
        val meanDTurn = DoubleArray(W)
        val meanTurnDist = DoubleArray(W)
        for (k in 0 until W) {
            val t = i - W + 1 + k
            val on = DoubleArray(n) { si ->
                val o = panel.open[si][t]
                val pc = if (t > 0) panel.close[si][t - 1] else Double.NaN
                if (o.isNaN() || pc.isNaN() || pc <= 0) Double.NaN else o / pc - 1.0
            }
            meanOn[k] = crossMean(on)
            val dt = DoubleArray(n) { si ->
                if (t <= 0) Double.NaN else {
                    val a = panel.turnover[si][t - 1]
                    val b = panel.turnover[si][t]
                    if (a.isNaN() || b.isNaN()) Double.NaN else b - a
                }
            }
            meanDTurn[k] = crossMean(dt)
            val td = DoubleArray(n) { si ->
                if (dt[si].isNaN() || meanDTurn[k].isNaN()) Double.NaN else abs(dt[si] - meanDTurn[k])
            }
            meanTurnDist[k] = crossMean(td)
        }

        // 各股票的 6 个原始分量
        val flipInterVol = DoubleArray(n) { Double.NaN }
        val flipInterTurn = DoubleArray(n) { Double.NaN }
        val flipIntraVol = DoubleArray(n) { Double.NaN }
        val flipIntraTurn = DoubleArray(n) { Double.NaN }
        val flipOnVol = DoubleArray(n) { Double.NaN }
        val flipOnTurn = DoubleArray(n) { Double.NaN }

        val volInterArr = DoubleArray(n) { Double.NaN }
        val volIntraArr = DoubleArray(n) { Double.NaN }
        val volOnArr = DoubleArray(n) { Double.NaN }

        val interSeries = Array(n) { DoubleArray(W) { Double.NaN } }
        val intraSeries = Array(n) { DoubleArray(W) { Double.NaN } }
        val onDistSeries = Array(n) { DoubleArray(W) { Double.NaN } }
        val dtSeries = Array(n) { DoubleArray(W) { Double.NaN } }

        for (si in 0 until n) {
            val close = panel.close[si]
            val open = panel.open[si]
            val turn = panel.turnover[si]
            for (k in 0 until W) {
                val t = i - W + 1 + k
                val pc = if (t > 0) close[t - 1] else Double.NaN
                val c = close[t]
                val o = open[t]
                val tv = turn[t]
                val tp = if (t > 0) turn[t - 1] else Double.NaN
                if (!pc.isNaN() && !c.isNaN() && pc > 0) {
                    interSeries[si][k] = c / pc - 1.0
                }
                if (!o.isNaN() && !c.isNaN() && o > 0) {
                    intraSeries[si][k] = c / o - 1.0
                }
                if (!o.isNaN() && !pc.isNaN() && pc > 0 && !meanOn[k].isNaN()) {
                    onDistSeries[si][k] = abs(o / pc - 1.0 - meanOn[k])
                }
                if (!tv.isNaN() && !tp.isNaN()) {
                    dtSeries[si][k] = tv - tp
                }
            }
            volInterArr[si] = std(interSeries[si]) ?: Double.NaN
            volIntraArr[si] = std(intraSeries[si]) ?: Double.NaN
            volOnArr[si] = std(onDistSeries[si]) ?: Double.NaN
        }

        val meanVolInter = crossMean(volInterArr)
        val meanVolIntra = crossMean(volIntraArr)
        val meanVolOn = crossMean(volOnArr)

        for (si in 0 until n) {
            val inter = interSeries[si].filter { !it.isNaN() }
            val intra = intraSeries[si].filter { !it.isNaN() }
            val onDist = onDistSeries[si].filter { !it.isNaN() }
            if (inter.size < 15 || intra.size < 15 || onDist.size < 15) continue

            val mInter = inter.average()
            val mIntra = intra.average()
            val mOn = onDist.average()

            flipInterVol[si] = if (!meanVolInter.isNaN() && volInterArr[si] < meanVolInter) -mInter else mInter
            flipIntraVol[si] = if (!meanVolIntra.isNaN() && volIntraArr[si] < meanVolIntra) -mIntra else mIntra
            flipOnVol[si] = if (!meanVolOn.isNaN() && volOnArr[si] < meanVolOn) -mOn else mOn

            var sumInterTurn = 0.0
            var sumIntraTurn = 0.0
            var sumOnTurn = 0.0
            var countInterTurn = 0
            var countIntraTurn = 0
            var countOnTurn = 0
            for (k in 1 until W) {
                val dt = dtSeries[si][k]
                if (!dt.isNaN() && !meanDTurn[k].isNaN()) {
                    val coin = dt < meanDTurn[k]
                    if (!interSeries[si][k].isNaN()) {
                        sumInterTurn += if (coin) -interSeries[si][k] else interSeries[si][k]
                        countInterTurn++
                    }
                    if (!intraSeries[si][k].isNaN()) {
                        sumIntraTurn += if (coin) -intraSeries[si][k] else intraSeries[si][k]
                        countIntraTurn++
                    }
                }
                val prev = k - 1
                if (!dtSeries[si][prev].isNaN() && !meanDTurn[prev].isNaN() && !meanTurnDist[prev].isNaN()) {
                    val dist = abs(dtSeries[si][prev] - meanDTurn[prev])
                    val coin = dist < meanTurnDist[prev]
                    if (!onDistSeries[si][k].isNaN()) {
                        sumOnTurn += if (coin) -onDistSeries[si][k] else onDistSeries[si][k]
                        countOnTurn++
                    }
                }
            }
            if (countInterTurn >= 10) flipInterTurn[si] = sumInterTurn / countInterTurn
            if (countIntraTurn >= 10) flipIntraTurn[si] = sumIntraTurn / countIntraTurn
            if (countOnTurn >= 10) flipOnTurn[si] = sumOnTurn / countOnTurn
        }

        val zInterVol = zscore(flipInterVol)
        val zInterTurn = zscore(flipInterTurn)
        val zIntraVol = zscore(flipIntraVol)
        val zIntraTurn = zscore(flipIntraTurn)
        val zOnVol = zscore(flipOnVol)
        val zOnTurn = zscore(flipOnTurn)

        val revisedInter = DoubleArray(n) { si ->
            if (zInterVol[si].isNaN() || zInterTurn[si].isNaN()) Double.NaN else zInterVol[si] + zInterTurn[si]
        }
        val revisedIntra = DoubleArray(n) { si ->
            if (zIntraVol[si].isNaN() || zIntraTurn[si].isNaN()) Double.NaN else zIntraVol[si] + zIntraTurn[si]
        }
        val revisedOn = DoubleArray(n) { si ->
            if (zOnVol[si].isNaN() || zOnTurn[si].isNaN()) Double.NaN else zOnVol[si] + zOnTurn[si]
        }
        val zRevisedInter = zscore(revisedInter)
        val zRevisedIntra = zscore(revisedIntra)
        val zRevisedOn = zscore(revisedOn)

        return DoubleArray(n) { si ->
            if (zRevisedInter[si].isNaN() || zRevisedIntra[si].isNaN() || zRevisedOn[si].isNaN()) {
                Double.NaN
            } else {
                (zRevisedInter[si] + zRevisedIntra[si] + zRevisedOn[si]) / 3.0
            }
        }
    }

    private fun std(a: DoubleArray): Double? {
        val v = a.filter { !it.isNaN() }
        if (v.size < 3) return null
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }
}

object ScreenerFunnel {
    fun build(panel: Panel, config: StrategyConfig, scoredCount: Int): List<FunnelStep> {
        val i = panel.lastIndex
        if (i < 0) return emptyList()
        var remaining = panel.stocks.indices.toList()
        val steps = ArrayList<FunnelStep>(9)
        steps += FunnelStep("全池", remaining.size, 0)

        fun groupOf(reason: FilterReason): String? = when (reason) {
            FilterReason.BOARD -> "板块/资产"
            FilterReason.PRICE, FilterReason.AMOUNT -> "价格/流动性"
            FilterReason.ST, FilterReason.SUSPENDED -> "ST/停牌"
            FilterReason.OWNER -> "企业性质"
            FilterReason.LISTED -> "上市天数"
            FilterReason.PE, FilterReason.PB, FilterReason.CAP -> "估值/市值"
            FilterReason.PROFILE -> "资料完整"
        }

        listOf("板块/资产", "价格/流动性", "ST/停牌", "企业性质", "上市天数", "估值/市值", "资料完整").forEach { group ->
            val before = remaining.size
            val reasons = LinkedHashMap<String, Int>()
            remaining = remaining.filter { si ->
                val r = FactorEngine.filterReason(panel, si, i, config.filters)
                if (r != null && groupOf(r) == group) {
                    reasons[r.label] = (reasons[r.label] ?: 0) + 1
                    false
                } else {
                    true
                }
            }
            if (before != remaining.size) {
                steps += FunnelStep(group, remaining.size, before - remaining.size, reasons)
            }
        }

        val beforeScore = remaining.size
        steps += FunnelStep(
            "因子数据完整",
            scoredCount,
            (beforeScore - scoredCount).coerceAtLeast(0),
            if (beforeScore > scoredCount) mapOf("因子覆盖不足" to (beforeScore - scoredCount)) else emptyMap(),
        )
        return steps
    }
}

object ScreenerEngine {
    fun screen(panel: Panel, config: StrategyConfig): ScreenerResult {
        val i = panel.lastIndex
        if (i < 0) return ScreenerResult(emptyList(), 0, panel.stocks.size)
        val scored = FactorEngine.scoreAt(panel, i, config)
        val rows = scored.take(config.maxPositions.coerceAtLeast(1)).map { s ->
            val stock = panel.stocks[s.stockIndex]
            val price = panel.close[s.stockIndex][i].takeIf { !it.isNaN() } ?: stock.price
            val prev = if (i > 0) panel.close[s.stockIndex][i - 1] else Double.NaN
            val chg = if (!prev.isNaN() && prev > 0) (price / prev - 1.0) * 100.0 else stock.changePct
            Pick(
                code = stock.code,
                name = stock.name,
                price = price,
                changePct = chg,
                score = s.score,
                factors = s.values,
                board = stock.board,
                isSt = stock.isSt,
                contributions = s.contributions,
                customValues = s.customValues,
                customContributions = s.customContributions,
                customNames = s.customNames,
            )
        }
        val funnel = ScreenerFunnel.build(panel, config, scored.size) + FunnelStep(
            label = "最终命中",
            remaining = rows.size,
            dropped = (scored.size - rows.size).coerceAtLeast(0),
            reasons = if (scored.size > rows.size) mapOf("TopK 截断" to (scored.size - rows.size)) else emptyMap(),
        )
        return ScreenerResult(rows, scored.size, panel.stocks.size, funnel = funnel)
    }
}
