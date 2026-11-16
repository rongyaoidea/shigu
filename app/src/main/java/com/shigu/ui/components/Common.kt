package com.shigu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shigu.core.Board
import com.shigu.core.FactorKey
import com.shigu.core.format1
import com.shigu.core.format2
import com.shigu.core.formatPct
import com.shigu.core.formatYi
import com.shigu.ui.theme.ShiguPalette
import java.util.Locale

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = ShiguPalette.Ink)
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = ShiguPalette.InkSoft)
        }
    }
}

@Composable
fun PaperPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .rotate(if (selected) -0.6f else 0.3f)
            .background(if (selected) ShiguPalette.Ink else ShiguPalette.Paper, RoundedCornerShape(7.dp))
            .border(1.dp, if (selected) ShiguPalette.Ink else ShiguPalette.InkFaint, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (selected) ShiguPalette.Paper else ShiguPalette.InkSoft,
            maxLines = 1,
        )
    }
}

@Composable
fun MiniTag(text: String, color: Color = ShiguPalette.InkSoft) {
    Box(
        Modifier
            .rotate(-0.8f)
            .border(0.8.dp, color.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 9.sp, color = color)
    }
}

@Composable
fun ChangeText(
    changePct: Double,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val color = changeColor(changePct)
    val arrow = when {
        changePct > 0.0001 -> "▲"
        changePct < -0.0001 -> "▼"
        else -> "—"
    }
    Text(
        text = "$arrow ${format2(changePct)}%",
        modifier = modifier,
        color = color,
        fontSize = if (big) 15.sp else 10.sp,
        fontWeight = if (big) FontWeight.SemiBold else FontWeight.Medium,
    )
}

@Composable
fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ShiguPalette.Ink,
) {
    Column(modifier) {
        Text(label, fontSize = 9.sp, color = ShiguPalette.InkSoft)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun EmptyCard(text: String, hint: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ShiguPalette.InkSoft, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, fontSize = 10.sp, color = ShiguPalette.InkFaint, textAlign = TextAlign.Center)
        }
    }
}

fun changeColor(v: Double): Color = when {
    v > 0.0001 -> ShiguPalette.Up
    v < -0.0001 -> ShiguPalette.Down
    else -> ShiguPalette.InkSoft
}

fun boardText(b: Board): String = b.label

fun factorText(key: FactorKey, v: Double?): String {
    if (v == null || v.isNaN()) return "—"
    return when (key) {
        FactorKey.TURNOVER20 -> String.format(Locale.CHINA, "%.1f%%", v)
        FactorKey.AMOUNT20, FactorKey.MARKET_CAP -> formatYi(v)
        FactorKey.PE, FactorKey.PB -> format2(v)
        FactorKey.AMIHUD20 -> String.format(Locale.CHINA, "%.2e", v)
        FactorKey.AMOUNT_STABILITY, FactorKey.TURNOVER_STABILITY -> String.format(Locale.CHINA, "%.2f", v)
        else -> if (key.unit == "%") formatPct(v) else format2(v)
    }
}

fun scoreText(v: Double): String = String.format(Locale.CHINA, "%.2f", v)

fun shortDate(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    val d = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return String.format(Locale.CHINA, "%02d-%02d", d.monthValue, d.dayOfMonth)
}
