package com.shigu.core

import kotlin.math.sqrt

/**
 * 单因子体检：Rank IC、ICIR、正 IC 比例、Top/Bottom 十分位多空。
 * 只用本地日线；快照类因子（财务/企业性质）在历史区间是常数，结果只作参考。
 */
object FactorAnalytics {

    data class CorrelationPair(
        val a: FactorKey,
        val b: FactorKey,
        val corr: Double,
        val samples: Int,
    )

    fun correlations(
        panel: Panel,
        keys: List<FactorKey> = FactorKey.entries,
        minSamples: Int = 30,
    ): List<CorrelationPair> {
        val i = panel.lastIndex
        if (i < 0) return emptyList()
        val arrays = keys.associateWith { key ->
            DoubleArray(panel.stocks.size) { si ->
                FactorEngine.valueAt(panel, si, i, key) ?: Double.NaN
            }
        }
        val out = ArrayList<CorrelationPair>()
        for (a in keys.indices) {
            for (b in a + 1 until keys.size) {
                val ka = keys[a]
                val kb = keys[b]
                val xa = arrays[ka] ?: continue
                val xb = arrays[kb] ?: continue
                var n = 0
                var sx = 0.0
                var sy = 0.0
                var sxx = 0.0
                var syy = 0.0
                var sxy = 0.0
                for (k in xa.indices) {
                    val x = xa[k]
                    val y = xb[k]
                    if (x.isNaN() || y.isNaN()) continue
                    n++
                    sx += x
                    sy += y
                    sxx += x * x
                    syy += y * y
                    sxy += x * y
                }
                if (n < minSamples) continue
                val cov = sxy - sx * sy / n
                val vx = sxx - sx * sx / n
                val vy = syy - sy * sy / n
                if (vx <= 1e-12 || vy <= 1e-12) continue
                out += CorrelationPair(ka, kb, cov / sqrt(vx * vy), n)
            }
        }
        return out.sortedByDescending { kotlin.math.abs(it.corr) }
    }

    fun analyze(
        panel: Panel,
        key: FactorKey,
        forwardDays: Int = 5,
        sampleStep: Int = 5,
        maxSamples: Int = 120,
    ): FactorStats? {
        val last = panel.lastIndex
        if (last < 100) return null
        val first = (last - maxSamples * sampleStep).coerceAtLeast(80)
        if (first >= last - forwardDays) return null

        val ics = ArrayList<Double>(maxSamples)
        var coverageSum = 0.0
        var spreadSum = 0.0
        var samples = 0

        var i = first
        while (i <= last - forwardDays) {
            val values = DoubleArray(panel.stocks.size) { si ->
                FactorEngine.valueAt(panel, si, i, key) ?: Double.NaN
            }
            val pairs = ArrayList<Pair<Double, Double>>(panel.stocks.size)
            for (si in panel.stocks.indices) {
                val v = values[si]
                if (v.isNaN()) continue
                val a = panel.close[si][i]
                val b = panel.close[si][i + forwardDays]
                if (a.isNaN() || b.isNaN() || a <= 0) continue
                pairs += v to (b / a - 1.0)
            }
            if (pairs.size >= 20) {
                val ic = spearman(pairs)
                if (!ic.isNaN()) {
                    ics += ic
                    val sorted = pairs.sortedBy { it.first }
                    val cut = (sorted.size / 10).coerceAtLeast(1)
                    val bottom = sorted.take(cut).map { it.second }.average()
                    val top = sorted.takeLast(cut).map { it.second }.average()
                    spreadSum += top - bottom
                    coverageSum += pairs.size.toDouble() / panel.stocks.size
                    samples++
                }
            }
            i += sampleStep
        }

        if (samples < 5) return null
        val mean = ics.average()
        val variance = ics.sumOf { (it - mean) * (it - mean) } / (ics.size - 1).coerceAtLeast(1)
        val sd = sqrt(variance)
        val icIr = if (sd > 1e-9) mean / sd * sqrt(252.0 / sampleStep) else 0.0
        return FactorStats(
            key = key,
            coverage = coverageSum / samples,
            meanIc = mean,
            icIr = icIr,
            positiveRate = ics.count { it > 0 }.toDouble() / ics.size,
            decileSpread = spreadSum / samples,
            sampleDays = samples,
            forwardDays = forwardDays,
        )
    }

    private fun spearman(pairs: List<Pair<Double, Double>>): Double {
        val x = rank(pairs.map { it.first })
        val y = rank(pairs.map { it.second })
        val mx = x.average()
        val my = y.average()
        var cov = 0.0
        var vx = 0.0
        var vy = 0.0
        for (k in x.indices) {
            val dx = x[k] - mx
            val dy = y[k] - my
            cov += dx * dy
            vx += dx * dx
            vy += dy * dy
        }
        if (vx <= 1e-12 || vy <= 1e-12) return Double.NaN
        return cov / sqrt(vx * vy)
    }

    /** 平均秩，处理并列值。 */
    private fun rank(values: List<Double>): DoubleArray {
        val order = values.withIndex().sortedBy { it.value }
        val out = DoubleArray(values.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && order[j + 1].value == order[i].value) j++
            val avg = (i + j) / 2.0
            for (k in i..j) out[order[k].index] = avg
            i = j + 1
        }
        return out
    }
}
