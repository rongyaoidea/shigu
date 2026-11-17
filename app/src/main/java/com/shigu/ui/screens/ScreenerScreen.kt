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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shigu.core.Board
import com.shigu.core.CustomFactor
import com.shigu.core.CustomFactorWeight
import com.shigu.core.FactorKey
import com.shigu.core.FactorAnalytics
import com.shigu.core.FactorStats
import com.shigu.core.FactorVariables
import com.shigu.core.FactorWeight
import com.shigu.core.FunnelStep
import com.shigu.core.OwnerType
import com.shigu.core.Pick
import com.shigu.core.ScoringMode
import com.shigu.core.StStatus
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyFilters
import com.shigu.core.StrategyPresets
import com.shigu.core.SyncState
import com.shigu.core.formatPct
import com.shigu.ui.UiState
import com.shigu.ui.components.EmptyCard
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.MiniTag
import com.shigu.ui.components.PaperPill
import com.shigu.ui.components.SectionLabel
import com.shigu.ui.components.ChangeText
import com.shigu.ui.components.boardText
import com.shigu.ui.components.factorText
import com.shigu.ui.components.scoreText
import com.shigu.ui.sketch.SketchButton
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun ScreenerScreen(
    state: UiState,
    onOpenStock: (String) -> Unit,
    onPreset: (String) -> Unit,
    onPool: (Int) -> Unit,
    onAssetFilter: (String) -> Unit,
    onApplyFactors: (List<FactorWeight>, Int, ScoringMode) -> Unit,
    onApplyFilters: (StrategyFilters) -> Unit,
    onAnalyzeFactor: (FactorKey) -> Unit,
    onCloseFactorStats: () -> Unit,
    onAnalyzeCorrelations: () -> Unit,
    onCloseCorrelations: () -> Unit,
    onSaveCustomFactor: (CustomFactor) -> String?,
    onDeleteCustomFactor: (String) -> Unit,
    onAddCustomFactor: (CustomFactor) -> Unit,
    onRemoveCustomFactor: (String) -> Unit,
    onCustomFactorWeight: (String, Double) -> Unit,
    onSaveStrategy: (StrategyConfig, String) -> Unit,
    onRun: () -> Unit,
    onQuickSync: () -> Unit,
) {
    var showFactor by remember { mutableStateOf(false) }
    var showBasic by remember { mutableStateOf(false) }
    var showFunnel by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var strategyName by remember { mutableStateOf("") }
    val boards = state.screenConfig.filters.boards
    val currentFilters = state.screenConfig.filters
    val assetKind = when {
        boards == setOf(com.shigu.core.Board.ETF) -> "etf"
        com.shigu.core.Board.ETF in boards -> "all"
        else -> "stock"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { DataStrip(state.sync, state.stats.stockCount, state.stats.barCount, state.stats.lastTradeDate, onQuickSync) }

        item { SectionLabel("筛选条件", trailing = "研究型因子 · 非荐股") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(StrategyPresets.screenerPresets) { preset ->
                    PaperPill(
                        text = preset.name,
                        selected = state.screenConfig.id == preset.id,
                        onClick = { onPreset(preset.id) },
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("资产池", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                listOf(300, 800, 1500).forEach { n ->
                    PaperPill("$n", state.screenPool == n, { onPool(n) })
                }
                Spacer(Modifier.weight(1f))
                SmallFilterButton(
                    label = "基础",
                    active = !currentFilters.isDefault,
                    filled = false,
                ) { showBasic = true }
                SmallFilterButton(
                    label = "自定义",
                    active = state.screenConfig.customFactors.isNotEmpty(),
                    filled = false,
                ) { showCustom = true }
                SmallFilterButton(
                    label = "因子",
                    active = true,
                    filled = true,
                ) { showFactor = true }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("资产", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                PaperPill("全部", assetKind == "all", { onAssetFilter("all") })
                PaperPill("股票", assetKind == "stock", { onAssetFilter("stock") })
                PaperPill("ETF", assetKind == "etf", { onAssetFilter("etf") })
                Spacer(Modifier.weight(1f))
                Text(
                    "存为策略 →",
                    fontSize = 10.sp,
                    color = ShiguPalette.Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            strategyName = "${state.screenConfig.name}·我的"
                            showSave = true
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }

        val filterText = filterSummary(currentFilters)
        if (filterText.isNotBlank()) {
            item {
                Text(
                    "基础过滤：$filterText",
                    fontSize = 8.5.sp,
                    color = ShiguPalette.InkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Text(
                "因子只负责排序/筛选；企业性质、ST、停牌与财务为当前快照。存为策略后才会进入回测和创建后模拟。",
                fontSize = 8.5.sp,
                color = ShiguPalette.InkFaint,
            )
        }

        item {
            val count = state.screenResult?.rows?.size ?: 0
            val total = state.screenResult?.totalCandidates ?: 0
            val pool = state.screenResult?.poolSize ?: 0
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        if (state.screenLoading) "正在计算…" else "命中 $count 只",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "候选 $total · 池 $pool · ${state.screenConfig.factorsLabel}",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val funnel = state.screenResult?.funnel.orEmpty()
                    if (funnel.isNotEmpty()) {
                        Text(
                            "漏斗 · 全池 ${funnel.first().remaining} → 命中 $count →",
                            fontSize = 8.5.sp,
                            color = ShiguPalette.Ink,
                            modifier = Modifier
                                .clickable { showFunnel = true }
                                .padding(top = 3.dp),
                        )
                    }
                }
                Text("${state.screenConfig.scoring.label} 加权", fontSize = 9.sp, color = ShiguPalette.InkFaint)
            }
        }

        if (state.screenLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = ShiguPalette.Ink,
                    trackColor = ShiguPalette.Well,
                )
            }
        }

        val rows = state.screenResult?.rows.orEmpty()
        if (rows.isEmpty() && !state.screenLoading) {
            item {
                SketchCard {
                    EmptyCard(
                        if (state.stats.stockCount == 0) "还没有本地行情" else "没有股票同时满足这些因子",
                        if (state.stats.stockCount == 0) "点上面的“更新”先同步数据；首次约 1–3 分钟。" else "减少因子、降低权重集中度，或换一个股票池。",
                    )
                }
            }
        } else {
            items(rows, key = { it.code }) { pick ->
                PickCard(pick, onClick = { onOpenStock(pick.code) })
            }
        }

        item {
            Text(
                "因子分数只表示当前截面上的相对位置，不是预期收益；回测请在“回测”页运行。",
                fontSize = 9.sp,
                color = ShiguPalette.InkSoft,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }
    }

    if (showFactor) {
        FactorPickerDialog(
            initial = state.screenConfig.factors,
            initialTopN = state.screenConfig.maxPositions,
            initialScoring = state.screenConfig.scoring,
            analytics = state.factorStats,
            analyticsLoading = state.factorStatsLoading,
            analyticsKey = state.factorStatsKey,
            onAnalyze = onAnalyzeFactor,
            onCloseAnalytics = onCloseFactorStats,
            correlations = state.factorCorrelations,
            correlationsLoading = state.factorCorrelationsLoading,
            onCorrelations = onAnalyzeCorrelations,
            onCloseCorrelations = onCloseCorrelations,
            onDismiss = {
                showFactor = false
                onCloseFactorStats()
            },
            onApply = { factors, topN, scoring ->
                showFactor = false
                onCloseFactorStats()
                onApplyFactors(factors, topN, scoring)
            },
        )
    }

    if (showBasic) {
        BasicFilterDialog(
            initial = currentFilters,
            onDismiss = { showBasic = false },
            onApply = { filters ->
                showBasic = false
                onApplyFilters(filters)
            },
        )
    }

    if (showFunnel) {
        FunnelDialog(
            steps = state.screenResult?.funnel.orEmpty(),
            onDismiss = { showFunnel = false },
        )
    }

    if (showCustom) {
        CustomFactorDialog(
            library = state.customFactors,
            selected = state.screenConfig.customFactors,
            onSave = onSaveCustomFactor,
            onDelete = onDeleteCustomFactor,
            onAdd = onAddCustomFactor,
            onRemove = onRemoveCustomFactor,
            onWeight = onCustomFactorWeight,
            onDismiss = { showCustom = false },
        )
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("存为策略", fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "保存后自动进入模拟观察：从今天开始按同一套因子和规则记录收益。",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = strategyName,
                        onValueChange = { strategyName = it },
                        label = { Text("策略名") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前规则：${state.screenConfig.factors.size} 个因子 · 持仓 ${state.screenConfig.maxPositions} 只 · 调仓 ${state.screenConfig.rebalanceDays} 日 · 止损 ${state.screenConfig.stopLossPct?.let { "${(it * 100).toInt()}%" } ?: "关"}",
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = strategyName.isNotBlank(),
                    onClick = {
                        showSave = false
                        onSaveStrategy(
                            state.screenConfig.copy(
                                universeSize = state.screenPool,
                                maxPositions = state.screenConfig.maxPositions,
                            ),
                            strategyName,
                        )
                    },
                ) { Text("保存并开始模拟") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("取消") } },
            containerColor = ShiguPalette.Paper,
        )
    }
}

@Composable
private fun DataStrip(
    sync: SyncState,
    stockCount: Int,
    barCount: Long,
    lastTradeDate: String,
    onQuickSync: () -> Unit,
) {
    SketchCard(contentPadding = 12.dp) {
        if (sync.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("正在同步 · ${sync.phase}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${sync.done}/${sync.total} · 失败 ${sync.failed}",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                }
                Text("${(sync.fraction * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { sync.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = ShiguPalette.Ink,
                trackColor = ShiguPalette.Well,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本地行情", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$stockCount 只资产 · 日线 ${formatCount(barCount)} 条 · 最后 $lastTradeDate",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                }
                Box(
                    Modifier
                        .background(ShiguPalette.Ink, RoundedCornerShape(7.dp))
                        .clickable(onClick = onQuickSync)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("更新", fontSize = 10.sp, color = ShiguPalette.Paper)
                }
            }
            if (sync.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(sync.message, fontSize = 8.5.sp, color = ShiguPalette.InkFaint, maxLines = 2)
            }
        }
    }
}

@Composable
private fun PickCard(pick: Pick, onClick: () -> Unit) {
    SketchCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pick.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Text(pick.code, fontSize = 9.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.width(4.dp))
                    MiniTag(boardText(pick.board))
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    pick.factors.entries.take(3).forEach { (k, v) ->
                        Text(
                            "${k.shortLabel} ${factorText(k, v)}",
                            fontSize = 9.sp,
                            color = ShiguPalette.InkSoft,
                        )
                    }
                }
                if (pick.contributions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pick.contributions.entries
                            .sortedByDescending { kotlin.math.abs(it.value) }
                            .take(3)
                            .forEach { (k, v) ->
                                Text(
                                    "${k.shortLabel} ${if (v >= 0) "+" else ""}%.2f".format(v),
                                    fontSize = 8.sp,
                                    color = if (v >= 0) ShiguPalette.Up else ShiguPalette.Down,
                                )
                            }
                    }
                }
                if (pick.customValues.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pick.customValues.entries.take(2).forEach { (id, v) ->
                            val name = pick.customNames[id] ?: "自定义"
                            Text(
                                "$name ${v?.let { "%.2f".format(it) } ?: "—"}",
                                fontSize = 8.sp,
                                color = ShiguPalette.InkSoft,
                            )
                        }
                    }
                }
                if (pick.customContributions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pick.customContributions.entries
                            .sortedByDescending { kotlin.math.abs(it.value) }
                            .take(2)
                            .forEach { (id, v) ->
                                Text(
                                    "${pick.customNames[id] ?: "自定义"} ${if (v >= 0) "+" else ""}%.2f".format(v),
                                    fontSize = 8.sp,
                                    color = if (v >= 0) ShiguPalette.Up else ShiguPalette.Down,
                                )
                            }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.2f".format(pick.price),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShiguPalette.Ink,
                )
                ChangeText(pick.changePct)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("综合分 ${scoreText(pick.score)}", fontSize = 9.sp, color = ShiguPalette.InkFaint)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(13.dp), tint = ShiguPalette.InkFaint)
        }
    }
}

@Composable
fun FactorPickerDialog(
    initial: List<FactorWeight>,
    initialTopN: Int,
    initialScoring: ScoringMode,
    analytics: FactorStats?,
    analyticsLoading: Boolean,
    analyticsKey: FactorKey?,
    onAnalyze: ((FactorKey) -> Unit)?,
    onCloseAnalytics: () -> Unit,
    correlations: List<FactorAnalytics.CorrelationPair>? = null,
    correlationsLoading: Boolean = false,
    onCorrelations: () -> Unit = {},
    onCloseCorrelations: () -> Unit = {},
    onDismiss: () -> Unit,
    onApply: (List<FactorWeight>, Int, ScoringMode) -> Unit,
) {
    val selected = remember {
        mutableStateMapOf<FactorKey, Double>().apply {
            initial.forEach { put(it.key, it.weight) }
        }
    }
    var topN by remember { mutableStateOf(initialTopN) }
    var scoring by remember { mutableStateOf(initialScoring) }
    var query by remember { mutableStateOf("") }
    var showCorr by remember { mutableStateOf(false) }
    val filtered = FactorKey.entries.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.shortLabel.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 16.dp),
            color = ShiguPalette.Paper,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("因子库 · ${FactorKey.entries.size} 个", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "等权",
                        fontSize = 9.sp,
                        color = ShiguPalette.Ink,
                        modifier = Modifier
                            .clickable {
                                val keys = selected.keys.toList()
                                keys.forEach { selected[it] = 1.0 / keys.size.coerceAtLeast(1) }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Text(
                        "相关性",
                        fontSize = 9.sp,
                        color = ShiguPalette.Ink,
                        modifier = Modifier
                            .clickable {
                                showCorr = true
                                onCorrelations()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "越高越好的因子权重为正；越低越好的因子方向已内置。点“体检”看 Rank IC。",
                    fontSize = 9.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier.padding(top = 2.dp, bottom = 5.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索因子 / 类别", fontSize = 10.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                SketchDivider(seed = "factor-dialog")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("显示前", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.width(6.dp))
                    listOf(5, 10, 20, 30, 50).forEach { n ->
                        PaperPill("$n", topN == n, { topN = n })
                        Spacer(Modifier.width(5.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("打分", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.width(6.dp))
                    PaperPill("z-score", scoring == ScoringMode.ZSCORE, { scoring = ScoringMode.ZSCORE })
                    Spacer(Modifier.width(5.dp))
                    PaperPill("排名", scoring == ScoringMode.RANK, { scoring = ScoringMode.RANK })
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已选 ${selected.size} · 合计 ${"%.0f".format(selected.values.sum() * 100)}%",
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
                LazyColumn(
                    Modifier
                        .heightIn(max = 380.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FactorKey.categories.forEach { cat ->
                        val list = filtered.filter { it.category == cat }
                        if (list.isEmpty()) return@forEach
                        item(key = "cat_$cat") {
                            Text(
                                cat,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ShiguPalette.InkSoft,
                                modifier = Modifier.padding(top = 7.dp, bottom = 2.dp),
                            )
                        }
                        items(list, key = { it.name }) { key ->
                            FactorRow(
                                key = key,
                                selected = selected.containsKey(key),
                                weight = selected[key] ?: 0.2,
                                onToggle = {
                                    if (selected.containsKey(key)) selected.remove(key)
                                    else selected[key] = 0.2
                                },
                                onWeight = { selected[key] = it },
                                onAnalyze = onAnalyze?.let { analyze -> { analyze(key) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(0.35f)
                            .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(10.dp))
                            .clickable { selected.clear() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("清空", fontSize = 12.sp, color = ShiguPalette.InkSoft)
                    }
                    Box(Modifier.weight(0.65f)) {
                        SketchButton(
                            label = "应用 ${selected.size} 个因子",
                            onClick = {
                                val total = selected.values.sum().coerceAtLeast(1e-9)
                                val list = selected.entries
                                    .sortedByDescending { it.value }
                                    .map { FactorWeight(it.key, it.value / total) }
                                if (list.isNotEmpty()) onApply(list, topN, scoring)
                            },
                            enabled = selected.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }

    if (analyticsKey != null) {
        FactorStatsDialog(
            key = analyticsKey,
            stats = analytics,
            loading = analyticsLoading,
            onClose = onCloseAnalytics,
        )
    }

    if (showCorr) {
        FactorCorrelationDialog(
            pairs = correlations,
            loading = correlationsLoading,
            onClose = {
                showCorr = false
                onCloseCorrelations()
            },
        )
    }
}

@Composable
private fun FactorRow(
    key: FactorKey,
    selected: Boolean,
    weight: Double,
    onToggle: () -> Unit,
    onWeight: (Double) -> Unit,
    onAnalyze: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(15.dp)
                    .background(if (selected) ShiguPalette.Ink else Color.Transparent, RoundedCornerShape(3.dp))
                    .border(1.dp, ShiguPalette.Ink, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = ShiguPalette.Paper, modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key.label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            key.snapshotOnly -> "快照"
                            key.currentOnly -> "当前"
                            else -> "日频"
                        },
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
                Text(
                    key.description,
                    fontSize = 8.sp,
                    color = ShiguPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onAnalyze != null) {
                Text(
                    "体检",
                    fontSize = 8.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier
                        .clickable(onClick = onAnalyze)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                )
            }
            if (selected) {
                Text("${(weight * 100).toInt()}%", fontSize = 9.sp, color = ShiguPalette.InkSoft)
            }
        }
        if (selected) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("权重", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(4.dp))
                        .clickable { onWeight((weight - 0.05).coerceAtLeast(0.01)) },
                    contentAlignment = Alignment.Center,
                ) { Text("−", fontSize = 12.sp, color = ShiguPalette.InkSoft) }
                Text(
                    "${(weight * 100).toInt()}%",
                    fontSize = 9.sp,
                    color = ShiguPalette.Ink,
                    modifier = Modifier
                        .width(38.dp)
                        .padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(4.dp))
                        .clickable { onWeight((weight + 0.05).coerceAtMost(1.0)) },
                    contentAlignment = Alignment.Center,
                ) { Text("+", fontSize = 12.sp, color = ShiguPalette.InkSoft) }
            }
        }
    }
}

@Composable
fun FactorPickerDialog(
    initial: List<FactorWeight>,
    initialTopN: Int,
    onDismiss: () -> Unit,
    onApply: (List<FactorWeight>, Int) -> Unit,
) {
    FactorPickerDialog(
        initial = initial,
        initialTopN = initialTopN,
        initialScoring = ScoringMode.ZSCORE,
        analytics = null,
        analyticsLoading = false,
        analyticsKey = null,
        onAnalyze = null,
        onCloseAnalytics = {},
        onDismiss = onDismiss,
        onApply = { factors, topN, _ -> onApply(factors, topN) },
    )
}

@Composable
private fun FactorStatsDialog(
    key: FactorKey,
    stats: FactorStats?,
    loading: Boolean,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(color = ShiguPalette.Paper, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("因子体检 · ${key.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("关闭", fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onClose))
                }
                Spacer(Modifier.height(6.dp))
                if (loading) {
                    Text("正在计算 Rank IC / 分层收益…", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                } else if (stats == null) {
                    Text("样本不足，无法计算。至少需要 100 个交易日和 20 只股票。", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                } else {
                    Text(
                        "将来 ${stats.forwardDays} 日收益 · ${stats.sampleDays} 个截面 · 覆盖率 ${"%.0f".format(stats.coverage * 100)}%",
                        fontSize = 8.5.sp,
                        color = ShiguPalette.InkFaint,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("Rank IC", "%.3f".format(stats.meanIc), Modifier.weight(1f))
                        MetricCell("ICIR", "%.2f".format(stats.icIr), Modifier.weight(1f))
                        MetricCell("正 IC 比例", "%.0f%%".format(stats.positiveRate * 100), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricCell("Top-Bottom", formatPct(stats.decileSpread), Modifier.weight(1f))
                        MetricCell("样本截面", "${stats.sampleDays}", Modifier.weight(1f))
                        MetricCell("方向", if (key.higherIsBetter) "越高越好" else "越低越好", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "IC 只说明历史截面相关性；快照因子（财务/企业性质）在历史区间是常数，结果不代表可交易收益。",
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun FactorCorrelationDialog(
    pairs: List<FactorAnalytics.CorrelationPair>?,
    loading: Boolean,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(color = ShiguPalette.Paper, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("因子相关性", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("关闭", fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onClose))
                }
                Text(
                    "当前截面的 Pearson 相关；|r| > 0.7 视为高度重叠，不建议重复加权。",
                    fontSize = 8.sp,
                    color = ShiguPalette.InkFaint,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                when {
                    loading -> Text("正在计算相关性…", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    pairs == null -> Text("暂无结果", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    else -> LazyColumn(Modifier.heightIn(max = 500.dp)) {
                        items(pairs.take(25)) { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.a.shortLabel} × ${p.b.shortLabel}", fontSize = 9.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "%.2f".format(p.corr),
                                    fontSize = 10.sp,
                                    color = if (kotlin.math.abs(p.corr) > 0.7) ShiguPalette.Alert else ShiguPalette.InkSoft,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("n=${p.samples}", fontSize = 7.5.sp, color = ShiguPalette.InkFaint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFactorDialog(
    library: List<CustomFactor>,
    selected: List<CustomFactorWeight>,
    onSave: (CustomFactor) -> String?,
    onDelete: (String) -> Unit,
    onAdd: (CustomFactor) -> Unit,
    onRemove: (String) -> Unit,
    onWeight: (String, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CustomFactor?>(null) }
    var creating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 16.dp),
            color = ShiguPalette.Paper,
            shape = RoundedCornerShape(16.dp),
        ) {
            if (creating || editing != null) {
                CustomFactorEditor(
                    initial = editing,
                    onCancel = {
                        creating = false
                        editing = null
                    },
                    onSave = { factor ->
                        val err = onSave(factor)
                        if (err == null) {
                            creating = false
                            editing = null
                        }
                        err
                    },
                )
            } else {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自定义因子", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("新建公式", fontSize = 9.sp, color = ShiguPalette.Ink, modifier = Modifier.clickable { creating = true })
                        Spacer(Modifier.width(10.dp))
                        Text("关闭", fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onDismiss))
                    }
                    Text(
                        "用现有因子做算术：+ - * / ^、括号、abs/sqrt/log/min/max/clip。保存后加入当前筛选，权重默认 20%。",
                        fontSize = 8.5.sp,
                        color = ShiguPalette.InkSoft,
                        modifier = Modifier.padding(top = 2.dp, bottom = 5.dp),
                    )
                    if (selected.isNotEmpty()) {
                        Text("当前筛选已加入", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = ShiguPalette.InkSoft)
                        selected.forEach { cw ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${cw.factor.name} · ${(cw.weight * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    Text(cw.factor.expression, fontSize = 7.5.sp, color = ShiguPalette.InkFaint)
                                }
                                Text("−", fontSize = 11.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable { onWeight(cw.factor.id, cw.weight - 0.05) }.padding(horizontal = 4.dp))
                                Text("+", fontSize = 11.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable { onWeight(cw.factor.id, cw.weight + 0.05) }.padding(horizontal = 4.dp))
                                Text("移除", fontSize = 8.sp, color = ShiguPalette.Alert, modifier = Modifier.clickable { onRemove(cw.factor.id) })
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    SketchDivider(seed = "custom-factor")
                    Text("因子库", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = ShiguPalette.InkSoft, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
                    if (library.isEmpty()) {
                        Text("还没有自定义因子。点右上角“新建公式”。", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(library, key = { it.id }) { f ->
                                val added = selected.any { it.factor.id == f.id }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(f.name, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (f.higherIsBetter) "↑" else "↓", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                                        }
                                        Text(f.expression, fontSize = 7.5.sp, color = ShiguPalette.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (!added) {
                                        Text("加入", fontSize = 8.5.sp, color = ShiguPalette.Ink, modifier = Modifier.clickable { onAdd(f) }.padding(horizontal = 5.dp))
                                    } else {
                                        Text("已加入", fontSize = 8.sp, color = ShiguPalette.InkFaint, modifier = Modifier.padding(horizontal = 5.dp))
                                    }
                                    Text("编辑", fontSize = 8.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable { editing = f }.padding(horizontal = 4.dp))
                                    Text("删除", fontSize = 8.sp, color = ShiguPalette.Alert, modifier = Modifier.clickable { onDelete(f.id) }.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFactorEditor(
    initial: CustomFactor?,
    onCancel: () -> Unit,
    onSave: (CustomFactor) -> String?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "新公式") }
    var expression by remember { mutableStateOf(initial?.expression ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var higher by remember { mutableStateOf(initial?.higherIsBetter ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (initial == null) "新建公式" else "编辑公式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("取消", fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onCancel))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("名称", fontSize = 9.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = expression,
            onValueChange = { expression = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("公式", fontSize = 9.sp) },
            placeholder = { Text("例如 ROE / PE 或 RSRS_RIGHT / VOL20", fontSize = 9.sp) },
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Text(
            "变量可用 FactorKey.name 或别名：RSRS、LLT、VOL20、ROE、PE、PB、MKT_CAP、AMOUNT20、TURNOVER20…",
            fontSize = 7.5.sp,
            color = ShiguPalette.InkFaint,
            modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("方向", fontSize = 9.sp, color = ShiguPalette.InkSoft)
            PaperPill("越高越好", higher, { higher = true })
            PaperPill("越低越好", !higher, { higher = false })
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                modifier = Modifier.weight(0.4f),
                label = { Text("单位", fontSize = 9.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.weight(0.6f),
                label = { Text("说明", fontSize = 9.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        Text("常用变量", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = ShiguPalette.InkSoft, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(FactorVariables.list.take(24)) { v ->
                Text(
                    v.name,
                    fontSize = 7.5.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier
                        .border(0.8.dp, ShiguPalette.InkFaint, RoundedCornerShape(4.dp))
                        .clickable {
                            expression = if (expression.isBlank()) v.name else "$expression ${v.name}"
                            error = null
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        if (error != null) {
            Text(error!!, fontSize = 8.5.sp, color = ShiguPalette.Alert, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(0.35f)
                    .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("取消", fontSize = 12.sp, color = ShiguPalette.InkSoft) }
            Box(Modifier.weight(0.65f)) {
                SketchButton(
                    label = "保存公式",
                    onClick = {
                        val factor = CustomFactor(
                            id = initial?.id ?: UUID.randomUUID().toString().take(8),
                            name = name.trim(),
                            expression = expression.trim(),
                            higherIsBetter = higher,
                            unit = unit.trim(),
                            description = description.trim(),
                        )
                        error = onSave(factor)
                    },
                    enabled = name.isNotBlank() && expression.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun FunnelDialog(steps: List<FunnelStep>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(color = ShiguPalette.Paper, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("筛选漏斗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("关闭", fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onDismiss))
                }
                Text("每一步只统计“首次被该步骤淘汰”的股票；因子覆盖不足单独统计。", fontSize = 8.sp, color = ShiguPalette.InkFaint, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(steps) { step ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(step.label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                if (step.reasons.isNotEmpty()) {
                                    Text(
                                        step.reasons.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                                        fontSize = 8.sp,
                                        color = ShiguPalette.InkFaint,
                                    )
                                }
                            }
                            Text("${step.remaining} 只", fontSize = 10.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (step.dropped > 0) "-${step.dropped}" else "—",
                                fontSize = 9.sp,
                                color = if (step.dropped > 0) ShiguPalette.Alert else ShiguPalette.InkFaint,
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallFilterButton(
    label: String,
    active: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .background(
                when {
                    filled -> ShiguPalette.Ink
                    active -> ShiguPalette.Highlighter
                    else -> Color.Transparent
                },
                RoundedCornerShape(7.dp),
            )
            .border(1.dp, if (filled) ShiguPalette.Ink else ShiguPalette.InkFaint, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (label == "因子") {
                Icon(
                    Icons.Filled.Tune,
                    null,
                    tint = if (filled) ShiguPalette.Paper else ShiguPalette.InkSoft,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                label,
                fontSize = 10.sp,
                color = if (filled) ShiguPalette.Paper else ShiguPalette.Ink,
            )
        }
    }
}

@Composable
private fun BasicFilterDialog(
    initial: StrategyFilters,
    onDismiss: () -> Unit,
    onApply: (StrategyFilters) -> Unit,
) {
    var owners by remember { mutableStateOf(initial.owners) }
    var stStatuses by remember { mutableStateOf(initial.stStatuses) }
    var minDays by remember { mutableStateOf(initial.minListedDays) }
    var excludeSuspended by remember { mutableStateOf(initial.excludeSuspended) }
    var requireProfile by remember { mutableStateOf(initial.requireProfile) }
    var boards by remember { mutableStateOf(initial.boards) }
    var minPrice by remember { mutableStateOf(trimNum(initial.minPrice)) }
    var minAmount by remember { mutableStateOf(trimNum(initial.minAmount20 / 1e8)) }
    var maxPe by remember { mutableStateOf(initial.maxPe?.let { trimNum(it) } ?: "") }
    var maxPb by remember { mutableStateOf(initial.maxPb?.let { trimNum(it) } ?: "") }
    var minCap by remember { mutableStateOf(initial.minMarketCap?.let { trimNum(it / 1e8) } ?: "") }
    var maxCap by remember { mutableStateOf(initial.maxMarketCap?.let { trimNum(it / 1e8) } ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 16.dp),
            color = ShiguPalette.Paper,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("基础过滤", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("属性快照", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                }
                Text(
                    "企业性质按实控人/第一大股东推断；ST、停牌与财务为当前快照。历史回测使用当前属性，存在前视偏差。",
                    fontSize = 8.5.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                SketchDivider(seed = "basic-filter")

                LazyColumn(
                    Modifier
                        .heightIn(max = 430.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        FilterHeader(
                            title = "企业性质",
                            hint = "可多选；至少选 1 个",
                            action = if (owners.size == OwnerType.entries.size) "清空" else "全选",
                            onAction = {
                                owners = if (owners.size == OwnerType.entries.size) {
                                    emptySet()
                                } else {
                                    OwnerType.entries.toSet()
                                }
                            },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(OwnerType.entries) { owner ->
                                PaperPill(
                                    text = owner.label,
                                    selected = owner in owners,
                                    onClick = {
                                        owners = if (owner in owners) owners - owner else owners + owner
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterHeader(
                            title = "ST 状态",
                            hint = "默认只看正常；*ST/退市整理风险更高",
                            action = if (stStatuses.size == StStatus.entries.size) "清空" else "全选",
                            onAction = {
                                stStatuses = if (stStatuses.size == StStatus.entries.size) {
                                    emptySet()
                                } else {
                                    StStatus.entries.toSet()
                                }
                            },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(StStatus.entries) { status ->
                                PaperPill(
                                    text = status.label,
                                    selected = status in stStatuses,
                                    onClick = {
                                        stStatuses = if (status in stStatuses) stStatuses - status else stStatuses + status
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterHeader("上市天数", "按行情日计算，过滤次新股")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(listOf(0, 60, 120, 250, 500)) { days ->
                                PaperPill(
                                    text = if (days == 0) "不限" else "≥${days}天",
                                    selected = minDays == days,
                                    onClick = { minDays = days },
                                )
                            }
                        }
                    }

                    item {
                        FilterHeader("停牌", "停牌股无法按次日开盘成交")
                        PaperPill(
                            text = "排除停牌",
                            selected = excludeSuspended,
                            onClick = { excludeSuspended = !excludeSuspended },
                        )
                    }

                    item {
                        FilterHeader("数据质量", "资料完整 = 有企业性质/财务快照；会排除 ETF")
                        PaperPill(
                            text = "只看资料完整",
                            selected = requireProfile,
                            onClick = { requireProfile = !requireProfile },
                        )
                    }

                    item {
                        FilterHeader(
                            title = "板块",
                            hint = "可多选；与上方资产池联动",
                            action = if (boards.size == StrategyFilters().boards.size) "清空" else "全选",
                            onAction = {
                                boards = if (boards.size == StrategyFilters().boards.size) {
                                    emptySet()
                                } else {
                                    StrategyFilters().boards
                                }
                            },
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(listOf(Board.SH_MAIN, Board.SZ_MAIN, Board.CHINEXT, Board.STAR, Board.BSE, Board.ETF)) { board ->
                                PaperPill(
                                    text = board.label,
                                    selected = board in boards,
                                    onClick = {
                                        boards = if (board in boards) boards - board else boards + board
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterHeader("价格 / 流动性", "最低价、20 日平均成交额（亿元）")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactNumberField("最低价", minPrice, { minPrice = it }, Modifier.weight(1f))
                            CompactNumberField("20日均额(亿)", minAmount, { minAmount = it }, Modifier.weight(1f))
                        }
                    }

                    item {
                        FilterHeader("估值上限", "留空=不限；亏损股在设 PE 上限时会被排除")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactNumberField("PE ≤", maxPe, { maxPe = it }, Modifier.weight(1f))
                            CompactNumberField("PB ≤", maxPb, { maxPb = it }, Modifier.weight(1f))
                        }
                    }

                    item {
                        FilterHeader("市值区间", "单位：亿元；留空=不限")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactNumberField("最小市值(亿)", minCap, { minCap = it }, Modifier.weight(1f))
                            CompactNumberField("最大市值(亿)", maxCap, { maxCap = it }, Modifier.weight(1f))
                        }
                    }

                    item {
                        Text(
                            "央企/地方国企/民企/外资/无实控人来自东方财富 F10 实控人与第一大股东名称推断，不是官方标签；" +
                                "资料未同步时企业性质多为“未知”，请先在数据页点“基础资料”。",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(0.35f)
                            .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(10.dp))
                            .clickable {
                                owners = OwnerType.entries.toSet()
                                stStatuses = setOf(StStatus.NORMAL)
                                minDays = 0
                                excludeSuspended = false
                                requireProfile = false
                                boards = StrategyFilters().boards
                                minPrice = "2"
                                minAmount = "0.5"
                                maxPe = ""
                                maxPb = ""
                                minCap = ""
                                maxCap = ""
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("重置", fontSize = 12.sp, color = ShiguPalette.InkSoft)
                    }
                    Box(Modifier.weight(0.65f)) {
                        SketchButton(
                            label = "应用基础过滤",
                            onClick = {
                                val cleanOwners = if (owners.isEmpty()) OwnerType.entries.toSet() else owners
                                val cleanSt = if (stStatuses.isEmpty()) setOf(StStatus.NORMAL) else stStatuses
                                val cleanBoards = if (boards.isEmpty()) StrategyFilters().boards else boards
                                onApply(
                                    initial.copy(
                                        minPrice = minPrice.toDoubleOrNull() ?: 0.0,
                                        minAmount20 = (minAmount.toDoubleOrNull() ?: 0.0) * 1e8,
                                        maxPe = maxPe.toDoubleOrNull(),
                                        maxPb = maxPb.toDoubleOrNull(),
                                        minMarketCap = minCap.toDoubleOrNull()?.times(1e8),
                                        maxMarketCap = maxCap.toDoubleOrNull()?.times(1e8),
                                        excludeSt = cleanSt == setOf(StStatus.NORMAL),
                                        stStatuses = cleanSt,
                                        owners = cleanOwners,
                                        minListedDays = minDays,
                                        excludeSuspended = excludeSuspended,
                                        requireProfile = requireProfile,
                                        boards = cleanBoards,
                                    ),
                                )
                            },
                            enabled = owners.isNotEmpty() && stStatuses.isNotEmpty() && boards.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterHeader(
    title: String,
    hint: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = ShiguPalette.InkSoft)
            if (hint != null) Text(hint, fontSize = 8.sp, color = ShiguPalette.InkFaint)
        }
        if (action != null && onAction != null) {
            Text(
                action,
                fontSize = 9.sp,
                color = ShiguPalette.Ink,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(start = 8.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun CompactNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() || it == '.' || it == '-' }.take(10))
        },
        modifier = modifier,
        label = { Text(label, fontSize = 8.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun filterSummary(f: StrategyFilters): String {
    val parts = ArrayList<String>(8)
    if (f.owners.size < OwnerType.entries.size) parts += f.ownerLabel
    if (f.stStatuses != setOf(StStatus.NORMAL)) parts += f.stLabel
    if (f.minListedDays > 0) parts += "上市≥${f.minListedDays}天"
    if (f.excludeSuspended) parts += "排除停牌"
    if (f.boards != StrategyFilters().boards) parts += f.boards.joinToString("/") { it.label }
    f.maxPe?.let { parts += "PE≤${trimNum(it)}" }
    f.maxPb?.let { parts += "PB≤${trimNum(it)}" }
    f.minMarketCap?.let { parts += "市值≥${trimNum(it / 1e8)}亿" }
    f.maxMarketCap?.let { parts += "市值≤${trimNum(it / 1e8)}亿" }
    if (f.minPrice > 2.0) parts += "价≥${trimNum(f.minPrice)}"
    if (f.minAmount20 > 50_000_000.0) parts += "均额≥${trimNum(f.minAmount20 / 1e8)}亿"
    return parts.joinToString(" · ")
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(java.util.Locale.CHINA, "%.2f", v)

private fun formatCount(v: Long): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
    v >= 1_000 -> "%.1fK".format(v / 1_000.0)
    else -> v.toString()
}
