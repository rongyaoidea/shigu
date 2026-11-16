package com.shigu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shigu.core.DailyBar
import com.shigu.core.EquityPoint
import com.shigu.ui.theme.ShiguPalette
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandleChart(
    bars: List<DailyBar>,
    modifier: Modifier = Modifier,
    showVolume: Boolean = true,
) {
    val visible = remember(bars) { bars.takeLast(140) }
    Canvas(modifier = modifier.fillMaxWidth().height(if (showVolume) 208.dp else 160.dp)) {
        if (visible.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val priceH = if (showVolume) h * 0.74f else h
        val volTop = priceH + 10f
        val volBottom = h - 2f
        var lo = visible.minOf { it.low }
        var hi = visible.maxOf { it.high }
        val pad = (hi - lo) * 0.06
        if (pad > 0) {
            lo -= pad
            hi += pad
        }
        val span = (hi - lo).coerceAtLeast(0.0001)
        fun y(v: Double): Float = (priceH.toDouble() * (1.0 - (v - lo) / span)).toFloat()

        // 网格
        for (i in 1..3) {
            val yy = priceH * i / 4f
            drawLine(
                color = ShiguPalette.InkFaint.copy(alpha = 0.35f),
                start = Offset(0f, yy),
                end = Offset(w, yy),
                strokeWidth = 0.8f,
            )
        }

        val step = w / visible.size
        val body = max(2.4f, step * 0.55f)
        visible.forEachIndexed { i, b ->
            val cx = step * i + step / 2f
            val color = if (b.close >= b.open) ShiguPalette.Up else ShiguPalette.Down
            drawLine(color, Offset(cx, y(b.high)), Offset(cx, y(b.low)), strokeWidth = 1.0f)
            val yo = y(b.open)
            val yc = y(b.close)
            val top = min(yo, yc)
            val bh = max(1.2f, kotlin.math.abs(yc - yo))
            if (b.close >= b.open) {
                drawRect(
                    color = color,
                    topLeft = Offset(cx - body / 2, top),
                    size = androidx.compose.ui.geometry.Size(body, bh),
                    style = Stroke(width = 1.1f),
                )
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(cx - body / 2, top),
                    size = androidx.compose.ui.geometry.Size(body, bh),
                )
            }
        }

        // MA20 / MA60
        fun drawMa(n: Int, color: Color, width: Float) {
            val path = Path()
            var started = false
            val startIndex = bars.size - visible.size
            for (i in visible.indices) {
                val absIndex = startIndex + i
                if (absIndex - n + 1 < 0) continue
                var sum = 0.0
                for (k in absIndex - n + 1..absIndex) sum += bars[k].close
                val ma = sum / n
                val x = step * i + step / 2f
                val yy = y(ma)
                if (!started) {
                    path.moveTo(x, yy)
                    started = true
                } else {
                    path.lineTo(x, yy)
                }
            }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
        }
        drawMa(20, ShiguPalette.Ink.copy(alpha = 0.72f), 1.5f)
        drawMa(60, ShiguPalette.InkFaint.copy(alpha = 0.9f), 1.2f)

        // 成交量
        if (showVolume) {
            val maxVol = visible.maxOf { it.volume }.coerceAtLeast(1.0)
            val vh = volBottom - volTop
            visible.forEachIndexed { i, b ->
                val bh = (b.volume / maxVol * vh).toFloat()
                val cx = step * i + step / 2f
                val color = if (b.close >= b.open) ShiguPalette.Up else ShiguPalette.Down
                drawRect(
                    color = color.copy(alpha = 0.30f),
                    topLeft = Offset(cx - body / 2, volBottom - bh),
                    size = androidx.compose.ui.geometry.Size(body, bh),
                )
            }
        }

        // 最新价虚线
        val last = visible.last()
        val lastY = y(last.close)
        drawLine(
            color = ShiguPalette.Ink.copy(alpha = 0.5f),
            start = Offset(0f, lastY),
            end = Offset(w, lastY),
            strokeWidth = 0.8f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
        )
    }
}

@Composable
fun EquityChart(
    points: List<EquityPoint>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(170.dp)) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val pad = 4f
        var lo = min(points.minOf { it.strategy }, points.minOf { it.benchmark })
        var hi = max(points.maxOf { it.strategy }, points.maxOf { it.benchmark })
        val span = (hi - lo).coerceAtLeast(0.0001)
        fun x(i: Int): Float = pad + (w - pad * 2) * i / (points.size - 1)
        fun y(v: Double): Float = (pad + (h - pad * 2).toDouble() * (1.0 - (v - lo) / span)).toFloat()

        for (i in 1..3) {
            val yy = h * i / 4f
            drawLine(ShiguPalette.InkFaint.copy(alpha = 0.35f), Offset(0f, yy), Offset(w, yy), 0.8f)
        }
        // strategy area
        val area = Path()
        area.moveTo(x(0), y(1.0))
        points.forEachIndexed { i, p -> area.lineTo(x(i), y(p.strategy)) }
        area.lineTo(x(points.lastIndex), y(1.0))
        area.close()
        drawPath(area, ShiguPalette.Ink.copy(alpha = 0.06f))

        fun line(selector: (EquityPoint) -> Double, color: Color, sw: Float, dash: Boolean) {
            val p = Path()
            points.forEachIndexed { i, e ->
                if (i == 0) p.moveTo(x(i), y(selector(e))) else p.lineTo(x(i), y(selector(e)))
            }
            drawPath(
                p, color,
                style = Stroke(
                    width = sw,
                    cap = StrokeCap.Round,
                    pathEffect = if (dash) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f)) else null,
                ),
            )
        }
        line({ it.benchmark }, ShiguPalette.InkFaint, 1.2f, true)
        line({ it.strategy }, ShiguPalette.Ink, 1.8f, false)
        // 1.0 基准
        drawLine(
            ShiguPalette.InkFaint,
            Offset(0f, y(1.0)), Offset(w, y(1.0)),
            strokeWidth = 0.8f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
        )
    }
}
