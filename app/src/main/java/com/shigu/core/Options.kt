package com.shigu.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

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

enum class OptionSignalKind {
    FACTOR_VOTE,
    MFI_REVERSION,
    RSI_REVERSION,
    BOLL_REVERSION,
    PANIC_1D,
    PANIC_5D,
}

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
    val kind: OptionSignalKind = OptionSignalKind.FACTOR_VOTE,
    val params: Map<String, Double> = emptyMap(),
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
    private val base: List<OptionSignalPreset> = listOf(
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
        OptionSignalPreset(
            id = "mfi_reversion",
            name = "MFI 超跌反转",
            subtitle = "20/80 · 持有到反向极值",
            description = "MFI14 < 20 买 call，MFI14 > 80 买 put；持有到反向极值再调仓。5 年期权回测在 510050/510300 均为正。",
            rules = emptyList(),
            kind = OptionSignalKind.MFI_REVERSION,
            params = mapOf("low" to 20.0, "high" to 80.0),
        ),
        OptionSignalPreset(
            id = "mfi_reversion_wide",
            name = "MFI 宽幅反转",
            subtitle = "25/75 · 信号更多",
            description = "把 MFI 阈值放宽到 25/75，交易更频繁；5 年收益更高，但高 IV 压力测试下 510300 会转负。",
            rules = emptyList(),
            kind = OptionSignalKind.MFI_REVERSION,
            params = mapOf("low" to 25.0, "high" to 75.0),
        ),
        OptionSignalPreset(
            id = "rsi21_reversion",
            name = "RSI21 超跌反转",
            subtitle = "25/75 · 更平滑",
            description = "RSI21 < 25 买 call，RSI21 > 75 买 put；持有到反向极值。5 年收益高，但高 IV 下 510300 不稳。",
            rules = emptyList(),
            kind = OptionSignalKind.RSI_REVERSION,
            params = mapOf("low" to 25.0, "high" to 75.0),
        ),
        OptionSignalPreset(
            id = "panic_1d",
            name = "单日恐慌反弹",
            subtitle = "跌 3% 买 call",
            description = "单日跌幅 > 3% 后买 call；反弹 1% 或 RSI14 > 55 卖出。5 年回测回撤小、胜率高。",
            rules = emptyList(),
            kind = OptionSignalKind.PANIC_1D,
            params = mapOf("drop" to 0.03, "gain" to 0.01, "rsi" to 55.0),
        ),
        OptionSignalPreset(
            id = "panic_5d",
            name = "五日恐慌反弹",
            subtitle = "5 日跌 6% 买 call",
            description = "5 日跌幅 > 6% 后买 call；5 日反弹 2% 或 RSI14 > 55 卖出。5 年回测回撤小。",
            rules = emptyList(),
            kind = OptionSignalKind.PANIC_5D,
            params = mapOf("drop" to 0.06, "gain" to 0.02, "rsi" to 55.0),
        ),
        OptionSignalPreset(
            id = "boll_reversion_30",
            name = "布林30日回归",
            subtitle = "30日 ±2σ",
            description = "跌破 30 日布林下轨买 call，突破上轨买 put；持有到反向轨道。5 年基准下两个 ETF 均跑赢，但 IV 1.20 压力下会转负。",
            rules = emptyList(),
            kind = OptionSignalKind.BOLL_REVERSION,
            params = mapOf("n" to 30.0, "k" to 2.0),
        ),
    )

    private val featured = setOf(
        "mfi_reversion",
        "mfi_reversion_wide",
        "rsi21_reversion",
        "panic_1d",
        "panic_5d",
        "boll_reversion_30",
    )

    /** 回测优选排在最前，原趋势策略保留作对照。 */
    val all: List<OptionSignalPreset> = base.sortedBy { if (it.id in featured) 0 else 1 }

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

    /** 按预设类型评估当天方向；自定义因子仍走 [evaluate] 的投票逻辑。 */
    fun evaluate(panel: Panel, preset: OptionSignalPreset, date: String): OptionSignal {
        if (preset.kind == OptionSignalKind.FACTOR_VOTE) return evaluate(panel, preset.rules, date)
        val dirs = series(panel, preset)
        val last = panel.lastIndex
        val direction = when {
            last < 0 -> OptionDirection.NEUTRAL
            dirs[last] > 0 -> OptionDirection.BULL
            dirs[last] < 0 -> OptionDirection.BEAR
            else -> OptionDirection.NEUTRAL
        }
        val details = ArrayList<OptionSignalDetail>(2)
        when (preset.kind) {
            OptionSignalKind.MFI_REVERSION -> {
                val v = FactorEngine.value(panel, 0, last, FactorKey.MFI14)
                details += OptionSignalDetail(FactorKey.MFI14, v, direction.value, 1.0)
            }
            OptionSignalKind.RSI_REVERSION -> {
                val v = FactorEngine.value(panel, 0, last, FactorKey.RSI21)
                details += OptionSignalDetail(FactorKey.RSI21, v, direction.value, 1.0)
            }
            OptionSignalKind.BOLL_REVERSION -> {
                val v = FactorEngine.value(panel, 0, last, FactorKey.BOLL_Z)
                details += OptionSignalDetail(FactorKey.BOLL_Z, v, direction.value, 1.0)
            }
            OptionSignalKind.PANIC_1D, OptionSignalKind.PANIC_5D -> {
                val key = if (preset.kind == OptionSignalKind.PANIC_1D) FactorKey.RET1 else FactorKey.RET5
                details += OptionSignalDetail(key, FactorEngine.value(panel, 0, last, key), direction.value, 1.0)
                details += OptionSignalDetail(FactorKey.RSI14, FactorEngine.value(panel, 0, last, FactorKey.RSI14), direction.value, 1.0)
            }
            OptionSignalKind.FACTOR_VOTE -> Unit
        }
        return OptionSignal(direction, direction.value, date, details)
    }

    /** 生成整段方向序列，供历史回测使用；+1 看涨、-1 看跌、0 空仓。 */
    fun series(panel: Panel, preset: OptionSignalPreset): IntArray = when (preset.kind) {
        OptionSignalKind.FACTOR_VOTE -> factorSeries(panel, preset.rules)
        OptionSignalKind.MFI_REVERSION -> hysteresis(
            valueSeries(panel, FactorKey.MFI14),
            preset.params["low"] ?: 20.0,
            preset.params["high"] ?: 80.0,
        )
        OptionSignalKind.RSI_REVERSION -> hysteresis(
            valueSeries(panel, FactorKey.RSI21),
            preset.params["low"] ?: 25.0,
            preset.params["high"] ?: 75.0,
        )
        OptionSignalKind.BOLL_REVERSION -> bollSeries(
            panel = panel,
            n = (preset.params["n"] ?: 30.0).toInt(),
            k = preset.params["k"] ?: 2.0,
        )
        OptionSignalKind.PANIC_1D -> panicSeries(
            panel = panel,
            days = 1,
            drop = preset.params["drop"] ?: 0.03,
            gain = preset.params["gain"] ?: 0.01,
            rsiExit = preset.params["rsi"] ?: 55.0,
        )
        OptionSignalKind.PANIC_5D -> panicSeries(
            panel = panel,
            days = 5,
            drop = preset.params["drop"] ?: 0.06,
            gain = preset.params["gain"] ?: 0.02,
            rsiExit = preset.params["rsi"] ?: 55.0,
        )
    }

    private fun valueSeries(panel: Panel, key: FactorKey): DoubleArray =
        DoubleArray(panel.calendar.size) { i -> FactorEngine.value(panel, 0, i, key) ?: Double.NaN }

    private fun factorSeries(panel: Panel, rules: List<OptionSignalRule>): IntArray {
        val n = panel.calendar.size
        if (rules.isEmpty()) return IntArray(n)
        val values = rules.map { it to valueSeries(panel, it.key) }
        return IntArray(n) { i ->
            var weighted = 0.0
            var total = 0.0
            values.forEach { (rule, series) ->
                val v = series[i]
                if (!v.isNaN()) {
                    val vote = sign(v - rule.threshold) * (if (rule.key.higherIsBetter) 1.0 else -1.0)
                    weighted += vote * rule.weight
                    total += rule.weight
                }
            }
            val score = if (total > 0) weighted / total else 0.0
            when {
                score >= 0.25 -> 1
                score <= -0.25 -> -1
                else -> 0
            }
        }
    }

    private fun hysteresis(values: DoubleArray, low: Double, high: Double): IntArray {
        val out = IntArray(values.size)
        var state = 0
        values.forEachIndexed { i, v ->
            if (!v.isNaN()) {
                when (state) {
                    0 -> when {
                        v < low -> state = 1
                        v > high -> state = -1
                    }
                    1 -> if (v > high) state = -1
                    -1 -> if (v < low) state = 1
                }
            }
            out[i] = state
        }
        return out
    }

    private fun bollSeries(panel: Panel, n: Int, k: Double): IntArray {
        val close = panel.close[0]
        val out = IntArray(close.size)
        var state = 0
        for (i in close.indices) {
            if (i < n - 1 || close[i].isNaN()) {
                out[i] = state
                continue
            }
            var sum = 0.0
            var sum2 = 0.0
            var count = 0
            for (j in i - n + 1..i) {
                val v = close[j]
                if (v.isNaN()) continue
                sum += v
                sum2 += v * v
                count++
            }
            if (count < n) {
                out[i] = state
                continue
            }
            val mean = sum / count
            val variance = (sum2 / count - mean * mean).coerceAtLeast(0.0)
            val sd = sqrt(variance)
            val lower = mean - k * sd
            val upper = mean + k * sd
            when (state) {
                0 -> when {
                    close[i] < lower -> state = 1
                    close[i] > upper -> state = -1
                }
                1 -> if (close[i] > upper) state = -1
                -1 -> if (close[i] < lower) state = 1
            }
            out[i] = state
        }
        return out
    }

    private fun panicSeries(panel: Panel, days: Int, drop: Double, gain: Double, rsiExit: Double): IntArray {
        val close = panel.close[0]
        val rsi = valueSeries(panel, FactorKey.RSI14)
        val out = IntArray(close.size)
        var state = 0
        for (i in close.indices) {
            val r = if (i - days < 0 || close[i - days] <= 0) Double.NaN else close[i] / close[i - days] - 1.0
            if (state == 0) {
                if (!r.isNaN() && r <= -drop) state = 1
            } else if (state == 1) {
                if ((!r.isNaN() && r >= gain) || (!rsi[i].isNaN() && rsi[i] > rsiExit)) state = 0
            }
            out[i] = state
        }
        return out
    }
}

private val OptionDirection.value: Double
    get() = when (this) {
        OptionDirection.BULL -> 1.0
        OptionDirection.BEAR -> -1.0
        OptionDirection.NEUTRAL -> 0.0
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

        val newPos: OptionPosition?
        val action: String
        when {
            signal.direction == OptionDirection.NEUTRAL -> {
                newPos = null
                action = if (pos != null) "卖出平仓 · 信号中性" else "空仓 · 信号中性"
            }
            target == null -> {
                newPos = null
                action = if (pos != null) "卖出平仓 · 无可用合约" else "空仓 · 无可用合约"
            }
            pos == null -> {
                newPos = buy(target)
                action = "买入 ${target.type.label} ${target.strike}"
            }
            expired || mustRoll || pos.type != target.type -> {
                newPos = buy(target)
                action = "卖出 ${pos.contractCode} · 买入 ${target.type.label} ${target.strike}"
            }
            else -> {
                newPos = pos
                action = "持有 ${pos.type.label} ${pos.strike}"
            }
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
