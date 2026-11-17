package com.shigu.data

import com.shigu.core.Board
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.OwnerType
import com.shigu.core.ScoringMode
import com.shigu.core.StStatus
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
        o.put("stampDutyBps", c.stampDutyBps)
        o.put("slippageBps", c.slippageBps)
        o.put("transferFeeBps", c.transferFeeBps)
        o.put("scoring", c.scoring.name)
        o.put("builtin", c.builtin)
        o.put("createdAt", c.createdAt)
        val fs = JSONArray()
        c.factors.forEach {
            fs.put(JSONObject().put("key", it.key.name).put("weight", it.weight))
        }
        o.put("factors", fs)
        val cf = JSONArray()
        c.customFactors.forEach { cw ->
            cf.put(
                JSONObject()
                    .put("id", cw.factor.id)
                    .put("name", cw.factor.name)
                    .put("expression", cw.factor.expression)
                    .put("higherIsBetter", cw.factor.higherIsBetter)
                    .put("unit", cw.factor.unit)
                    .put("description", cw.factor.description)
                    .put("weight", cw.weight),
            )
        }
        o.put("customFactors", cf)
        val f = JSONObject()
        f.put("minPrice", c.filters.minPrice)
        f.put("minAmount20", c.filters.minAmount20)
        f.put("maxPe", c.filters.maxPe ?: JSONObject.NULL)
        f.put("maxPb", c.filters.maxPb ?: JSONObject.NULL)
        f.put("minMarketCap", c.filters.minMarketCap ?: JSONObject.NULL)
        f.put("maxMarketCap", c.filters.maxMarketCap ?: JSONObject.NULL)
        f.put("excludeSt", c.filters.excludeSt)
        f.put("stStatuses", JSONArray(c.filters.stStatuses.map { it.name }))
        f.put("owners", JSONArray(c.filters.owners.map { it.name }))
        f.put("minListedDays", c.filters.minListedDays)
        f.put("excludeSuspended", c.filters.excludeSuspended)
        f.put("requireProfile", c.filters.requireProfile)
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
        val customFactors = ArrayList<com.shigu.core.CustomFactorWeight>()
        val cfa = o.optJSONArray("customFactors") ?: JSONArray()
        for (i in 0 until cfa.length()) {
            val x = cfa.optJSONObject(i) ?: continue
            val id = x.optString("id")
            val name = x.optString("name")
            val expression = x.optString("expression")
            if (id.isBlank() || name.isBlank() || expression.isBlank()) continue
            customFactors += com.shigu.core.CustomFactorWeight(
                factor = com.shigu.core.CustomFactor(
                    id = id,
                    name = name,
                    expression = expression,
                    higherIsBetter = x.optBoolean("higherIsBetter", true),
                    unit = x.optString("unit", ""),
                    description = x.optString("description", ""),
                ),
                weight = x.optDouble("weight", 0.2),
            )
        }
        val f = o.optJSONObject("filters") ?: JSONObject()
        val boards = HashSet<Board>()
        f.optJSONArray("boards")?.let { a ->
            for (i in 0 until a.length()) {
                runCatching { Board.valueOf(a.getString(i)) }.getOrNull()?.let { boards += it }
            }
        }
        val owners = HashSet<OwnerType>()
        f.optJSONArray("owners")?.let { a ->
            for (i in 0 until a.length()) {
                runCatching { OwnerType.valueOf(a.getString(i)) }.getOrNull()?.let { owners += it }
            }
        }
        val excludeSt = f.optBoolean("excludeSt", true)
        val stStatuses = HashSet<StStatus>()
        f.optJSONArray("stStatuses")?.let { a ->
            for (i in 0 until a.length()) {
                runCatching { StStatus.valueOf(a.getString(i)) }.getOrNull()?.let { stStatuses += it }
            }
        }
        fun nullableDouble(key: String): Double? =
            f.optDouble(key).takeIf { !it.isNaN() && f.has(key) && !f.isNull(key) }
        StrategyConfig(
            id = o.getString("id"),
            name = o.getString("name"),
            subtitle = o.optString("subtitle", ""),
            evidence = o.optString("evidence", ""),
            caveat = o.optString("caveat", ""),
            factors = factors,
            customFactors = customFactors,
            filters = StrategyFilters(
                minPrice = f.optDouble("minPrice", 2.0),
                minAmount20 = f.optDouble("minAmount20", 50_000_000.0),
                maxPe = nullableDouble("maxPe"),
                maxPb = nullableDouble("maxPb"),
                minMarketCap = nullableDouble("minMarketCap"),
                maxMarketCap = nullableDouble("maxMarketCap"),
                excludeSt = excludeSt,
                stStatuses = if (stStatuses.isEmpty()) {
                    if (excludeSt) setOf(StStatus.NORMAL) else StStatus.entries.toSet()
                } else {
                    stStatuses
                },
                owners = if (owners.isEmpty()) OwnerType.entries.toSet() else owners,
                minListedDays = f.optInt("minListedDays", 0),
                excludeSuspended = f.optBoolean("excludeSuspended", false),
                requireProfile = f.optBoolean("requireProfile", false),
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
            stampDutyBps = o.optDouble("stampDutyBps", 5.0),
            slippageBps = o.optDouble("slippageBps", 5.0),
            transferFeeBps = o.optDouble("transferFeeBps", 0.1),
            scoring = runCatching { ScoringMode.valueOf(o.optString("scoring", "ZSCORE")) }
                .getOrDefault(ScoringMode.ZSCORE),
            builtin = o.optBoolean("builtin", false),
            createdAt = o.optLong("createdAt", 0L),
        )
    }.getOrNull()
}
