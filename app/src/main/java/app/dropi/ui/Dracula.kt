package app.dropi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Dracula's dripping-blood edge across the top of the screen: a dark red band behind the status
 * bar with drips of different lengths that run down when the screen opens. Purely decorative; it
 * draws over the page but never takes a touch.
 */
@Composable
fun BloodDrips(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    // The drips run down once when the screen opens, then hold still: a slow endless loop would
    // be distracting (and about the rate that bothers motion-sensitive people).
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val ooze = grow.value

    // x position (share of the width), drip length (dp), width (dp), how much it stretches.
    val drips = listOf(
        floatArrayOf(0.04f, 9f, 9f, 0.3f),
        floatArrayOf(0.13f, 20f, 12f, 0.6f),
        floatArrayOf(0.22f, 7f, 8f, 0.2f),
        floatArrayOf(0.34f, 15f, 10f, 0.8f),
        floatArrayOf(0.47f, 26f, 13f, 0.5f),
        floatArrayOf(0.58f, 8f, 8f, 0.4f),
        floatArrayOf(0.69f, 18f, 11f, 0.9f),
        floatArrayOf(0.81f, 11f, 9f, 0.3f),
        floatArrayOf(0.92f, 22f, 12f, 0.7f),
    )
    val deep = Color(0xFF5C0010)
    val blood = Color(0xFF9E0019)
    val shine = Color(0x33FFFFFF)

    Canvas(modifier.fillMaxWidth().height(top + 46.dp)) {
        val band = top.toPx() + 6.dp.toPx()
        val fill = Brush.verticalGradient(listOf(deep, blood), startY = 0f, endY = band + 40.dp.toPx())
        drawRect(fill, size = androidx.compose.ui.geometry.Size(size.width, band))
        for (d in drips) {
            val cx = size.width * d[0]
            val w = d[2].dp.toPx()
            val len = (d[1] * (0.35f + 0.65f * ooze) * (1f + 0.15f * d[3])).dp.toPx()
            val tipR = w * 0.62f
            // A drip: wide where it leaves the band, narrowing, then a round bead at the end.
            val p = Path().apply {
                moveTo(cx - w, band - 1f)
                cubicTo(cx - w * 0.55f, band + len * 0.25f, cx - tipR, band + len * 0.6f, cx - tipR, band + len)
                cubicTo(cx - tipR, band + len + tipR * 1.4f, cx + tipR, band + len + tipR * 1.4f, cx + tipR, band + len)
                cubicTo(cx + tipR, band + len * 0.6f, cx + w * 0.55f, band + len * 0.25f, cx + w, band - 1f)
                close()
            }
            drawPath(p, fill)
            // A small highlight on the bead, so it reads as wet.
            drawCircle(shine, tipR * 0.32f, Offset(cx - tipR * 0.35f, band + len + tipR * 0.1f))
        }
    }
}
