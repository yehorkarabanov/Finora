package com.yehor.finora.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Horizontal bar split into rounded segments sized by normalised weight, 3dp gaps between. */
@Composable
fun SplitBar(
    parts: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    SplitBar(parts = { parts }, modifier = modifier, height = height)
}

/**
 * [SplitBar] whose [parts] are read only while drawing, so animated weights redraw the bar without
 * recomposing it.
 */
@Composable
fun SplitBar(
    parts: () -> List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val visible = parts().filter { it.first > 0f }
        val total = visible.sumOf { it.first.toDouble() }.toFloat()
        if (visible.isEmpty() || total <= 0f) return@Canvas
        val gap = 3.dp.toPx()
        val usable = (size.width - gap * (visible.size - 1)).coerceAtLeast(0f)
        val radius = CornerRadius(size.height / 2f)
        var x = 0f
        visible.forEach { (weight, color) ->
            val w = usable * weight / total
            drawRoundRect(color, Offset(x, 0f), Size(w, size.height), radius)
            x += w + gap
        }
    }
}
