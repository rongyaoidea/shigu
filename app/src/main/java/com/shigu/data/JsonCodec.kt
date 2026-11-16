package com.shigu.data

import com.shigu.core.Board
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {

    fun encodeConfig(c: StrategyConfig): String {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("name", c.name)
        o.put("subtitle", c.subtitle)
        o.put("evidence", c.evidence)
        o.put("caveat", c.caveat)
        o.put("rebalanceDays", c.rebalanceDays)
        o.put("maxPositions", c.maxPositions)
        o.put("stopLossPct", c.stopLossPct ?: JSONObject.NULL)
        o.put("takeProfitPct", c.takeProfitPct ?: JSONObject.NULL)
        o.put("maxHoldDays", c.maxHoldDays ?: JSONObject.NULL)
        o.put("lookbackDays", c.lookbackDays)
        o.put("universeSize", c.universeSize)
        o.put("costBps", c.costBps)
        o.put("builtin", c.builtin)
        o.put("createdAt", c.createdAt)
        val fs = JSONArray()
        c.factors.forEach {
            fs.put(JSONObject().put("key", it.key.name).put("weight", it.weight))
        }
        o.put("factors", fs)
        val f = JSONObject()
        f.put("minPrice", c.filters.minPrice)
        f.put("minAmount20", c.filters.minAmount20)
        f.put("maxPe", c.filters.maxPe ?: JSONObject.NULL)
        f.put("maxPb", c.filters.maxPb ?: JSONObject.NULL)
        f.put("excludeSt", c.filters.excludeSt)
        f.put("boards", JSONArray(c.filters.boards.map { it.name }))
        o.put("filters", f)
        return o.toString()
    }

    fun decodeConfig(json: String): StrategyConfig? = runCatching {
        val o = JSONObject(json)
        val factors = ArrayList<FactorWeight>()
        val fa = o.optJSONArray("factors") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val x = fa.getJSONObject(i)
            val key = FactorKey.valueOf(x.getString("key"))
            factors += FactorWeight(key, x.optDouble("weight", 0.2))
        }
        val f = o.optJSONObject("filters") ?: JSONObject()
        val boards = HashSet<Board>()
        f.optJSONArray("boards")?.let { a ->
            for (i in 0 until a.length()) {
                runCatching { Board.valueOf(a.getString(i)) }.getOrNull()?.let { boards += it }
            }
        }
        StrategyConfig(
            id = o.getString("id"),
            name = o.getString("name"),
            subtitle = o.optString("subtitle", ""),
            evidence = o.optString("evidence", ""),
            caveat = o.optString("caveat", ""),
            factors = factors,
            filters = StrategyFilters(
                minPrice = f.optDouble("minPrice", 2.0),
                minAmount20 = f.optDouble("minAmount20", 50_000_000.0),
                maxPe = f.optDouble("maxPe").takeIf { !it.isNaN() && f.has("maxPe") && !f.isNull("maxPe") },
                maxPb = f.optDouble("maxPb").takeIf { !it.isNaN() && f.has("maxPb") && !f.isNull("maxPb") },
                excludeSt = f.optBoolean("excludeSt", true),
                boards = if (boards.isEmpty()) StrategyFilters().boards else boards,
            ),
            rebalanceDays = o.optInt("rebalanceDays", 20),
            maxPositions = o.optInt("maxPositions", 10),
            stopLossPct = o.optDouble("stopLossPct").takeIf { !it.isNaN() && o.has("stopLossPct") && !o.isNull("stopLossPct") },
            takeProfitPct = o.optDouble("takeProfitPct").takeIf { !it.isNaN() && o.has("takeProfitPct") && !o.isNull("takeProfitPct") },
            maxHoldDays = o.optInt("maxHoldDays", 0).takeIf { it > 0 },
            lookbackDays = o.optInt("lookbackDays", 500),
            universeSize = o.optInt("universeSize", 800),
            costBps = o.optDouble("costBps", 8.0),
            builtin = o.optBoolean("builtin", false),
            createdAt = o.optLong("createdAt", 0L),
        )
    }.getOrNull()
}
