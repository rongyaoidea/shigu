package com.shigu.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

enum class OptionType(val label: String) {
    CALL("认购"),
    PUT("认沽"),
}

enum class OptionDirection(val label: String) {
    BULL("看涨"),
    BEAR("看跌"),
    NEUTRAL("中性"),
}

enum class OptionLegKind { ETF, CALL, PUT }

data class OptionContract(
    val code: String,
    val underlying: String,
    val name: String,
    val type: OptionType,
    val strike: Double,
    val expiry: String,
    val remainingDays: Int,
    val last: Double,
    val changePct: Double,
    val volume: Double,
    val amount: Double,
    val openInterest: Double,
    val updatedAt: Long = 0L,
)

/** 用 ETF 的现有技术因子投票判断方向；只做多期权。 */
data class OptionSignalRule(
    val key: FactorKey,
    val weight: Double = 1.0,
    val threshold: Double = 0.0,
)

data class OptionSignalPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val rules: List<OptionSignalRule>,
)

data class OptionSignalDetail(
    val key: FactorKey,
    val value: Double?,
    val vote: Double,
    val weight: Double,
)

data class OptionSignal(
    val direction: OptionDirection,
    val score: Double,
    val date: String,
    val details: List<OptionSignalDetail>,
)

object OptionSignalPresets {
    val all: List<OptionSignalPreset> = listOf(
        OptionSignalPreset(
            id = "rsrs_llt",
            name = "RSRS + LLT",
            subtitle = "趋势确认",
            description = "RSRS 右偏与 LLT 趋势同向时买 call；反向时买 put。",
            rules = listOf(
                OptionSignalRule(FactorKey.RSRS_RIGHT, 0.55),
                OptionSignalRule(FactorKey.LLT_GAP, 0.45),
            ),
        ),
        OptionSignalPreset(
            id = "llt_ma",
            name = "LLT + 均线",
            subtitle = "低延迟趋势",
            description = "LLT 与 MA20/60 同向确认；适合趋势较流畅的 ETF。",
            rules = listOf(
                OptionSignalRule(FactorKey.LLT_GAP, 0.50),
                OptionSignalRule(FactorKey.MA_TREND, 0.30),
                OptionSignalRule(FactorKey.MA60_GAP, 0.20),
            ),
        ),
        OptionSignalPreset(
            id = "ma_trend",
            name = "双均线方向",
            subtitle = "MA20 / MA60",
            description = "MA20/60 + 收盘距 MA60 + 20 日动量；最朴素的趋势过滤。",
            rules = listOf(
                OptionSignalRule(FactorKey.MA_TREND, 0.45),
                OptionSignalRule(FactorKey.MA60_GAP, 0.30),
                OptionSignalRule(FactorKey.RET20, 0.25),
            ),
        ),
        OptionSignalPreset(
            id = "momentum",
            name = "量价动量",
            subtitle = "20/60 日 + 量能",
            description = "20/60 日动量为正且量比 > 1 时看涨；反之为看跌。",
            rules = listOf(
                OptionSignalRule(FactorKey.RET20, 0.40),
                OptionSignalRule(FactorKey.RET60, 0.30),
                OptionSignalRule(FactorKey.VOLUME_RATIO, 0.30, threshold = 1.0),
            ),
        ),
        OptionSignalPreset(
            id = "composite",
            name = "综合方向",
            subtitle = "RSRS + LLT + MA + 动量",
            description = "四个趋势/动量因子等权投票，降低单因子反复翻向。",
            rules = listOf(
                OptionSignalRule(FactorKey.RSRS_RIGHT, 0.30),
                OptionSignalRule(FactorKey.LLT_GAP, 0.25),
                OptionSignalRule(FactorKey.MA_TREND, 0.25),
                OptionSignalRule(FactorKey.RET20, 0.20),
            ),
        ),
    )

    fun byId(id: String): OptionSignalPreset? = all.firstOrNull { it.id == id }
}

object OptionSignalEngine {
    fun evaluate(panel: Panel, rules: List<OptionSignalRule>, date: String): OptionSignal {
        val last = panel.lastIndex
        if (last < 0 || rules.isEmpty()) {
            return OptionSignal(OptionDirection.NEUTRAL, 0.0, date, emptyList())
        }
        var weighted = 0.0
        var total = 0.0
        val details = ArrayList<OptionSignalDetail>(rules.size)
        rules.forEach { rule ->
            val v = FactorEngine.value(panel, 0, last, rule.key)
            val vote = if (v == null) {
                0.0
            } else {
                val raw = sign(v - rule.threshold)
                raw * (if (rule.key.higherIsBetter) 1.0 else -1.0)
            }
            details += OptionSignalDetail(rule.key, v, vote, rule.weight)
            if (v != null) {
                weighted += vote * rule.weight
                total += rule.weight
            }
        }
        val score = if (total > 0) weighted / total else 0.0
        val direction = when {
            score >= 0.25 -> OptionDirection.BULL
            score <= -0.25 -> OptionDirection.BEAR
            else -> OptionDirection.NEUTRAL
        }
        return OptionSignal(direction, score, date, details)
    }
}

data class OptionPosition(
    val contractCode: String,
    val name: String,
    val type: OptionType,
    val strike: Double,
    val expiry: String,
    val entryPrice: Double,
    val quantity: Long,
    val entryDate: String,
)

data class OptionPaperStrategy(
    val id: String,
    val name: String,
    val underlying: String,
    val presetId: String,
    val presetName: String,
    val rules: List<OptionSignalRule>,
    val strikeOffset: Int,
    val lots: Int,
    val createdAt: Long,
    val entryDate: String,
    val lastDate: String,
    val lastSignal: OptionDirection,
    val lastAction: String,
    val position: OptionPosition?,
    val closed: Boolean = false,
)

data class OptionPaperView(
    val strategy: OptionPaperStrategy,
    val signal: OptionSignal,
    val currentPrice: Double,
    val pnl: Double,
    val pnlPct: Double,
    val expired: Boolean,
)

object OptionSelector {
    fun nextExpiry(contracts: List<OptionContract>, underlying: String, today: String, minDays: Int = 3): String? {
        val minDate = today.plusDaysCompat(minDays)
        return contracts
            .filter { it.underlying == underlying && it.expiry >= minDate }
            .map { it.expiry }
            .distinct()
            .sorted()
            .firstOrNull()
    }

    fun target(
        contracts: List<OptionContract>,
        underlying: String,
        direction: OptionDirection,
        strikeOffset: Int,
        underlyingPrice: Double,
        today: String,
    ): OptionContract? {
        if (direction == OptionDirection.NEUTRAL) return null
        val expiry = nextExpiry(contracts, underlying, today) ?: return null
        val type = if (direction == OptionDirection.BULL) OptionType.CALL else OptionType.PUT
        val list = contracts
            .filter { it.underlying == underlying && it.expiry == expiry && it.type == type && it.last > 0 }
            .sortedBy { it.strike }
        if (list.isEmpty()) return null
        val atm = list.indices.minByOrNull { abs(list[it].strike - underlyingPrice) } ?: 0
        return list[(atm + strikeOffset).coerceIn(0, list.lastIndex)]
    }
}

object OptionPaperEngine {
    /**
     * 每日收盘后调用：按 ETF 方向信号买 call/put；方向反转或到期时卖出旧仓、买入新仓。
     * 只买期权，不卖裸期权；卖出只用于平仓/调仓。
     */
    fun rebalance(
        strategy: OptionPaperStrategy,
        signal: OptionSignal,
        contracts: List<OptionContract>,
        underlyingPrice: Double,
        today: String,
    ): OptionPaperStrategy {
        val target = OptionSelector.target(
            contracts = contracts,
            underlying = strategy.underlying,
            direction = signal.direction,
            strikeOffset = strategy.strikeOffset,
            underlyingPrice = underlyingPrice,
            today = today,
        )
        val pos = strategy.position
        val expired = pos != null && today >= pos.expiry
        val mustRoll = pos != null && pos.expiry <= today.plusDaysCompat(2)

        fun buy(target: OptionContract): OptionPosition = OptionPosition(
            contractCode = target.code,
            name = target.name,
            type = target.type,
            strike = target.strike,
            expiry = target.expiry,
            entryPrice = target.last,
            quantity = strategy.lots.toLong(),
            entryDate = today,
        )

        val (newPos, action) = when {
            signal.direction == OptionDirection.NEUTRAL -> {
                if (pos != null) null to "卖出平仓 · 信号中性" else null to "空仓 · 信号中性"
            }
            target == null -> {
                if (pos != null) null to "卖出平仓 · 无可用合约" else null to "空仓 · 无可用合约"
            }
            pos == null || expired || mustRoll || pos.contractCode != target.code -> {
                val action = if (pos == null) {
                    "买入 ${target.type.label} ${target.strike}"
                } else {
                    "卖出 ${pos.contractCode} · 买入 ${target.type.label} ${target.strike}"
                }
                buy(target) to action
            }
            else -> pos to "持有 ${pos.type.label} ${pos.strike}"
        }
        return strategy.copy(
            lastDate = today,
            lastSignal = signal.direction,
            lastAction = action,
            position = newPos,
        )
    }

    fun mark(
        strategy: OptionPaperStrategy,
        contracts: List<OptionContract>,
        underlyingPrice: Double,
        today: String,
    ): OptionPaperView {
        val pos = strategy.position
        val contract = pos?.let { p -> contracts.firstOrNull { it.code == p.contractCode } }
        val price = when {
            pos == null -> 0.0
            today >= pos.expiry -> {
                if (pos.type == OptionType.CALL) max(0.0, underlyingPrice - pos.strike)
                else max(0.0, pos.strike - underlyingPrice)
            }
            else -> contract?.last ?: pos.entryPrice
        }
        val signal = OptionSignal(strategy.lastSignal, 0.0, strategy.lastDate, emptyList())
        val pnl = if (pos == null) 0.0 else (price - pos.entryPrice) * pos.quantity * 10_000.0
        val pnlPct = if (pos != null && pos.entryPrice > 0) price / pos.entryPrice - 1.0 else 0.0
        return OptionPaperView(strategy, signal, price, pnl, pnlPct, pos != null && today >= pos.expiry)
    }
}

private fun String.plusDaysCompat(days: Int): String =
    runCatching { java.time.LocalDate.parse(this).plusDays(days.toLong()).toString() }
        .getOrDefault(this)
