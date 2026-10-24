package app.dropi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit

/**
 * The aurora behind the top of every page: three soft lights (green, cyan, violet) that fade
 * into the night. Fixed to the screen, so the content scrolls over it and the header blurs it.
 */
@Composable
fun AuroraGlow(modifier: Modifier) {
    val (a, b, c) = C.Aurora
    val strength = if (C.dracula) 1f else if (C.dark) 0.55f else 0.45f
    Canvas(modifier) {
        val w = size.width
        fun light(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha * strength), Color.Transparent), Offset(cx, cy), r),
                radius = r, center = Offset(cx, cy),
            )
        }
        light(a, w * 0.22f, w * 0.62f, w * 0.85f, 0.5f)
        light(c, w * 0.86f, w * 0.38f, w * 0.75f, 0.42f)
        light(b, w * 0.55f, w * 0.12f, w * 0.6f, 0.32f)
    }
}

/**
 * Where a value sits on the consensus bands: low, in range, high, very high as one bar with hard
 * edges, and a glowing marker that glides to each new value. 3.9 and 10.0 are labelled.
 */
@Composable
fun RangeRuler(mmol: Double, unit: GlucoseUnit, modifier: Modifier = Modifier) {
    val lo = 2.2
    val hi = 16.0
    fun frac(v: Double) = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    val pos by animateFloatAsState(frac(mmol), spring(dampingRatio = 0.75f, stiffness = 140f), label = "ruler")
    val low = C.Low
    val inRange = C.InRange
    val high = C.High
    val veryHigh = C.VeryHigh
    val marker = C.Text
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = "On the range bar: ${Glucose.format(mmol, unit)} ${unit.label}" },
    ) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val bar = 10.dp.toPx()
            val y = 12.dp.toPx()
            val w = size.width
            // Round ends on the bar as a whole, hard edges between bands.
            val segs = listOf(lo to Glucose.LOW to low, Glucose.LOW to Glucose.TIR_HIGH to inRange,
                Glucose.TIR_HIGH to Glucose.VERY_HIGH to high, Glucose.VERY_HIGH to hi to veryHigh)
            segs.forEachIndexed { i, (range, color) ->
                val x0 = frac(range.first) * w
                val x1 = frac(range.second) * w
                val r = if (i == 0 || i == segs.lastIndex) CornerRadius(bar / 2) else CornerRadius.Zero
                drawRoundRect(color, Offset(x0, y), Size(x1 - x0 + if (i < segs.lastIndex) 1f else 0f, bar), r)
            }
            // Square inner ends for the first and last bands.
            drawRect(low, Offset(frac(Glucose.LOW) * w - bar / 2, y), Size(bar / 2, bar))
            drawRect(veryHigh, Offset(frac(Glucose.VERY_HIGH) * w, y), Size(bar / 2, bar))
            val mx = pos * w
            val mw = 6.dp.toPx()
            drawRoundRect(marker.copy(alpha = 0.25f), Offset(mx - mw * 1.5f, 0f), Size(mw * 3, size.height), CornerRadius(mw * 1.5f))
            drawRoundRect(marker, Offset(mx - mw / 2, 2.dp.toPx()), Size(mw, size.height - 4.dp.toPx()), CornerRadius(mw / 2))
        }
        val labelStyle = inter(12.sp, color = C.TextMuted)
        Text(Glucose.format(Glucose.LOW, unit), style = labelStyle, modifier = Modifier.offset(x = maxWidth * frac(Glucose.LOW) - 10.dp, y = 32.dp))
        Text(Glucose.format(Glucose.TIR_HIGH, unit), style = labelStyle, modifier = Modifier.offset(x = maxWidth * frac(Glucose.TIR_HIGH) - 12.dp, y = 32.dp))
    }
}
