package com.shigu

import com.shigu.core.Board
import com.shigu.core.CustomFactor
import com.shigu.core.CustomFactorEngine
import com.shigu.core.CustomFactorWeight
import com.shigu.core.FactorAnalytics
import com.shigu.core.FactorEngine
import com.shigu.core.FormulaParser
import com.shigu.core.Panel
import com.shigu.core.Stock
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFactorTest {

    private val days = 120
    private val calendar = (0 until days).map { "d%03d".format(it) }

    private fun panel(): Panel {
        val stocks = (0 until 8).map { i ->
            Stock(
                code = "60000$i",
                name = "测试$i",
                secid = "sh60000$i",
                board = Board.SH_MAIN,
                roe = 5.0 + i * 2.0,
                debtRatio = 30.0 + i,
                marketCap = 10_000_000_000.0,
            )
        }
        val close = Array(8) { i -> DoubleArray(days) { d -> 10.0 + i + d * (0.01 + i * 0.002) } }
        val open = Array(8) { i -> DoubleArray(days) { d -> close[i][d] - 0.02 } }
        val high = Array(8) { i -> DoubleArray(days) { d -> close[i][d] + 0.1 } }
        val low = Array(8) { i -> DoubleArray(days) { d -> close[i][d] - 0.1 } }
        val volume = Array(8) { DoubleArray(days) { 1_000_000.0 } }
        val amount = Array(8) { DoubleArray(days) { 500_000_000.0 } }
        val turnover = Array(8) { DoubleArray(days) { 1.0 } }
        val pe = Array(8) { DoubleArray(days) { 10.0 + it } }
        val pb = Array(8) { DoubleArray(days) { 1.0 } }
        val cap = Array(8) { DoubleArray(days) { 10_000_000_000.0 } }
        val bench = DoubleArray(days) { 3000.0 + it }
        return Panel(calendar, stocks, open, close, high, low, volume, amount, turnover, pe, pb, cap, bench)
    }

    @Test
    fun `公式支持优先级与函数`() {
        assertEquals(14.0, FormulaParser.parse("2 + 3 * 4").eval { null }!!, 1e-9)
        assertEquals(10.0, FormulaParser.parse("max(4, 10)").eval { null }!!, 1e-9)
        assertEquals(5.0, FormulaParser.parse("clip(20, 1, 5)").eval { null }!!, 1e-9)
        assertEquals(10.0, FormulaParser.parse("ROE / PE").eval { name ->
            when (name) {
                "ROE" -> 100.0
                "PE" -> 10.0
                else -> null
            }
        }!!, 1e-9)
    }

    @Test
    fun `未知变量会被校验出来`() {
        assertTrue(CustomFactorEngine.validate("ROE / NOT_A_FACTOR")!!.contains("NOT_A_FACTOR"))
        assertTrue(CustomFactorEngine.validate("ROE + ") != null)
    }

    @Test
    fun `自定义因子参与打分并产生贡献`() {
        val panel = panel()
        val custom = CustomFactor("c1", "ROE/PE", "ROE / PE", higherIsBetter = true)
        val config = StrategyConfig(
            id = "c",
            name = "自定义",
            subtitle = "",
            evidence = "",
            caveat = "",
            factors = emptyList(),
            customFactors = listOf(CustomFactorWeight(custom, 1.0)),
            filters = StrategyFilters(minPrice = 0.0, minAmount20 = 0.0),
            maxPositions = 5,
            universeSize = 8,
        )
        val scored = FactorEngine.scoreAt(panel, panel.lastIndex, config)
        assertTrue(scored.isNotEmpty())
        assertTrue(scored.first().customContributions.getValue("c1") != 0.0)
        assertTrue(scored.first().customValues.getValue("c1")!! > 0)
    }

    @Test
    fun `因子相关性返回成对结果`() {
        val panel = panel()
        val pairs = FactorAnalytics.correlations(
            panel,
            listOf(com.shigu.core.FactorKey.RET20, com.shigu.core.FactorKey.RET60, com.shigu.core.FactorKey.VOL20),
            minSamples = 5,
        )
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.first().samples >= 5)
        assertTrue(kotlin.math.abs(pairs.first().corr) <= 1.0)
    }
}
