package com.shigu.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shigu.ShiguApp
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.OptionBacktestResult
import com.shigu.core.OptionContract
import com.shigu.core.OptionPaperView
import com.shigu.core.OptionSelector
import com.shigu.core.OptionSignal
import com.shigu.core.OptionSignalPreset
import com.shigu.core.OptionSignalPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class OptionUiState(
    val contracts: List<OptionContract> = emptyList(),
    val strategies: List<OptionPaperView> = emptyList(),
    val underlying: String = "510050",
    val underlyingPrice: Double = 0.0,
    val preset: OptionSignalPreset = OptionSignalPresets.all.first(),
    val offset: Int = 0,
    val lots: Int = 1,
    val signal: OptionSignal? = null,
    val target: OptionContract? = null,
    val chainLoading: Boolean = false,
    val backtestLoading: Boolean = false,
    val backtestResults: List<OptionBacktestResult> = emptyList(),
    val backtestDetail: OptionBacktestResult? = null,
    val backtestIv: Double = 1.05,
    val message: String? = null,
    val lastSync: Long = 0L,
) {
    val presets: List<OptionSignalPreset> get() = OptionSignalPresets.all
    val etfFactors: List<FactorKey> get() = FactorKey.etfDirectional
}

class OptionViewModel(app: Application) : AndroidViewModel(app) {
    private val shigu = app as ShiguApp
    private val repo = shigu.options

    private val _state = MutableStateFlow(OptionUiState())
    val state: StateFlow<OptionUiState> = _state

    init {
        viewModelScope.launch {
            loadLocal()
            if (_state.value.contracts.isEmpty()) syncChain()
        }
    }

    private suspend fun loadLocal() {
        val current = _state.value
        val contracts = withContext(Dispatchers.IO) { repo.contracts(current.underlying) }
        val price = withContext(Dispatchers.IO) { repo.underlyingPrice(current.underlying) }
        val strategies = withContext(Dispatchers.IO) {
            repo.strategies().map { repo.refresh(it) }
        }
        _state.update {
            it.copy(
                contracts = contracts,
                underlyingPrice = price,
                strategies = strategies,
                lastSync = contracts.maxOfOrNull { c -> c.updatedAt } ?: 0L,
            )
        }
        rebuildPreview()
    }

    fun syncChain() {
        if (_state.value.chainLoading) return
        _state.update { it.copy(chainLoading = true, message = "正在同步期权链…") }
        viewModelScope.launch {
            val r = repo.syncChain()
            _state.update { it.copy(chainLoading = false) }
            if (r.isSuccess) {
                loadLocal()
                _state.update { it.copy(message = "期权链已更新：${_state.value.contracts.size} 个合约") }
            } else {
                _state.update { it.copy(message = "同步失败：${r.exceptionOrNull()?.message ?: "未知错误"}") }
            }
        }
    }

    fun setUnderlying(code: String) {
        if (_state.value.underlying == code) return
        _state.update {
            it.copy(
                underlying = code,
                target = null,
                signal = null,
                backtestResults = emptyList(),
                backtestDetail = null,
            )
        }
        viewModelScope.launch { loadLocal() }
    }

    fun setPreset(preset: OptionSignalPreset) {
        _state.update { it.copy(preset = preset) }
        rebuildPreview()
    }

    fun setCustomRules(rules: List<com.shigu.core.OptionSignalRule>) {
        if (rules.isEmpty()) return
        _state.update {
            it.copy(
                preset = OptionSignalPreset(
                    id = "custom",
                    name = "自选方向",
                    subtitle = "自选 ETF 因子",
                    description = "按所选因子的符号投票；只做多 call/put。",
                    rules = rules,
                ),
            )
        }
        rebuildPreview()
    }

    fun setOffset(offset: Int) {
        _state.update { it.copy(offset = offset) }
        rebuildPreview()
    }

    fun setLots(lots: Int) {
        _state.update { it.copy(lots = lots.coerceIn(1, 20)) }
        rebuildPreview()
    }

    fun runBacktest() {
        val s = _state.value
        if (s.backtestLoading) return
        val target = s.underlying
        val iv = s.backtestIv
        _state.update {
            it.copy(
                backtestLoading = true,
                message = "正在补 5 年日线并回测 ${s.presets.size} 个内置策略…",
            )
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                repo.backtestAll(target, s.offset, iv)
            }
            if (_state.value.underlying != target || _state.value.backtestIv != iv) return@launch
            if (r.isSuccess) {
                val results = r.getOrThrow().sortedByDescending { it.stats.optionReturn }
                val selected = results.firstOrNull { it.stats.presetId == s.preset.id } ?: results.firstOrNull()
                _state.update {
                    it.copy(
                        backtestLoading = false,
                        backtestResults = results,
                        backtestDetail = selected,
                        message = "回测完成：${target} · ${results.firstOrNull()?.stats?.startDate ?: ""} 起",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        backtestLoading = false,
                        message = "回测失败：${r.exceptionOrNull()?.message ?: "没有足够日线"}",
                    )
                }
            }
        }
    }

    fun selectBacktest(presetId: String) {
        val detail = _state.value.backtestResults.firstOrNull { it.stats.presetId == presetId } ?: return
        _state.update { it.copy(backtestDetail = detail) }
    }

    fun setBacktestIv(iv: Double) {
        if (_state.value.backtestIv == iv) return
        _state.update { it.copy(backtestIv = iv, backtestResults = emptyList(), backtestDetail = null) }
    }

    fun create() {
        val s = _state.value
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                repo.plan(s.underlying, s.preset, s.offset, s.lots)
            }
            if (r.isSuccess) {
                loadLocal()
                _state.update { it.copy(message = "已创建期权纸面策略；方向反转/到期会自动卖出调仓") }
            } else {
                _state.update { it.copy(message = "创建失败：${r.exceptionOrNull()?.message ?: "没有可用合约"}") }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            loadLocal()
            _state.update { it.copy(message = "已删除期权策略") }
        }
    }

    fun refreshValues() {
        viewModelScope.launch { loadLocal() }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun rebuildPreview() {
        val s = _state.value
        if (s.contracts.isEmpty()) {
            _state.update { it.copy(signal = null, target = null) }
            return
        }
        val sig = repo.signal(s.underlying, s.preset).getOrNull()
        val target = sig?.let {
            OptionSelector.target(
                contracts = s.contracts,
                underlying = s.underlying,
                direction = it.direction,
                strikeOffset = s.offset,
                underlyingPrice = s.underlyingPrice,
                today = LocalDate.now().toString(),
            )
        }
        _state.update { it.copy(signal = sig, target = target) }
    }
}
