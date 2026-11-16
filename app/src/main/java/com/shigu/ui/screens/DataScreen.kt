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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shigu.core.FactorKey
import com.shigu.core.StrategyPresets
import com.shigu.core.Universe
import com.shigu.ui.UiState
import com.shigu.ui.components.MetricCell
import com.shigu.ui.components.PaperPill
import com.shigu.ui.components.SectionLabel
import com.shigu.ui.components.shortDate
import com.shigu.ui.sketch.SketchCard
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import java.util.Locale

@Composable
fun DataScreen(
    state: UiState,
    onQuick: () -> Unit,
    onDaily: () -> Unit,
    onFull: () -> Unit,
    onValuation: () -> Unit,
    onProfile: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onDailyUniverse: (Universe) -> Unit,
) {
    val stats = state.stats
    val sync = state.sync

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("本地数据", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${stats.stockCount} 只资产 · 日线 ${formatCount(stats.barCount)} · 估值 ${formatCount(stats.valuationCount)}",
                            fontSize = 9.sp,
                            color = ShiguPalette.InkSoft,
                        )
                        Text(
                            "基础资料 ${stats.profileCount} 只" +
                                if (stats.profileReportDate.isBlank()) " · 未同步" else " · 报告期 ${stats.profileReportDate}",
                            fontSize = 8.5.sp,
                            color = if (stats.profileCount > 0) ShiguPalette.InkSoft else ShiguPalette.InkFaint,
                        )
                    }
                    Text(
                        "%.1f MB".format(stats.dbBytes / 1024.0 / 1024.0),
                        fontSize = 10.sp,
                        color = ShiguPalette.InkSoft,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("最后交易日", stats.lastTradeDate.ifBlank { "—" }, Modifier.weight(1f))
                    MetricCell("最后同步", if (stats.lastSync > 0) shortDate(stats.lastSync) else "—", Modifier.weight(1f))
                    MetricCell("数据源", "新浪+腾讯", Modifier.weight(1f))                }
            }
        }

        item {
            if (sync.running) {
                SketchCard(contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("同步中 · ${sync.phase}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${sync.done}/${sync.total} · 失败 ${sync.failed}", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                        }
                        Text("${(sync.fraction * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { sync.fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = ShiguPalette.Ink,
                        trackColor = ShiguPalette.Well,
                    )
                    if (sync.message.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(sync.message, fontSize = 8.5.sp, color = ShiguPalette.InkFaint)
                    }
                }
            }
        }

        item { SectionLabel("同步", trailing = "自用 · 公开接口") }

        item {
            SketchCard(contentPadding = 12.dp) {
                SyncButton("快速同步 · 市值前300", "首次用这个，约 1–3 分钟", onQuick)
                Spacer(Modifier.height(7.dp))
                SyncButton("日常更新 · ${state.dailyUniverse.label}", "只拉缺失交易日；每日 16:10 自动跑", onDaily)
                Spacer(Modifier.height(7.dp))
                SyncButton("全量下载 · 全部 A 股", "约 5,900 只 × 300 日线，耗时较长", onFull)
                Spacer(Modifier.height(7.dp))
                SyncButton("估值历史 · 回测池前800", "PE/PB/市值，价值因子回测用", onValuation)
                Spacer(Modifier.height(7.dp))
                SyncButton("基础资料 · 企业性质/ST/财务", "东方财富 F10 批量分页，约 1–2 分钟", onProfile)
            }
        }

        item { SectionLabel("自动更新", trailing = "WorkManager") }

        item {
            SketchCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每日 16:10 自动更新", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("仅在有网络时运行；默认市值前800，避免跑太久", fontSize = 8.5.sp, color = ShiguPalette.InkSoft)
                    }
                    Switch(
                        checked = state.autoUpdate,
                        onCheckedChange = onAutoUpdate,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShiguPalette.Paper,
                            checkedTrackColor = ShiguPalette.Ink,
                            uncheckedThumbColor = ShiguPalette.InkSoft,
                            uncheckedTrackColor = ShiguPalette.Well,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                SketchDivider(seed = "data-universe")
                Text("自动更新池", fontSize = 9.sp, color = ShiguPalette.InkSoft)
                Spacer(Modifier.height(5.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Universe.entries) { u ->
                        PaperPill(u.label, state.dailyUniverse == u, { onDailyUniverse(u) })
                    }
                }
            }
        }

        item { SectionLabel("因子与策略", trailing = "研究记录") }

        item {
            SketchCard(contentPadding = 12.dp) {
                Text("${FactorKey.entries.size} 个因子 · ${StrategyPresets.all.size} 个内置策略", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "趋势：RSRS 右偏/标准分/斜率/R²、LLT、均线、距20日高、回撤。\n" +
                        "量价：球队硬币、隔夜距离、量价背离、换手/成交额稳定、Amihud、量比。\n" +
                        "风险：特质波动、低波、Beta、MAX、偏度、峰度、ATR。\n" +
                        "基础：企业性质（央企/地方国企/民企/外资/无实控人）、ST/*ST/退市整理、停牌、上市天数、板块、市值。\n" +
                        "基本面：ROE、毛利率、净利率、营收/净利增速、资产负债率、EPS/BPS、每股经营现金流。\n" +
                        "传统动量/反转和当前 PE/PB/市值也保留在库里，供自定义筛选。",
                    fontSize = 8.5.sp,
                    lineHeight = 13.sp,
                    color = ShiguPalette.InkSoft,
                )
                Spacer(Modifier.height(7.dp))
                SketchDivider(seed = "data-research")
                Text("研报口径", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "光大证券 RSRS（2017）；广发证券 LLT（2013/2017）；方正金工球队硬币（2022/2025）；" +
                        "华西证券量价背离/交易稳定性（2025）；国盛证券换手稳定（2025）；" +
                        "Journal of Asset Management 低波动异象（2021）。\n" +
                        "本工具用日频数据做近似，不宣称复现原报告；所有回测为历史模拟，不构成投资建议。",
                    fontSize = 8.5.sp,
                    lineHeight = 13.sp,
                    color = ShiguPalette.InkSoft,
                )
            }
        }

        item {
            Text(
                "自用工具：行情来自新浪列表 + 腾讯日线，延迟约 15 分钟；估值历史走东方财富，可能被限流。不要用它做自动交易。",
                fontSize = 8.5.sp,
                color = ShiguPalette.InkFaint,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun SyncButton(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, ShiguPalette.InkFaint, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 8.sp, color = ShiguPalette.InkSoft)
        }
        Text("→", fontSize = 12.sp, color = ShiguPalette.InkFaint)
    }
}

private fun formatCount(v: Long): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
    v >= 1_000 -> "%.1fK".format(v / 1_000.0)
    else -> v.toString()
}
