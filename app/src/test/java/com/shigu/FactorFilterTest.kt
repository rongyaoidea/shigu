package com.shigu

import com.shigu.core.Board
import com.shigu.core.FactorEngine
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.OwnerType
import com.shigu.core.Panel
import com.shigu.core.ScreenerEngine
import com.shigu.core.StStatus
import com.shigu.core.Stock
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FactorFilterTest {

    private val days = 120
    private val calendar = (0 until days).map { LocalDate.of(2025, 6, 1).plusDays(it.toLong()).toString() }
    private val listedOld = "2010-01-01"
    private val listedNew = calendar[days - 20]

    private fun stock(
        code: String,
        owner: OwnerType,
        st: StStatus,
        listed: String,
        suspended: Boolean = false,
        roe: Double? = 10.0,
    ) = Stock(
        code = code,
        name = "测试$code",
        secid = "sh$code",
        board = Board.SH_MAIN,
        listedDate = listed,
        ownerType = owner,
        stStatus = st,
        suspended = suspended,
        roe = roe,
        marketCap = 10_000_000_000.0,
    )

    private fun panel(): Panel {
        val stocks = listOf(
            stock("600000", OwnerType.CENTRAL_SOE, StStatus.NORMAL, listedOld, roe = 12.0),
            stock("600001", OwnerType.PRIVATE, StStatus.NORMAL, listedNew, roe = 18.0),
            stock("600002", OwnerType.LOCAL_SOE, StStatus.ST, listedOld, roe = 8.0),
            stock("600003", OwnerType.LOCAL_SOE, StStatus.STAR_ST, listedOld, roe = 9.0),
            stock("600004", OwnerType.PRIVATE, StStatus.NORMAL, listedOld, suspended = true, roe = 20.0),
            stock("600005", OwnerType.PRIVATE, StStatus.NORMAL, listedOld, roe = 11.0),
        )
        val n = stocks.size
        val close = Array(n) { i -> DoubleArray(days) { d -> 10.0 + i + d * 0.02 } }
        val open = Array(n) { i -> DoubleArray(days) { d -> close[i][d] - 0.02 } }
        val high = Array(n) { i -> DoubleArray(days) { d -> close[i][d] + 0.1 } }
        val low = Array(n) { i -> DoubleArray(days) { d -> close[i][d] - 0.1 } }
        val volume = Array(n) { DoubleArray(days) { 1_000_000.0 } }
        val amount = Array(n) { DoubleArray(days) { 500_000_000.0 } }
        val turnover = Array(n) { DoubleArray(days) { 1.0 } }
        val pe = Array(n) { DoubleArray(days) { 10.0 } }
        val pb = Array(n) { DoubleArray(days) { 1.0 } }
        val cap = Array(n) { DoubleArray(days) { 10_000_000_000.0 } }
        val bench = DoubleArray(days) { 3000.0 + it }
        return Panel(calendar, stocks, open, close, high, low, volume, amount, turnover, pe, pb, cap, bench)
    }

    private fun config(filters: StrategyFilters) = StrategyConfig(
        id = "filter_test",
        name = "过滤测试",
        subtitle = "",
        evidence = "",
        caveat = "",
        factors = listOf(FactorWeight(FactorKey.RET20, 1.0)),
        filters = filters.copy(minPrice = 0.0, minAmount20 = 0.0),
        maxPositions = 20,
        rebalanceDays = 20,
        universeSize = 20,
    )

    @Test
    fun `企业性质 ST 上市天数与停牌过滤同时生效`() {
        val panel = panel()
        val result = FactorEngine.scoreAt(
            panel,
            panel.lastIndex,
            config(
                StrategyFilters(
                    owners = setOf(OwnerType.CENTRAL_SOE),
                    stStatuses = setOf(StStatus.NORMAL),
                    minListedDays = 100,
                    excludeSuspended = true,
                    excludeSt = true,
                ),
            ),
        )
        assertEquals(1, result.size)
        assertEquals("600000", panel.stocks[result.first().stockIndex].code)
    }

    @Test
    fun `只看 ST 和星号ST 时不会被 excludeSt 误杀`() {
        val panel = panel()
        val result = FactorEngine.scoreAt(
            panel,
            panel.lastIndex,
            config(
                StrategyFilters(
                    owners = OwnerType.entries.toSet(),
                    stStatuses = setOf(StStatus.ST, StStatus.STAR_ST),
                    excludeSt = false,
                    excludeSuspended = false,
                ),
            ),
        )
        val codes = result.map { panel.stocks[it.stockIndex].code }.toSet()
        assertEquals(setOf("600002", "600003"), codes)
    }

    @Test
    fun `排除停牌只剔除停牌资产`() {
        val panel = panel()
        val result = FactorEngine.scoreAt(
            panel,
            panel.lastIndex,
            config(
                StrategyFilters(
                    owners = OwnerType.entries.toSet(),
                    stStatuses = StStatus.entries.toSet(),
                    excludeSt = false,
                    excludeSuspended = true,
                ),
            ),
        )
        val codes = result.map { panel.stocks[it.stockIndex].code }.toSet()
        assertTrue("600004" !in codes)
        assertTrue("600000" in codes)
    }

    @Test
    fun `财务因子读取股票快照`() {
        val panel = panel()
        assertEquals(12.0, FactorEngine.value(panel, 0, panel.lastIndex, FactorKey.ROE)!!, 1e-9)
    }

    @Test
    fun `筛选漏斗单调递减且覆盖全池`() {
        val panel = panel()
        val result = ScreenerEngine.screen(
            panel,
            config(
                StrategyFilters(
                    owners = setOf(OwnerType.CENTRAL_SOE),
                    stStatuses = setOf(StStatus.NORMAL),
                    minListedDays = 100,
                    excludeSuspended = true,
                    excludeSt = true,
                ),
            ),
        )
        assertTrue(result.funnel.isNotEmpty())
        assertEquals(panel.stocks.size, result.funnel.first().remaining)
        result.funnel.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.label} -> ${b.label}: ${a.remaining} -> ${b.remaining}", b.remaining <= a.remaining)
        }
        assertEquals(1, result.rows.size)
    }
}
