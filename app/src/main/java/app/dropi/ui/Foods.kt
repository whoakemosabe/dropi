package app.dropi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Foods
import app.dropi.data.GlucoseUnit

/** How big a rise is, in words and colour (always shown with the number). */
private fun riseTone(mmol: Double): Pair<String, Color> = when {
    mmol < 2.0 -> "gentle" to C.InRange
    mmol <= 3.0 -> "moderate" to C.High
    else -> "big" to C.VeryHigh
}

/**
 * Trends › Your plate: foods ranked by how much they lift the person's own numbers. A short
 * ladder (gentlest at the top, biggest at the bottom) with the rise as a bar and a number.
 */
@Composable
fun FoodsCard(state: AppState) {
    val scores = remember(state.readings, state.sensor) { Foods.scores(state.readings, state.sensor) }
    val ranked = scores.filter { it.ranked }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your plate", style = SectionLabel, modifier = Modifier.weight(1f))
            if (scores.isNotEmpty()) {
                Text(
                    "All foods",
                    style = inter(13.sp, FontWeight.SemiBold, C.AccentText),
                    modifier = Modifier.clip(RoundedCornerShape(50)).tap {
                        state.foodOpen = null
                        state.foodsOpen = true
                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (ranked.isEmpty()) {
            Text(
                if (scores.isEmpty()) "Add what you ate to a before-meal reading (Details › What you ate), then log an " +
                    "after-meal one. After two meals with a food, you'll see how it moves your numbers."
                else "Almost there: each food needs two meals with an after-meal reading before it's ranked.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
            return@Column
        }
        val shown = if (ranked.size <= 6) ranked else ranked.take(3) + ranked.takeLast(3)
        val top = shown.maxOf { it.rise }.coerceAtLeast(1.0)
        shown.forEachIndexed { i, s ->
            if (ranked.size > 6 && i == 3) {
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(C.Border.copy(alpha = 0.6f)))
            }
            RiseRow(Foods.display(s.food), s.rise, top, state.unit) {
                state.foodOpen = s.food
                state.foodsOpen = true
            }
        }
        Text(
            "Typical peak 45 min–3 h after eating, compared with before. Gentlest first.",
            style = inter(11.sp, color = C.TextMuted),
        )
    }
}

@Composable
private fun RiseRow(name: String, rise: Double, top: Double, unit: GlucoseUnit, onClick: () -> Unit) {
    val (word, tone) = riseTone(rise)
    val delta = Foods.delta(rise, unit)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tap(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name: $word rise, $delta ${unit.label}" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = inter(14.sp, FontWeight.Medium), modifier = Modifier.width(0.dp).weight(0.42f), maxLines = 2)
        Spacer(Modifier.width(10.dp))
        BoxWithConstraints(Modifier.weight(0.4f).height(10.dp)) {
            val frac = (rise.coerceAtLeast(0.0) / top).toFloat().coerceIn(0.04f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.SurfaceLight))
            Box(Modifier.width(maxWidth * frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(tone))
        }
        Spacer(Modifier.width(10.dp))
        Text(delta, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.width(52.dp), maxLines = 1)
    }
}

@Composable
fun FoodsSheet(state: AppState) {
    if (!state.foodsOpen) return
    SlideSheet(onDismiss = { state.foodsOpen = false; state.foodOpen = null }) { close -> FoodsContent(state, close) }
}

@Composable
private fun ColumnScope.FoodsContent(state: AppState, close: () -> Unit) {
    val scores = remember(state.readings, state.sensor) { Foods.scores(state.readings, state.sensor) }
    StickySheetBody("Your plate", close) {
        Text(
            "How each food tends to move your numbers: the rise from your before-meal reading to the highest " +
                "value 45 min–3 h later. Tap a food for its curve.",
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        if (scores.isEmpty()) {
            SheetHint("No meals with foods and an after-meal reading yet.")
            return@StickySheetBody
        }
        val top = scores.maxOf { it.rise }.coerceAtLeast(1.0)
        for (s in scores) {
            val open = state.foodOpen == s.food
            Column(
                Modifier.fillMaxWidth().card().animateContentSize().padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                RiseRow(Foods.display(s.food), s.rise, top, state.unit) { state.foodOpen = if (open) null else s.food }
                if (open) {
                    val peak = s.curve.drop(1).maxByOrNull { it.second }
                    val (word, _) = riseTone(s.rise)
                    Text(
                        "A $word rise of ${Foods.delta(s.rise, state.unit)} ${state.unit.label}" +
                            (peak?.let { ", highest around ${minutesWords(it.first)} after eating" } ?: "") +
                            ". From ${plural(s.meals, "meal")}" + (if (!s.ranked) ", so it isn't ranked yet." else "."),
                        style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    Curve(s.curve, s.points, Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Eating", "2 h", "4 h").forEach { Text(it, style = inter(11.sp, color = C.TextMuted)) }
                    }
                }
            }
        }
        SheetHint("Foods eaten together share a meal's rise. Portions, activity and medicine all play a part too.")
    }
}

private fun minutesWords(m: Int) = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

/** The typical curve over every reading that went into it (dots), starting from zero at eating. */
@Composable
private fun Curve(curve: List<Pair<Int, Double>>, points: List<Pair<Int, Double>>, modifier: Modifier) {
    val line = C.Text
    val dot = C.TextMuted
    val zero = C.Border
    val hiC = C.High
    val lo = minOf(-1.0, (points.minOfOrNull { it.second } ?: 0.0) - 0.3)
    val hi = maxOf(3.0, (points.maxOfOrNull { it.second } ?: 0.0) + 0.5)
    Canvas(modifier.semantics { contentDescription = "Typical change in the four hours after eating" }) {
        fun x(m: Int) = (m / 240f) * size.width
        fun y(v: Double) = (1f - ((v - lo) / (hi - lo)).toFloat()) * size.height
        drawLine(zero, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        // The 45 min–3 h window the score looks at.
        drawRect(hiC.copy(alpha = 0.06f), Offset(x(45), 0f), androidx.compose.ui.geometry.Size(x(180) - x(45), size.height))
        for (p in points) drawCircle(dot.copy(alpha = 0.5f), 2.5.dp.toPx(), Offset(x(p.first), y(p.second)))
        if (curve.size >= 2) {
            val path = Path()
            curve.forEachIndexed { i, (m, v) -> if (i == 0) path.moveTo(x(m), y(v)) else path.lineTo(x(m), y(v)) }
            drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        for ((m, v) in curve.drop(1)) drawCircle(line, 3.5.dp.toPx(), Offset(x(m), y(v)))
        drawLine(
            zero, Offset(x(240), 0f), Offset(x(240), size.height), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}
