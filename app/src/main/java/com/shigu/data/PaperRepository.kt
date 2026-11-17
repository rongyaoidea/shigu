package com.shigu.data

import com.shigu.core.Holding
import com.shigu.core.PaperDetail
import com.shigu.core.SinceCreationResult
import com.shigu.core.SimulationRefresh
import com.shigu.core.StrategyChange
import com.shigu.core.StrategyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 策略创建后的纸面组合。
 *
 * 不自己写第二套持仓引擎：每天收盘后用同一个 [com.shigu.core.BacktestEngine]
 * 从创建日重算到最新交易日，得到最新持仓/净值；再与上一次持仓对比，
 * 生成“买入/卖出/继续持有”的调仓提示。
 */
class PaperRepository(
    private val db: ShiguDb,
    private val market: MarketRepository,
) {

    suspend fun refreshAll(strategies: List<StrategyConfig>): SimulationRefresh = withContext(Dispatchers.IO) {
        val since = LinkedHashMap<String, SinceCreationResult>()
        val changes = ArrayList<StrategyChange>()
        strategies.forEach { config ->
            val r = runCatching { market.sinceCreation(config) }.getOrNull() ?: return@forEach
            since[config.id] = r
            val prev = db.getPaperState(config.id)
            val prevHoldings = parseHoldings(prev?.holdingsJson)
            val current = r.portfolio?.holdings
            val newCodes = if (current != null) current.map { it.code }.toSet() else prevHoldings.map { it.code }.toSet()
            val prevCodes = prevHoldings.map { it.code }.toSet()
            val buys = (newCodes - prevCodes).map { code -> nameOf(code, current, prevHoldings) }
            val sells = (prevCodes - newCodes).map { code -> nameOf(code, current, prevHoldings) }
            val holds = newCodes.intersect(prevCodes).size
            val newer = prev == null || r.endDate >= prev.lastTradeDate
            val computed = StrategyChange(
                strategyId = config.id,
                strategyName = config.name,
                date = r.endDate,
                buys = if (newer) buys else emptyList(),
                sells = if (newer) sells else emptyList(),
                holds = if (newer) newCodes.size else prevCodes.size,
            )
            // 同一天重启 App 时，持仓对比会变成 0 买 0 卖；用当天已保存的调仓提示恢复红标。
            val persisted = if (prev?.lastTradeDate == r.endDate) {
                parseChange(config, prev?.lastChangeJson)
            } else {
                null
            }
            val change = if (newer && !computed.hasChange && persisted != null) persisted else computed
            changes += change

            if (newer) {
                val equity = 1.0 + r.returnPct
                val benchmark = 1.0 + r.benchmarkPct
                db.upsertPaperState(
                    strategyId = config.id,
                    now = System.currentTimeMillis(),
                    lastTradeDate = r.endDate.ifBlank { prev?.lastTradeDate ?: "" },
                    equity = equity,
                    benchmark = benchmark,
                    cash = r.portfolio?.cash ?: 1_000_000.0,
                    holdingsJson = encodeHoldings(current ?: emptyList()),
                    lastChangeJson = if (change.hasChange) encodeChange(change) else null,
                    tradesJson = encodeTrades(r.trades.takeLast(200)),
                )
                if (r.equity.isNotEmpty()) {
                    r.equity.forEach { db.insertPaperEquity(config.id, it.date, it.strategy, it.benchmark) }
                } else if (r.endDate.isNotBlank()) {
                    db.insertPaperEquity(config.id, r.endDate, equity, benchmark)
                }
            }
        }
        SimulationRefresh(since, changes)
    }

    fun detail(config: StrategyConfig): PaperDetail {
        val st = db.getPaperState(config.id)
        val holdings = parseHoldings(st?.holdingsJson)
        return PaperDetail(
            config = config,
            equity = db.getPaperEquity(config.id),
            holdings = holdings,
            cash = st?.cash ?: 1_000_000.0,
            returnPct = (st?.equity ?: 1.0) - 1.0,
            benchmarkPct = (st?.benchmark ?: 1.0) - 1.0,
            change = parseChange(config, st?.lastChangeJson),
            trades = parseTrades(st?.tradesJson),
        )
    }

    private fun nameOf(code: String, current: List<Holding>?, prev: List<Holding>): String =
        current?.firstOrNull { it.code == code }?.name
            ?: prev.firstOrNull { it.code == code }?.name
            ?: code

    private fun encodeChange(change: StrategyChange): String =
        JSONObject()
            .put("date", change.date)
            .put("buys", JSONArray(change.buys))
            .put("sells", JSONArray(change.sells))
            .put("holds", change.holds)
            .toString()

    private fun parseChange(config: StrategyConfig, json: String?): StrategyChange? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val date = o.optString("date")
            val buys = o.optJSONArray("buys")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val sells = o.optJSONArray("sells")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            StrategyChange(config.id, config.name, date, buys, sells, o.optInt("holds", 0))
        }.getOrNull()
    }

    private fun encodeHoldings(list: List<Holding>): String {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("code", h.code)
                    .put("name", h.name)
                    .put("shares", h.shares)
                    .put("entryPrice", h.entryPrice)
                    .put("lastPrice", h.lastPrice)
                    .put("weight", h.weight)
                    .put("entryDate", h.entryDate),
            )
        }
        return arr.toString()
    }

    private fun encodeTrades(trades: List<com.shigu.core.Trade>): String {
        val arr = JSONArray()
        trades.forEach { t ->
            arr.put(
                JSONObject()
                    .put("code", t.code)
                    .put("name", t.name)
                    .put("entryDate", t.entryDate)
                    .put("exitDate", t.exitDate ?: JSONObject.NULL)
                    .put("entryPrice", t.entryPrice)
                    .put("exitPrice", t.exitPrice ?: JSONObject.NULL)
                    .put("shares", t.shares)
                    .put("pnl", t.pnl)
                    .put("pnlPct", t.pnlPct)
                    .put("holdDays", t.holdDays)
                    .put("reason", t.reason)
                    .put("fees", t.fees),
            )
        }
        return arr.toString()
    }

    private fun parseTrades(json: String?): List<com.shigu.core.Trade> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                com.shigu.core.Trade(
                    code = o.optString("code"),
                    name = o.optString("name"),
                    entryDate = o.optString("entryDate"),
                    exitDate = if (o.isNull("exitDate")) null else o.optString("exitDate"),
                    entryPrice = o.optDouble("entryPrice", 0.0),
                    exitPrice = if (o.isNull("exitPrice")) null else o.optDouble("exitPrice"),
                    shares = o.optLong("shares"),
                    pnl = o.optDouble("pnl", 0.0),
                    pnlPct = o.optDouble("pnlPct", 0.0),
                    holdDays = o.optInt("holdDays"),
                    reason = o.optString("reason"),
                    fees = o.optDouble("fees", 0.0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseHoldings(json: String?): List<Holding> {        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Holding(
                    code = o.optString("code"),
                    name = o.optString("name"),
                    shares = o.optLong("shares"),
                    entryPrice = o.optDouble("entryPrice", 0.0),
                    lastPrice = o.optDouble("lastPrice", 0.0),
                    weight = o.optDouble("weight", 0.0),
                    entryDate = o.optString("entryDate"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
