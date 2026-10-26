package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.R
import app.dropi.data.Glucose

/*
 * "Instrument & logbook" (1.1): hairlines and gutters instead of cards, IBM Plex Mono for times,
 * grams and small labels, Bricolage for the numbers that matter, colour only where it means
 * something (the range colours).
 */

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/** IBM Plex Mono: times, grams, counts and the small labels. Never under 12 sp. */
fun mono(size: TextUnit = 13.sp, weight: FontWeight = FontWeight.Normal, color: Color = C.TextSecondary, tracking: Float = 0f) =
    TextStyle(fontFamily = PlexMono, fontSize = size, fontWeight = weight, color = color, letterSpacing = tracking.em, fontFeatureSettings = "tnum")

/** A small label in capitals ("TODAY"), in the app's language. */
fun caps(s: String): String = s.uppercase(L.locale)

/**
 * A hairline across the top of a row: the line between it and the row above. Pass
 * `show = false` for the first row of a list (under a [RuleHeader], whose own line is enough, or
 * at the top of a panel), so lines only ever sit between rows.
 */
fun Modifier.ruleTop(show: Boolean = true, color: Color = C.Border.copy(alpha = 0.55f)): Modifier =
    if (!show) this else drawBehind {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }

/** "TODAY ───────── 3 READINGS": a section's name, a hairline to the edge, and a count. */
@Composable
fun RuleHeader(label: String, trailing: String? = null, modifier: Modifier = Modifier) {
    val line = C.Border
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(caps(label), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.08f), maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(line))
        if (trailing != null) Text(caps(trailing), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.08f), maxLines = 1)
    }
}

/** "ALL READINGS →": a quiet link in mono capitals, a full 48 dp tall. */
@Composable
fun MonoLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        caps(label) + " →",
        style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f),
        modifier = modifier.heightIn(min = 48.dp).tap(onClick = onClick).padding(vertical = 15.dp),
    )
}

/**
 * Where a value sits, small enough for a row: a short track, the in-range band on it, and a tick
 * at the value. Decoration only; the row says the value in words.
 */
@Composable
fun MicroScale(mmol: Double, modifier: Modifier = Modifier, width: Dp = 44.dp) {
    val band = C.InRange
    val track = C.Border
    val tick = C.Text
    Canvas(modifier.width(width).height(9.dp).clearAndSetSemantics {}) {
        val lo = 2.0
        val hi = 16.0
        fun x(v: Double) = (((v - lo) / (hi - lo)).coerceIn(0.0, 1.0) * size.width).toFloat()
        val y = size.height / 2
        val h = 3.dp.toPx()
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = h)
        drawLine(band.copy(alpha = 0.5f), Offset(x(Glucose.LOW), y), Offset(x(Glucose.TIR_HIGH), y), strokeWidth = h)
        val tx = x(mmol)
        drawLine(tick, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 2.dp.toPx())
    }
}

/**
 * Tally marks for a streak: bundles of five (four strokes and a slash). Past [max] it's just the
 * number, which reads better than a fence.
 */
@Composable
fun Tally(count: Int, color: Color, modifier: Modifier = Modifier, max: Int = 15) {
    if (count > max || count < 1) return
    val groups = count / 5
    val rest = count % 5
    val groupW = 16.dp
    val gap = 8.dp
    val w = groupW * groups + gap * groups + 4.dp * rest
    Canvas(modifier.width(w).height(14.dp).clearAndSetSemantics {}) {
        val s = 1.6.dp.toPx()
        val step = 4.dp.toPx()
        var x0 = 1.dp.toPx()
        repeat(groups) {
            repeat(4) { i -> val x = x0 + i * step; drawLine(color, Offset(x, 1f), Offset(x, size.height - 1f), s, StrokeCap.Round) }
            drawLine(color, Offset(x0 - 2.dp.toPx(), size.height * 0.78f), Offset(x0 + 3 * step + 2.dp.toPx(), size.height * 0.22f), s, StrokeCap.Round)
            x0 += (groupW + gap).toPx()
        }
        repeat(rest) { i -> val x = x0 + i * step; drawLine(color, Offset(x, 1f), Offset(x, size.height - 1f), s, StrokeCap.Round) }
    }
}

/** A rubber stamp ("GENTLE +1.9"): mono capitals in a box, a few degrees off true. */
@Composable
fun Stamp(text: String, color: Color, modifier: Modifier = Modifier, big: Boolean = false, description: String? = null) {
    Text(
        caps(text),
        style = mono(if (big) 13.sp else 12.sp, FontWeight.SemiBold, color, 0.06f).copy(lineHeight = if (big) 16.sp else 14.sp),
        modifier = modifier
            .rotate(if (big) -7f else -5f)
            .border(if (big) 2.dp else 1.5.dp, color)
            .padding(horizontal = if (big) 8.dp else 5.dp, vertical = if (big) 3.dp else 1.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    )
}

/** A hollow, dashed ring: a check that's planned but not done. */
@Composable
fun PlannedRing(color: Color = C.TextMuted, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        drawCircle(
            color, radius = this.size.minDimension / 2 - 1.dp.toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))),
        )
    }
}

/** A small square of a range colour, beside its word. */
@Composable
fun RangeSquare(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(color))
}
