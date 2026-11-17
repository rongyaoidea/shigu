package com.shigu.core

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

private data class Position(
    val code: String,
    val stockIndex: Int,
    val shares: Long,
    val entryPrice: Double,
    val entryDate: String,
    val entryDay: Int,
    val entryFee: Double,
    var lastClose: Double,
)

object BacktestEngine {

    private const val INITIAL_CAPITAL = 1_000_000.0

    fun run(
        panel: Panel,
        config: StrategyConfig,
        fromDate: String? = null,
        toDate: String? = null,
    ): BacktestResult {
        val calendar = panel.calendar
        val end = if (toDate == null) calendar.lastIndex else {
            calendar.indexOfLast { it <= toDate }.coerceAtLeast(0)
        }
        val start = if (fromDate == null) {
            (end - config.lookbackDays).coerceAtLeast(130).coerceAtMost(end)
        } else {
            val idx = calendar.indexOfFirst { it >= fromDate }
            if (idx < 0) end else idx.coerceAtMost(end)
        }
        if (end <= 0 || start >= end) {
            return flatResult(panel, config, start.coerceIn(0, calendar.lastIndex), end)
        }

        val benchmarkBase = (start..end).firstNotNullOfOrNull {
            panel.benchmark[it].takeIf { v -> !v.isNaN() && v > 0 }
        } ?: 1.0

        var cash = INITIAL_CAPITAL
        val positions = LinkedHashMap<String, Position>()
        val trades = ArrayList<Trade>()
        val equity = ArrayList<EquityPoint>(end - start + 1)
        var pendingRebalance: List<ScoredStock>? = null
        var lastSignalPicks: List<Pick> = emptyList()
        val pendingStop = LinkedHashSet<String>()
        val pendingTake = LinkedHashSet<String>()
        val pendingExpiry = LinkedHashSet<String>()

        fun markAndEquity(d: Int) {
            var value = cash
            positions.values.forEach { p ->
                val c = panel.close[p.stockIndex][d]
                if (!c.isNaN() && c > 0) p.lastClose = c
                value += p.shares * p.lastClose
            }
            val bench = panel.benchmark[d].takeIf { !it.isNaN() && it > 0 } ?: benchmarkBase
            equity += EquityPoint(calendar[d], value / INITIAL_CAPITAL, bench / benchmarkBase)
        }

        fun sellPosition(p: Position, d: Int, reason: String): Boolean {
            val px = panel.open[p.stockIndex][d]
            if (px.isNaN() || px <= 0) return false
            if (isLimitDown(panel, p.stockIndex, d)) return false
            val exec = px * (1.0 - config.slippageBps / 10_000.0)
            val gross = exec * p.shares
            val fee = gross * (config.costBps + config.transferFeeBps + config.stampDutyBps) / 10_000.0
            cash += gross - fee
            positions.remove(p.code)
            val pnl = (exec - p.entryPrice) * p.shares - p.entryFee - fee
            trades += Trade(
                code = p.code,
                name = panel.stocks[p.stockIndex].name,
                entryDate = p.entryDate,
                exitDate = calendar[d],
                entryPrice = p.entryPrice,
                exitPrice = exec,
                shares = p.shares,
                pnl = pnl,
                pnlPct = if (p.entryPrice > 0) exec / p.entryPrice - 1.0 else 0.0,
                holdDays = (d - p.entryDay).coerceAtLeast(0),
                reason = reason,
                fees = p.entryFee + fee,
            )
            return true
        }

        fun buyTargets(targets: List<ScoredStock>, d: Int) {
            if (targets.isEmpty()) return
            val heldCodes = positions.keys.toSet()
            val buyList = targets.filter { panel.stocks[it.stockIndex].code !in heldCodes }
            if (buyList.isEmpty()) return
            var available = cash
            var remaining = buyList.size
            buyList.forEach { s ->
                val si = s.stockIndex
                val open = panel.open[si][d]
                if (open.isNaN() || open <= 0 || isLimitUp(panel, si, d)) {
                    remaining--
                    return@forEach
                }
                val board = panel.stocks[si].board
                val lot = lotSize(board)
                val budget = available / remaining.coerceAtLeast(1)
                val exec = open * (1.0 + config.slippageBps / 10_000.0)
                var shares = floor(budget / (exec * lot)).toLong() * lot
                while (shares > 0) {
                    val cost = shares * exec
                    val fee = buyFee(cost, config)
                    if (cost + fee <= available) break
                    shares -= lot
                }
                if (shares <= 0) {
                    remaining--
                    return@forEach
                }
                val cost = shares * exec
                val fee = buyFee(cost, config)
                cash -= cost + fee
                available -= cost + fee
                val stock = panel.stocks[si]
                positions[stock.code] = Position(
                    code = stock.code,
                    stockIndex = si,
                    shares = shares,
                    entryPrice = exec,
                    entryDate = calendar[d],
                    entryDay = d,
                    entryFee = fee,
                    lastClose = exec,
                )
                remaining--
            }
        }

        for (d in start..end) {
            // 1) 执行昨日收盘产生的卖单 / 全部换仓
            if (d > start) {
                val stopSells = pendingStop.toList()
                pendingStop.clear()
                stopSells.forEach { code ->
                    val p = positions[code] ?: return@forEach
                    val ok = sellPosition(p, d, "止损")
                    if (!ok) pendingStop += code
                }
                val takeSells = pendingTake.toList()
                pendingTake.clear()
                takeSells.forEach { code ->
                    val p = positions[code] ?: return@forEach
                    val ok = sellPosition(p, d, "止盈")
                    if (!ok) pendingTake += code
                }
                val expirySells = pendingExpiry.toList()
                pendingExpiry.clear()
                expirySells.forEach { code ->
                    val p = positions[code] ?: return@forEach
                    val ok = sellPosition(p, d, "持有到期")
                    if (!ok) pendingExpiry += code
                }
                pendingRebalance?.let { targets ->
                    // 只卖出跌出目标的持仓；仍在目标里的继续持有，避免“卖出再买回”的假换手。
                    val targetCodes = targets.map { panel.stocks[it.stockIndex].code }.toSet()
                    val toSell = positions.values.filter { it.code !in targetCodes }.toList()
                    val unsold = LinkedHashSet<String>()
                    toSell.forEach { p ->
                        val ok = sellPosition(p, d, "调仓")
                        if (!ok) unsold += p.code
                    }
                    val newTargets = targets.filter {
                        val code = panel.stocks[it.stockIndex].code
                        code !in unsold && code !in positions.keys
                    }
                    buyTargets(newTargets, d)
                    pendingRebalance = null
                }
            }

            // 2) 收盘估值
            markAndEquity(d)
            if (d == end) break

            // 3) 收盘后检查止损 / 止盈
            val stop = config.stopLossPct
            val take = config.takeProfitPct
            val maxHold = config.maxHoldDays
            if (stop != null || take != null || maxHold != null) {
                positions.values.forEach { p ->
                    if (d <= p.entryDay) return@forEach
                    val c = panel.close[p.stockIndex][d]
                    if (c.isNaN() || c <= 0) return@forEach
                    if (stop != null && c <= p.entryPrice * (1.0 - stop)) pendingStop += p.code
                    if (take != null && c >= p.entryPrice * (1.0 + take)) pendingTake += p.code
                    if (maxHold != null && d - p.entryDay >= maxHold) pendingExpiry += p.code
                }
            }

            // 4) 调仓信号：收盘出信号，次日开盘执行
            val isRebalance = d == start || (d - start) % config.rebalanceDays.coerceAtLeast(1) == 0
            if (isRebalance) {
                val scored = FactorEngine.scoreAt(panel, d, config)
                val targets = scored.take(config.maxPositions.coerceAtLeast(1))
                pendingRebalance = targets
                lastSignalPicks = picksFrom(panel, targets, d)
            }
        }

        // 未平仓：按最后收盘记录浮动盈亏
        val lastDay = end
        positions.values.forEach { p ->
            val c = panel.close[p.stockIndex][lastDay].takeIf { !it.isNaN() } ?: p.lastClose
            val pnl = (c - p.entryPrice) * p.shares - p.entryFee
            trades += Trade(
                code = p.code,
                name = panel.stocks[p.stockIndex].name,
                entryDate = p.entryDate,
                exitDate = null,
                entryPrice = p.entryPrice,
                exitPrice = null,
                shares = p.shares,
                pnl = pnl,
                pnlPct = if (p.entryPrice > 0) c / p.entryPrice - 1.0 else 0.0,
                holdDays = (lastDay - p.entryDay).coerceAtLeast(0),
                reason = "持仓中",
                fees = p.entryFee,
            )
        }

        val stats = stats(panel, config, equity, trades, start, end)
        val snapshotFactors = config.factors.map { it.key }.filter { it.snapshotOnly }
        val snapshotFilters = config.filters.owners.size < OwnerType.entries.size ||
            config.filters.stStatuses != setOf(StStatus.NORMAL) ||
            config.filters.excludeSuspended ||
            config.filters.minListedDays > 0
        val baseWarning = when {
            snapshotFactors.isNotEmpty() -> {
                "含财务快照因子（${snapshotFactors.joinToString("、") { it.label }}）：历史区间使用当前值，存在前视偏差，回测仅作参考。"
            }
            snapshotFilters -> {
                "含企业性质/ST/停牌/上市天数过滤：历史区间使用当前属性，存在前视偏差；结果只作参考。"
            }
            config.factors.any { it.key.currentOnly } -> {
                "含估值/市值因子：历史估值缺失时自动降低权重；首次使用请先在数据页准备估值历史，否则可能有前视偏差。"
            }
            else -> null
        }
        val bseWarning = if (panel.stocks.any { it.board == Board.BSE }) {
            "含北交所：日线不复权，送转/拆股会造成假跌，技术因子结果仅作参考。"
        } else {
            null
        }
        val warning = listOfNotNull(baseWarning, bseWarning).joinToString(" ").ifBlank { null }
        val totalValue = cash + positions.values.sumOf { p ->
            p.shares * (panel.close[p.stockIndex][lastDay].takeIf { !it.isNaN() } ?: p.lastClose)
        }
        val snapshot = PortfolioSnapshot(
            cash = cash,
            equity = totalValue,
            holdings = positions.values.map { p ->
                val last = panel.close[p.stockIndex][lastDay].takeIf { !it.isNaN() } ?: p.lastClose
                Holding(
                    code = p.code,
                    name = panel.stocks[p.stockIndex].name,
                    shares = p.shares,
                    entryPrice = p.entryPrice,
                    lastPrice = last,
                    weight = if (totalValue > 0) p.shares * last / totalValue else 0.0,
                    entryDate = p.entryDate,
                )
            }.sortedByDescending { it.weight },
        )
        return BacktestResult(
            config = config,
            stats = stats,
            equity = equity,
            trades = trades,
            picks = lastSignalPicks.ifEmpty { picksFromPositions(panel, positions, end) },
            warning = warning,
            portfolio = snapshot,
        )
    }

    fun sinceCreation(panel: Panel, config: StrategyConfig): SinceCreationResult {
        val createdDate = java.time.Instant.ofEpochMilli(
            if (config.createdAt > 0) config.createdAt else System.currentTimeMillis(),
        ).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val result = run(panel, config, fromDate = createdDate)
        return SinceCreationResult(
            returnPct = result.stats.totalReturn,
            benchmarkPct = result.stats.benchmarkReturn,
            startDate = result.stats.startDate,
            endDate = result.stats.endDate,
            points = result.equity.size,
            picks = result.picks,
            portfolio = result.portfolio,
            equity = result.equity,
            trades = result.trades,
        )
    }

    private fun picksFrom(panel: Panel, targets: List<ScoredStock>, d: Int): List<Pick> =
        targets.mapNotNull { s ->
            val stock = panel.stocks[s.stockIndex]
            val price = panel.close[s.stockIndex][d].takeIf { !it.isNaN() } ?: return@mapNotNull null
            val prev = if (d > 0) panel.close[s.stockIndex][d - 1] else Double.NaN
            val chg = if (!prev.isNaN() && prev > 0) (price / prev - 1.0) * 100.0 else stock.changePct
            Pick(stock.code, stock.name, price, chg, s.score, s.values, stock.board, stock.isSt, s.contributions)
        }

    private fun picksFromPositions(panel: Panel, positions: Map<String, Position>, d: Int): List<Pick> =
        positions.values.mapNotNull { p ->
            val stock = panel.stocks[p.stockIndex]
            val price = panel.close[p.stockIndex][d].takeIf { !it.isNaN() } ?: return@mapNotNull null
            val prev = if (d > 0) panel.close[p.stockIndex][d - 1] else Double.NaN
            val chg = if (!prev.isNaN() && prev > 0) (price / prev - 1.0) * 100.0 else stock.changePct
            Pick(stock.code, stock.name, price, chg, 0.0, emptyMap(), stock.board, stock.isSt)
        }

    private fun stats(
        panel: Panel,
        config: StrategyConfig,
        equity: List<EquityPoint>,
        trades: List<Trade>,
        start: Int,
        end: Int,
    ): BacktestStats {
        if (equity.size < 2) {
            return BacktestStats(
                startDate = equity.firstOrNull()?.date ?: "",
                endDate = equity.lastOrNull()?.date ?: "",
                totalReturn = 0.0,
                annualized = 0.0,
                benchmarkReturn = 0.0,
                excess = 0.0,
                maxDrawdown = 0.0,
                sharpe = 0.0,
                volatility = 0.0,
                winRate = 0.0,
                tradeCount = 0,
                avgHoldDays = 0.0,
                universeSize = panel.stocks.size,
                note = "样本不足，尚未产生可评估的净值。",
                totalFees = 0.0,
            )
        }
        val total = equity.last().strategy - 1.0
        val bench = equity.last().benchmark - 1.0
        val days = equity.size - 1
        val annualized = if (total > -1.0) (1.0 + total).pow(252.0 / days) - 1.0 else -1.0
        var peak = equity.first().strategy
        var maxDd = 0.0
        equity.forEach {
            if (it.strategy > peak) peak = it.strategy
            val dd = it.strategy / peak - 1.0
            if (dd < maxDd) maxDd = dd
        }
        val rets = equity.zipWithNext { a, b ->
            if (a.strategy > 0) b.strategy / a.strategy - 1.0 else 0.0
        }
        val mean = rets.average()
        val variance = rets.sumOf { (it - mean) * (it - mean) } / (rets.size - 1).coerceAtLeast(1)
        val sd = sqrt(variance)
        val sharpe = if (sd > 1e-9) mean / sd * sqrt(252.0) else 0.0
        val closed = trades.filter { it.exitDate != null }
        val wins = closed.count { it.pnl > 0 }
        val avgHold = closed.map { it.holdDays }.average().takeIf { !it.isNaN() } ?: 0.0
        val note = buildString {
            append("${config.rebalanceDays} 日调仓 · 等权 ${config.maxPositions} 只 · 初始资金 100 万 · 收盘信号次日开盘成交")
            append(" · 佣金 ${config.costBps.toInt()}bps + 印花税 ${config.stampDutyBps.toInt()}bps(卖) + 滑点 ${config.slippageBps.toInt()}bps")
        }
        val totalFees = trades.sumOf { it.fees }
        return BacktestStats(
            startDate = equity.first().date,
            endDate = equity.last().date,
            totalReturn = total,
            annualized = annualized,
            benchmarkReturn = bench,
            excess = total - bench,
            maxDrawdown = maxDd,
            sharpe = sharpe,
            volatility = sd * sqrt(252.0),
            winRate = if (closed.isEmpty()) 0.0 else wins.toDouble() / closed.size,
            tradeCount = closed.size,
            avgHoldDays = avgHold,
            universeSize = panel.stocks.size,
            note = note,
            totalFees = totalFees,
        )
    }

    private fun flatResult(panel: Panel, config: StrategyConfig, start: Int, end: Int): BacktestResult {
        val date = panel.calendar.getOrNull(start) ?: ""
        val stats = BacktestStats(
            startDate = date,
            endDate = panel.calendar.getOrNull(end) ?: date,
            totalReturn = 0.0,
            annualized = 0.0,
            benchmarkReturn = 0.0,
            excess = 0.0,
            maxDrawdown = 0.0,
            sharpe = 0.0,
            volatility = 0.0,
            winRate = 0.0,
            tradeCount = 0,
            avgHoldDays = 0.0,
            universeSize = panel.stocks.size,
            note = "样本不足，尚未产生可评估的净值。",
            totalFees = 0.0,
        )
        return BacktestResult(
            config,
            stats,
            emptyList(),
            emptyList(),
            emptyList(),
            portfolio = PortfolioSnapshot(1_000_000.0, 1_000_000.0, emptyList()),
        )
    }

    private fun isLimitUp(panel: Panel, si: Int, d: Int): Boolean {
        if (d <= 0) return false
        if (isNewListing(panel, si, d)) return false
        val prev = panel.close[si][d - 1]
        val open = panel.open[si][d]
        if (prev.isNaN() || open.isNaN() || prev <= 0) return false
        val up = limitPrice(prev, panel.stocks[si].limitPct, up = true)
        return open >= up - 0.001
    }

    private fun isLimitDown(panel: Panel, si: Int, d: Int): Boolean {
        if (d <= 0) return false
        if (isNewListing(panel, si, d)) return false
        val prev = panel.close[si][d - 1]
        val open = panel.open[si][d]
        if (prev.isNaN() || open.isNaN() || prev <= 0) return false
        val down = limitPrice(prev, panel.stocks[si].limitPct, up = false)
        return open <= down + 0.001
    }

    private fun limitPrice(prev: Double, pct: Double, up: Boolean): Double {
        val raw = prev * (1.0 + (if (up) pct else -pct) / 100.0)
        return round(raw * 100.0) / 100.0
    }

    /** 科创板/创业板前 5 日、主板/北交所前 2 日不设涨跌停（近似处理）。 */
    private fun isNewListing(panel: Panel, si: Int, d: Int): Boolean {
        val stock = panel.stocks[si]
        val days = stock.listedDaysAt(panel.calendar[d]) ?: return false
        return when (stock.board) {
            Board.STAR, Board.CHINEXT -> days < 5
            Board.SH_MAIN, Board.SZ_MAIN, Board.BSE -> days < 2
            else -> false
        }
    }

    private fun lotSize(board: Board): Long = if (board == Board.STAR) 200L else 100L

    private fun buyFee(gross: Double, config: StrategyConfig): Double =
        gross * (config.costBps + config.transferFeeBps) / 10_000.0
}
