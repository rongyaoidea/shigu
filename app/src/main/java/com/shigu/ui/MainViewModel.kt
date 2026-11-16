package com.shigu.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shigu.ShiguApp
import com.shigu.core.BacktestResult
import com.shigu.core.DailyBar
import com.shigu.core.DataStats
import com.shigu.core.FactorEngine
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.PanelBuilder
import com.shigu.core.ScreenerResult
import com.shigu.core.SinceCreationResult
import com.shigu.core.Stock
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import com.shigu.core.StrategyPresets
import com.shigu.core.SyncMode
import com.shigu.core.SyncState
import com.shigu.core.Universe
import com.shigu.core.WatchItem
import com.shigu.data.SyncBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class StockDetailUi(
    val stock: Stock,
    val bars: List<DailyBar>,
    val factors: Map<FactorKey, Double?>,
    val watched: Boolean,
)

data class UiState(
    val stats: DataStats = DataStats(),
    val watchlist: List<WatchItem> = emptyList(),
    val sync: SyncState = SyncState(),
    val screenConfig: StrategyConfig = StrategyPresets.screenerPresets.first(),
    val screenPool: Int = 300,
    val screenResult: ScreenerResult? = null,
    val screenLoading: Boolean = false,
    val strategies: List<StrategyConfig> = emptyList(),
    val selectedStrategyId: String? = null,
    val autoUpdate: Boolean = true,
    val dailyUniverse: Universe = Universe.TOP_800,
    val summaries: Map<String, BacktestResult> = emptyMap(),
    val backtests: Map<String, BacktestResult> = emptyMap(),
    val since: Map<String, SinceCreationResult> = emptyMap(),
    val strategyChanges: List<com.shigu.core.StrategyChange> = emptyList(),
    val paperDetail: com.shigu.core.PaperDetail? = null,
    val paperDetailLoading: Boolean = false,
    val summaryLoading: Boolean = false,
    val summaryDone: Int = 0,
    val summaryTotal: Int = 0,
    val simulationLoading: Boolean = false,
    val backtestLoading: Boolean = false,
    val stockDetail: StockDetailUi? = null,
    val stockLoading: Boolean = false,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val shigu = app as ShiguApp
    private val db = shigu.db
    private val market = shigu.market
    private val repo = shigu.strategies
    private val paper = shigu.paper
    private val syncManager = shigu.syncManager
    private val settings = shigu.settings

    private val _state = MutableStateFlow(UiState(screenPool = 300))
    val state: StateFlow<UiState> = _state

    private var lastSyncFinished = 0L
    private var lastSimulationAt = 0L

    init {
        viewModelScope.launch {
            SyncBus.state.collect { s ->
                _state.update { it.copy(sync = s) }
                if (!s.running && s.finishedAt > 0 && s.finishedAt != lastSyncFinished) {
                    lastSyncFinished = s.finishedAt
                    refreshLocal()
                }
            }
        }
        viewModelScope.launch {
            refreshLocal()
            if (db.countStocks() == 0L) {
                startSync(SyncMode.QUICK, Universe.QUICK)
            }
        }
    }

    private suspend fun refreshLocal() {
        val stats = withContext(Dispatchers.IO) { db.stats() }
        val watch = withContext(Dispatchers.IO) { db.getWatchlist() }
        val strategies = withContext(Dispatchers.IO) { repo.list() }
        val selected = _state.value.selectedStrategyId
            ?: strategies.firstOrNull()?.id
        _state.update {
            it.copy(
                stats = stats,
                watchlist = watch,
                strategies = strategies,
                selectedStrategyId = selected,
                autoUpdate = settings.autoUpdate,
                dailyUniverse = settings.dailyUniverse,
                screenPool = if (it.screenPool <= 0) settings.backtestUniverse.limit else it.screenPool,
            )
        }
        if (stats.stockCount > 0 && _state.value.screenResult == null) runScreen()
        if (stats.stockCount > 0 && strategies.isNotEmpty() && _state.value.since.isEmpty()) {
            refreshSimulations()
        }
    }

    // ---------------- sync ----------------

    fun quickSync() = startSync(SyncMode.QUICK, Universe.QUICK)
    fun dailySync() = startSync(SyncMode.DAILY, settings.dailyUniverse)
    fun fullSync() = startSync(SyncMode.FULL, Universe.ALL)
    fun valuationSync() = startValuationSync(Universe.TOP_800)

    /** 企业性质/上市日期/财务快照；不跟行情同步绑定，避免每次更新都多打几十个请求。 */
    fun profileSync() {
        viewModelScope.launch {
            syncManager.syncProfiles()
            refreshLocal()
            runScreen()
        }
    }

    private fun startSync(mode: SyncMode, universe: Universe) {
        viewModelScope.launch {
            syncManager.sync(mode, universe)
            // 首次行情同步后顺手补一次基础资料；之后由数据页手动刷新，避免每天多打几十个请求。
            if (withContext(Dispatchers.IO) { db.countProfiles() } < 100) {
                syncManager.syncProfiles()
            }
            refreshLocal()
            refreshSimulations(force = true)
        }
    }

    private fun startValuationSync(universe: Universe) {
        viewModelScope.launch {
            syncManager.syncValuationHistory(universe)
            refreshLocal()
        }
    }

    fun setAutoUpdate(value: Boolean) {
        settings.autoUpdate = value
        com.shigu.data.SyncScheduler.schedule(shigu, settings)
        _state.update { it.copy(autoUpdate = value, message = if (value) "已开启每日 16:10 自动更新" else "已关闭自动更新") }
    }

    fun setDailyUniverse(u: Universe) {
        settings.dailyUniverse = u
        com.shigu.data.SyncScheduler.schedule(shigu, settings)
        _state.update { it.copy(dailyUniverse = u, message = "自动更新池：${u.label}") }
    }

    // ---------------- screen ----------------

    fun selectScreenPreset(id: String) {
        val preset = StrategyPresets.screenerPresets.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(screenConfig = preset) }
        runScreen()
    }

    fun setScreenPool(n: Int) {
        _state.update { it.copy(screenPool = n) }
        runScreen()
    }

    fun setAssetFilter(kind: String) {
        val all = setOf(
            com.shigu.core.Board.SH_MAIN, com.shigu.core.Board.SZ_MAIN,
            com.shigu.core.Board.CHINEXT, com.shigu.core.Board.STAR,
            com.shigu.core.Board.BSE, com.shigu.core.Board.ETF,
        )
        val boards = when (kind) {
            "etf" -> setOf(com.shigu.core.Board.ETF)
            "stock" -> all - com.shigu.core.Board.ETF
            else -> all
        }
        val cfg = _state.value.screenConfig.copy(
            filters = _state.value.screenConfig.filters.copy(boards = boards),
        )
        _state.update { it.copy(screenConfig = cfg) }
        runScreen()
    }

    fun applyFactors(factors: List<FactorWeight>, topN: Int) {
        if (factors.isEmpty()) {
            _state.update { it.copy(message = "至少选择一个因子") }
            return
        }
        val cfg = _state.value.screenConfig.copy(
            id = "custom_screen",
            name = "自定义筛选",
            subtitle = "用户自选因子",
            factors = factors,
            maxPositions = topN,
        )
        _state.update { it.copy(screenConfig = cfg) }
        runScreen()
    }

    fun applyFilters(filters: StrategyFilters) {
        val cfg = _state.value.screenConfig.copy(filters = filters)
        _state.update { it.copy(screenConfig = cfg) }
        runScreen()
    }

    fun runScreen() {
        val current = _state.value
        if (current.screenLoading) return
        val config = current.screenConfig.copy(universeSize = current.screenPool)
        _state.update { it.copy(screenLoading = true, message = null) }
        viewModelScope.launch {
            val r = runCatching { market.screen(config) }
            _state.update {
                it.copy(
                    screenLoading = false,
                    screenResult = r.getOrNull() ?: it.screenResult,
                    message = r.exceptionOrNull()?.message ?: it.message,
                )
            }
        }
    }

    // ---------------- strategies ----------------

    fun selectStrategy(id: String) {
        _state.update { it.copy(selectedStrategyId = id) }
        val config = _state.value.strategies.firstOrNull { it.id == id } ?: return
        if (_state.value.backtests[id] == null) runBacktest(config)
        if (_state.value.since[id] == null) refreshSimulations()
    }

    fun runBacktest(config: StrategyConfig) {
        if (_state.value.backtestLoading) return
        _state.update { it.copy(backtestLoading = true, message = null) }
        viewModelScope.launch {
            val r = runCatching { market.backtest(config) }
            _state.update {
                it.copy(
                    backtestLoading = false,
                    backtests = if (r.isSuccess) it.backtests + (config.id to r.getOrThrow()) else it.backtests,
                    message = r.exceptionOrNull()?.message ?: it.message,
                )
            }
        }
    }

    fun computeSince(config: StrategyConfig) {
        viewModelScope.launch {
            val r = runCatching { market.sinceCreation(config) }
            _state.update {
                it.copy(
                    since = if (r.isSuccess) it.since + (config.id to r.getOrThrow()) else it.since,
                )
            }
        }
    }

    /** 创建后的纸面模拟：把所有策略的“自创建日收益”算出来，放进模拟页。 */
    fun refreshSimulations(force: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.simulationLoading || snapshot.strategies.isEmpty() || snapshot.stats.stockCount == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSimulationAt < 60_000) return
        lastSimulationAt = now
        _state.update { it.copy(simulationLoading = true) }
        viewModelScope.launch {
            val refresh = runCatching { paper.refreshAll(snapshot.strategies) }.getOrNull()
            if (refresh != null) {
                _state.update {
                    it.copy(
                        since = it.since + refresh.since,
                        strategyChanges = refresh.changes,
                        simulationLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(simulationLoading = false) }
            }
        }
    }

    /**
     * 策略卡片概览：用 300 只池、近 1 年，跑得快；
     * 选中策略的详情回测再用用户参数完整跑。
     */
    fun warmSummaries() {
        val current = _state.value
        if (current.summaryLoading || current.strategies.isEmpty()) return
        _state.update {
            it.copy(summaryLoading = true, summaryDone = 0, summaryTotal = current.strategies.size)
        }
        refreshSimulations()
        viewModelScope.launch {
            current.strategies.forEachIndexed { index, config ->
                if (_state.value.summaries[config.id] == null) {
                    val quick = config.copy(universeSize = 300, lookbackDays = 250)
                    val r = runCatching { market.backtest(quick) }
                    if (r.isSuccess) {
                        _state.update { s -> s.copy(summaries = s.summaries + (config.id to r.getOrThrow())) }
                    }
                }
                _state.update { it.copy(summaryDone = index + 1) }
            }
            _state.update { it.copy(summaryLoading = false) }
        }
    }

    fun saveCustom(base: StrategyConfig, name: String) {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { repo.saveCustom(base, name) }
            val strategies = withContext(Dispatchers.IO) { repo.list() }
            _state.update {
                it.copy(
                    strategies = strategies,
                    selectedStrategyId = config.id,
                    message = "已保存策略：${config.name}，从今天开始模拟",
                )
            }
            runBacktest(config)
            refreshSimulations(force = true)
        }
    }

    fun updateCustom(config: StrategyConfig, name: String) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repo.updateCustom(config, name) }
            val strategies = withContext(Dispatchers.IO) { repo.list() }
            _state.update {
                it.copy(
                    strategies = strategies,
                    selectedStrategyId = updated.id,
                    since = it.since - updated.id,
                    strategyChanges = it.strategyChanges.filterNot { c -> c.strategyId == updated.id },
                    message = "已更新 ${updated.name}，模拟从今天重置",
                )
            }
            lastSimulationAt = 0L
            refreshSimulations(force = true)
            runBacktest(updated)
        }
    }

    fun loadPaperDetail(config: StrategyConfig) {
        _state.update { it.copy(paperDetailLoading = true) }
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { paper.detail(config) }
            _state.update { it.copy(paperDetail = d, paperDetailLoading = false) }
        }
    }

    fun clearPaperDetail() {
        _state.update { it.copy(paperDetail = null) }
    }

    fun deleteStrategy(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            val strategies = withContext(Dispatchers.IO) { repo.list() }
            _state.update {
                it.copy(
                    strategies = strategies,
                    selectedStrategyId = strategies.firstOrNull()?.id,
                    backtests = it.backtests - id,
                    since = it.since - id,
                )
            }
        }
    }

    // ---------------- stock detail ----------------

    fun loadStock(code: String) {
        _state.update { it.copy(stockLoading = true) }
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val stock = db.getStock(code) ?: return@withContext null
                val bars = db.getBars(code, 260)
                val c60 = if (bars.size > 60) (bars.last().close / bars[bars.size - 61].close - 1.0) * 100.0 else null
                val stock2 = stock.copy(change60 = c60)
                val bench = db.getBenchmarkBars(420)
                val calendar = bench.map { it.date }.takeLast(300)
                val panel = PanelBuilder.build(
                    stocks = listOf(stock2),
                    calendar = calendar,
                    barsByCode = mapOf(code to bars),
                    benchmarkBars = bench,
                    fillCurrentSnapshot = true,
                )
                val i = panel.lastIndex
                val keys = listOf(
                    FactorKey.RSRS_RIGHT, FactorKey.RSRS_Z, FactorKey.LLT_GAP,
                    FactorKey.VOLUME_PRICE_DIV, FactorKey.TURNOVER_STABILITY,
                    FactorKey.AMOUNT_STABILITY, FactorKey.VOL20, FactorKey.IDIO_VOL,
                    FactorKey.MAX_RET20, FactorKey.SKEW20, FactorKey.TURNOVER20,
                    FactorKey.HIGH20_GAP,
                )
                val factors = keys.associateWith { FactorEngine.value(panel, 0, i, it) }
                StockDetailUi(stock2, bars, factors, db.isWatched(code))
            }
            _state.update { it.copy(stockLoading = false, stockDetail = detail) }
        }
    }

    fun toggleWatch(code: String, thesis: String = "") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (db.isWatched(code)) {
                    db.removeWatch(code)
                } else {
                    val stock = db.getStock(code)
                    db.addWatch(code, stock?.price ?: 0.0, thesis.ifBlank { "手动加入观察" })
                }
            }
            loadStock(code)
            val watch = withContext(Dispatchers.IO) { db.getWatchlist() }
            _state.update { it.copy(watchlist = watch) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
