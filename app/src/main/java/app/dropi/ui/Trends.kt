package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Analytics
import app.dropi.data.Bands
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.InsightKind
import app.dropi.data.Reading
import app.dropi.data.Report
import app.dropi.data.Status
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Trends: averages, time in range, variability, estimated A1C, a daily chart and insights. */
@Composable
fun TrendsPage(state: AppState) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val periods = listOf(7, 14, 30, 90)
    val period = periods.indexOf(state.trendsDays).coerceAtLeast(0)
    val today = LocalDate.now(state.zone)
    val report = remember(state.readings, period, today) { Analytics.report(state.readings, periods[period], today, state.zone) }
    val unit = state.unit
    fun v(m: Double) = Glucose.format(m, unit)
    val insights = remember(report, unit) { Analytics.insights(report, state.targets) { v(it) } }
    val bars = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            SlidingSegments(listOf("7 days", "14 days", "30 days", "90 days"), period, Modifier.fillMaxWidth()) { state.trendsDays = periods[it] }
        }
        if (report.count < 3) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().card().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ic(R.drawable.lc_droplet, C.AccentText, 32.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Not enough readings yet", style = inter(16.sp, FontWeight.Bold))
                    Text(
                        "Trends appear after a few readings in this period. Keep logging and check back.",
                        style = inter(13.sp, color = C.TextSecondary),
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "headline") { Column { Headline(report, unit) } }
        item(key = "lab") { LabA1cCard(state, report) }
        report.bands?.let { b -> item(key = "tir") { RangeCard(b, unit) } }
        item(key = "chart") { TimeChart(state, fmt, now) }
        item(key = "agp") { TypicalDay(state, report) }
        if (insights.isNotEmpty()) {
            item(key = "insights") {
                // One card, read top to bottom: a coloured mark says good, worth a look, or just so.
                Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("What stands out", style = SectionLabel)
                    insights.forEachIndexed { i, ins ->
                        val tint = when (ins.kind) {
                            InsightKind.GOOD -> C.InRange
                            InsightKind.ATTENTION -> C.High
                            InsightKind.INFO -> C.TextMuted
                        }
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.6f)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 4.dp).width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                            Spacer(Modifier.width(12.dp))
                            Text(ins.text, style = inter(14.sp, color = C.Text).copy(lineHeight = 20.sp))
                        }
                    }
                }
            }
        }
        item(key = "ask") { AskEntry(state) }
        item(key = "plate") { FoodsCard(state) }
        if (report.byTag.isNotEmpty()) item(key = "tags") { ByMoment(state, report) }
        item(key = "table") { DailyTable(report, unit) }
        item(key = "pdf") {
            // The doctor's report for exactly the days on screen.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            PrimaryButton(
                "Share a PDF of these ${report.days} days",
                Modifier.fillMaxWidth(),
                icon = R.drawable.lc_share,
                enabled = !busy,
            ) {
                busy = true
                scope.launch {
                    try {
                        val f = app.dropi.work.Export.writePdf(context, state.readings, report.days, today, state.zone, state.unit, state.targets)
                        app.dropi.work.Export.share(context, f, "application/pdf", "Blood glucose report")
                    } catch (e: Exception) { }
                    busy = false
                }
            }
        }
        item(key = "note") {
            Text(
                "Based on your logged readings, not a sensor: time in range here is the share of readings. " +
                    "Estimated A1C (GMI) can differ from a lab A1C.",
                style = inter(11.sp, color = C.TextMuted),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Headline(r: Report, unit: GlucoseUnit) {
    // The average big under the aurora, the estimated A1C beside it, then the period in a sentence.
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Average", style = inter(14.sp, FontWeight.Medium, C.TextSecondary))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.mean?.let { Glucose.format(it, unit) } ?: "—", style = numeral(84.sp).copy(lineHeight = 88.sp))
                Spacer(Modifier.width(6.dp))
                Text(unit.label, style = inter(13.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.padding(bottom = 16.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
            Text("Estimated A1C", style = inter(13.sp, FontWeight.Medium, C.TextSecondary))
            val g = r.gmiPercent
            Text(g?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "—", style = numeral(36.sp, if (g != null) C.Text else C.TextMuted))
            Text(
                if (g != null) "${r.gmiMmolMol?.roundToInt()} mmol/mol" else "after a week",
                style = inter(12.sp, FontWeight.Medium, C.TextMuted),
            )
        }
    }
    val mint = androidx.compose.ui.text.SpanStyle(color = C.Text, fontWeight = FontWeight.ExtraBold)
    val period = if (r.days == 7) "this week" else "in ${r.days} days"
    val sentence = androidx.compose.ui.text.buildAnnotatedString {
        val tir = r.bands?.inRange?.roundToInt()
        if (tir != null) {
            withStyle(mint) { append("$tir%") }
            append(" in range $period")
            r.previousInRange?.roundToInt()?.let { p ->
                append(
                    when {
                        tir > p -> ", up from $p%"
                        tir < p -> ", down from $p%"
                        else -> ", the same as before"
                    }
                )
            }
            append(". ")
        }
        if (r.lows == 0) append("No lows.")
        else withStyle(androidx.compose.ui.text.SpanStyle(color = C.Low)) { append(if (r.lows == 1) "1 low." else "${r.lows} lows.") }
    }
    Text(sentence, style = heading(19.sp, C.TextSecondary).copy(lineHeight = 26.sp), modifier = Modifier.padding(horizontal = 4.dp))
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 4.dp)) {
        SmallStat("Variability", r.cv?.let { "${it.roundToInt()}%" } ?: "—", "goal 36% or less", Modifier.weight(1f))
        Box(Modifier.fillMaxHeight().width(1.dp).background(C.Border))
        SmallStat("Lowest", r.readings.minOfOrNull { it.mmol }?.let { Glucose.format(it, unit) } ?: "—", unit.label, Modifier.weight(1f).padding(start = 14.dp))
        Box(Modifier.fillMaxHeight().width(1.dp).background(C.Border))
        SmallStat("Highest", r.readings.maxOfOrNull { it.mmol }?.let { Glucose.format(it, unit) } ?: "—", unit.label, Modifier.weight(1f).padding(start = 14.dp))
    }
}

@Composable
private fun SmallStat(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = numeral(28.sp, C.Text), maxLines = 1)
        Text(label, style = inter(12.sp, FontWeight.SemiBold, C.TextSecondary), maxLines = 1)
        Text(sub, style = inter(11.sp, color = C.TextMuted), maxLines = 1)
    }
}

/** The consensus bands as one stacked bar (2 dp gaps, rounded ends) with a labelled legend. */
@Composable
private fun RangeCard(b: Bands, unit: GlucoseUnit) {
    fun v(m: Double) = Glucose.format(m, unit)
    data class Seg(val label: String, val range: String, val pct: Double, val color: Color, val goal: String)
    val segs = listOf(
        Seg("Very high", "over ${v(Glucose.VERY_HIGH)}", b.veryHigh, C.VeryHigh, "under 5%"),
        Seg("High", "${v(Glucose.TIR_HIGH)}–${v(Glucose.VERY_HIGH)}", b.high, C.High, "under 25%"),
        Seg("In range", "${v(Glucose.LOW)}–${v(Glucose.TIR_HIGH)}", b.inRange, C.InRange, "over 70%"),
        Seg("Low", "${v(Glucose.URGENT_LOW)}–${v(Glucose.LOW)}", b.low, C.Low, "under 4%"),
        Seg("Very low", "under ${v(Glucose.URGENT_LOW)}", b.veryLow, C.UrgentLow, "under 1%"),
    )
    val gapColor = C.Card
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("In range", style = SectionLabel)
                Spacer(Modifier.height(4.dp))
                Text("${b.inRange.roundToInt()}%", style = numeral(34.sp, C.Text))
            }
            Text("Goal: over 70%", style = inter(12.sp, FontWeight.Medium, C.TextMuted), modifier = Modifier.padding(bottom = 8.dp))
        }
        // Low on the left, high on the right, like reading a scale.
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val ordered = segs.reversed().filter { it.pct > 0 }
            val gap = 2.dp.toPx()
            val usable = size.width - gap * (ordered.size - 1).coerceAtLeast(0)
            var x = 0f
            ordered.forEachIndexed { i, s ->
                val w = (usable * (s.pct / 100.0)).toFloat().coerceAtLeast(2f)
                drawRoundRect(s.color, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                x += w
                if (i < ordered.size - 1) {
                    drawRect(gapColor, Offset(x, 0f), Size(gap, size.height))
                    x += gap
                }
            }
        }
        segs.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = inter(14.sp, FontWeight.SemiBold, C.Text))
                    Text("${s.range} · goal ${s.goal}", style = inter(11.sp, color = C.TextMuted))
                }
                Text("${s.pct.roundToInt()}%", style = inter(15.sp, FontWeight.Bold, C.Text))
            }
        }
    }
}

/** Average for each moment of the day, as short bars on a shared scale with the value at the tip. */
@Composable
private fun ByMoment(state: AppState, r: Report) {
    val unit = state.unit
    val maxV = maxOf(12.0, r.byTag.maxOf { it.average } + 1.0)
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("By time of day", style = SectionLabel)
        r.byTag.forEach { t ->
            val s = Glucose.status(t.average, t.tag, state.targets)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(t.tag.icon(), C.TextMuted, 13.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(t.tag.label, style = inter(13.sp, FontWeight.SemiBold, C.Text), modifier = Modifier.weight(1f))
                    Text(plural(t.count, "reading"), style = inter(11.sp, color = C.TextMuted))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(12.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth((t.average / maxV).toFloat().coerceIn(0.04f, 1f))
                                .height(12.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(C.status(s))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(Glucose.format(t.average, unit), style = inter(14.sp, FontWeight.Bold, C.Text))
                }
                Text(s.label, style = inter(11.sp, color = C.TextMuted))
            }
        }
    }
}

/** The same numbers as a table: one row per day. */
@Composable
private fun DailyTable(r: Report, unit: GlucoseUnit) {
    var open by remember { mutableStateOf(false) }
    val df = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).tap { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("Daily averages", style = SectionLabel, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show table", style = inter(13.sp, FontWeight.SemiBold, C.AccentText))
        }
        if (open) {
            Row {
                Text("Day", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.weight(1.4f))
                Text("Readings", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.weight(1f))
                Text("Average", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.weight(1f))
                Text("Range", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.weight(1.2f))
            }
            r.byDay.reversed().forEach { d ->
                Row {
                    Text(df.format(d.day), style = inter(13.sp, color = C.Text), modifier = Modifier.weight(1.4f))
                    Text("${d.count}", style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
                    Text(Glucose.format(d.average, unit), style = inter(13.sp, FontWeight.SemiBold, C.Text), modifier = Modifier.weight(1f))
                    Text("${Glucose.format(d.min, unit)}–${Glucose.format(d.max, unit)}", style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.weight(1.2f))
                }
            }
        }
    }
}
