package com.shigu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.OptionBacktestResult
import com.shigu.core.OptionDirection
import com.shigu.core.OptionPaperView
import com.shigu.core.OptionSignal
import com.shigu.core.OptionSignalRule
import com.shigu.core.OptionSignalPresets
import com.shigu.core.OptionType
import com.shigu.core.ScoringMode
import com.shigu.core.formatPct
import com.shigu.core.formatYi
import com.shigu.ui.OptionUiState
import com.shigu.ui.OptionViewModel
import com.shigu.ui.components.EquityChart
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.MiniTag
import com.shigu.ui.components.PaperPill
import com.shigu.ui.components.SectionLabel
import com.shigu.ui.components.factorText
import com.shigu.ui.sketch.SketchButton
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

@Composable
fun OptionScreen(vm: OptionViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showFactorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshValues()
        while (true) {
            delay(5 * 60_000)
            vm.refreshValues()
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3600)
            vm.clearMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("期权模拟", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "只买 call / put · 510050 / 510300 · 日线信号，方向反转或到期卖出调仓",
                            fontSize = 8.5.sp,
                            color = ShiguPalette.InkSoft,
                        )
                    }
                    Text(
                        if (state.chainLoading) "同步中…" else "同步期权链",
                        fontSize = 9.sp,
                        color = ShiguPalette.Ink,
                        modifier = Modifier
                            .clickable(enabled = !state.chainLoading) { vm.syncChain() }
                            .padding(6.dp),
                    )
                }
                if (state.chainLoading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = ShiguPalette.Ink,
                        trackColor = ShiguPalette.Well,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("期权合约", "${state.contracts.size} 个", Modifier.weight(1f))
                    MetricCell("标的价", "%.4f".format(state.underlyingPrice), Modifier.weight(1f))
                    MetricCell("最后同步", if (state.lastSync > 0) "已同步" else "未同步", Modifier.weight(1f))
                }
            }
        }

        item {
            SectionLabel("标的 ETF", trailing = "只做这两个")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("510050 上证50ETF", state.underlying == "510050", { vm.setUnderlying("510050") })
                PaperPill("510300 沪深300ETF", state.underlying == "510300", { vm.setUnderlying("510300") })
            }
        }

        item {
            SectionLabel("方向策略", trailing = "ETF 因子投票")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.presets) { preset ->
                    PaperPill(preset.name, state.preset.id == preset.id, { vm.setPreset(preset) })
                }
                item {
                    PaperPill("自选因子", state.preset.id == "custom", { showFactorPicker = true })
                }
            }
            Text(
                state.preset.description,
                fontSize = 8.sp,
                color = ShiguPalette.InkFaint,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        item {
            val sig = state.signal
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日方向", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                        Text(
                            sig?.direction?.label ?: "等待计算",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = directionColor(sig?.direction),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("投票分 ${sig?.let { "%.2f".format(it.score) } ?: "—"}", fontSize = 10.sp)
                        Text(
                            if (sig != null && sig.direction == OptionDirection.NEUTRAL) "不建仓 / 平掉旧仓" else "收盘信号 · 次日开盘执行",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                        )
                    }
                }
                if (sig != null) {
                    Spacer(Modifier.height(5.dp))
                    sig.details.forEach { d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.key.label, fontSize = 8.5.sp, color = ShiguPalette.InkSoft, modifier = Modifier.weight(1f))
                            Text(factorText(d.key, d.value), fontSize = 8.5.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    d.vote > 0 -> "看涨"
                                    d.vote < 0 -> "看跌"
                                    else -> "缺数据"
                                },
                                fontSize = 8.sp,
                                color = when {
                                    d.vote > 0 -> ShiguPalette.Up
                                    d.vote < 0 -> ShiguPalette.Down
                                    else -> ShiguPalette.InkFaint
                                },
                                modifier = Modifier.width(34.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("买入目标", trailing = "只买期权")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("行权价", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                listOf(-2, -1, 0, 1, 2).forEach { offset ->
                    PaperPill(
                        if (offset == 0) "ATM" else if (offset > 0) "+$offset" else "$offset",
                        state.offset == offset,
                        { vm.setOffset(offset) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("张数", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                listOf(1, 2, 5).forEach { n -> PaperPill("$n", state.lots == n, { vm.setLots(n) }) }
                Spacer(Modifier.weight(1f))
                Text("每张 10,000 份", fontSize = 8.sp, color = ShiguPalette.InkFaint)
            }
            Spacer(Modifier.height(5.dp))
            val target = state.target
            SketchCard(contentPadding = 12.dp) {
                if (target == null) {
                    Text(
                        if (state.signal?.direction == OptionDirection.NEUTRAL) "信号中性：今天不买，已有仓位会卖出平仓。" else "没有可用合约，先同步期权链。",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("买入 ${target.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${target.type.label} · 行权价 ${"%.3f".format(target.strike)} · 到期 ${target.expiry}",
                                fontSize = 8.5.sp,
                                color = ShiguPalette.InkSoft,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("权利金", fontSize = 8.sp, color = ShiguPalette.InkSoft)
                            Text("¥%.0f".format(target.last * 10_000 * state.lots), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val be = if (target.type == OptionType.CALL) target.strike + target.last else target.strike - target.last
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("最大亏损", "¥%.0f".format(target.last * 10_000 * state.lots), Modifier.weight(1f))
                        MetricCell("盈亏平衡", "%.4f".format(be), Modifier.weight(1f))
                        MetricCell("剩余天数", "${target.remainingDays} 天", Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            SketchButton(
                label = if (state.target == null) "等待方向/合约" else "开始纸面模拟 · 买入 ${state.lots} 张",
                onClick = vm::create,
                enabled = state.target != null && !state.chainLoading,
            )
        }

        if (state.message != null) {
            item {
                SketchCard(contentPadding = 10.dp) {
                    Text(state.message!!, fontSize = 9.sp, color = ShiguPalette.InkSoft)
                }
            }
        }

        item {
            OptionBacktestSection(state, vm)
        }

        item { SectionLabel("期权纸面策略", trailing = "方向反转/到期自动卖出调仓") }
        if (state.strategies.isEmpty()) {
            item {
                SketchCard(contentPadding = 12.dp) {
                    Text("还没有期权纸面策略。选好方向策略和行权价后点上方按钮。", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                }
            }
        } else {
            items(state.strategies, key = { it.strategy.id }) { view ->
                OptionPaperCard(view, onDelete = { vm.delete(view.strategy.id) })
            }
        }

        item {
            Text(
                "只买 call/put，不卖裸期权；卖出只用于平仓/调仓。到期按内在价值做纸面估算，实际 ETF 期权是实物交割。不是实盘，不接券商。",
                fontSize = 8.5.sp,
                color = ShiguPalette.InkFaint,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    if (showFactorPicker) {
        FactorPickerDialog(
            initial = state.preset.rules.map { FactorWeight(it.key, it.weight) },
            initialTopN = 20,
            initialScoring = ScoringMode.ZSCORE,
            analytics = null,
            analyticsLoading = false,
            analyticsKey = null,
            onAnalyze = null,
            onCloseAnalytics = {},
            allowedKeys = FactorKey.etfDirectional.toSet(),
            onDismiss = { showFactorPicker = false },
            onApply = { factors, _, _ ->
                showFactorPicker = false
                vm.setCustomRules(factors.map { OptionSignalRule(it.key, it.weight) })
            },
        )
    }
}

@Composable
private fun OptionBacktestSection(state: OptionUiState, vm: OptionViewModel) {
    SectionLabel("5 年历史回测", trailing = "理论 BS 定价")
    SketchCard(contentPadding = 12.dp) {
        Text(
            "用 ${state.underlying} 日线生成方向，期权价格按 20 日历史波动率做 Black-Scholes 理论定价；不是历史盘口成交价。",
            fontSize = 8.sp,
            color = ShiguPalette.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IV", fontSize = 8.5.sp, color = ShiguPalette.InkSoft)
            PaperPill("1.05", state.backtestIv == 1.05, { vm.setBacktestIv(1.05) })
            PaperPill("1.20 压力", state.backtestIv == 1.20, { vm.setBacktestIv(1.20) })
            Spacer(Modifier.weight(1f))
            Text("2021-11 起", fontSize = 8.sp, color = ShiguPalette.InkFaint)
        }
        Spacer(Modifier.height(6.dp))
        if (state.backtestLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = ShiguPalette.Ink,
                trackColor = ShiguPalette.Well,
            )
            Spacer(Modifier.height(5.dp))
            Text("正在补 5 年日线并回测…首次约需 10–30 秒。", fontSize = 8.5.sp, color = ShiguPalette.InkSoft)
        } else if (state.backtestResults.isEmpty()) {
            SketchButton("跑 ${state.underlying} 5 年期权回测", onClick = vm::runBacktest)
        } else {
            val first = state.backtestResults.first().stats
            Text(
                "${first.startDate} ~ ${first.endDate} · 基准：ETF ${formatPct(first.etfReturn)} / 沪深300 ${formatPct(first.csiReturn)}",
                fontSize = 7.8.sp,
                color = ShiguPalette.InkFaint,
            )
            Spacer(Modifier.height(4.dp))
            state.backtestResults.forEach { result ->
                BacktestRow(
                    result = result,
                    selected = state.backtestDetail?.stats?.presetId == result.stats.presetId,
                    onClick = { vm.selectBacktest(result.stats.presetId) },
                )
            }
            val detail = state.backtestDetail
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                SketchDivider()
                Spacer(Modifier.height(6.dp))
                Text(detail.stats.presetName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("期权收益", formatPct(detail.stats.optionReturn), Modifier.weight(1f))
                    MetricCell("ETF", formatPct(detail.stats.etfReturn), Modifier.weight(1f))
                    MetricCell("沪深300", formatPct(detail.stats.csiReturn), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("最大回撤", formatPct(detail.stats.maxDrawdown), Modifier.weight(1f))
                    MetricCell("夏普", "%.2f".format(detail.stats.sharpe), Modifier.weight(1f))
                    MetricCell("交易 / 胜率", "${detail.stats.trades} / ${(detail.stats.winRate * 100).toInt()}%", Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                EquityChart(detail.equity)
                Text(
                    "实线：期权纸面 · 虚线：ETF 买入持有。${detail.warning}",
                    fontSize = 7.3.sp,
                    color = ShiguPalette.InkFaint,
                )
            }
        }
    }
}

@Composable
private fun BacktestRow(result: OptionBacktestResult, selected: Boolean, onClick: () -> Unit) {
    val s = result.stats
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) ShiguPalette.Well else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.presetName, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${s.trades} 笔 · 胜率 ${(s.winRate * 100).toInt()}% · 回撤 ${formatPct(s.maxDrawdown)}",
                fontSize = 7.3.sp,
                color = ShiguPalette.InkFaint,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatPct(s.optionReturn),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (s.optionReturn > 0) ShiguPalette.Up else if (s.optionReturn < 0) ShiguPalette.Down else ShiguPalette.InkSoft,
            )
            Text(
                "超额ETF ${formatPct(s.excessEtf)}",
                fontSize = 7.3.sp,
                color = if (s.excessEtf > 0) ShiguPalette.Up else ShiguPalette.Down,
            )
        }
        if (s.beatBoth) {
            Spacer(Modifier.width(5.dp))
            MiniTag("跑赢", ShiguPalette.Up)
        }
    }
}

@Composable
private fun OptionPaperCard(view: OptionPaperView, onDelete: () -> Unit) {
    val s = view.strategy
    SketchCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.underlying} · ${s.presetName} · 创建 ${s.entryDate}",
                    fontSize = 8.sp,
                    color = ShiguPalette.InkSoft,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (s.position == null) "空仓" else formatPct(view.pnlPct),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        s.position == null -> ShiguPalette.InkSoft
                        view.pnl > 0 -> ShiguPalette.Up
                        view.pnl < 0 -> ShiguPalette.Down
                        else -> ShiguPalette.InkSoft
                    },
                )
                Text(
                    if (s.position == null) "无持仓" else "¥%.0f".format(view.pnl),
                    fontSize = 8.sp,
                    color = ShiguPalette.InkFaint,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniTag(s.lastSignal.label, directionColor(s.lastSignal))
            Spacer(Modifier.width(5.dp))
            Text(s.lastAction, fontSize = 8.5.sp, color = ShiguPalette.InkSoft, modifier = Modifier.weight(1f))
            Text("删除", fontSize = 8.sp, color = ShiguPalette.Alert, modifier = Modifier.clickable(onClick = onDelete))
        }
        val p = s.position
        if (p != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                "${p.type.label} ${p.name} · 行权价 ${"%.3f".format(p.strike)} · 到期 ${p.expiry}",
                fontSize = 8.5.sp,
                color = ShiguPalette.InkFaint,
            )
            Text(
                "买入 ${p.quantity} 张 @ ${"%.4f".format(p.entryPrice)} · 现价 ${"%.4f".format(view.currentPrice)}",
                fontSize = 8.sp,
                color = ShiguPalette.InkFaint,
            )
        }
        Text(
            "最后 ${s.lastDate.ifBlank { "—" }} · 日线收盘信号，次日开盘执行",
            fontSize = 7.5.sp,
            color = ShiguPalette.InkFaint,
        )
    }
}

private fun directionColor(direction: OptionDirection?): androidx.compose.ui.graphics.Color = when (direction) {
    OptionDirection.BULL -> ShiguPalette.Up
    OptionDirection.BEAR -> ShiguPalette.Down
    else -> ShiguPalette.InkSoft
}
