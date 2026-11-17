package com.shigu.core

import kotlin.math.sqrt

data class CombinedHolding(
    val code: String,
    val name: String,
    val marketValue: Double,
    val weight: Double,
    val strategyCount: Int,
)

data class StrategyCorrelation(
    val aId: String,
    val aName: String,
    val bId: String,
    val bName: String,
    val corr: Double,
)

data class PortfolioSummary(
    val totalCapital: Double,
    val equity: Double,
    val cash: Double,
    val returnPct: Double,
    val holdings: List<CombinedHolding>,
    val overlap: List<CombinedHolding>,
    val topWeight: Double,
    val correlations: List<StrategyCorrelation>,
)

/**
 * 组合层：把每个策略当作 100 万独立 sleeve，合并持仓/现金/相关性。
 * 不改变单策略纸面账本，只是给模拟页一个总览。
 */
object PortfolioAggregator {

    fun build(
        strategies: List<StrategyConfig>,
        since: Map<String, SinceCreationResult>,
    ): PortfolioSummary? {
        val active = strategies.filter { since[it.id] != null }
        if (active.isEmpty()) return null
        val totalCapital = 1_000_000.0 * active.size
        val equity = active.sumOf { 1_000_000.0 * (1.0 + (since[it.id]?.returnPct ?: 0.0)) }
        val cash = active.sumOf { since[it.id]?.portfolio?.cash ?: 0.0 }

        val byCode = LinkedHashMap<String, Triple<String, Double, Int>>()
        active.forEach { config ->
            val portfolio = since[config.id]?.portfolio ?: return@forEach
            portfolio.holdings.forEach { h ->
                val value = h.shares * h.lastPrice
                val prev = byCode[h.code]
                byCode[h.code] = if (prev == null) {
                    Triple(h.name, value, 1)
                } else {
                    Triple(prev.first, prev.second + value, prev.third + 1)
                }
            }
        }
        val holdings = byCode.map { (code, v) ->
            CombinedHolding(
                code = code,
                name = v.first,
                marketValue = v.second,
                weight = if (equity > 0) v.second / equity else 0.0,
                strategyCount = v.third,
            )
        }.sortedByDescending { it.marketValue }

        val correlations = ArrayList<StrategyCorrelation>()
        for (a in active.indices) {
            for (b in a + 1 until active.size) {
                val ca = active[a]
                val cb = active[b]
                val corr = correlation(
                    since[ca.id]?.equity.orEmpty(),
                    since[cb.id]?.equity.orEmpty(),
                )
                if (!corr.isNaN()) {
                    correlations += StrategyCorrelation(ca.id, ca.name, cb.id, cb.name, corr)
                }
            }
        }

        return PortfolioSummary(
            totalCapital = totalCapital,
            equity = equity,
            cash = cash,
            returnPct = if (totalCapital > 0) equity / totalCapital - 1.0 else 0.0,
            holdings = holdings,
            overlap = holdings.filter { it.strategyCount > 1 },
            topWeight = holdings.firstOrNull()?.weight ?: 0.0,
            correlations = correlations.sortedByDescending { kotlin.math.abs(it.corr) },
        )
    }

    private fun correlation(a: List<EquityPoint>, b: List<EquityPoint>): Double {
        val ra = dailyReturns(a)
        val rb = dailyReturns(b)
        val common = ra.keys.intersect(rb.keys).sorted()
        if (common.size < 10) return Double.NaN
        val xs = common.map { ra[it]!! }
        val ys = common.map { rb[it]!! }
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
        if (vx <= 1e-12 || vy <= 1e-12) return Double.NaN
        return cov / sqrt(vx * vy)
    }

    private fun dailyReturns(points: List<EquityPoint>): Map<String, Double> {
        val out = HashMap<String, Double>(points.size)
        points.zipWithNext { prev, next ->
            if (prev.strategy > 1e-9) {
                out[next.date] = next.strategy / prev.strategy - 1.0
            }
        }
        return out
    }
}
