package com.shigu.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

data class OptionBacktestTrade(
    val entryDate: String,
    val exitDate: String,
    val type: OptionType,
    val strike: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val lots: Long,
    val pnl: Double,
    val pnlPct: Double,
    val holdDays: Int,
    val reason: String,
)

data class OptionBacktestStats(
    val underlying: String,
    val presetId: String,
    val presetName: String,
    val startDate: String,
    val endDate: String,
    val optionReturn: Double,
    val etfReturn: Double,
    val csiReturn: Double,
    val annualized: Double,
    val maxDrawdown: Double,
    val sharpe: Double,
    val volatility: Double,
    val trades: Int,
    val winRate: Double,
    val avgHoldDays: Double,
    val ivMultiplier: Double,
) {
    val excessEtf: Double get() = optionReturn - etfReturn
    val excessCsi: Double get() = optionReturn - csiReturn
    val beatBoth: Boolean get() = optionReturn > etfReturn && optionReturn > csiReturn
}

data class OptionBacktestResult(
    val stats: OptionBacktestStats,
    val equity: List<EquityPoint>,
    val trades: List<OptionBacktestTrade>,
    val warning: String,
)

/**
 * 期权历史回测：方向信号来自 ETF 日线，期权价格用 Black-Scholes 理论定价。
 * 没有历史期权盘口，因此用 20 日历史波动率 * IV 倍数近似；结果用于比较方向策略，
 * 不是实际期权成交价，也不模拟 IV 曲面、盘口冲击和实物交割。
 */
object OptionBacktestEngine {
    const val DEFAULT_START = "2021-11-17"
    private const val INITIAL_CAPITAL = 1_000_000.0
    private const val RATE = 0.02
    private const val DIVIDEND = 0.0
    private const val MULTIPLIER = 10_000.0
    private const val MIN_DAYS = 10
    private const val ROLL_DAYS = 3

    private data class Leg(
        val lots: Long,
        val strike: Double,
        val type: OptionType,
        val dir: Int,
        val expiry: LocalDate,
        val entryPrice: Double,
        val entryDate: String,
        val entryFee: Double,
    )

    fun run(
        panel: Panel,
        preset: OptionSignalPreset,
        underlying: String,
        strikeOffset: Int = 0,
        ivMultiplier: Double = 1.05,
        startDate: String = DEFAULT_START,
        initialCapital: Double = INITIAL_CAPITAL,
    ): OptionBacktestResult {
        val calendar = panel.calendar
        val start = calendar.indexOfFirst { it >= startDate }.let { if (it < 0) 0 else it }
        val end = calendar.lastIndex
        if (panel.stocks.isEmpty() || end <= start) {
            return emptyResult(panel, preset, underlying, startDate, ivMultiplier, initialCapital)
        }
        val dates = calendar.map { runCatching { LocalDate.parse(it) }.getOrNull() }
        val close = panel.close[0]
        val open = panel.open[0]
        val volume = panel.volume[0]
        val vol = realizedVol(close, 20)
        val signals = OptionSignalEngine.series(panel, preset)
        val grid = strikeGrid(underlying)
        val benchmarkBase = panel.benchmark[start].takeIf { !it.isNaN() && it > 0 } ?: close[start]
        val etfBase = close[start].takeIf { !it.isNaN() && it > 0 } ?: 1.0

        var cash = initialCapital
        var pos: Leg? = null
        val equity = ArrayList<EquityPoint>(end - start + 1)
        val trades = ArrayList<OptionBacktestTrade>()

        fun closeLeg(i: Int, price: Double, reason: String) {
            val leg = pos ?: return
            val value = price * MULTIPLIER * leg.lots
            val fee = tradeFee(leg.lots, price)
            cash += value - fee
            val pnl = (price - leg.entryPrice) * MULTIPLIER * leg.lots - leg.entryFee - fee
            trades += OptionBacktestTrade(
                entryDate = leg.entryDate,
                exitDate = calendar[i],
                type = leg.type,
                strike = leg.strike,
                entryPrice = leg.entryPrice,
                exitPrice = price,
                lots = leg.lots,
                pnl = pnl,
                pnlPct = if (leg.entryPrice > 0) price / leg.entryPrice - 1.0 else 0.0,
                holdDays = (dates[i]?.toEpochDay() ?: 0L).let { d ->
                    val e = runCatching { LocalDate.parse(leg.entryDate).toEpochDay() }.getOrDefault(d)
                    (d - e).toInt().coerceAtLeast(0)
                },
                reason = reason,
            )
            pos = null
        }

        fun openLeg(i: Int, desired: Int) {
            val today = dates[i] ?: return
            val expiry = nextExpiry(today, MIN_DAYS) ?: return
            val days = expiry.toEpochDay() - today.toEpochDay()
            if (days <= 0) return
            val s = open[i]
            if (s.isNaN() || s <= 0) return
            val k = round(s / grid) * grid + strikeOffset * grid
            if (k <= 0) return
            val type = if (desired > 0) OptionType.CALL else OptionType.PUT
            val v = (vol[if (i > 0) i - 1 else i] * ivMultiplier).coerceIn(0.08, 0.8)
            val premium = bs(s, k, days / 365.0, RATE, DIVIDEND, v, type)
            if (premium <= 0) return
            val delta = abs(bsDelta(s, k, days / 365.0, RATE, DIVIDEND, v, type))
            var lots = (cash / (max(delta, 0.05) * s * MULTIPLIER)).toLong()
            lots = min(lots, (cash / (premium * MULTIPLIER)).toLong())
            if (lots <= 0) return
            val cost = premium * MULTIPLIER * lots
            val fee = tradeFee(lots, premium)
            if (cost + fee > cash) return
            cash -= cost + fee
            pos = Leg(
                lots = lots,
                strike = k,
                type = type,
                dir = desired,
                expiry = expiry,
                entryPrice = premium,
                entryDate = calendar[i],
                entryFee = fee,
            )
        }

        for (i in start..end) {
            val today = dates[i]
            if (i > start && today != null) {
                val desired = signals[i - 1]
                val leg = pos
                if (leg != null) {
                    val d = leg.expiry.toEpochDay() - today.toEpochDay()
                    if (d < 0) {
                        closeLeg(i, intrinsicPrice(leg, open[i]), "到期")
                    } else if (desired == 0 || desired != leg.dir || d <= ROLL_DAYS) {
                        val v = (vol[if (i > 0) i - 1 else i] * ivMultiplier).coerceIn(0.08, 0.8)
                        closeLeg(i, bs(open[i], leg.strike, d / 365.0, RATE, DIVIDEND, v, leg.type), "调仓")
                    }
                }
                if (pos == null && desired != 0) openLeg(i, desired)
            }

            var value = 0.0
            val leg = pos
            if (leg != null && today != null) {
                val d = leg.expiry.toEpochDay() - today.toEpochDay()
                value = if (d <= 0) {
                    intrinsic(leg, close[i])
                } else {
                    val v = (vol[i] * ivMultiplier).coerceIn(0.08, 0.8)
                    bs(close[i], leg.strike, d / 365.0, RATE, DIVIDEND, v, leg.type) * MULTIPLIER * leg.lots
                }
            }
            equity += EquityPoint(
                date = calendar[i],
                strategy = (cash + value) / initialCapital,
                benchmark = if (panel.benchmark[i].isNaN() || benchmarkBase <= 0) close[i] / etfBase else panel.benchmark[i] / benchmarkBase,
            )
            if (leg != null && today != null && today.toEpochDay() >= leg.expiry.toEpochDay()) {
                closeLeg(i, intrinsic(leg, close[i]), "到期")
            }
        }
        if (pos != null && end >= 0) {
            val leg = pos!!
            closeLeg(end, intrinsic(leg, close[end]), "结束")
        }

        val csiBase = panel.benchmark[start].takeIf { !it.isNaN() && it > 0 }
        val csiReturn = if (csiBase != null && !panel.benchmark[end].isNaN()) panel.benchmark[end] / csiBase - 1.0 else 0.0
        val stats = stats(
            underlying = underlying,
            preset = preset,
            equity = equity,
            trades = trades,
            startDate = calendar[start],
            endDate = calendar[end],
            etfReturn = if (close[start] > 0) close[end] / close[start] - 1.0 else 0.0,
            csiReturn = csiReturn,
            ivMultiplier = ivMultiplier,
        )
        return OptionBacktestResult(
            stats = stats,
            equity = equity,
            trades = trades,
            warning = "期权价格用 Black-Scholes + 20 日历史波动率模拟，不是历史盘口成交价；用于比较方向策略，不构成投资建议。",
        )
    }

    private fun stats(
        underlying: String,
        preset: OptionSignalPreset,
        equity: List<EquityPoint>,
        trades: List<OptionBacktestTrade>,
        startDate: String,
        endDate: String,
        etfReturn: Double,
        csiReturn: Double,
        ivMultiplier: Double,
    ): OptionBacktestStats {
        val total = if (equity.isEmpty()) 0.0 else equity.last().strategy / equity.first().strategy - 1.0
        val years = runCatching {
            (LocalDate.parse(endDate).toEpochDay() - LocalDate.parse(startDate).toEpochDay()).toDouble() / 365.25
        }.getOrDefault(1.0).coerceAtLeast(0.01)
        val rets = ArrayList<Double>(equity.size)
        for (i in 1 until equity.size) {
            val prev = equity[i - 1].strategy
            if (prev > 0) rets += equity[i].strategy / prev - 1.0
        }
        val mean = if (rets.isEmpty()) 0.0 else rets.average()
        val variance = if (rets.size < 2) 0.0 else rets.sumOf { (it - mean) * (it - mean) } / (rets.size - 1)
        val vol = sqrt(variance) * sqrt(252.0)
        val sharpe = if (vol > 1e-9) (mean * 252.0 - RATE) / vol else 0.0
        var peak = Double.NEGATIVE_INFINITY
        var mdd = 0.0
        equity.forEach { p ->
            if (p.strategy > peak) peak = p.strategy
            if (peak > 0) mdd = min(mdd, p.strategy / peak - 1.0)
        }
        val wins = trades.count { it.pnl > 0 }
        return OptionBacktestStats(
            underlying = underlying,
            presetId = preset.id,
            presetName = preset.name,
            startDate = startDate,
            endDate = endDate,
            optionReturn = total,
            etfReturn = etfReturn,
            csiReturn = csiReturn,
            annualized = if (years > 0 && total > -1) (1.0 + total).pow(1.0 / years) - 1.0 else total,
            maxDrawdown = mdd,
            sharpe = sharpe,
            volatility = vol,
            trades = trades.size,
            winRate = if (trades.isEmpty()) 0.0 else wins.toDouble() / trades.size,
            avgHoldDays = if (trades.isEmpty()) 0.0 else trades.sumOf { it.holdDays }.toDouble() / trades.size,
            ivMultiplier = ivMultiplier,
        )
    }

    private fun emptyResult(
        panel: Panel,
        preset: OptionSignalPreset,
        underlying: String,
        startDate: String,
        ivMultiplier: Double,
        initialCapital: Double,
    ): OptionBacktestResult {
        val date = panel.calendar.lastOrNull() ?: startDate
        val stats = OptionBacktestStats(
            underlying, preset.id, preset.name, startDate, date, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, ivMultiplier,
        )
        return OptionBacktestResult(stats, emptyList(), emptyList(), "日线不足，无法回测。")
    }

    private fun realizedVol(close: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(close.size)
        for (i in n..close.lastIndex) {
            var sum = 0.0
            var sum2 = 0.0
            var count = 0
            for (k in i - n + 1..i) {
                val a = close[k - 1]
                val b = close[k]
                if (a.isNaN() || b.isNaN() || a <= 0 || b <= 0) continue
                val r = ln(b / a)
                sum += r
                sum2 += r * r
                count++
            }
            if (count >= 3) {
                val mean = sum / count
                val variance = (sum2 / count - mean * mean).coerceAtLeast(0.0)
                out[i] = sqrt(variance) * sqrt(252.0)
            }
        }
        return out
    }

    private fun strikeGrid(underlying: String): Double = when (underlying) {
        "510050" -> 0.05
        "510300" -> 0.10
        else -> 0.05
    }

    private fun nextExpiry(today: LocalDate, minDays: Int): LocalDate? {
        for (k in 0..3) {
            val month = today.monthValue + k
            val year = today.year + (month - 1) / 12
            val m = (month - 1) % 12 + 1
            val exp = LocalDate.of(year, m, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.WEDNESDAY))
            if (exp.toEpochDay() - today.toEpochDay() >= minDays) return exp
        }
        return null
    }

    private fun intrinsic(leg: Leg, price: Double): Double =
        intrinsicPrice(leg.type, leg.strike, price) * MULTIPLIER * leg.lots

    private fun intrinsicPrice(leg: Leg, price: Double): Double = intrinsicPrice(leg.type, leg.strike, price)

    private fun intrinsicPrice(type: OptionType, strike: Double, price: Double): Double =
        if (price.isNaN()) 0.0 else if (type == OptionType.CALL) max(0.0, price - strike) else max(0.0, strike - price)

    private fun tradeFee(lots: Long, premium: Double): Double =
        lots * 5.0 + premium * MULTIPLIER * lots * 0.005

    private fun bs(s: Double, k: Double, t: Double, r: Double, q: Double, vol: Double, type: OptionType): Double {
        if (t <= 0) return if (type == OptionType.CALL) max(0.0, s - k) else max(0.0, k - s)
        if (s <= 0 || k <= 0 || vol <= 0) return 0.0
        val st = vol * sqrt(t)
        val d1 = (ln(s / k) + (r - q + 0.5 * vol * vol) * t) / st
        val d2 = d1 - st
        return if (type == OptionType.CALL) {
            s * exp(-q * t) * normCdf(d1) - k * exp(-r * t) * normCdf(d2)
        } else {
            k * exp(-r * t) * normCdf(-d2) - s * exp(-q * t) * normCdf(-d1)
        }
    }

    private fun bsDelta(s: Double, k: Double, t: Double, r: Double, q: Double, vol: Double, type: OptionType): Double {
        if (t <= 0) {
            return if (type == OptionType.CALL) (if (s > k) 1.0 else 0.0) else (if (s < k) -1.0 else 0.0)
        }
        if (s <= 0 || k <= 0 || vol <= 0) return 0.0
        val st = vol * sqrt(t)
        val d1 = (ln(s / k) + (r - q + 0.5 * vol * vol) * t) / st
        return exp(-q * t) * (if (type == OptionType.CALL) normCdf(d1) else normCdf(d1) - 1.0)
    }

    /** 标准正态 CDF，Abramowitz-Stegun 近似，回测精度足够。 */
    private fun normCdf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val d = 0.3989422804014327 * exp(-x * x / 2.0)
        val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0) 1.0 - p else p
    }
}
