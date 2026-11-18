package com.shigu

import com.shigu.core.Board
import com.shigu.core.OptionBacktestEngine
import com.shigu.core.OptionSignalEngine
import com.shigu.core.OptionSignalPresets
import com.shigu.core.Panel
import com.shigu.core.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sin

class OptionBacktestTest {

    private fun panel(days: Int = 520): Panel {
        val calendar = (0 until days).map { LocalDate.of(2021, 1, 1).plusDays(it.toLong()).toString() }
        val stock = Stock("510050", "上证50ETF", "sh510050", Board.ETF, 3.0)
        val close = DoubleArray(days) { i ->
            when {
                i < 180 -> 3.0 + 0.02 * sin(i / 9.0)
                i < 240 -> 3.0 - (i - 180) * 0.012
                i < 260 -> 2.28 + (i - 240) * 0.02
                else -> 2.68 + 0.03 * sin(i / 7.0)
            }
        }
        close[210] = close[209] * 0.94
        val open = DoubleArray(days) { i -> if (i == 0) close[0] else close[i - 1] }
        val high = DoubleArray(days) { close[it] * 1.01 }
        val low = DoubleArray(days) { close[it] * 0.99 }
        val volume = DoubleArray(days) { 1_000_000.0 }
        val amount = DoubleArray(days) { volume[it] * close[it] }
        val turnover = DoubleArray(days) { 1.0 }
        val nan = DoubleArray(days) { Double.NaN }
        val bench = DoubleArray(days) { 4000.0 + it }
        return Panel(
            calendar,
            listOf(stock),
            arrayOf(open),
            arrayOf(close),
            arrayOf(high),
            arrayOf(low),
            arrayOf(volume),
            arrayOf(amount),
            arrayOf(turnover),
            arrayOf(nan),
            arrayOf(nan),
            arrayOf(nan),
            bench,
        )
    }

    @Test
    fun `MFI 超跌后给出看涨方向`() {
        val p = panel()
        val preset = OptionSignalPresets.byId("mfi_reversion")!!
        val series = OptionSignalEngine.series(p, preset)
        assertTrue("MFI 应该在下跌段触发看涨", series.any { it > 0 })
    }

    @Test
    fun `期权历史回测能产生净值和交易`() {
        val p = panel()
        val preset = OptionSignalPresets.byId("panic_1d")!!
        val result = OptionBacktestEngine.run(
            panel = p,
            preset = preset,
            underlying = "510050",
            strikeOffset = 0,
            ivMultiplier = 1.05,
            startDate = p.calendar[200],
        )
        assertEquals(p.calendar.last(), result.stats.endDate)
        assertTrue(result.equity.size > 100)
        assertTrue(result.trades.isNotEmpty())
        assertFalse(result.stats.optionReturn.isNaN())
        assertTrue(result.stats.oosStart.isNotBlank())
        assertTrue(result.equity.all { it.strategy > 0.0 })
        assertTrue(result.stats.winRate in 0.0..1.0)
    }

    @Test
    fun `内置策略至少包含 5 个原策略和 5 个回测候选`() {
        assertTrue(OptionSignalPresets.all.size >= 10)
        assertTrue(OptionSignalPresets.all.any { it.id == "mfi_reversion" })
        assertTrue(OptionSignalPresets.all.any { it.id == "panic_1d" })
    }
}
