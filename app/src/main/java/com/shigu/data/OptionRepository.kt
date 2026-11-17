package com.shigu.data

import com.shigu.core.OptionContract
import com.shigu.core.OptionPaperEngine
import com.shigu.core.OptionPaperStrategy
import com.shigu.core.OptionPaperView
import com.shigu.core.OptionSignal
import com.shigu.core.OptionSignalEngine
import com.shigu.core.OptionSignalPreset
import com.shigu.core.OptionSignalRule
import com.shigu.core.PanelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class OptionRepository(
    private val db: ShiguDb,
    private val api: OptionApi,
    private val marketApi: EastmoneyApi,
) {

    suspend fun syncChain(): Result<List<OptionContract>> = withContext(Dispatchers.IO) {
        runCatching {
            val contracts = api.fetchChain()
            if (contracts.isEmpty()) error("期权链为空")
            db.upsertOptionContracts(contracts)
            listOf("510050", "510300").forEach { code ->
                if (db.getBars(code, 1).isEmpty()) {
                    val secid = db.getStock(code)?.secid ?: "sh$code"
                    val bars = marketApi.fetchDailyBars(secid, 300)
                    if (bars.isNotEmpty()) db.upsertBars(bars)
                }
            }
            contracts
        }
    }

    fun contracts(underlying: String? = null): List<OptionContract> = db.getOptionContracts(underlying)

    fun expiries(underlying: String): List<String> =
        db.getOptionContracts(underlying).map { it.expiry }.filter { it.isNotBlank() }.distinct().sorted()

    fun underlyingPrice(underlying: String): Double {
        val stock = db.getStock(underlying)
        if (stock != null && stock.price > 0) return stock.price
        return db.getBars(underlying, 1).lastOrNull()?.close ?: 0.0
    }

    fun signal(underlying: String, preset: OptionSignalPreset): Result<OptionSignal> = runCatching {
        val stock = db.getStock(underlying) ?: error("本地没有 $underlying")
        val bars = db.getBars(underlying, 420)
        if (bars.size < 80) error("$underlying 日线不足 80 条，请先同步数据")
        val bench = db.getBenchmarkBars(620)
        if (bench.size < 80) error("缺少沪深300基准，请先同步")
        val calendar = bench.map { it.date }.takeLast(460)
        val panel = PanelBuilder.build(
            stocks = listOf(stock),
            calendar = calendar,
            barsByCode = mapOf(underlying to bars),
            benchmarkBars = bench,
            fillCurrentSnapshot = true,
        )
        OptionSignalEngine.evaluate(panel, preset.rules, LocalDate.now().toString())
    }

    fun plan(
        underlying: String,
        preset: OptionSignalPreset,
        strikeOffset: Int,
        lots: Int,
    ): Result<OptionPaperStrategy> = runCatching {
        val today = LocalDate.now().toString()
        val signal = signal(underlying, preset).getOrThrow()
        val contracts = db.getOptionContracts(underlying)
        if (contracts.isEmpty()) error("期权链为空，请先同步")
        val strategy = OptionPaperEngine.rebalance(
            strategy = OptionPaperStrategy(
                id = "opt_${System.currentTimeMillis()}",
                name = "${preset.name} · $underlying",
                underlying = underlying,
                presetId = preset.id,
                presetName = preset.name,
                rules = preset.rules,
                strikeOffset = strikeOffset,
                lots = lots,
                createdAt = System.currentTimeMillis(),
                entryDate = today,
                lastDate = "",
                lastSignal = com.shigu.core.OptionDirection.NEUTRAL,
                lastAction = "",
                position = null,
            ),
            signal = signal,
            contracts = contracts,
            underlyingPrice = underlyingPrice(underlying),
            today = today,
        )
        if (strategy.position == null && signal.direction != com.shigu.core.OptionDirection.NEUTRAL) {
            error("没有可用期权合约（可能已到期/缺少行情）")
        }
        db.upsertOptionStrategy(strategy)
        strategy
    }

    fun strategies(): List<OptionPaperStrategy> = db.getOptionStrategies()

    fun refresh(strategy: OptionPaperStrategy): OptionPaperView {
        val today = LocalDate.now().toString()
        val preset = OptionSignalPreset(
            id = strategy.presetId,
            name = strategy.presetName,
            subtitle = "",
            description = "",
            rules = strategy.rules,
        )
        val signal = signal(strategy.underlying, preset).getOrElse {
            return OptionPaperEngine.mark(strategy, db.getOptionContracts(strategy.underlying), underlyingPrice(strategy.underlying), today)
        }
        val contracts = db.getOptionContracts(strategy.underlying)
        val updated = OptionPaperEngine.rebalance(
            strategy = strategy,
            signal = signal,
            contracts = contracts,
            underlyingPrice = underlyingPrice(strategy.underlying),
            today = today,
        )
        if (updated != strategy) db.upsertOptionStrategy(updated)
        return OptionPaperEngine.mark(updated, contracts, underlyingPrice(strategy.underlying), today)
    }

    fun view(strategy: OptionPaperStrategy): OptionPaperView {
        val today = LocalDate.now().toString()
        val contracts = db.getOptionContracts(strategy.underlying)
        return OptionPaperEngine.mark(strategy, contracts, underlyingPrice(strategy.underlying), today)
    }

    fun delete(id: String) = db.deleteOptionStrategy(id)

    fun customPreset(rules: List<OptionSignalRule>): OptionSignalPreset =
        OptionSignalPreset(
            id = "custom",
            name = "自定义方向",
            subtitle = "自选 ETF 因子",
            description = "按所选因子的符号投票；只做多 call/put。",
            rules = rules,
        )
}
