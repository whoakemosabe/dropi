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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.Foods
import app.dropi.data.GlucoseUnit

/** How big a rise is, as a step and a colour (always shown with the number). */
private enum class RiseStep { GENTLE, MODERATE, BIG }

private fun riseTone(mmol: Double): Pair<RiseStep, Color> = when {
    mmol < 2.0 -> RiseStep.GENTLE to C.InRange
    mmol <= 3.0 -> RiseStep.MODERATE to C.High
    else -> RiseStep.BIG to C.VeryHigh
}

/**
 * Meals › Your plate: foods ranked by how much they lift the person's own numbers. A short
 * ruled ladder (gentlest at the top, biggest at the bottom) with the rise as a bar and a number.
 */
@Composable
fun FoodsCard(state: AppState) {
    val scores = state.foodScores
    val ranked = scores.filter { it.ranked }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        RuleHeader(tr("Your plate"))
        if (ranked.isEmpty()) {
            Text(
                if (scores.isEmpty()) tr("Log a meal with the + button, with a reading before you eat and one after. After two meals with a food, you'll see how it moves your numbers.")
                else tr("Almost there: each food needs two meals with an after-eating reading before it's ranked."),
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        } else {
            val shown = if (ranked.size <= 6) ranked else ranked.take(3) + ranked.takeLast(3)
            val top = shown.maxOf { it.rise }.coerceAtLeast(1.0)
            shown.forEachIndexed { i, s ->
                // The gentlest three, a gap, then the biggest three.
                if (ranked.size > 6 && i == 3) Spacer(Modifier.height(12.dp))
                RiseRow(Foods.display(s.food), s.rise, top, state.unit, ruled = true) {
                    state.foodOpen = s.food
                    state.foodsOpen = true
                }
            }
            Text(
                tr("Typical peak 45 min–3 h after eating, compared with before. Gentlest first."),
                style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                modifier = Modifier.ruleTop().padding(top = 8.dp),
            )
        }
        if (scores.isNotEmpty()) {
            MonoLink(tr("All foods")) {
                state.foodOpen = null
                state.foodsOpen = true
            }
        }
    }
}

/** A food's rise as a bar and a number. [ruled]: a row of the ruled list on the Meals page. */
@Composable
private fun RiseRow(name: String, rise: Double, top: Double, unit: GlucoseUnit, ruled: Boolean = false, onClick: () -> Unit) {
    val (step, tone) = riseTone(rise)
    val delta = Foods.delta(rise, unit)
    val spoken = when (step) {
        RiseStep.GENTLE -> tr("{0}: gentle rise, {1} {2}", name, delta, unit.label)
        RiseStep.MODERATE -> tr("{0}: moderate rise, {1} {2}", name, delta, unit.label)
        RiseStep.BIG -> tr("{0}: big rise, {1} {2}", name, delta, unit.label)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (ruled) Modifier.heightIn(min = 48.dp).ruleTop() else Modifier.clip(RoundedCornerShape(Corner.S)))
            .tap(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = inter(15.sp, FontWeight.Medium), modifier = Modifier.width(0.dp).weight(0.42f), maxLines = 2)
        Spacer(Modifier.width(12.dp))
        BoxWithConstraints(Modifier.weight(0.4f).height(if (ruled) 8.dp else 10.dp)) {
            val h = if (ruled) 8.dp else 10.dp
            val frac = (rise.coerceAtLeast(0.0) / top).toFloat().coerceIn(0.04f, 1f)
            Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(if (ruled) 1.dp else Corner.S)).background(C.SurfaceLight))
            Box(Modifier.width(maxWidth * frac).height(h).clip(RoundedCornerShape(if (ruled) 1.dp else Corner.S)).background(tone))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            delta,
            style = if (ruled) mono(14.sp, FontWeight.SemiBold, C.Text).copy(textAlign = TextAlign.End) else inter(15.sp, FontWeight.SemiBold),
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun FoodsSheet(state: AppState) {
    if (!state.foodsOpen) return
    SlideSheet(onDismiss = { state.foodsOpen = false; state.foodOpen = null }) { close -> FoodsContent(state, close) }
}

@Composable
private fun ColumnScope.FoodsContent(state: AppState, close: () -> Unit) {
    val scores = state.foodScores
    StickySheetBody(tr("Your plate"), close) {
        Text(
            tr("How each food tends to move your numbers: the rise from your reading before eating to the highest value 45 min–3 h later. Tap a food for its curve."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        if (scores.isEmpty()) {
            SheetHint(tr("No meals with a reading before and after yet."))
            return@StickySheetBody
        }
        val top = scores.maxOf { it.rise }.coerceAtLeast(1.0)
        for (s in scores) {
            val open = state.foodOpen == s.food
            Column(
                Modifier.fillMaxWidth().card().animateContentSize().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                RiseRow(Foods.display(s.food), s.rise, top, state.unit) { state.foodOpen = if (open) null else s.food }
                if (open) {
                    val peak = s.curve.drop(1).maxByOrNull { it.second }
                    val (step, _) = riseTone(s.rise)
                    val amount = "${Foods.delta(s.rise, state.unit)} ${state.unit.label}"
                    val riseText = when {
                        peak == null -> when (step) {
                            RiseStep.GENTLE -> tr("A gentle rise of {0}.", amount)
                            RiseStep.MODERATE -> tr("A moderate rise of {0}.", amount)
                            RiseStep.BIG -> tr("A big rise of {0}.", amount)
                        }
                        else -> when (step) {
                            RiseStep.GENTLE -> tr("A gentle rise of {0}, highest around {1} after eating.", amount, minutesWords(peak.first))
                            RiseStep.MODERATE -> tr("A moderate rise of {0}, highest around {1} after eating.", amount, minutesWords(peak.first))
                            RiseStep.BIG -> tr("A big rise of {0}, highest around {1} after eating.", amount, minutesWords(peak.first))
                        }
                    }
                    val fromText =
                        if (s.ranked) trn(s.meals, "From {0} meal.", "From {0} meals.")
                        else trn(s.meals, "From {0} meal, so it isn't ranked yet.", "From {0} meals, so it isn't ranked yet.")
                    Text(
                        "$riseText $fromText",
                        style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Curve(s.curve, s.points, Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(tr("Eating"), tr("{0} h", 2), tr("{0} h", 4)).forEach { Text(it, style = inter(13.sp, color = C.TextMuted)) }
                    }
                }
            }
        }
        SheetHint(tr("Foods eaten together share a meal's rise. Portions, activity and medicine all play a part too."))
    }
}

private fun minutesWords(m: Int) = when {
    m < 60 -> tr("{0} min", m)
    m % 60 == 0 -> tr("{0} h", m / 60)
    else -> tr("{0} h {1} min", m / 60, m % 60)
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
    Canvas(modifier.semantics { contentDescription = tr("Typical change in the four hours after eating") }) {
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
