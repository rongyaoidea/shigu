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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shigu.core.FactorKey
import com.shigu.core.OwnerType
import com.shigu.core.StStatus
import com.shigu.core.formatPct
import com.shigu.core.formatYi
import com.shigu.ui.StockDetailUi
import com.shigu.ui.UiState
import com.shigu.ui.components.CandleChart
import com.shigu.ui.components.ChangeText
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.MiniTag
import com.shigu.ui.components.boardText
import com.shigu.ui.components.factorText
import com.shigu.ui.components.scoreText
import com.shigu.ui.sketch.SketchButton
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import java.util.Locale

@Composable
fun StockScreen(
    code: String,
    state: UiState,
    onBack: () -> Unit,
    onLoad: (String) -> Unit,
    onToggleWatch: (String, String) -> Unit,
) {
    LaunchedEffect(code) { onLoad(code) }
    var showWatch by remember { mutableStateOf(false) }
    var thesis by remember { mutableStateOf("") }
    val detail = state.stockDetail

    Column(Modifier.fillMaxSize().background(ShiguPalette.Paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .size(20.dp),
                tint = ShiguPalette.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text("个股", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(36.dp))
        }
        SketchDivider(seed = "stock-top", ink = ShiguPalette.InkFaint)

        if (detail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.stockLoading) "加载中…" else "没有数据，请先同步", fontSize = 12.sp, color = ShiguPalette.InkSoft)
            }
            return@Column
        }

        val s = detail.stock
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Text(s.code, fontSize = 10.sp, color = ShiguPalette.InkSoft)
                        Spacer(Modifier.width(4.dp))
                        MiniTag(boardText(s.board))
                        if (s.stStatus != StStatus.NORMAL) {
                            Spacer(Modifier.width(4.dp))
                            MiniTag(s.stStatus.label, ShiguPalette.Up)
                        }
                        if (s.ownerType != OwnerType.UNKNOWN) {
                            Spacer(Modifier.width(4.dp))
                            MiniTag(s.ownerType.label)
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.2f".format(s.price),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        ChangeText(s.changePct, big = true)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "距20日高 ${formatPct(detail.factors[FactorKey.HIGH20_GAP])}",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                        )
                    }
                }
            }

            item {
                SketchCard(contentPadding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("日 K · 前复权", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("MA20 / MA60", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    }
                    Spacer(Modifier.height(3.dp))
                    CandleChart(detail.bars)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("— MA20", fontSize = 8.sp, color = ShiguPalette.InkSoft)
                        Text("--- MA60", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                        Spacer(Modifier.weight(1f))
                        Text("${detail.bars.size} 日", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("总市值", formatYi(s.marketCap), Modifier.weight(1f))
                    MetricCell("PE-TTM", s.peLabel, Modifier.weight(1f))
                    MetricCell("PB", s.pb?.let { "%.2f".format(it) } ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("换手", "%.2f%%".format(s.turnover), Modifier.weight(1f))
                    MetricCell("成交额", formatYi(s.amount), Modifier.weight(1f))
                    MetricCell("60日涨幅", formatPct(s.change60?.div(100.0)), Modifier.weight(1f))
                }
            }

            item {
                SketchCard(contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("基础资料", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                s.profileUpdatedAt <= 0 -> "未同步"
                                s.reportDate.isNotBlank() -> "财务 ${s.reportDate}"
                                else -> "已同步"
                            },
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    InfoLine("企业性质", s.ownerType.label)
                    InfoLine("实控人", s.actualHolder.ifBlank { "—" })
                    InfoLine(
                        "第一大股东",
                        if (s.firstHolder.isBlank()) "—" else buildString {
                            append(s.firstHolder)
                            if (s.firstHolderType.isNotBlank()) append(" · ${s.firstHolderType}")
                            s.firstHoldRatio?.let { append(" · ${"%.2f".format(it)}%") }
                        },
                    )
                    InfoLine(
                        "上市",
                        if (s.listedDate.isBlank()) "—" else buildString {
                            append(s.listedDate)
                            s.listedDaysAt(java.time.LocalDate.now().toString())?.let { append(" · ${it}天") }
                        },
                    )
                    if (s.profileUpdatedAt <= 0) {
                        Text(
                            "去“数据”页点“基础资料”同步：企业性质/上市日期/ROE/毛利率/增速/负债率。",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        SketchDivider(seed = "stock-profile")
                        Row(Modifier.fillMaxWidth()) {
                            MetricCell("ROE", pctPoint(s.roe), Modifier.weight(1f))
                            MetricCell("毛利率", pctPoint(s.grossMargin), Modifier.weight(1f))
                            MetricCell("净利率", pctPoint(s.netMargin), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MetricCell("营收增速", pctPoint(s.revenueGrowth), Modifier.weight(1f))
                            MetricCell("净利增速", pctPoint(s.profitGrowth), Modifier.weight(1f))
                            MetricCell("负债率", pctPoint(s.debtRatio), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MetricCell("EPS", num2(s.eps), Modifier.weight(1f))
                            MetricCell("BPS", num2(s.bps), Modifier.weight(1f))
                            MetricCell("每股现金", num2(s.ocfps), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "企业性质为实控人/第一大股东名称的启发式推断，不是官方标签；财务为最新报告期快照。",
                            fontSize = 8.sp,
                            color = ShiguPalette.InkFaint,
                        )
                    }
                }
            }

            item {
                SketchCard(contentPadding = 12.dp) {
                    Text("因子快照", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("日频近似 · 横截面因子不在此页", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                    Spacer(Modifier.height(5.dp))
                    detail.factors.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { (k, v) ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text(k.shortLabel, fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.width(48.dp))
                                    Text(factorText(k, v), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(3.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    SketchDivider(seed = "stock-factor")
                    Text(
                        "RSRS：18 日 high~low 回归的 60 日标准分；LLT：低延迟趋势线；球队硬币/隔夜距离需要全市场截面，在选股页看。",
                        fontSize = 8.sp,
                        color = ShiguPalette.InkFaint,
                    )
                }
            }

            item {
                SketchCard(contentPadding = 12.dp) {
                    Text("观察", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (detail.watched) "已在观察池。写下理由，30 天后回来复盘。" else "只在“为什么是它”写清楚之后才加入。",
                        fontSize = 9.sp,
                        color = ShiguPalette.InkSoft,
                    )
                    Spacer(Modifier.height(7.dp))
                    if (detail.watched) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(11.dp))
                                .clickable { onToggleWatch(s.code, "") }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("移出观察池", fontSize = 12.sp, color = ShiguPalette.InkSoft)
                        }
                    } else {
                        SketchButton("加入观察", onClick = { showWatch = true })
                    }
                }
            }

            item {
                Text(
                    "数据为公开行情延迟快照；因子与回测均为历史/统计描述，不构成投资建议。",
                    fontSize = 8.5.sp,
                    color = ShiguPalette.InkFaint,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }

    if (showWatch) {
        AlertDialog(
            onDismissRequest = { showWatch = false },
            title = { Text("加入观察", fontSize = 15.sp) },
            text = {
                Column {
                    Text("${detail!!.stock.name} ${detail!!.stock.code} · ¥%.2f".format(detail!!.stock.price), fontSize = 10.sp, color = ShiguPalette.InkSoft)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = thesis,
                        onValueChange = { thesis = it },
                        label = { Text("为什么是它？跌 10% 还拿得住吗？") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("写不写都能加；但没理由的观察，30 天后没法复盘。", fontSize = 8.sp, color = ShiguPalette.InkFaint)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWatch = false
                    onToggleWatch(detail!!.stock.code, thesis)
                }) { Text("确认加入") }
            },
            dismissButton = { TextButton(onClick = { showWatch = false }) { Text("取消") } },
            containerColor = ShiguPalette.Paper,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 9.sp, color = ShiguPalette.InkSoft, modifier = Modifier.width(62.dp))
        Text(value, fontSize = 9.5.sp, color = ShiguPalette.Ink, modifier = Modifier.weight(1f), maxLines = 2)
    }
}

private fun pctPoint(v: Double?): String =
    if (v == null || v.isNaN()) "—" else String.format(Locale.CHINA, "%.1f%%", v)

private fun num2(v: Double?): String =
    if (v == null || v.isNaN()) "—" else String.format(Locale.CHINA, "%.2f", v)
