package com.example.cashapp.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/*
 * Egyszerű, függőség nélküli diagramok (dataviz irányelvek szerint):
 * vékony jelek, 2dp felületszínű rés a kitöltések között, recesszív rács, egy tengely.
 * A számok a diagram mellett, szövegszínnel jelennek meg – a szín csak azonosít.
 */

/**
 * Kördiagram (gyűrű). [shares] összege 1. Koppintásra [onSliceTap] a szelet indexével.
 * [highlighted] = null → minden szelet teljes színnel; különben a többi halványabb.
 */
@Composable
fun DonutChart(
    shares: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thickness: Dp = 26.dp,
    gap: Dp = 2.dp,
    highlighted: Int? = null,
    onSliceTap: (Int) -> Unit = {},
) {
    Canvas(
        modifier
            .size(size)
            .pointerInput(shares) {
                detectTapGestures { pos ->
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val r = this.size.width / 2f
                    val dist = hypot(pos.x - center.x, pos.y - center.y)
                    if (dist < r - thickness.toPx() - 8.dp.toPx() || dist > r + 8.dp.toPx()) return@detectTapGestures
                    // Szög 12 órától, óramutató szerint, 0..360
                    var deg = Math.toDegrees(atan2((pos.y - center.y).toDouble(), (pos.x - center.x).toDouble())).toFloat() + 90f
                    if (deg < 0) deg += 360f
                    var acc = 0f
                    shares.forEachIndexed { i, s ->
                        acc += s * 360f
                        if (deg <= acc) { onSliceTap(i); return@detectTapGestures }
                    }
                }
            },
    ) {
        val stroke = thickness.toPx()
        val radius = (this.size.minDimension - stroke) / 2f
        val topLeft = Offset((this.size.width - radius * 2) / 2f, (this.size.height - radius * 2) / 2f)
        // A 2dp rés szögben kifejezve ezen a sugáron (egyetlen szeletnél nincs rés).
        val gapDeg = if (shares.size > 1) (gap.toPx() / (2 * PI.toFloat() * radius)) * 360f else 0f
        var start = -90f
        shares.forEachIndexed { i, share ->
            val sweep = share * 360f
            val color = colors.getOrElse(i) { Color.Gray }
            val alpha = if (highlighted == null || highlighted == i) 1f else 0.35f
            if (sweep - gapDeg > 0.1f) {
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = start + gapDeg / 2,
                    sweepAngle = sweep - gapDeg,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke),
                )
            }
            start += sweep
        }
    }
}

/**
 * Csoportosított oszlopdiagram: hónaponként két oszlop (bevétel, kiadás), közös skálán.
 * Koppintásra [onTap] a hónap indexével; a kijelölt csoport teljes színű, a többi halvány.
 */
@Composable
fun GroupedBarChart(
    values: List<Pair<Long, Long>>,
    max: Long,
    firstColor: Color,
    secondColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onTap: (Int) -> Unit = {},
) {
    Canvas(
        modifier.pointerInput(values.size) {
            detectTapGestures { pos ->
                if (values.isEmpty()) return@detectTapGestures
                val index = (pos.x / (this.size.width / values.size)).toInt().coerceIn(0, values.lastIndex)
                onTap(index)
            }
        },
    ) {
        val h = this.size.height
        // Recesszív rács: alapvonal + fél + teljes magasság.
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = h - f * h
            drawLine(gridColor, Offset(0f, y), Offset(this.size.width, y), strokeWidth = if (f == 0f) 1.5f else 1f)
        }
        if (values.isEmpty() || max <= 0) return@Canvas

        val groupW = this.size.width / values.size
        val barW = (groupW * 0.28f).coerceAtMost(14.dp.toPx())
        val innerGap = 2.dp.toPx()
        val radius = 4.dp.toPx()
        values.forEachIndexed { i, (a, b) ->
            val cx = groupW * i + groupW / 2f
            val alpha = if (selected == null || selected == i) 1f else 0.35f
            drawTopRoundedBar(cx - innerGap / 2 - barW, barW, h * a / max, h, radius, firstColor.copy(alpha = alpha))
            drawTopRoundedBar(cx + innerGap / 2, barW, h * b / max, h, radius, secondColor.copy(alpha = alpha))
        }
    }
}

/** Oszlop, csak a felső sarkai lekerekítve (az alapvonalhoz simul). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTopRoundedBar(
    left: Float, width: Float, height: Float, baseline: Float, radius: Float, color: Color,
) {
    if (height <= 0.5f) return
    val r = minOf(radius, height, width / 2)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left, top = baseline - height, right = left + width, bottom = baseline,
                topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color)
}
