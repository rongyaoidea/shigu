package com.shigu.data

import com.shigu.core.BacktestEngine
import com.shigu.core.BacktestResult
import com.shigu.core.Board
import com.shigu.core.FactorEngine
import com.shigu.core.Panel
import com.shigu.core.PanelBuilder
import com.shigu.core.Pick
import com.shigu.core.ScreenerEngine
import com.shigu.core.ScreenerResult
import com.shigu.core.SinceCreationResult
import com.shigu.core.StrategyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MarketRepository(private val db: ShiguDb) {

    suspend fun screen(config: StrategyConfig): ScreenerResult = withContext(Dispatchers.IO) {
        val panel = loadPanel(
            universeSize = config.universeSize,
            lookbackDays = 260,
            includeValuation = false,
            fillCurrent = true,
            maxStocks = 1500,
            boards = config.filters.boards,
        )
        ScreenerEngine.screen(panel, config)
    }

    suspend fun backtest(config: StrategyConfig): BacktestResult = withContext(Dispatchers.IO) {
        val panel = loadPanel(
            universeSize = config.universeSize,
            lookbackDays = config.lookbackDays,
            includeValuation = config.factors.any { it.key.currentOnly },
            fillCurrent = false,
            maxStocks = 1200,
            boards = config.filters.boards,
        )
        BacktestEngine.run(panel, config)
    }

    suspend fun sinceCreation(config: StrategyConfig): SinceCreationResult = withContext(Dispatchers.IO) {
        val created = java.time.Instant.ofEpochMilli(
            if (config.createdAt > 0) config.createdAt else System.currentTimeMillis(),
        ).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(created, LocalDate.now()).toInt().coerceAtLeast(1)
        val panel = loadPanel(
            universeSize = config.universeSize,
            lookbackDays = config.lookbackDays.coerceAtLeast(days + 300),
            includeValuation = config.factors.any { it.key.currentOnly },
            fillCurrent = false,
            maxStocks = 1200,
            boards = config.filters.boards,
        )
        val r = BacktestEngine.sinceCreation(panel, config)
        val picks = r.picks.ifEmpty { currentPicks(panel, config) }
        r.copy(picks = picks)
    }

    suspend fun currentPicks(config: StrategyConfig): List<Pick> = withContext(Dispatchers.IO) {
        val panel = loadPanel(
            universeSize = config.universeSize,
            lookbackDays = 260,
            includeValuation = false,
            fillCurrent = true,
            maxStocks = 1500,
            boards = config.filters.boards,
        )
        currentPicks(panel, config)
    }

    private fun currentPicks(panel: Panel, config: StrategyConfig): List<Pick> {
        val i = panel.lastIndex
        if (i < 0) return emptyList()
        return FactorEngine.scoreAt(panel, i, config).take(config.maxPositions.coerceAtLeast(1)).map { s ->
            val stock = panel.stocks[s.stockIndex]
            val price = panel.close[s.stockIndex][i].takeIf { !it.isNaN() } ?: stock.price
            val prev = if (i > 0) panel.close[s.stockIndex][i - 1] else Double.NaN
            val chg = if (!prev.isNaN() && prev > 0) (price / prev - 1.0) * 100.0 else stock.changePct
            Pick(stock.code, stock.name, price, chg, s.score, s.values, stock.board, stock.isSt)
        }
    }

    fun loadPanel(
        universeSize: Int,
        lookbackDays: Int,
        includeValuation: Boolean,
        fillCurrent: Boolean,
        maxStocks: Int = 1200,
        boards: Set<Board> = com.shigu.core.StrategyFilters().boards,
    ): Panel {
        val benchmark = db.getBenchmarkBars(lookbackDays + 320)
        if (benchmark.size < 80) error("本地没有足够的沪深300数据，请先在数据页同步")
        val calendar = benchmark.map { it.date }.takeLast(lookbackDays + 260)
        val startDate = calendar.first()

        val all = db.getStocks().filter { it.board != Board.INDEX }
        if (all.isEmpty()) error("本地没有股票列表，请先同步")
        val limit = if (universeSize <= 0) maxStocks else universeSize.coerceAtMost(maxStocks).coerceAtLeast(50)
        val wantsEtf = Board.ETF in boards
        val wantsStock = boards.any { it != Board.ETF }
        val stockPool = all.filter { it.board != Board.ETF }.sortedByDescending { it.marketCap ?: 0.0 }
        val etfPool = all.filter {
            it.board == Board.ETF &&
                listOf("保证金", "货币", "短融", "日利", "债").none { bad -> it.name.contains(bad) }
        }.sortedByDescending { it.amount }
        val etfTake = if (wantsEtf) {
            if (limit >= all.size) etfPool.size else minOf(etfPool.size, maxOf(20, limit / 5))
        } else {
            0
        }
        val stockTake = if (wantsStock) (limit - etfTake).coerceAtLeast(0) else 0
        val stocks = (stockPool.take(stockTake) + etfPool.take(etfTake))
        val codes = stocks.map { it.code }
        val bars = db.getBarsForCodes(codes, startDate)
        // 只把真正有足够日线的股票算进池子，避免“池 800、实际 300”的错觉
        val active = stocks.filter { (bars[it.code]?.size ?: 0) >= 60 }
        val activeCodes = active.map { it.code }
        val valuations = if (includeValuation) db.getValuationsForCodes(activeCodes, startDate) else emptyMap()
        return PanelBuilder.build(
            stocks = active,
            calendar = calendar,
            barsByCode = bars.filterKeys { it in activeCodes },
            valuationsByCode = valuations,
            benchmarkBars = benchmark,
            fillCurrentSnapshot = fillCurrent,
        )
    }
}
