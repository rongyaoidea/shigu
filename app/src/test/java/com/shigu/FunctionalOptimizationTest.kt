package com.shigu

import com.shigu.core.Board
import com.shigu.core.EquityPoint
import com.shigu.core.Holding
import com.shigu.core.Pick
import com.shigu.core.PortfolioAggregator
import com.shigu.core.PortfolioSnapshot
import com.shigu.core.RebalanceEstimator
import com.shigu.core.SinceCreationResult
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionalOptimizationTest {

    private fun config() = StrategyConfig(
        id = "t",
        name = "测试",
        subtitle = "",
        evidence = "",
        caveat = "",
        factors = emptyList(),
        filters = StrategyFilters(),
        maxPositions = 2,
        universeSize = 10,
    )

    private fun pick(code: String, name: String, price: Double, board: Board = Board.SH_MAIN) =
        Pick(code, name, price, 0.0, 1.0, emptyMap(), board, false)

    @Test
    fun `调仓预览会卖出跌出目标的持仓并买入新目标`() {
        val since = SinceCreationResult(
            returnPct = 0.0,
            benchmarkPct = 0.0,
            startDate = "2026-01-01",
            endDate = "2026-01-10",
            points = 10,
            picks = listOf(pick("600000", "新目标", 10.0), pick("600002", "第二目标", 20.0)),
            portfolio = PortfolioSnapshot(
                cash = 0.0,
                equity = 1_000_000.0,
                holdings = listOf(Holding("600001", "旧持仓", 1000, 10.0, 10.0, 0.5, "2026-01-01")),
            ),
            equity = listOf(EquityPoint("2026-01-10", 1.0, 1.0)),
        )
        val plan = RebalanceEstimator.estimate(config(), since)
        assertTrue(plan.orders.any { it.code == "600001" && it.side == "卖出" })
        assertTrue(plan.orders.any { it.code == "600000" && it.side == "买入" })
        assertTrue(plan.estimatedFees > 0.0)
        assertEquals(2, plan.targetCount)
    }

    @Test
    fun `组合层合并持仓并识别重叠`() {
        val a = SinceCreationResult(
            returnPct = 0.10,
            benchmarkPct = 0.0,
            startDate = "2026-01-01",
            endDate = "2026-01-10",
            points = 10,
            picks = emptyList(),
            portfolio = PortfolioSnapshot(
                cash = 100_000.0,
                equity = 1_100_000.0,
                holdings = listOf(
                    Holding("600000", "共同", 1000, 10.0, 12.0, 0.5, "2026-01-01"),
                    Holding("600001", "A独有", 1000, 10.0, 10.0, 0.5, "2026-01-01"),
                ),
            ),
            equity = (1..20).map { EquityPoint("2026-01-${it.toString().padStart(2, '0')}", 1.0 + it * 0.005, 1.0) },
        )
        val b = a.copy(
            returnPct = 0.20,
            portfolio = PortfolioSnapshot(
                cash = 200_000.0,
                equity = 1_200_000.0,
                holdings = listOf(
                    Holding("600000", "共同", 1000, 10.0, 13.0, 0.5, "2026-01-01"),
                    Holding("600002", "B独有", 1000, 10.0, 10.0, 0.5, "2026-01-01"),
                ),
            ),
        )
        val summary = PortfolioAggregator.build(
            listOf(config().copy(id = "a", name = "A"), config().copy(id = "b", name = "B")),
            mapOf("a" to a, "b" to b),
        )!!
        assertEquals(2_300_000.0, summary.equity, 1e-6)
        assertEquals(1, summary.overlap.size)
        assertEquals("600000", summary.overlap.first().code)
        assertTrue(summary.correlations.isNotEmpty())
    }

    @Test
    fun `调仓预览对科创板按200股整手`() {
        val since = SinceCreationResult(
            returnPct = 0.0,
            benchmarkPct = 0.0,
            startDate = "2026-01-01",
            endDate = "2026-01-10",
            points = 10,
            picks = listOf(pick("688001", "科创", 10.0, Board.STAR)),
            portfolio = PortfolioSnapshot(1_000_000.0, 1_000_000.0, emptyList()),
            equity = emptyList(),
        )
        val plan = RebalanceEstimator.estimate(config().copy(maxPositions = 1), since)
        val buy = plan.orders.first { it.side == "买入" }
        assertEquals(0L, buy.shares % 200L)
    }
}
