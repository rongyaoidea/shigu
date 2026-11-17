package com.shigu.data

import com.shigu.core.SyncMode
import com.shigu.core.SyncState
import com.shigu.core.Universe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

object SyncBus {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    @Volatile
    var cancelRequested: Boolean = false
        private set

    fun requestCancel() {
        cancelRequested = true
    }

    fun resetCancel() {
        cancelRequested = false
    }

    fun checkCancelled() {
        if (cancelRequested) throw kotlinx.coroutines.CancellationException("同步已取消")
    }

    fun update(block: (SyncState) -> SyncState) {
        _state.value = block(_state.value)
    }

    fun reset() {
        _state.value = SyncState()
    }
}

class SyncManager(
    private val db: ShiguDb,
    private val api: EastmoneyApi,
    private val settings: Settings,
) {
    private val mutex = Mutex()

    fun cancel() {
        SyncBus.requestCancel()
    }

    suspend fun sync(
        mode: SyncMode,
        universe: Universe = settings.dailyUniverse,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) {
            return@withContext Result.failure(IllegalStateException("同步正在进行"))
        }
        try {
            SyncBus.resetCancel()
            SyncBus.update {
                SyncState(
                    running = true,
                    mode = mode,
                    phase = "获取股票列表",
                    message = "新浪列表 + 腾讯日线 · 延迟约 15 分钟",
                    startedAt = System.currentTimeMillis(),
                )
            }
            val stocks = api.fetchStockList()
            SyncBus.checkCancelled()
            if (stocks.isEmpty()) error("股票列表为空")            // 部分分页失败时不要把旧代码清掉：新列表至少要和上次差不多大。
            val previousCount = db.countStocks()
            if (stocks.size < 3000) {
                error("股票列表异常：本次仅 ${stocks.size} 只，已停止清理，请重试")
            }
            if (previousCount >= 3000 && stocks.size < previousCount * 0.98) {
                error("股票列表异常：本次 ${stocks.size} 只，上次 $previousCount 只；已停止清理，请重试")
            }
            db.upsertStocks(stocks)
            db.pruneStocks(stocks.map { it.code })
            db.recordStatusSnapshot(stocks, LocalDate.now().toString())
            val today = LocalDate.now().toString()
            db.upsertValuations(
                stocks.map {
                    com.shigu.core.ValuationPoint(
                        code = it.code,
                        date = today,
                        pe = it.pe,
                        pb = it.pb,
                        marketCap = it.marketCap,
                        close = it.price,
                    )
                },
            )

            SyncBus.update { it.copy(phase = "同步沪深300基准") }
            val bench = api.fetchBenchmark(700)
            if (bench.isNotEmpty()) db.upsertBars(bench)

            val all = db.getStocks()
            val codes = selectCodes(all, universe, mode)
            val total = codes.size
            val done = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val pending = ArrayList<com.shigu.core.DailyBar>(4096)
            val pendingLock = Mutex()
            val sem = Semaphore(12)
            val todayDate = LocalDate.now()

            SyncBus.update {
                it.copy(phase = "同步日线", total = total, done = 0, message = "并发 12 · 可锁屏，进度会保留")
            }

            coroutineScope {
                codes.map { code ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            try {
                                SyncBus.checkCancelled()
                                val last = db.getLastBarDate(code)
                                val limit = when {
                                    mode == SyncMode.FULL || last == null -> 300
                                    else -> {
                                        val days = runCatching {
                                            ChronoUnit.DAYS.between(LocalDate.parse(last), todayDate).toInt()
                                        }.getOrDefault(30)
                                        if (days <= 0) 0 else (days + 10).coerceIn(10, 300)
                                    }
                                }
                                if (limit > 0) {
                                    val stock = all.firstOrNull { it.code == code }
                                    val secid = stock?.secid ?: code
                                    val floatShares = stock?.let {
                                        if (it.price > 0) (it.floatCap ?: 0.0) / it.price else 0.0
                                    } ?: 0.0
                                    val bars = api.fetchDailyBars(secid, limit, floatShares)
                                    if (bars.isNotEmpty()) {
                                        pendingLock.withLock {
                                            pending += bars
                                            if (pending.size >= 24_000) {
                                                db.upsertBars(pending.toList())
                                                pending.clear()
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                failed.incrementAndGet()
                            } finally {
                                val d = done.incrementAndGet()
                                if (d % 10 == 0 || d == total) {
                                    SyncBus.update {
                                        it.copy(
                                            done = d,
                                            failed = failed.get(),
                                            current = code,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            pendingLock.withLock {
                if (pending.isNotEmpty()) {
                    db.upsertBars(pending.toList())
                    pending.clear()
                }
            }

            val finish = System.currentTimeMillis()
            db.putMeta("last_sync", finish.toString())
            db.putMeta("last_universe", universe.name)
            settings.lastSync = finish
            SyncBus.update {
                it.copy(
                    running = false,
                    phase = "完成",
                    done = total,
                    total = total,
                    failed = failed.get(),
                    message = "股票 ${db.countStocks()} 只 · 日线 ${db.countBars()} 条 · 失败 ${failed.get()} 只",
                    finishedAt = finish,
                )
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            SyncBus.update {
                it.copy(running = false, phase = "已取消", message = "同步已取消", finishedAt = System.currentTimeMillis())
            }
            Result.failure(e)
        } catch (e: Exception) {
            SyncBus.update {
                it.copy(
                    running = false,
                    phase = "失败",
                    message = e.message ?: "同步失败",
                    finishedAt = System.currentTimeMillis(),
                )
            }
            Result.failure(e)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 估值历史：按股票拉 1000 条（约 4 年）。用于价值/低 PE 因子回测。
     * 只对回测池做，避免一次性请求全市场。
     */
    suspend fun syncValuationHistory(
        universe: Universe = Universe.TOP_800,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext Result.failure(IllegalStateException("同步正在进行"))
        try {
            SyncBus.resetCancel()
            SyncBus.update {
                SyncState(
                    running = true,
                    mode = SyncMode.VALUATION,
                    phase = "准备估值历史",
                    message = "PE/PB/市值 · 每只约 1000 条",
                    startedAt = System.currentTimeMillis(),
                )
            }
            val all = db.getStocks()
            val codes = selectCodes(all, universe, SyncMode.VALUATION)
            val total = codes.size
            val done = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val sem = Semaphore(10)
            SyncBus.update { it.copy(total = total, phase = "同步估值历史") }
            coroutineScope {
                codes.map { code ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            try {
                                SyncBus.checkCancelled()
                                val v = api.fetchValuationHistory(code)
                                if (v.isNotEmpty()) db.upsertValuations(v)
                            } catch (_: Exception) {
                                failed.incrementAndGet()
                            } finally {
                                val d = done.incrementAndGet()
                                if (d % 10 == 0 || d == total) {
                                    SyncBus.update { it.copy(done = d, failed = failed.get(), current = code) }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            SyncBus.update {
                it.copy(
                    running = false,
                    phase = "完成",
                    done = total,
                    total = total,
                    failed = failed.get(),
                    message = "估值 ${db.countValuations()} 条",
                    finishedAt = System.currentTimeMillis(),
                )
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            SyncBus.update { it.copy(running = false, phase = "已取消", message = "同步已取消", finishedAt = System.currentTimeMillis()) }
            Result.failure(e)
        } catch (e: Exception) {
            SyncBus.update { it.copy(running = false, phase = "失败", message = e.message ?: "估值同步失败", finishedAt = System.currentTimeMillis()) }
            Result.failure(e)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 基础资料：企业性质（实控人/第一大股东推断）、上市日期、最新报告期财务快照。
     * 走东方财富 F10 批量分页，不逐只请求。
     */
    suspend fun syncProfiles(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext Result.failure(IllegalStateException("同步正在进行"))
        try {
            SyncBus.resetCancel()
            val startedAt = System.currentTimeMillis()
            SyncBus.update {
                SyncState(
                    running = true,
                    mode = SyncMode.PROFILE,
                    phase = "确定报告期",
                    message = "企业性质 · 上市日期 · 财务快照",
                    startedAt = startedAt,
                )
            }
            val reportDate = api.resolveReportDate()
            if (reportDate.isBlank()) error("无法确定最新报告期")
            SyncBus.checkCancelled()

            SyncBus.update {
                it.copy(phase = "同步企业资料", message = "报告期 $reportDate · 实控人/上市日期", done = 0, total = 0)
            }
            val orgs = api.fetchOrgProfiles { done, total ->
                SyncBus.checkCancelled()
                SyncBus.update { it.copy(phase = "同步企业资料", done = done, total = total) }
            }

            SyncBus.update {
                it.copy(phase = "同步财务快照", message = "报告期 $reportDate · ROE/毛利率/增速/负债率", done = 0, total = 0)
            }
            val finances = api.fetchFinanceProfiles(reportDate) { done, total ->
                SyncBus.checkCancelled()
                SyncBus.update { it.copy(phase = "同步财务快照", done = done, total = total) }
            }
            SyncBus.checkCancelled()

            SyncBus.update {
                it.copy(phase = "同步第一大股东", message = "报告期 $reportDate · 用于辅助判断企业性质", done = 0, total = 0)
            }
            val holders = api.fetchHolderProfiles(reportDate) { done, total ->
                SyncBus.checkCancelled()
                SyncBus.update { it.copy(phase = "同步第一大股东", done = done, total = total) }
            }

            val profiles = com.shigu.data.ProfileAssembler.assemble(
                orgs = orgs,
                holders = holders,
                finances = finances.associateBy { it.code },
            )
            db.updateProfiles(profiles)
            db.upsertFundamentals(finances)
            db.recordStatusSnapshot(db.getStocks(), LocalDate.now().toString())
            db.putMeta("profile_report_date", reportDate)
            db.putMeta("profile_sync", System.currentTimeMillis().toString())
            val covered = db.countProfiles()
            SyncBus.update {
                it.copy(
                    running = false,
                    phase = "完成",
                    done = profiles.size,
                    total = profiles.size,
                    message = "基础资料 $covered 只 · 报告期 $reportDate · 原始实控人/股东可在个股详情复核",
                    finishedAt = System.currentTimeMillis(),
                )
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            SyncBus.update { it.copy(running = false, phase = "已取消", message = "同步已取消", finishedAt = System.currentTimeMillis()) }
            Result.failure(e)
        } catch (e: Exception) {
            SyncBus.update {
                it.copy(
                    running = false,
                    phase = "失败",
                    message = e.message ?: "基础资料同步失败",
                    finishedAt = System.currentTimeMillis(),
                )
            }
            Result.failure(e)
        } finally {
            mutex.unlock()
        }
    }

    private fun selectCodes(all: List<com.shigu.core.Stock>, universe: Universe, mode: SyncMode): List<String> {        val list = all.filter { it.board != com.shigu.core.Board.INDEX }
        val limit = if (universe == Universe.ALL) list.size else universe.limit
        val stocks = list.filter { it.board != com.shigu.core.Board.ETF }
            .sortedByDescending { it.marketCap ?: 0.0 }
        val etfs = list.filter { it.board == com.shigu.core.Board.ETF }
            .sortedByDescending { it.amount }
        val etfTake = if (limit >= list.size) etfs.size else minOf(etfs.size, maxOf(20, limit / 5))
        val stockTake = (limit - etfTake).coerceAtLeast(0)
        return (stocks.take(stockTake) + etfs.take(etfTake)).map { it.code }
    }

    private fun secOf(code: String, all: List<com.shigu.core.Stock>): String =
        all.firstOrNull { it.code == code }?.secid ?: code
}
