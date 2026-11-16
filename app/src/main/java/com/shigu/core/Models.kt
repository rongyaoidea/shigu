package com.shigu.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Board(val label: String, val limitPct: Double) {
    SH_MAIN("沪主板", 10.0),
    SZ_MAIN("深主板", 10.0),
    CHINEXT("创业板", 20.0),
    STAR("科创板", 20.0),
    BSE("北交所", 30.0),
    ETF("ETF", 10.0),
    INDEX("指数", 0.0),
}

/** 企业性质：按东方财富 F10 的实际控制人 / 第一大股东名称推断，不是官方标签。 */
enum class OwnerType(val label: String) {
    CENTRAL_SOE("央企"),
    LOCAL_SOE("地方国企"),
    PRIVATE("民企"),
    FOREIGN("外资"),
    NO_CONTROLLER("无实控人"),
    UNKNOWN("未知"),
}

/** ST 状态：从证券简称解析，当前快照。 */
enum class StStatus(val label: String) {
    NORMAL("正常"),
    ST("ST"),
    STAR_ST("*ST"),
    DELISTING("退市整理"),
    ;

    companion object {
        fun fromName(name: String): StStatus = when {
            name.contains("退") -> DELISTING
            name.contains("*ST") -> STAR_ST
            name.contains("ST") -> ST
            else -> NORMAL
        }
    }
}

data class Stock(
    val code: String,
    val name: String,
    val secid: String,
    val board: Board,
    val price: Double = 0.0,
    val changePct: Double = 0.0,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val prevClose: Double = 0.0,
    val volume: Double = 0.0,
    val amount: Double = 0.0,
    val turnover: Double = 0.0,
    val pe: Double? = null,
    val pb: Double? = null,
    val marketCap: Double? = null,
    val floatCap: Double? = null,
    val volumeRatio: Double? = null,
    val change60: Double? = null,
    val changeYtd: Double? = null,
    val stStatus: StStatus = StStatus.NORMAL,
    val suspended: Boolean = false,
    val updatedAt: Long = 0L,
    // —— 基础资料（东方财富 F10 批量同步）——
    val ownerType: OwnerType = OwnerType.UNKNOWN,
    val listedDate: String = "",
    val actualHolder: String = "",
    val firstHolder: String = "",
    val firstHolderType: String = "",
    val firstHoldRatio: Double? = null,
    val reportDate: String = "",
    val roe: Double? = null,
    val grossMargin: Double? = null,
    val netMargin: Double? = null,
    val revenueGrowth: Double? = null,
    val profitGrowth: Double? = null,
    val debtRatio: Double? = null,
    val eps: Double? = null,
    val bps: Double? = null,
    val ocfps: Double? = null,
    val profileUpdatedAt: Long = 0L,
) {
    val isSt: Boolean get() = stStatus != StStatus.NORMAL

    val limitPct: Double
        get() = if (
            stStatus in setOf(StStatus.ST, StStatus.STAR_ST) &&
            board in setOf(Board.SH_MAIN, Board.SZ_MAIN)
        ) 5.0 else board.limitPct

    /** 上市自然日；用行情日计算，避免回测时用到“今天”的未来信息。 */
    fun listedDaysAt(date: String): Int? {
        if (listedDate.length < 10 || date.length < 10) return null
        return runCatching {
            val from = LocalDate.parse(listedDate.take(10))
            val to = LocalDate.parse(date.take(10))
            ChronoUnit.DAYS.between(from, to).toInt()
        }.getOrNull()
    }

    val marketLabel: String
        get() = when {
            board == Board.INDEX -> "指数"
            board == Board.ETF -> if (code.startsWith("5")) "沪" else "深"
            code.startsWith("6") -> "沪"
            else -> "深"
        }

    val peLabel: String
        get() = when {
            pe == null -> "—"
            pe <= 0 -> "亏损"
            else -> format1(pe)
        }
}

data class DailyBar(
    val code: String,
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val amount: Double,
    val pct: Double,
    val turnover: Double,
)

data class ValuationPoint(
    val code: String,
    val date: String,
    val pe: Double?,
    val pb: Double?,
    val marketCap: Double?,
    val close: Double?,
)

data class WatchItem(
    val code: String,
    val name: String,
    val addedAt: Long,
    val entryPrice: Double,
    val thesis: String,
    val group: String,
    val price: Double,
    val changePct: Double,
    val board: Board,
) {
    val returnPct: Double
        get() = if (entryPrice > 0.0) price / entryPrice - 1.0 else 0.0
}

/**
 * 因子库。方向由 [higherIsBetter] 决定；权重永远为正，
 * 打分时 direction 会乘到 z-score 上。
 */
data class FactorWeight(val key: FactorKey, val weight: Double = 0.2)

data class StrategyFilters(
    val minPrice: Double = 2.0,
    val minAmount20: Double = 50_000_000.0,
    val maxPe: Double? = null,
    val maxPb: Double? = null,
    val minMarketCap: Double? = null,
    val maxMarketCap: Double? = null,
    val excludeSt: Boolean = true,
    val stStatuses: Set<StStatus> = setOf(StStatus.NORMAL),
    val owners: Set<OwnerType> = OwnerType.entries.toSet(),
    val minListedDays: Int = 0,
    val excludeSuspended: Boolean = false,
    val boards: Set<Board> = setOf(Board.SH_MAIN, Board.SZ_MAIN, Board.CHINEXT, Board.STAR, Board.BSE, Board.ETF),
) {
    val isDefault: Boolean
        get() = this == StrategyFilters()

    val ownerLabel: String
        get() = if (owners.size >= OwnerType.entries.size) "全部性质" else owners.joinToString("/") { it.label }

    val stLabel: String
        get() = if (stStatuses.size >= StStatus.entries.size) "全部ST状态" else stStatuses.joinToString("/") { it.label }
}

data class StrategyConfig(
    val id: String,
    val name: String,
    val subtitle: String,
    val evidence: String,
    val caveat: String,
    val factors: List<FactorWeight>,
    val filters: StrategyFilters = StrategyFilters(),
    val rebalanceDays: Int = 20,
    val maxPositions: Int = 10,
    val stopLossPct: Double? = null,
    val takeProfitPct: Double? = null,
    val maxHoldDays: Int? = null,
    val lookbackDays: Int = 500,
    val universeSize: Int = 800,
    val costBps: Double = 8.0,
    val builtin: Boolean = false,
    val createdAt: Long = 0L,
) {
    val factorsLabel: String
        get() = factors.joinToString(" · ") { "${it.key.shortLabel} ${(it.weight * 100).toInt()}%" }
}

data class Pick(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val score: Double,
    val factors: Map<FactorKey, Double?>,
    val board: Board,
    val isSt: Boolean,
)

data class Holding(
    val code: String,
    val name: String,
    val shares: Long,
    val entryPrice: Double,
    val lastPrice: Double,
    val weight: Double,
    val entryDate: String,
)

data class PortfolioSnapshot(
    val cash: Double,
    val equity: Double,
    val holdings: List<Holding>,
)

data class StrategyChange(
    val strategyId: String,
    val strategyName: String,
    val date: String,
    val buys: List<String>,
    val sells: List<String>,
    val holds: Int,
) {
    val hasChange: Boolean get() = buys.isNotEmpty() || sells.isNotEmpty()
    val text: String
        get() {
            if (!hasChange) return "今日无调仓 · 持仓 $holds 只"
            val b = if (buys.isEmpty()) "" else "买入 ${buys.size} 只"
            val s = if (sells.isEmpty()) "" else "卖出 ${sells.size} 只"
            return listOf(b, s).filter { it.isNotEmpty() }.joinToString(" · ") + " · 持仓 $holds 只"
        }
}

data class SimulationRefresh(
    val since: Map<String, SinceCreationResult>,
    val changes: List<StrategyChange>,
)

data class PaperDetail(
    val config: StrategyConfig,
    val equity: List<EquityPoint>,
    val holdings: List<Holding>,
    val cash: Double,
    val returnPct: Double,
    val benchmarkPct: Double,
    val change: StrategyChange?,
)

data class EquityPoint(val date: String, val strategy: Double, val benchmark: Double)

data class Trade(
    val code: String,
    val name: String,
    val entryDate: String,
    val exitDate: String?,
    val entryPrice: Double,
    val exitPrice: Double?,
    val shares: Long,
    val pnl: Double,
    val pnlPct: Double,
    val holdDays: Int,
    val reason: String,
)

data class BacktestStats(
    val startDate: String,
    val endDate: String,
    val totalReturn: Double,
    val annualized: Double,
    val benchmarkReturn: Double,
    val excess: Double,
    val maxDrawdown: Double,
    val sharpe: Double,
    val volatility: Double,
    val winRate: Double,
    val tradeCount: Int,
    val avgHoldDays: Double,
    val universeSize: Int,
    val note: String,
)

data class BacktestResult(
    val config: StrategyConfig,
    val stats: BacktestStats,
    val equity: List<EquityPoint>,
    val trades: List<Trade>,
    val picks: List<Pick>,
    val warning: String? = null,
    val portfolio: PortfolioSnapshot? = null,
)

data class SinceCreationResult(
    val returnPct: Double,
    val benchmarkPct: Double,
    val startDate: String,
    val endDate: String,
    val points: Int,
    val picks: List<Pick>,
    val portfolio: PortfolioSnapshot? = null,
    val equity: List<EquityPoint> = emptyList(),
)

data class ScreenerResult(
    val rows: List<Pick>,
    val totalCandidates: Int,
    val poolSize: Int,
    val factorValues: Map<FactorKey, Double> = emptyMap(),
)

enum class SyncMode { QUICK, DAILY, FULL, VALUATION, PROFILE }

enum class Universe(val label: String, val limit: Int) {
    QUICK("快速池 · 市值前300", 300),
    TOP_800("市值前800", 800),
    TOP_1500("市值前1500", 1500),
    ALL("全部A股", 6000),
}

data class SyncState(
    val running: Boolean = false,
    val mode: SyncMode? = null,
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val current: String = "",
    val message: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class DataStats(
    val stockCount: Int = 0,
    val barCount: Long = 0,
    val valuationCount: Long = 0,
    val lastSync: Long = 0,
    val lastTradeDate: String = "",
    val dbBytes: Long = 0,
    val profileCount: Int = 0,
    val profileReportDate: String = "",
)

fun format1(v: Double?): String = if (v == null || v.isNaN()) "—" else String.format(Locale.CHINA, "%.1f", v)
fun format2(v: Double?): String = if (v == null || v.isNaN()) "—" else String.format(Locale.CHINA, "%.2f", v)
fun formatPct(v: Double?, digits: Int = 2): String {
    if (v == null || v.isNaN()) return "—"
    val s = String.format(Locale.CHINA, if (digits == 1) "%.1f" else "%.2f", v * 100.0)
    return if (v > 0) "+$s%" else "$s%"
}
fun formatPp(v: Double?): String {
    if (v == null || v.isNaN()) return "—"
    val s = String.format(Locale.CHINA, "%.1f", v * 100.0)
    return if (v > 0) "+${s}pp" else "${s}pp"
}
fun formatYi(v: Double?): String = when {
    v == null || v.isNaN() -> "—"
    v >= 1e12 -> String.format(Locale.CHINA, "%.2f万亿", v / 1e12)
    v >= 1e8 -> String.format(Locale.CHINA, "%.1f亿", v / 1e8)
    v >= 1e4 -> String.format(Locale.CHINA, "%.0f万", v / 1e4)
    else -> String.format(Locale.CHINA, "%.0f", v)
}
