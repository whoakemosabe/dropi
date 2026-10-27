package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Glucose
import app.dropi.data.Report
import app.dropi.data.StandardDay
import app.dropi.tr
import app.dropi.trn

/**
 * Standard day on Trends: one row per check (on waking, after breakfast…), each a small box plot
 * on a shared scale. Dot = the usual (middle) value, box = the middle half of the checks, line =
 * lowest to highest. Rows run sideways so the check names read in full on a phone.
 */
@Composable
fun StandardDayView(state: AppState, report: Report) {
    val unit = state.unit
    val groups = remember(report, state.meals) {
        StandardDay.groups(report.readings, state.meals, state.readings, state.zone)
    }
    Column(Modifier.fillMaxWidth()) {
        RuleHeader(tr("Standard day"), if (groups.isEmpty()) null else trn(groups.sumOf { it.count }, "{0} check", "{0} checks"))
        if (groups.isEmpty()) {
            Text(
                tr("Your usual value at each check time: on waking, after breakfast and so on. It needs at least 3 checks at a time, so keep logging."),
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
            return@Column
        }
        // One scale for every row, from the lowest and highest values in the period.
        val lo = minOf(3.0, groups.minOf { it.low } - 0.3)
        val hi = maxOf(12.0, groups.maxOf { it.high } + 0.3).coerceAtMost(Glucose.MAX_MMOL)
        groups.forEachIndexed { i, g ->
            val color = C.status(Glucose.status(g.median, g.block.tag, state.targets))
            val desc = tr(
                "{0}: usually {1}, the middle half from {2} to {3}, lowest {4}, highest {5}, from {6} checks.",
                g.block.label, Glucose.format(g.median, unit), Glucose.format(g.q1, unit), Glucose.format(g.q3, unit),
                Glucose.format(g.low, unit), Glucose.format(g.high, unit), g.count,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .ruleTop(i > 0)
                    .padding(vertical = 8.dp)
                    .clearAndSetSemantics { contentDescription = desc },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(118.dp)) {
                    Text(g.block.label, style = inter(13.sp, FontWeight.SemiBold), maxLines = 1)
                    Text(trn(g.count, "{0} check", "{0} checks"), style = Chart.label)
                }
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.weight(1f).height(28.dp)) {
                    val pad = 6.dp.toPx()
                    val w = size.width - 2 * pad
                    fun x(v: Double) = pad + w * ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
                    val cy = size.height / 2
                    // The in-range band, standing up behind the row (3.9 to 10).
                    drawRect(Chart.band, Offset(x(Glucose.LOW), 0f), Size(x(Glucose.TIR_HIGH) - x(Glucose.LOW), size.height))
                    drawLine(Chart.rule, Offset(x(Glucose.LOW), 0f), Offset(x(Glucose.LOW), size.height), strokeWidth = 1.dp.toPx())
                    drawLine(Chart.rule, Offset(x(Glucose.TIR_HIGH), 0f), Offset(x(Glucose.TIR_HIGH), size.height), strokeWidth = 1.dp.toPx())
                    // Lowest to highest.
                    drawLine(C.TextMuted, Offset(x(g.low), cy), Offset(x(g.high), cy), strokeWidth = 1.5.dp.toPx())
                    // The middle half.
                    val bh = 14.dp.toPx()
                    val left = x(g.q1)
                    val bw = (x(g.q3) - left).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(Chart.spreadHalf, Offset(left, cy - bh / 2), Size(bw, bh), CornerRadius(3.dp.toPx()))
                    drawRoundRect(Chart.ink, Offset(left, cy - bh / 2), Size(bw, bh), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                    // The usual value.
                    with(Chart) { readingDot(color, Offset(x(g.median), cy), C.Bg) }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    Glucose.format(g.median, unit),
                    style = numeral(17.sp, C.Text),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
        // The key, as on every chart.
        Column(Modifier.fillMaxWidth().ruleTop().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardKey(tr("Dot and number: your usual value (the middle one)"), dot = true)
            StandardKey(tr("Box: the middle half of your checks"), box = true)
            StandardKey(tr("Line: lowest to highest"))
            Text(
                tr("Shaded: in range, {0}", "${Glucose.format(Glucose.LOW, unit)}–${Glucose.format(Glucose.TIR_HIGH, unit)}"),
                style = inter(13.sp, color = C.TextSecondary),
            )
        }
    }
}

@Composable
private fun StandardKey(text: String, dot: Boolean = false, box: Boolean = false) {
    val ring = C.Bg
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Box(Modifier.width(26.dp).height(16.dp)) {
            Canvas(Modifier.width(26.dp).height(16.dp)) {
                val cy = size.height / 2
                when {
                    dot -> with(Chart) { readingDot(C.InRange, Offset(size.width / 2, cy), ring) }
                    box -> {
                        drawRoundRect(Chart.spreadHalf, Offset(4.dp.toPx(), cy - 6.dp.toPx()), Size(18.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                        drawRoundRect(Chart.ink, Offset(4.dp.toPx(), cy - 6.dp.toPx()), Size(18.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                    else -> drawLine(C.TextMuted, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.5.dp.toPx())
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp))
    }
}
