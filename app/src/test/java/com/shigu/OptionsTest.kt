package com.shigu

import com.shigu.core.Board
import com.shigu.core.OptionContract
import com.shigu.core.OptionDirection
import com.shigu.core.OptionPaperEngine
import com.shigu.core.OptionPaperStrategy
import com.shigu.core.OptionSelector
import com.shigu.core.OptionSignal
import com.shigu.core.OptionSignalEngine
import com.shigu.core.OptionSignalPresets
import com.shigu.core.OptionType
import com.shigu.core.Panel
import com.shigu.core.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsTest {

    private val days = 140
    private val calendar = (0 until days).map { "2026-01-${(it % 28 + 1).toString().padStart(2, '0')}" }

    private fun panel(): Panel {
        val stock = Stock(
            code = "510050",
            name = "上证50ETF",
            secid = "sh510050",
            board = Board.ETF,
            price = 3.0,
            marketCap = 100_000_000_000.0,
        )
        val close = DoubleArray(days) { 2.4 + it * 0.005 }
        val open = DoubleArray(days) { close[it] - 0.01 }
        val high = DoubleArray(days) { close[it] + 0.05 }
        val low = DoubleArray(days) { close[it] - 0.05 }
        val volume = DoubleArray(days) { 1_000_000.0 }
        val amount = DoubleArray(days) { 3_000_000_000.0 }
        val turnover = DoubleArray(days) { 1.0 }
        val pe = DoubleArray(days) { Double.NaN }
        val pb = DoubleArray(days) { Double.NaN }
        val cap = DoubleArray(days) { 100_000_000_000.0 }
        val bench = DoubleArray(days) { 3000.0 + it }
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
            arrayOf(pe),
            arrayOf(pb),
            arrayOf(cap),
            bench,
        )
    }

    private fun contracts(): List<OptionContract> {
        val expiry = "2026-12-23"
        return listOf(
            OptionContract("1001", "510050", "50ETF购12月2800", OptionType.CALL, 2.8, expiry, 30, 0.0800, 0.0, 1000.0, 0.0, 1000.0),
            OptionContract("1002", "510050", "50ETF购12月2900", OptionType.CALL, 2.9, expiry, 30, 0.0500, 0.0, 1000.0, 0.0, 1000.0),
            OptionContract("1003", "510050", "50ETF购12月3000", OptionType.CALL, 3.0, expiry, 30, 0.0300, 0.0, 1000.0, 0.0, 1000.0),
            OptionContract("2001", "510050", "50ETF沽12月2800", OptionType.PUT, 2.8, expiry, 30, 0.0300, 0.0, 1000.0, 0.0, 1000.0),
            OptionContract("2002", "510050", "50ETF沽12月2900", OptionType.PUT, 2.9, expiry, 30, 0.0500, 0.0, 1000.0, 0.0, 1000.0),
            OptionContract("2003", "510050", "50ETF沽12月3000", OptionType.PUT, 3.0, expiry, 30, 0.0800, 0.0, 1000.0, 0.0, 1000.0),
        )
    }

    private fun emptyStrategy() = OptionPaperStrategy(
        id = "s",
        name = "测试",
        underlying = "510050",
        presetId = "rsrs_llt",
        presetName = "RSRS + LLT",
        rules = OptionSignalPresets.byId("rsrs_llt")!!.rules,
        strikeOffset = 0,
        lots = 1,
        createdAt = 0L,
        entryDate = "2026-11-17",
        lastDate = "",
        lastSignal = OptionDirection.NEUTRAL,
        lastAction = "",
        position = null,
    )

    @Test
    fun `ETF 趋势因子能给出方向`() {
        val signal = OptionSignalEngine.evaluate(
            panel(),
            OptionSignalPresets.byId("ma_trend")!!.rules,
            "2026-11-17",
        )
        assertEquals(OptionDirection.BULL, signal.direction)
        assertTrue(signal.score > 0.0)
    }

    @Test
    fun `平值附近选择 call`() {
        val target = OptionSelector.target(
            contracts = contracts(),
            underlying = "510050",
            direction = OptionDirection.BULL,
            strikeOffset = 0,
            underlyingPrice = 3.02,
            today = "2026-11-17",
        )
        assertNotNull(target)
        assertEquals(OptionType.CALL, target!!.type)
        assertEquals(3.0, target.strike, 1e-9)
    }

    @Test
    fun `同方向不因平值漂移换合约`() {
        val bull = OptionSignal(OptionDirection.BULL, 1.0, "2026-11-17", emptyList())
        val bought = OptionPaperEngine.rebalance(emptyStrategy(), bull, contracts(), 3.0, "2026-11-17")
        assertEquals("1003", bought.position!!.contractCode)

        val drifted = OptionPaperEngine.rebalance(bought, bull, contracts(), 2.8, "2026-11-18")
        assertEquals("1003", drifted.position!!.contractCode)
        assertTrue(drifted.lastAction.startsWith("持有"))
    }

    @Test
    fun `只买期权，方向反转卖出旧仓再买新仓`() {
        val bull = OptionSignal(OptionDirection.BULL, 1.0, "2026-11-17", emptyList())
        val bear = OptionSignal(OptionDirection.BEAR, -1.0, "2026-11-18", emptyList())
        val neutral = OptionSignal(OptionDirection.NEUTRAL, 0.0, "2026-11-19", emptyList())

        val bought = OptionPaperEngine.rebalance(emptyStrategy(), bull, contracts(), 3.0, "2026-11-17")
        assertNotNull(bought.position)
        assertEquals(OptionType.CALL, bought.position!!.type)

        val switched = OptionPaperEngine.rebalance(bought, bear, contracts(), 3.0, "2026-11-18")
        assertNotNull(switched.position)
        assertEquals(OptionType.PUT, switched.position!!.type)
        assertTrue(switched.lastAction.contains("卖出"))

        val closed = OptionPaperEngine.rebalance(switched, neutral, contracts(), 3.0, "2026-11-19")
        assertEquals(null, closed.position)
    }
}
