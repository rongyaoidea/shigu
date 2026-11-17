package com.shigu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shigu.core.BacktestResult
import com.shigu.core.FactorWeight
import com.shigu.core.OwnerType
import com.shigu.core.StStatus
import com.shigu.core.StrategyConfig
import com.shigu.core.Trade
import com.shigu.core.formatPct
import com.shigu.ui.UiState
import com.shigu.ui.components.ChangeText
import com.shigu.ui.components.EquityChart
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.PaperPill
import com.shigu.ui.components.SectionLabel
import com.shigu.ui.components.scoreText
import com.shigu.ui.components.shortDate
import com.shigu.ui.sketch.SketchButton
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import java.util.Locale

@Composable
fun BacktestScreen(
    state: UiState,
    onSelectStrategy: (String) -> Unit,
    onRun: (StrategyConfig) -> Unit,
    onSaveCustom: (StrategyConfig, String) -> Unit,
    onUpdateCustom: (StrategyConfig, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenStock: (String) -> Unit,
    onWarm: () -> Unit,
    onRefreshSim: () -> Unit,
    onGoFactors: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onWarm()
        onRefreshSim()
    }
    val selected = state.strategies.firstOrNull { it.id == state.selectedStrategyId }
        ?: state.strategies.firstOrNull()
    var period by remember(selected?.id) { mutableIntStateOf(selected?.lookbackDays ?: 500) }
    var rebalance by remember(selected?.id) { mutableIntStateOf(selected?.rebalanceDays ?: 20) }
    var positions by remember(selected?.id) { mutableIntStateOf(selected?.maxPositions ?: 10) }
    var pool by remember(selected?.id) { mutableIntStateOf(selected?.universeSize ?: 800) }
    var stop by remember(selected?.id) { mutableStateOf(selected?.stopLossPct) }
    var editFactors by remember(selected?.id) { mutableStateOf(selected?.factors ?: emptyList()) }
    var showFactor by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("策略库", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        "先看研究依据，再看回测；不是收益承诺",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                }
                if (state.summaryLoading) {
                    Text("概览 ${state.summaryDone}/${state.summaryTotal}", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                } else {
                    Text(
                        "去因子页组策略 →",
                        fontSize = 9.sp,
                        color = ShiguPalette.Ink,
                        modifier = Modifier.clickable(onClick = onGoFactors),
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.strategies, key = { it.id }) { cfg ->
                    StrategyCard(
                        config = cfg,
                        selected = cfg.id == selected?.id,
                        summary = state.summaries[cfg.id] ?: state.backtests[cfg.id],
                        since = state.since[cfg.id],
                        change = state.strategyChanges.firstOrNull { it.strategyId == cfg.id },
                        onClick = { onSelectStrategy(cfg.id) },
                    )
                }
            }
        }

        if (selected == null) {
            item { SketchCard { Text("还没有策略。", fontSize = 12.sp, color = ShiguPalette.InkSoft) } }
        } else {
            item {
                SketchCard(contentPadding = 12.dp) {
                    Text(selected.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(selected.subtitle, fontSize = 9.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        editFactors.forEach { fw ->
                            Box(
                                Modifier
                                    .background(ShiguPalette.Well, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${fw.key.shortLabel} ${(fw.weight * 100).toInt()}%",
                                    fontSize = 8.sp,
                                    color = ShiguPalette.InkSoft,
                                )
                            }
                        }
                    }
                    val filterBits = buildList {
                        val f = selected.filters
                        if (f.owners.size < OwnerType.entries.size) add(f.ownerLabel)
                        if (f.stStatuses != setOf(StStatus.NORMAL)) add(f.stLabel)
                        if (f.minListedDays > 0) add("上市≥${f.minListedDays}天")
                        if (f.excludeSuspended) add("排除停牌")
                    }
                    if (filterBits.isNotEmpty()) {
                        Text(
                            "基础过滤：${filterBits.joinToString(" · ")}",
                            fontSize = 8.5.sp,
                            color = ShiguPalette.InkFaint,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        "编辑因子 →",
                        fontSize = 8.5.sp,
                        color = ShiguPalette.InkSoft,
                        modifier = Modifier
                            .clickable { showFactor = true }
                            .padding(vertical = 3.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    ParamRow("区间", listOf("1年" to 250, "2年" to 500, "3年" to 750), period) { period = it }
                    ParamRow("调仓", listOf("5日" to 5, "10日" to 10, "20日" to 20), rebalance) { rebalance = it }
                    ParamRow("持仓", listOf("5只" to 5, "10只" to 10, "20只" to 20, "30只" to 30, "50只" to 50), positions) { positions = it }
                    ParamRow("池", listOf("300" to 300, "800" to 800, "1500" to 1500), pool) { pool = it }
                    ParamRow("止损", listOf("关" to 0, "8%" to 8, "12%" to 12), (stop?.times(100)?.toInt() ?: 0)) {
                        stop = if (it == 0) null else it / 100.0
                    }
                    Spacer(Modifier.height(8.dp))
                    val updated = selected.copy(
                        lookbackDays = period,
                        rebalanceDays = rebalance,
                        maxPositions = positions,
                        universeSize = pool,
                        stopLossPct = stop,
                        factors = editFactors,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            SketchButton(
                                label = if (state.backtestLoading) "正在回测…" else "运行回测",
                                onClick = { onRun(updated) },
                                enabled = !state.backtestLoading,
                            )
                        }
                        if (!selected.builtin) {
                            Box(
                                Modifier
                                    .width(58.dp)
                                    .height(48.dp)
                                    .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(11.dp))
                                    .clickable { onDelete(selected.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("删除", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "回测：收盘出信号，次日开盘成交；等权；佣金 + 卖出印花税 + 滑点，不含冲击成本。",
                        fontSize = 8.5.sp,
                        color = ShiguPalette.InkFaint,
                    )
                    if (selected.builtin) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clickable { showSave = true }
                                .padding(vertical = 3.dp),
                        ) { Text("另存为我的策略 →", fontSize = 9.sp, color = ShiguPalette.InkSoft) }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clickable { showUpdate = true }
                                .padding(vertical = 3.dp),
                        ) {
                            Text(
                                "保存修改（重置创建后模拟）→",
                                fontSize = 9.sp,
                                color = ShiguPalette.Alert,
                            )
                        }
                    }
                }
            }

            item {
                SinceCard(state.since[selected.id], selected, onOpenStock, state.strategyChanges.firstOrNull { it.strategyId == selected.id })
            }

            item {
                if (state.backtestLoading) {
                    SketchCard {
                        Text("正在加载本地日线并计算…", fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = ShiguPalette.Ink,
                            trackColor = ShiguPalette.Well,
                        )
                    }
                } else {
                    val result = state.backtests[selected.id]
                    if (result == null) {
                        SketchCard {
                            Text("还没有完整回测结果。", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "选参数后点「运行回测」。首次会加载本地日线，约几秒。",
                                fontSize = 9.sp,
                                color = ShiguPalette.InkSoft,
                            )
                        }
                    } else {
                        ResultCard(result, onOpenStock)
                    }
                }
            }

            item {
                SketchCard {
                    Text("研究依据", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(selected.evidence, fontSize = 9.sp, lineHeight = 14.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.height(6.dp))
                    Text("边界", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(selected.caveat, fontSize = 9.sp, lineHeight = 14.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "数据：新浪列表 + 腾讯前复权日线（成交额/换手为估算）· 当前股票池（有幸存者偏差）。所有收益为历史模拟，不代表未来。",
                        fontSize = 8.5.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
            }
        }
    }

    if (showSave && selected != null) {
        SaveDialog(
            initial = "${selected.name}·我的",
            title = "保存为我的策略",
            note = "保存后从今天开始自动模拟。",
            onDismiss = { showSave = false },
            onConfirm = {
                showSave = false
                onSaveCustom(
                    selected.copy(
                        lookbackDays = period,
                        rebalanceDays = rebalance,
                        maxPositions = positions,
                        universeSize = pool,
                        stopLossPct = stop,
                        factors = editFactors,
                    ),
                    it,
                )
            },
        )
    }

    if (showUpdate && selected != null) {
        SaveDialog(
            initial = selected.name,
            title = "保存修改并重置模拟",
            note = "因子/规则会更新；创建后收益从今天重新开始。旧模拟净值会被清空。",
            onDismiss = { showUpdate = false },
            onConfirm = {
                showUpdate = false
                onUpdateCustom(
                    selected.copy(
                        lookbackDays = period,
                        rebalanceDays = rebalance,
                        maxPositions = positions,
                        universeSize = pool,
                        stopLossPct = stop,
                        factors = editFactors,
                    ),
                    it,
                )
            },
        )
    }

    if (showFactor) {
        FactorPickerDialog(
            initial = editFactors,
            initialTopN = positions,
            onDismiss = { showFactor = false },
            onApply = { factors, topN ->
                showFactor = false
                editFactors = factors
                positions = topN
            },
        )
    }
}

@Composable
private fun StrategyCard(
    config: StrategyConfig,
    selected: Boolean,
    summary: BacktestResult?,
    since: com.shigu.core.SinceCreationResult?,
    change: com.shigu.core.StrategyChange?,
    onClick: () -> Unit,
) {
    val stats = summary?.stats
    SketchCard(
        modifier = Modifier
            .width(218.dp)
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 1.6.dp else 0.dp,
                color = if (selected) ShiguPalette.Ink else ShiguPalette.InkFaint,
                shape = RoundedCornerShape(14.dp),
            ),
        contentPadding = 11.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(config.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Box(
                    Modifier
                        .background(ShiguPalette.Highlighter, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) { Text("当前", fontSize = 8.sp) }
            }
        }
        Text(config.subtitle, fontSize = 8.5.sp, color = ShiguPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (change?.hasChange == true) {
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .background(ShiguPalette.Alert, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    "调仓 · 买${change.buys.size} 卖${change.sells.size}",
                    fontSize = 8.sp,
                    color = ShiguPalette.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("回测 1年·300池", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                Text(
                    stats?.let { formatPct(it.totalReturn) } ?: "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = stats?.let { if (it.totalReturn >= 0) ShiguPalette.Up else ShiguPalette.Down }
                        ?: ShiguPalette.InkFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("创建后", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                Text(
                    since?.let { formatPct(it.returnPct) } ?: "—",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShiguPalette.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun SinceCard(
    since: com.shigu.core.SinceCreationResult?,
    config: StrategyConfig,
    onOpenStock: (String) -> Unit,
    change: com.shigu.core.StrategyChange?,
) {
    SketchCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("模拟收益（创建后）", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    if (change?.hasChange == true) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .background(ShiguPalette.Alert, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) { Text("今日调仓", fontSize = 7.5.sp, color = ShiguPalette.White) }
                    }
                }
                Text(
                    since?.let { formatPct(it.returnPct) } ?: "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = since?.let { if (it.returnPct >= 0) ShiguPalette.Up else ShiguPalette.Down }
                        ?: ShiguPalette.InkFaint,
                )
                Text(
                    if (since == null) "等待回测/数据" else "自 ${since.startDate} · ${since.points} 个净值点 · 基准 ${formatPct(since.benchmarkPct)}",
                    fontSize = 8.5.sp,
                    color = ShiguPalette.InkFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("首个创建日", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                Text(shortDate(config.createdAt), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("每日 16:10 更新", fontSize = 8.sp, color = ShiguPalette.InkFaint)
            }
        }
        if (!since?.picks.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            SketchDivider(seed = "since-divider")
            Text(
                if (since!!.portfolio?.holdings.isNullOrEmpty()) "目标持仓（尚未成交）" else "目标持仓",
                fontSize = 8.sp,
                color = ShiguPalette.InkFaint,
            )
            since!!.picks.take(3).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenStock(p.code) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(p.code, fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    Spacer(Modifier.weight(1f))
                    Text("%.2f".format(p.price), fontSize = 10.sp)
                    Spacer(Modifier.width(5.dp))
                    ChangeText(p.changePct)
                }
            }
        }
    }
}

@Composable
private fun ParamRow(
    label: String,
    options: List<Pair<String, Int>>,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.width(34.dp))
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(options) { (text, value) ->
                PaperPill(text, current == value, { onSelect(value) })
            }
        }
    }
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun ResultCard(result: BacktestResult, onOpenStock: (String) -> Unit) {
    val s = result.stats
    SketchCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("完整回测", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                Text(
                    formatPct(s.totalReturn),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (s.totalReturn >= 0) ShiguPalette.Up else ShiguPalette.Down,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.startDate} → ${s.endDate}", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                Text("基准 ${formatPct(s.benchmarkReturn)}", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                Text("超额 ${formatPct(s.excess)}", fontSize = 9.sp, color = if (s.excess >= 0) ShiguPalette.Up else ShiguPalette.Down)
            }
        }
        Spacer(Modifier.height(8.dp))
        EquityChart(result.equity)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("— 策略", fontSize = 8.sp, color = ShiguPalette.InkSoft)
            Text("--- 沪深300", fontSize = 8.sp, color = ShiguPalette.InkFaint)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            MetricCell("年化", formatPct(s.annualized), Modifier.weight(1f))
            MetricCell("最大回撤", formatPct(s.maxDrawdown), Modifier.weight(1f))
            MetricCell("夏普", String.format(Locale.CHINA, "%.2f", s.sharpe), Modifier.weight(1f))
            MetricCell("胜率", formatPct(s.winRate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            MetricCell("交易", "${s.tradeCount}", Modifier.weight(1f))
            MetricCell("平均持有", "%.1f天".format(s.avgHoldDays), Modifier.weight(1f))
            MetricCell("累计费用", "¥%.0f".format(s.totalFees), Modifier.weight(1f))
            MetricCell("池", "${s.universeSize}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(5.dp))
        Text(s.note, fontSize = 8.sp, color = ShiguPalette.InkFaint)

        if (result.picks.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            SketchDivider(seed = "result-picks")
            Text("最新持仓", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            result.picks.take(8).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenStock(p.code) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, fontSize = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(p.code, fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    Spacer(Modifier.weight(1f))
                    Text(scoreText(p.score), fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    Spacer(Modifier.width(7.dp))
                    Text("%.2f".format(p.price), fontSize = 10.sp)
                }
            }
        }
        if (result.warning != null) {
            Spacer(Modifier.height(6.dp))
            Text(result.warning, fontSize = 8.5.sp, color = ShiguPalette.InkSoft, lineHeight = 13.sp)
        }
        if (result.trades.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            SketchDivider(seed = "result-trades")
            Text("最近交易", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            result.trades.takeLast(5).reversed().forEach { t ->
                TradeRow(t)
            }
        }
    }
}

@Composable
private fun TradeRow(t: Trade) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${t.name} ${t.code}", fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${t.entryDate} → ${t.exitDate ?: "持仓中"} · ${t.holdDays}天 · ${t.reason}",
                fontSize = 7.5.sp,
                color = ShiguPalette.InkFaint,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPct(t.pnlPct), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (t.pnlPct >= 0) ShiguPalette.Up else ShiguPalette.Down)
            Text("¥%.0f".format(t.pnl), fontSize = 8.sp, color = ShiguPalette.InkFaint)
        }
    }
}

@Composable
private fun SaveDialog(
    initial: String,
    title: String = "保存为我的策略",
    note: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("策略名") },
                    singleLine = true,
                )
                if (note != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(note, fontSize = 9.sp, color = ShiguPalette.InkSoft)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = ShiguPalette.Paper,
    )
}
