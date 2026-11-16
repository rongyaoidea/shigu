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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shigu.core.FactorKey
import com.shigu.core.FactorWeight
import com.shigu.core.Pick
import com.shigu.core.StrategyConfig
import com.shigu.core.StrategyPresets
import com.shigu.core.SyncState
import com.shigu.ui.UiState
import com.shigu.ui.components.EmptyCard
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

@Composable
fun ScreenerScreen(
    state: UiState,
    onOpenStock: (String) -> Unit,
    onPreset: (String) -> Unit,
    onPool: (Int) -> Unit,
    onAssetFilter: (String) -> Unit,
    onApplyFactors: (List<FactorWeight>, Int) -> Unit,
    onSaveStrategy: (StrategyConfig, String) -> Unit,
    onRun: () -> Unit,
    onQuickSync: () -> Unit,
) {
    var showFactor by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var strategyName by remember { mutableStateOf("") }
    val boards = state.screenConfig.filters.boards
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
                Box(
                    Modifier
                        .clickable { showFactor = true }
                        .background(ShiguPalette.Ink, RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.Tune, null, tint = ShiguPalette.Paper, modifier = Modifier.size(12.dp))
                        Text("因子", fontSize = 10.sp, color = ShiguPalette.Paper)
                    }
                }
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

        item {
            Text(
                "因子只负责排序/筛选；存为策略后才会进入回测和创建后模拟。",
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
                }
                Text("z-score 加权", fontSize = 9.sp, color = ShiguPalette.InkFaint)
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
            onDismiss = { showFactor = false },
            onApply = { factors, topN ->
                showFactor = false
                onApplyFactors(factors, topN)
            },
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
    onDismiss: () -> Unit,
    onApply: (List<FactorWeight>, Int) -> Unit,
) {
    val selected = remember {
        mutableStateMapOf<FactorKey, Double>().apply {
            initial.forEach { put(it.key, it.weight) }
        }
    }
    var topN by remember { mutableStateOf(initialTopN) }

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
                    Text("点选 / 调权重", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                }
                Text(
                    "越高越好的因子权重为正；越低越好的因子方向已内置。至少要选 1 个。",
                    fontSize = 9.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
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
                LazyColumn(
                    Modifier
                        .heightIn(max = 430.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FactorKey.categories.forEach { cat ->
                        item(key = "cat_$cat") {
                            Text(
                                cat,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ShiguPalette.InkSoft,
                                modifier = Modifier.padding(top = 7.dp, bottom = 2.dp),
                            )
                        }
                        items(FactorKey.entries.filter { it.category == cat }, key = { it.name }) { key ->
                            FactorRow(
                                key = key,
                                selected = selected.containsKey(key),
                                weight = selected[key] ?: 0.2,
                                onToggle = {
                                    if (selected.containsKey(key)) selected.remove(key)
                                    else selected[key] = 0.2
                                },
                                onWeight = { selected[key] = it },
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
                                val list = selected.entries
                                    .sortedByDescending { it.value }
                                    .map { FactorWeight(it.key, it.value) }
                                if (list.isNotEmpty()) onApply(list, topN)
                            },
                            enabled = selected.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorRow(
    key: FactorKey,
    selected: Boolean,
    weight: Double,
    onToggle: () -> Unit,
    onWeight: (Double) -> Unit,
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
                        if (key.category == "估值") "当前" else "日频",
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
                        .clickable { onWeight((weight - 0.05).coerceAtLeast(0.05)) },
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

private fun formatCount(v: Long): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
    v >= 1_000 -> "%.1fK".format(v / 1_000.0)
    else -> v.toString()
}
