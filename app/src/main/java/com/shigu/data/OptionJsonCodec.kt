package com.shigu.data

import com.shigu.core.FactorKey
import com.shigu.core.OptionDirection
import com.shigu.core.OptionPaperStrategy
import com.shigu.core.OptionPosition
import com.shigu.core.OptionSignalRule
import com.shigu.core.OptionType
import org.json.JSONArray
import org.json.JSONObject

object OptionJsonCodec {

    fun encode(s: OptionPaperStrategy): String {
        val o = JSONObject()
        o.put("id", s.id)
        o.put("name", s.name)
        o.put("underlying", s.underlying)
        o.put("presetId", s.presetId)
        o.put("presetName", s.presetName)
        o.put("strikeOffset", s.strikeOffset)
        o.put("lots", s.lots)
        o.put("createdAt", s.createdAt)
        o.put("entryDate", s.entryDate)
        o.put("lastDate", s.lastDate)
        o.put("lastSignal", s.lastSignal.name)
        o.put("lastAction", s.lastAction)
        o.put("closed", s.closed)
        val rules = JSONArray()
        s.rules.forEach { r ->
            rules.put(
                JSONObject()
                    .put("key", r.key.name)
                    .put("weight", r.weight)
                    .put("threshold", r.threshold),
            )
        }
        o.put("rules", rules)
        val p = s.position
        o.put(
            "position",
            if (p == null) {
                JSONObject.NULL
            } else {
                JSONObject()
                    .put("contractCode", p.contractCode)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("strike", p.strike)
                    .put("expiry", p.expiry)
                    .put("entryPrice", p.entryPrice)
                    .put("quantity", p.quantity)
                    .put("entryDate", p.entryDate)
            },
        )
        return o.toString()
    }

    fun decode(json: String): OptionPaperStrategy? = runCatching {
        val o = JSONObject(json)
        val rules = ArrayList<OptionSignalRule>()
        val ra = o.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until ra.length()) {
            val x = ra.getJSONObject(i)
            rules += OptionSignalRule(
                key = FactorKey.valueOf(x.getString("key")),
                weight = x.optDouble("weight", 1.0),
                threshold = x.optDouble("threshold", 0.0),
            )
        }
        val posObj = o.optJSONObject("position")
        val position = posObj?.let {
            OptionPosition(
                contractCode = it.getString("contractCode"),
                name = it.optString("name", ""),
                type = OptionType.valueOf(it.getString("type")),
                strike = it.optDouble("strike", 0.0),
                expiry = it.optString("expiry", ""),
                entryPrice = it.optDouble("entryPrice", 0.0),
                quantity = it.optLong("quantity", 1L),
                entryDate = it.optString("entryDate", ""),
            )
        }
        OptionPaperStrategy(
            id = o.getString("id"),
            name = o.getString("name"),
            underlying = o.getString("underlying"),
            presetId = o.optString("presetId", ""),
            presetName = o.optString("presetName", ""),
            rules = rules,
            strikeOffset = o.optInt("strikeOffset", 0),
            lots = o.optInt("lots", 1),
            createdAt = o.optLong("createdAt", 0L),
            entryDate = o.optString("entryDate", ""),
            lastDate = o.optString("lastDate", ""),
            lastSignal = runCatching { OptionDirection.valueOf(o.optString("lastSignal", "NEUTRAL")) }
                .getOrDefault(OptionDirection.NEUTRAL),
            lastAction = o.optString("lastAction", ""),
            position = position,
            closed = o.optBoolean("closed", false),
        )
    }.getOrNull()
}
