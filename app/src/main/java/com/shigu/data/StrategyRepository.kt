package com.shigu.data

import com.shigu.core.BacktestResult
import com.shigu.core.SinceCreationResult
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyPresets
import java.util.UUID

class StrategyRepository(
    private val db: ShiguDb,
    private val market: MarketRepository,
) {
    /** 首次运行时给内置策略盖“创建时间”章；之后不再改。 */
    fun ensureBuiltins() {
        val existing = db.getStrategyRows().associateBy { it.id }
        val now = System.currentTimeMillis()
        StrategyPresets.all.forEach { preset ->
            if (preset.id !in existing) {
                db.upsertStrategy(
                    id = preset.id,
                    name = preset.name,
                    builtin = true,
                    createdAt = now,
                    configJson = null,
                )
            }
        }
    }

    fun list(): List<StrategyConfig> {
        ensureBuiltins()
        val rows = db.getStrategyRows()
        val builtinMap = StrategyPresets.all.associateBy { it.id }
        val out = ArrayList<StrategyConfig>(rows.size)
        rows.forEach { row ->
            if (row.builtin) {
                builtinMap[row.id]?.let { out += it.copy(createdAt = row.createdAt, builtin = true) }
            } else {
                row.configJson?.let { JsonCodec.decodeConfig(it) }?.let {
                    out += it.copy(createdAt = row.createdAt, builtin = false)
                }
            }
        }
        // 保证内置策略顺序稳定
        val order = StrategyPresets.all.map { it.id }
        out.sortWith(compareBy<StrategyConfig> {
            order.indexOf(it.id).let { i -> if (i < 0) 999 else i }
        }.thenBy { it.name })
        return out
    }

    fun saveCustom(
        base: StrategyConfig,
        name: String,
    ): StrategyConfig {
        val now = System.currentTimeMillis()
        val config = base.copy(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { "自定义策略" },
            builtin = false,
            createdAt = now,
        )
        db.upsertStrategy(
            id = config.id,
            name = config.name,
            builtin = false,
            createdAt = now,
            configJson = JsonCodec.encodeConfig(config),
        )
        return config
    }

    fun delete(id: String) {
        val row = db.getStrategyRows().firstOrNull { it.id == id } ?: return
        if (!row.builtin) db.deleteStrategy(id)
    }

    /** 编辑已有自定义策略：更新配置并把模拟重置到“今天”。 */
    fun updateCustom(config: StrategyConfig, name: String): StrategyConfig {
        val row = db.getStrategyRows().firstOrNull { it.id == config.id }
        if (row == null || row.builtin) {
            return saveCustom(config, name)
        }
        val now = System.currentTimeMillis()
        val updated = config.copy(
            name = name.ifBlank { config.name },
            builtin = false,
            createdAt = now,
        )
        db.updateStrategyConfig(updated.id, updated.name, now, JsonCodec.encodeConfig(updated))
        db.deletePaperState(updated.id)
        return updated
    }

    suspend fun backtest(config: StrategyConfig): BacktestResult = market.backtest(config)

    suspend fun sinceCreation(config: StrategyConfig): SinceCreationResult = market.sinceCreation(config)
}
