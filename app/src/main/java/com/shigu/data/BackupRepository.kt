package com.shigu.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻量备份：只导出用户自定义策略和观察池，不含行情/纸面净值。
 * 用剪贴板在手机之间搬，避免引入文件权限和 SAF。
 */
class BackupRepository(private val db: ShiguDb) {

    data class ImportStats(val strategies: Int, val watchlist: Int)

    fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val strategies = JSONArray()
        db.getStrategyRows()
            .filter { !it.builtin && !it.configJson.isNullOrBlank() }
            .forEach { row ->
                strategies.put(
                    JSONObject()
                        .put("id", row.id)
                        .put("name", row.name)
                        .put("createdAt", row.createdAt)
                        .put("configJson", row.configJson),
                )
            }
        root.put("strategies", strategies)

        val watch = JSONArray()
        db.getWatchlist().forEach { item ->
            watch.put(
                JSONObject()
                    .put("code", item.code)
                    .put("entryPrice", item.entryPrice)
                    .put("thesis", item.thesis)
                    .put("group", item.group)
                    .put("addedAt", item.addedAt),
            )
        }
        root.put("watchlist", watch)
        return root.toString()
    }

    fun importJson(json: String): ImportStats {
        val root = JSONObject(json)
        if (root.optInt("version", 0) != 1) error("备份版本不支持")

        var strategyCount = 0
        val strategies = root.optJSONArray("strategies") ?: JSONArray()
        for (i in 0 until strategies.length()) {
            val o = strategies.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val name = o.optString("name")
            val configJson = o.optString("configJson")
            if (id.isBlank() || name.isBlank() || configJson.isBlank()) continue
            if (JsonCodec.decodeConfig(configJson) == null) continue
            db.upsertStrategy(
                id = id,
                name = name,
                builtin = false,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                configJson = configJson,
            )
            strategyCount++
        }

        var watchCount = 0
        val watchlist = root.optJSONArray("watchlist") ?: JSONArray()
        for (i in 0 until watchlist.length()) {
            val o = watchlist.optJSONObject(i) ?: continue
            val code = o.optString("code")
            if (!code.matches(Regex("\\d{6}"))) continue
            db.addWatch(
                code = code,
                entryPrice = o.optDouble("entryPrice", 0.0),
                thesis = o.optString("thesis", ""),
                group = o.optString("group", "核心"),
            )
            watchCount++
        }
        return ImportStats(strategyCount, watchCount)
    }
}
