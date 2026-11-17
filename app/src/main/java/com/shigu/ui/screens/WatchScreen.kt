package com.shigu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shigu.core.Holding
import com.shigu.core.PaperDetail
import com.shigu.core.Trade
import com.shigu.core.WatchItem
import com.shigu.core.SinceCreationResult
import com.shigu.core.StrategyConfig
import com.shigu.core.formatPct
import com.shigu.ui.UiState
import com.shigu.ui.components.ChangeText
import com.shigu.ui.components.EmptyCard
import com.shigu.ui.components.EquityChart
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.MiniTag
import com.shigu.ui.components.SectionLabel
import com.shigu.ui.components.boardText
import com.shigu.ui.components.changeColor
import com.shigu.ui.components.shortDate
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import java.util.Locale

@Composable
fun WatchScreen(
    state: UiState,
    onOpenStock: (String) -> Unit,
    onRemove: (String) -> Unit,
    onWarm: () -> Unit,
    onForceRefresh: () -> Unit,
    onOpenPaper: (StrategyConfig) -> Unit,
    onClosePaper: () -> Unit,
) {
    LaunchedEffect(Unit) { onWarm() }
    val list = state.watchlist
    val changeMap = state.strategyChanges.associateBy { it.strategyId }
    val live = state.strategies.mapNotNull { state.since[it.id] }
    val combinedEquity = live.sumOf { 1_000_000.0 * (1.0 + it.returnPct) }
    val avgReturn = if (live.isEmpty()) 0.0 else live.map { it.returnPct }.average()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("模拟盘", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                        Text(
                            "${state.strategies.size} 个策略 · ${list.size} 只个股",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "策略创建后自动记录",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                        )
                        Text(
                            if (state.simulationLoading) "计算中…" else "每日 16:10 更新",
                            fontSize = 9.sp,
                            color = ShiguPalette.InkSoft,
                        )
                        Text(
                            "立即核对 →",
                            fontSize = 8.5.sp,
                            color = ShiguPalette.Ink,
                            modifier = Modifier.clickable(onClick = onForceRefresh),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("组合纸面", "¥%.0f".format(combinedEquity), Modifier.weight(1f))
                    MetricCell("平均收益", formatPct(avgReturn), Modifier.weight(1f))
                    MetricCell("策略数", "${live.size}", Modifier.weight(1f))
                }
            }
        }

        item { SectionLabel("策略模拟", trailing = "创建后收益 · 纸面组合") }

        item {
            val actual = state.strategyChanges.filter { it.hasChange }
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日调仓", fontSize = 10.sp, color = ShiguPalette.InkSoft)
                        Text(
                            when {
                                state.simulationLoading -> "正在核对收盘持仓…"
                                actual.isEmpty() -> "所有策略今日无调仓，持仓继续"
                                else -> "${actual.size} 个策略触发调仓"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        if (state.simulationLoading) "…" else "16:10",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
                actual.forEach { c ->
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .background(ShiguPalette.Alert, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) { Text("调仓", fontSize = 7.5.sp, color = ShiguPalette.White) }
                        Spacer(Modifier.width(4.dp))
                        Text(c.strategyName, fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.weight(1f))
                        Text(c.text, fontSize = 9.sp, color = ShiguPalette.Alert)
                    }
                }
            }
        }

        if (state.strategies.isEmpty()) {
            item {
                SketchCard {
                    EmptyCard("还没有策略", "在“策略”页运行回测，或从“因子”页把一组因子存为策略。")
                }
            }
        } else {
            items(state.strategies, key = { "sim_${it.id}" }) { config ->
                StrategySimCard(
                    config = config,
                    since = state.since[config.id],
                    change = changeMap[config.id],
                    onOpenStock = onOpenStock,
                    onOpenPaper = { onOpenPaper(config) },
                )
            }
        }

        item { SectionLabel("个股观察", trailing = "点开个股 · 右边移除") }

        if (list.isEmpty()) {
            item {
                SketchCard {
                    EmptyCard(
                        "还没有观察标的",
                        "从“选股”里点开一只股票，写下为什么关注它，再回来。",
                    )
                }
            }
        } else {
            items(list, key = { it.code }) { item ->
                WatchCard(item, onOpen = { onOpenStock(item.code) }, onRemove = { onRemove(item.code) })
            }
        }

        item {
            Text(
                "观察不是买入。这里只记录入选价、理由和当前价；没有真实交易，也没有收益承诺。",
                fontSize = 9.sp,
                color = ShiguPalette.InkSoft,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }

    if (state.paperDetail != null) {
        PaperDetailDialog(state.paperDetail!!, onClosePaper)
    }
}

@Composable
private fun StrategySimCard(
    config: StrategyConfig,
    since: SinceCreationResult?,
    change: com.shigu.core.StrategyChange?,
    onOpenStock: (String) -> Unit,
    onOpenPaper: () -> Unit,
) {
    SketchCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(config.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .background(ShiguPalette.Highlighter, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) { Text("模拟中", fontSize = 8.sp) }
                }
                Text(
                    "创建 ${shortDate(config.createdAt)} · ${config.factors.size} 因子 · 持仓 ${since?.portfolio?.holdings?.size ?: 0} 只",
                    fontSize = 8.5.sp,
                    color = ShiguPalette.InkSoft,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    since?.let { formatPct(it.returnPct) } ?: "计算中…",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor(since?.returnPct ?: 0.0),
                )
                Text(
                    since?.let { "基准 ${formatPct(it.benchmarkPct)} · ${it.points} 点" } ?: "等待数据",
                    fontSize = 7.5.sp,
                    color = ShiguPalette.InkFaint,
                )
                Text(
                    "详情 →",
                    fontSize = 8.sp,
                    color = ShiguPalette.InkSoft,
                    modifier = Modifier.clickable(onClick = onOpenPaper),
                )
            }
        }
        if (change != null) {
            Spacer(Modifier.height(3.dp))
            if (change.hasChange) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(5.dp))
                    Text(change.text, fontSize = 8.sp, color = ShiguPalette.Alert)
                }
            } else {
                Text(change.text, fontSize = 8.5.sp, color = ShiguPalette.InkFaint)
            }
        }
        if (!since?.picks.isNullOrEmpty()) {
            Spacer(Modifier.height(5.dp))
            SketchDivider(seed = "sim-${config.id}")
            if (since!!.portfolio?.holdings.isNullOrEmpty()) {
                Text("目标持仓（尚未成交）", fontSize = 7.5.sp, color = ShiguPalette.InkFaint)
            }
            since!!.picks.take(3).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenStock(p.code) }.padding(vertical = 1.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, fontSize = 9.5.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(p.code, fontSize = 7.5.sp, color = ShiguPalette.InkFaint)
                    Spacer(Modifier.weight(1f))
                    Text("%.2f".format(p.price), fontSize = 9.5.sp)
                    Spacer(Modifier.width(5.dp))
                    ChangeText(p.changePct)
                }
            }
        } else {
            Spacer(Modifier.height(3.dp))
            Text("暂无持仓；下一个调仓日按因子打分买入。", fontSize = 8.sp, color = ShiguPalette.InkFaint)
        }
    }
}

@Composable
private fun PaperDetailDialog(detail: PaperDetail, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
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
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 680.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(detail.config.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "模拟净值 · ${detail.equity.size} 个点 · ${detail.holdings.size} 只持仓",
                                fontSize = 8.5.sp,
                                color = ShiguPalette.InkSoft,
                            )
                        }
                        Text("关闭", fontSize = 10.sp, color = ShiguPalette.InkSoft, modifier = Modifier.clickable(onClick = onClose))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("创建后收益", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                            Text(
                                formatPct(detail.returnPct),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = changeColor(detail.returnPct),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("基准", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                            Text(formatPct(detail.benchmarkPct), fontSize = 12.sp, color = changeColor(detail.benchmarkPct))
                            Text("现金 ¥%.0f".format(detail.cash), fontSize = 8.sp, color = ShiguPalette.InkFaint)
                        }
                    }
                }
                item {
                    if (detail.equity.size >= 2) {
                        EquityChart(detail.equity)
                    } else {
                        Text(
                            "净值点不足：策略创建后需要至少一个交易日收盘数据才会出现曲线。",
                            fontSize = 9.sp,
                            color = ShiguPalette.InkSoft,
                        )
                    }
                }
                if (detail.change != null) {
                    item {
                        if (detail.change.hasChange) {
                            Box(
                                Modifier
                                    .background(ShiguPalette.Alert, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "调仓 · ${detail.change.text}",
                                    fontSize = 9.sp,
                                    color = ShiguPalette.White,
                                )
                            }
                        } else {
                            Text(detail.change.text, fontSize = 9.sp, color = ShiguPalette.InkFaint)
                        }
                    }
                }
                item { SketchDivider(seed = "paper-detail") }
                item {
                    Text("实际持仓", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (detail.holdings.isEmpty()) {
                    item {
                        Text("当前没有持仓，可能还在等待首个调仓日，或目标是空仓。", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                    }
                } else {
                    items(detail.holdings) { h ->
                        HoldingRow(h)
                    }
                }
                if (detail.trades.isNotEmpty()) {
                    item { SketchDivider(seed = "paper-trades") }
                    item { Text("交易流水 · 最近 ${minOf(12, detail.trades.size)} 笔", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                    items(detail.trades.takeLast(12).reversed()) { t ->
                        TradeRow(t)
                    }
                }
                item {
                    Text(
                        "纸面模拟：收盘信号、次日开盘、整手 100 股（科创 200）、等权；佣金 8bps + 卖出印花税 5bps + 滑点 5bps，不含冲击成本。",
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(h: Holding) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.name, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(
                "${h.code} · ${h.shares} 股 · 入选 ${h.entryDate}",
                fontSize = 8.sp,
                color = ShiguPalette.InkFaint,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.2f".format(h.lastPrice), fontSize = 10.sp)
            Text(
                "权重 %.1f%% · 入选 %.2f".format(h.weight * 100, h.entryPrice),
                fontSize = 8.sp,
                color = ShiguPalette.InkFaint,
            )
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
            Text(t.name, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
            Text(
                "${t.entryDate} → ${t.exitDate ?: "持仓中"} · ${t.reason}",
                fontSize = 7.5.sp,
                color = ShiguPalette.InkFaint,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (t.pnl >= 0) "+" else ""}%.0f".format(t.pnl),
                fontSize = 9.5.sp,
                color = changeColor(if (t.pnl >= 0) 1.0 else -1.0),
            )
            Text(
                "${formatPct(t.pnlPct)} · 费 %.0f".format(t.fees),
                fontSize = 7.5.sp,
                color = ShiguPalette.InkFaint,
            )
        }
    }
}

@Composable
private fun WatchCard(item: WatchItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    SketchCard(modifier = Modifier.clickable(onClick = onOpen), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Text(item.code, fontSize = 9.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.width(4.dp))
                    MiniTag(boardText(item.board))
                }
                Text(
                    "入选 ¥%.2f · %s · %s".format(item.entryPrice, shortDate(item.addedAt), item.group),
                    fontSize = 9.sp,
                    color = ShiguPalette.InkSoft,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("¥%.2f".format(item.price), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                ChangeText(item.changePct)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除观察",
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(4.dp),
                tint = ShiguPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(4.dp))
        SketchDivider(seed = "watch-${item.code}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.thesis.ifBlank { "（没写理由）" },
                fontSize = 9.5.sp,
                color = ShiguPalette.InkSoft,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Text(
                formatPct(item.returnPct),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = changeColor(item.returnPct),
            )
        }
    }
}
