package com.shigu.data

import com.shigu.core.EquityPoint
import com.shigu.core.OptionBacktestStats
import org.json.JSONArray
import org.json.JSONObject

object OptionBacktestCodec {

    fun encodeStats(s: OptionBacktestStats): String = JSONObject()
        .put("underlying", s.underlying)
        .put("presetId", s.presetId)
        .put("presetName", s.presetName)
        .put("startDate", s.startDate)
        .put("endDate", s.endDate)
        .put("optionReturn", s.optionReturn)
        .put("etfReturn", s.etfReturn)
        .put("csiReturn", s.csiReturn)
        .put("annualized", s.annualized)
        .put("maxDrawdown", s.maxDrawdown)
        .put("sharpe", s.sharpe)
        .put("volatility", s.volatility)
        .put("trades", s.trades)
        .put("winRate", s.winRate)
        .put("avgHoldDays", s.avgHoldDays)
        .put("ivMultiplier", s.ivMultiplier)
        .toString()

    fun decodeStats(json: String): OptionBacktestStats? = runCatching {
        val o = JSONObject(json)
        OptionBacktestStats(
            underlying = o.getString("underlying"),
            presetId = o.getString("presetId"),
            presetName = o.getString("presetName"),
            startDate = o.getString("startDate"),
            endDate = o.getString("endDate"),
            optionReturn = o.getDouble("optionReturn"),
            etfReturn = o.getDouble("etfReturn"),
            csiReturn = o.getDouble("csiReturn"),
            annualized = o.getDouble("annualized"),
            maxDrawdown = o.getDouble("maxDrawdown"),
            sharpe = o.getDouble("sharpe"),
            volatility = o.getDouble("volatility"),
            trades = o.getInt("trades"),
            winRate = o.getDouble("winRate"),
            avgHoldDays = o.getDouble("avgHoldDays"),
            ivMultiplier = o.getDouble("ivMultiplier"),
        )
    }.getOrNull()

    fun encodeEquity(points: List<EquityPoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONArray().put(p.date).put(p.strategy).put(p.benchmark))
        }
        return arr.toString()
    }

    fun decodeEquity(json: String): List<EquityPoint> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONArray(i) ?: return@mapNotNull null
            if (a.length() < 3) null else EquityPoint(a.getString(0), a.getDouble(1), a.getDouble(2))
        }
    }.getOrDefault(emptyList())
}
