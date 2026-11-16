package com.shigu

import com.shigu.core.BacktestEngine
import com.shigu.core.Board
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.Panel
import com.shigu.core.Stock
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class BacktestEngineTest {

    @Test
    fun `回测会产生交易与持仓且现金不为负`() {
        val days = 220
        val n = 6
        val calendar = (0 until days).map { "d%03d".format(it) }
        val stocks = (0 until n).map { i ->
            Stock(
                code = "60000$i",
                name = "测试$i",
                secid = "sh60000$i",
                board = Board.SH_MAIN,
                marketCap = 10_000_000_000.0,
            )
        }
        val close = Array(n) { i ->
            DoubleArray(days) { d ->
                10.0 + i + d * (0.02 + i * 0.002) + sin((d + i) / 5.0) * 0.3
            }
        }
        val open = Array(n) { i -> DoubleArray(days) { d -> close[i][d] - 0.05 } }
        val high = Array(n) { i -> DoubleArray(days) { d -> close[i][d] + 0.2 } }
        val low = Array(n) { i -> DoubleArray(days) { d -> close[i][d] - 0.2 } }
        val volume = Array(n) { DoubleArray(days) { 100_000.0 } }
        val amount = Array(n) { DoubleArray(days) { 1_000_000_000.0 } }
        val turnover = Array(n) { DoubleArray(days) { 1.0 } }
        val pe = Array(n) { DoubleArray(days) { 10.0 } }
        val pb = Array(n) { DoubleArray(days) { 1.0 } }
        val cap = Array(n) { DoubleArray(days) { 10_000_000_000.0 } }
        val bench = DoubleArray(days) { 3000.0 + it }
        val panel = Panel(calendar, stocks, open, close, high, low, volume, amount, turnover, pe, pb, cap, bench)

        val config = StrategyConfig(
            id = "test",
            name = "测试策略",
            subtitle = "",
            evidence = "",
            caveat = "",
            factors = listOf(FactorWeight(FactorKey.RET20, 1.0)),
            filters = StrategyFilters(minAmount20 = 0.0, boards = setOf(Board.SH_MAIN)),
            rebalanceDays = 5,
            maxPositions = 3,
            lookbackDays = 80,
            universeSize = n,
        )
        val result = BacktestEngine.run(panel, config)
        assertTrue("净值点不足", result.equity.size > 50)
        assertTrue("没有交易", result.trades.isNotEmpty())
        assertTrue("没有持仓", result.portfolio?.holdings?.isNotEmpty() == true)
        assertTrue("现金为负", (result.portfolio?.cash ?: 0.0) >= 0.0)
        assertTrue("净值异常", result.equity.last().strategy > 0.0)
    }
}
