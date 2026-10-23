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
        item(key = "headline") { Headline(report, unit) }
        item(key = "lab") { LabA1cCard(state, report) }
        report.bands?.let { b -> item(key = "tir") { RangeCard(b, unit) } }
        item(key = "chart") { DailyChart(state, report, fmt) }
        if (insights.isNotEmpty()) {
            item(key = "insights-label") { Text("INSIGHTS", style = SectionLabel, modifier = Modifier.padding(top = 4.dp, start = 4.dp)) }
            insights.forEachIndexed { i, ins ->
                item(key = "insight$i") {
                    val (icon, tint) = when (ins.kind) {
                        InsightKind.GOOD -> R.drawable.lc_check to C.InRange
                        InsightKind.ATTENTION -> R.drawable.lc_circle_alert to C.High
                        InsightKind.INFO -> R.drawable.lc_info to C.AccentText
                    }
                    Row(Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Ic(icon, tint, 16.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(ins.text, style = inter(14.sp, color = C.Text).copy(lineHeight = 20.sp))
                    }
                }
            }
        }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1.2f).card().padding(16.dp)) {
            Text("AVERAGE", style = SectionLabel)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.mean?.let { Glucose.format(it, unit) } ?: "—", style = inter(40.sp, FontWeight.ExtraBold, C.Text, -0.02f).copy(fontFeatureSettings = ""))
                Spacer(Modifier.width(4.dp))
                Text(unit.label, style = inter(12.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("${plural(r.count, "reading")} on ${plural(r.daysLogged, "day")}", style = inter(12.sp, color = C.TextMuted))
        }
        Column(Modifier.weight(1f).card().padding(16.dp)) {
            Text("EST. A1C", style = SectionLabel)
            Spacer(Modifier.height(6.dp))
            val g = r.gmiPercent
            if (g != null) {
                Text(String.format(Locale.ROOT, "%.1f%%", g), style = inter(28.sp, FontWeight.ExtraBold, C.Text).copy(fontFeatureSettings = ""))
                Text("${r.gmiMmolMol?.roundToInt()} mmol/mol", style = inter(12.sp, FontWeight.Medium, C.TextSecondary))
            } else {
                Text("—", style = inter(28.sp, FontWeight.ExtraBold, C.TextMuted))
                Text("Needs a week of readings", style = inter(12.sp, color = C.TextMuted))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallStat("Variability", r.cv?.let { "${it.roundToInt()}%" } ?: "—", "goal 36% or less", Modifier.weight(1f))
        SmallStat("Lowest", r.readings.minOfOrNull { it.mmol }?.let { Glucose.format(it, unit) } ?: "—", unit.label, Modifier.weight(1f))
        SmallStat("Highest", r.readings.maxOfOrNull { it.mmol }?.let { Glucose.format(it, unit) } ?: "—", unit.label, Modifier.weight(1f))
    }
}

@Composable
private fun SmallStat(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.card(RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label.uppercase(), style = SectionLabel, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = inter(20.sp, FontWeight.ExtraBold, C.Text).copy(fontFeatureSettings = ""), maxLines = 1)
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
                Text("IN RANGE", style = SectionLabel)
                Spacer(Modifier.height(4.dp))
                Text("${b.inRange.roundToInt()}%", style = inter(34.sp, FontWeight.ExtraBold, C.Text).copy(fontFeatureSettings = ""))
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

/**
 * Every reading in the period as a dot on the time axis, coloured by range, over the shaded
 * 3.9–10.0 band, with the daily average as a line. Tap near a dot to see it.
 */
@Composable
private fun DailyChart(state: AppState, r: Report, fmt: Fmt) {
    val unit = state.unit
    var selected by remember(r) { mutableStateOf<Reading?>(null) }
    val maxV = maxOf(15.0, (r.readings.maxOfOrNull { it.mmol } ?: 0.0) + 1.0).coerceAtMost(Glucose.MAX_MMOL)
    val minV = 2.0
    val start = r.from.atStartOfDay(state.zone).toInstant().toEpochMilli()
    val end = r.to.plusDays(1).atStartOfDay(state.zone).toInstant().toEpochMilli()
    val band = C.InRange.copy(alpha = 0.10f)
    val grid = C.Border
    val avgLine = C.TextSecondary
    val ring = C.Card
    val dotColors = r.readings.map { C.status(Glucose.status(it.mmol, it.tag, state.targets)) }

    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("EVERY READING", style = SectionLabel)
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            val axisW = 30.dp
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(r) {
                        detectTapGestures { p ->
                            val left = axisW.toPx()
                            val plotW = size.width - left
                            fun xOf(t: Long) = left + plotW * ((t - start).toFloat() / (end - start))
                            fun yOf(m: Double) = size.height * (1f - ((m - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f))
                            val near = r.readings.minByOrNull { abs(xOf(it.at) - p.x) + abs(yOf(it.mmol) - p.y) * 0.5f }
                            selected = near?.takeIf { abs(xOf(it.at) - p.x) < 24.dp.toPx() }
                        }
                    }
            ) {
                val left = axisW.toPx()
                val plotW = size.width - left
                fun xOf(t: Long) = left + plotW * ((t - start).toFloat() / (end - start))
                fun yOf(m: Double) = size.height * (1f - ((m - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f))
                // Target band and its two edges.
                drawRect(band, Offset(left, yOf(Glucose.TIR_HIGH)), Size(plotW, yOf(Glucose.LOW) - yOf(Glucose.TIR_HIGH)))
                listOf(Glucose.LOW, Glucose.TIR_HIGH).forEach { g ->
                    drawLine(grid, Offset(left, yOf(g)), Offset(size.width, yOf(g)), strokeWidth = 1.dp.toPx())
                }
                // Daily average line.
                if (r.byDay.size >= 2) {
                    val path = Path()
                    r.byDay.forEachIndexed { i, d ->
                        val t = d.day.atStartOfDay(state.zone).toInstant().toEpochMilli() + 12 * 3_600_000L
                        val x = xOf(t)
                        val y = yOf(d.average)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, avgLine.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // Dots with a ring in the card colour, so overlaps stay readable.
                val rad = if (r.readings.size > 150) 3.dp.toPx() else 4.dp.toPx()
                r.readings.forEachIndexed { i, rd ->
                    val c = Offset(xOf(rd.at), yOf(rd.mmol))
                    drawCircle(ring, rad + 2.dp.toPx(), c)
                    drawCircle(dotColors[i], rad, c)
                }
                selected?.let { s ->
                    val c = Offset(xOf(s.at), yOf(s.mmol))
                    drawCircle(avgLine, rad + 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
            }
            // Axis labels in text colour on the left.
            fun yDp(m: Double): Dp = 200.dp * (1f - ((m - minV) / (maxV - minV)).toFloat())
            listOf(Glucose.LOW, Glucose.TIR_HIGH, maxV).forEach { g ->
                Text(
                    Glucose.format(g, unit).removeSuffix(".0"),
                    style = inter(10.sp, FontWeight.Medium, C.TextMuted),
                    modifier = Modifier.padding(top = (yDp(g) - 7.dp).coerceIn(0.dp, 186.dp)),
                )
            }
        }
        Text(
            selected?.let { s ->
                "${fmt.dayTime(s.at)} · ${Glucose.format(s.mmol, unit)} ${unit.label} · ${s.tag.label}"
            } ?: "Tap a dot to see it. The line is each day's average.",
            style = inter(12.sp, FontWeight.Medium, if (selected != null) C.Text else C.TextMuted),
        )
    }
}

/** Average for each moment of the day, as short bars on a shared scale with the value at the tip. */
@Composable
private fun ByMoment(state: AppState, r: Report) {
    val unit = state.unit
    val maxV = maxOf(12.0, r.byTag.maxOf { it.average } + 1.0)
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BY TIME OF DAY", style = SectionLabel)
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
            Text("DAILY AVERAGES", style = SectionLabel, modifier = Modifier.weight(1f))
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
