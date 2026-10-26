package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.Analytics
import app.dropi.data.Bands
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.InsightKind
import app.dropi.data.Reading
import app.dropi.data.Report
import app.dropi.data.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The sparklines' value scale starts here (mmol/L), like the big chart's. */
private const val SPARK_MIN = 2.0

/**
 * Trends: the period as four numbers, time in range as one bar, then the period day by day (or
 * week by week) as small sparklines; tap one for the full chart. Insights, Ask, lab A1C, and the
 * details fold away under "More detail".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsPage(state: AppState) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val periods = listOf(7, 14, 30, 90)
    val period = periods.indexOf(state.trendsDays).coerceAtLeast(0)
    val today = LocalDate.now(state.zone)
    val zone = state.zone
    val report = remember(state.readings, period, today) { Analytics.report(state.readings, periods[period], today, state.zone) }
    val unit = state.unit
    fun v(m: Double) = Glucose.format(m, unit)
    val insights = remember(report, unit) { Analytics.insights(report, state.targets, state.meals) { v(it) } }
    val strips = remember(report, zone, state.readings) { buildStrips(report, zone, state.readings) }
    // One scale for every sparkline, so the rows compare: 06–24 unless something was logged earlier.
    val fromHour = remember(report, zone) {
        if (report.readings.any { Instant.ofEpochMilli(it.at).atZone(zone).hour < 6 }) 0 else 6
    }
    val sparkMax = remember(report) {
        maxOf(13.0, (report.readings.maxOfOrNull { it.mmol } ?: 0.0) + 0.5).coerceAtMost(Glucose.MAX_MMOL)
    }
    // The row whose full chart is open (its first day), if any.
    var focus by remember(state.trendsDays) { mutableStateOf<LocalDate?>(null) }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val listState = rememberLazyListState()
    // Where the first day row sits in the list: period tabs, box score, the range bar if any, the header.
    val stripsStart = 3 + (if (report.bands != null) 1 else 0)
    // A scroll asked for by an insight; it runs once the list has the newly opened chart in it.
    var scrollIndex by remember { mutableStateOf(-1) }
    var scrollTick by remember { mutableStateOf(0) }
    LaunchedEffect(scrollTick) {
        if (scrollIndex >= 0) listState.animateScrollToItem(scrollIndex)
    }
    /** Opens the row holding [day] (its full chart under it) and scrolls it to the top. */
    fun showDay(day: LocalDate) {
        val si = strips.indexOfFirst { !day.isBefore(it.first) && !day.isAfter(it.last) }
        if (si < 0) return
        focus = strips[si].first
        scrollIndex = stripsStart + si
        scrollTick++
    }

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
    ) {
        glideItem(key = "period") {
            PeriodTabs(periods, period) { state.trendsDays = periods[it] }
        }
        if (report.count < 3) {
            glideItem(key = "empty") {
                Text(
                    tr("Trends appear after a few readings in this period. Keep logging and check back."),
                    style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 21.sp),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            return@LazyColumn
        }
        glideItem(key = "box") { BoxScore(report, unit, Modifier.padding(top = 18.dp)) }
        report.bands?.let { b -> glideItem(key = "tir") { RangeBar(report, b) } }

        glideItem(key = "days-head") {
            RuleHeader(tr("Day by day"), String.format(Locale.ROOT, "%02d → 24", fromHour), Modifier.padding(top = 8.dp))
        }
        strips.forEachIndexed { si, s ->
            val open = focus == s.first
            glideItem(key = "strip-${s.first}") {
                StripRow(
                    s, fromHour, sparkMax,
                    isToday = !today.isBefore(s.first) && !today.isAfter(s.last),
                    now = now, state = state, open = open, first = si == 0,
                ) { focus = if (open) null else s.first }
            }
            if (open) glideItem(key = "chart-${s.first}") {
                // The full chart for that day (or week), right under its row: it fades in as the
                // rows below glide down, and out as they glide back.
                Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    TimeChart(
                        state, fmt, now,
                        windowStart = s.first.atStartOfDay(zone).toInstant().toEpochMilli(),
                        windowEnd = s.last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    )
                    Text(
                        caps(tr("Hide chart")) + " ↑",
                        style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                            .tap { focus = null }
                            .padding(vertical = 15.dp),
                    )
                }
            }
        }

        if (insights.isNotEmpty()) {
            glideItem(key = "insights-head") { RuleHeader(tr("What stands out")) }
            insights.forEachIndexed { i, ins ->
                glideItem(key = "insight-$i") {
                    // A small square says good, worth a look, or just so.
                    val tint = when (ins.kind) {
                        InsightKind.GOOD -> C.InRange
                        InsightKind.ATTENTION -> C.High
                        InsightKind.INFO -> C.TextMuted
                    }
                    // The day it's about, when Analytics can say and that day has a row here.
                    val day = ins.days.firstOrNull()?.takeIf { d -> strips.any { !d.isBefore(it.first) && !d.isAfter(it.last) } }
                    val weekly = strips.firstOrNull()?.week == true
                    val view = LocalView.current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (day == null) Modifier
                                else Modifier.clickable(
                                    onClickLabel = if (weekly) tr("Show that week") else tr("Show that day"),
                                    role = Role.Button,
                                ) {
                                    Haptics.tap(view)
                                    showDay(day)
                                }
                            )
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RangeSquare(tint, Modifier.padding(top = 6.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(ins.text, style = inter(15.sp, color = C.Text).copy(lineHeight = 20.sp), modifier = Modifier.weight(1f))
                        if (day != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "→",
                                style = mono(15.sp, FontWeight.Medium, C.TextSecondary).copy(lineHeight = 20.sp),
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
        glideItem(key = "ask") { AskEntry(state) }
        glideItem(key = "lab") { LabA1cCard(state, report) }
        glideItem(key = "links") {
            // More detail opens the fold below; the PDF is the doctor's report for exactly these days.
            val pdfScope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val more = state.trendsMore
                MonoLink(
                    tr("More detail"),
                    Modifier.padding(end = 16.dp).semantics {
                        stateDescription = if (more) tr("Shown") else tr("Hidden")
                        if (more) collapse { state.trendsMore = false; true } else expand { state.trendsMore = true; true }
                    },
                ) { state.trendsMore = !state.trendsMore }
                MonoLink(
                    tr("PDF for your care team"),
                    Modifier.semantics { contentDescription = tr("Share a PDF of these {0} days", report.days) },
                ) {
                    if (!busy) {
                        busy = true
                        pdfScope.launch {
                            try {
                                val f = app.dropi.work.Export.writePdf(context, state.readings, report.days, today, state.zone, state.unit, state.targets)
                                app.dropi.work.Export.share(context, f, "application/pdf", tr("Blood glucose report"))
                            } catch (e: Exception) { }
                            busy = false
                        }
                    }
                }
            }
        }
        if (state.trendsMore) {
            glideItem(key = "numbers") { MoreNumbers(report, unit) }
            glideItem(key = "agp") { TypicalDay(state, report) }
            if (report.byTag.isNotEmpty()) glideItem(key = "tags") { ByMoment(state, report) }
            glideItem(key = "table") { DailyTable(report, unit) }
        }
        glideItem(key = "note") {
            Text(
                tr("Based on your logged readings, not a sensor: time in range here is the share of readings. Estimated A1C (GMI) can differ from a lab A1C."),
                style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 18.sp),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** 7 · 14 · 30 · 90 as underline tabs across the width; the chosen one says "7 DAYS". */
@Composable
private fun PeriodTabs(periods: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    val rule = C.Border
    val accent = C.AccentText
    // The underline glides to the chosen period (same feel as the sliding segments elsewhere).
    val pos by androidx.compose.animation.core.animateFloatAsState(
        selected.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 380f), label = "period",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val segW = size.width / periods.size
                drawRect(accent, Offset(segW * pos, size.height - 2.dp.toPx()), Size(segW, 2.dp.toPx()))
            }
            .selectableGroup(),
    ) {
        periods.forEachIndexed { i, days ->
            val on = i == selected
            val name = trn(days, "{0} day", "{0} days")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab) {
                        if (!on) Haptics.segment(view)
                        onSelect(i)
                    }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (on) caps(name) else "$days",
                    style = mono(13.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) C.Text else C.TextMuted, 0.04f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** "92%" with a smaller per-cent sign, so a 100% still fits its column. */
private fun percentText(n: String): AnnotatedString = buildAnnotatedString {
    append(n)
    withStyle(SpanStyle(fontSize = 17.sp)) { append("%") }
}

/** "6.3" ("6,3" in Icelandic). */
private fun oneDecimal(v: Double) = String.format(Locale.ROOT, "%.1f", v).replace('.', L.decimal)

/** The period in four numbers, divided by hairlines: average, in range, lows, estimated A1C. */
@Composable
private fun BoxScore(r: Report, unit: GlucoseUnit, modifier: Modifier) {
    val gmi = r.gmiPercent
    val inRange = r.bands?.inRange?.roundToInt()
    val mean = r.mean
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        BoxCell(
            tr("Average"),
            AnnotatedString(mean?.let { Glucose.format(it, unit) } ?: "—"),
            C.Text,
            if (mean != null) tr("Average") + " " + Glucose.format(mean, unit) + " " + unit.label else tr("Average") + " —",
            first = true,
        )
        BoxCell(
            tr("In range"),
            inRange?.let { percentText("$it") } ?: AnnotatedString("—"),
            C.InRange,
            tr("In range") + " " + (inRange?.let { "$it%" } ?: "—"),
        )
        BoxCell(tr("Lows"), AnnotatedString("${r.lows}"), C.Text, trn(r.lows, "{0} low.", "{0} lows."))
        BoxCell(
            tr("A1C est."),
            gmi?.let { percentText(oneDecimal(it)) } ?: AnnotatedString("—"),
            if (gmi != null) C.Text else C.TextMuted,
            tr("Estimated A1C") + " " + (gmi?.let { oneDecimal(it) + "%" } ?: tr("after a week")),
        )
    }
}

@Composable
private fun RowScope.BoxCell(label: String, value: AnnotatedString, color: Color, description: String, first: Boolean = false) {
    val rule = C.Border
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (first) Modifier
                else Modifier.drawBehind { drawLine(rule, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
            )
            .padding(start = if (first) 0.dp else 10.dp, end = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(caps(label), style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.06f).copy(lineHeight = 15.sp), maxLines = 2)
        // Numbers roll like the Today readout when the period changes; a "%" stays put, smaller.
        val text = value.text
        val pct = text.endsWith("%")
        val digits = if (pct) text.dropLast(1) else text
        var last by remember { mutableStateOf(digits) }
        val up = (digits.replace(',', '.').toDoubleOrNull() ?: 0.0) >= (last.replace(',', '.').toDoubleOrNull() ?: 0.0)
        androidx.compose.runtime.SideEffect { last = digits }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            Odometer(digits, up, numeral(28.sp, color))
            if (pct) Text("%", style = numeral(17.sp, color), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/**
 * Time in range as one 8 dp bar, low on the left to very high on the right (only the bands that
 * happened), the shares in words under it, and the comparison with the period before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeBar(r: Report, b: Bands) {
    class Seg(val pct: Double, val color: Color, val text: String)
    fun p(x: Double): String = if (x > 0 && x < 0.5) "<1" else "${x.roundToInt()}"
    val segs = listOf(
        Seg(b.veryLow, C.UrgentLow, tr("{0}% very low", p(b.veryLow))),
        Seg(b.low, C.Low, tr("{0}% low", p(b.low))),
        Seg(b.inRange, C.InRange, tr("{0}% in range", p(b.inRange))),
        Seg(b.high, C.High, tr("{0}% high", p(b.high))),
        Seg(b.veryHigh, C.VeryHigh, tr("{0}% very high", p(b.veryHigh))),
    ).filter { it.pct > 0 }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clearAndSetSemantics { contentDescription = tr("Time in range: {0}.", segs.joinToString(", ") { it.text }) },
    ) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val gap = 2.dp.toPx()
            val usable = size.width - gap * (segs.size - 1).coerceAtLeast(0)
            var x = 0f
            segs.forEachIndexed { i, s ->
                val w = (usable * (s.pct / 100.0)).toFloat().coerceAtLeast(2f)
                drawRect(s.color, Offset(x, 0f), Size(w.coerceAtMost(size.width - x), size.height))
                x += w + if (i < segs.size - 1) gap else 0f
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            segs.forEachIndexed { i, s ->
                Text(
                    s.text,
                    style = mono(12.sp, color = C.TextMuted),
                    maxLines = 1,
                    modifier = if (i < segs.size - 1) Modifier.padding(end = 10.dp) else Modifier,
                )
            }
        }
    }
    val tir = b.inRange.roundToInt()
    val prev = r.previousInRange?.roundToInt()
    if (prev != null) {
        Text(
            when {
                tir > prev -> tr("In range: up from {0}% in the {1} days before.", prev, r.days)
                tir < prev -> tr("In range: down from {0}% in the {1} days before.", prev, r.days)
                else -> tr("In range: the same as the {0} days before.", r.days)
            },
            style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 16.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One row of the small multiples: a day, or (for 30 and 90 days) up to seven days laid over one. */
private class Strip(
    val first: LocalDate,
    val last: LocalDate,
    val week: Boolean,
    val readings: List<Reading>,
    /** A day row's ghost: the same weekday one week earlier (empty for week rows). */
    val ghost: List<Reading> = emptyList(),
) {
    val average: Double? = if (readings.isEmpty()) null else readings.map { it.mmol }.average()
    val ghostAverage: Double? = if (ghost.isEmpty()) null else ghost.map { it.mmol }.average()
    val lows = readings.count { it.mmol < Glucose.LOW }
    val highs = readings.count { it.mmol > Glucose.TIR_HIGH }
}

/**
 * The period's rows, newest first: days for 7 and 14 days, seven-day blocks back from today
 * otherwise. Day rows also carry the same weekday a week earlier, from [all] readings.
 */
private fun buildStrips(r: Report, zone: ZoneId, all: List<Reading>): List<Strip> {
    val byDay = r.readings.groupBy { it.date(zone) }
    val weekly = r.days > 14
    val ghostFrom = r.from.minusDays(7)
    val ghostTo = r.to.minusDays(7)
    val ghostByDay: Map<LocalDate, List<Reading>> = if (weekly) emptyMap()
    else all.filter { val d = it.date(zone); !d.isBefore(ghostFrom) && !d.isAfter(ghostTo) }.groupBy { it.date(zone) }
    val out = ArrayList<Strip>()
    var last = r.to
    while (!last.isBefore(r.from)) {
        val back = if (weekly) last.minusDays(6) else last
        val first = if (back.isBefore(r.from)) r.from else back
        val rs = ArrayList<Reading>()
        var d = first
        while (!d.isAfter(last)) {
            byDay[d]?.let { rs.addAll(it) }
            d = d.plusDays(1)
        }
        val ghost = if (weekly) emptyList() else ghostByDay[first.minusDays(7)].orEmpty().sortedBy { it.at }
        out += Strip(first, last, weekly, rs.sortedBy { it.at }, ghost)
        last = first.minusDays(1)
    }
    return out
}

/** "SUN 25" for a day; "13–19 OCT" (or "28 SEP–\n4 OCT") for a week. */
private fun stripLabel(s: Strip): String {
    if (!s.week) return caps(dayPattern("EEE d").format(s.first))
    if (s.first == s.last) return caps(dayPattern("d MMM").format(s.first))
    val sameMonth = s.first.month == s.last.month
    val a = if (sameMonth) dayPattern("d").format(s.first) else dayPattern("d MMM").format(s.first) + "–\n"
    return caps(if (sameMonth) a + "–" + dayPattern("d MMM").format(s.last) else a + dayPattern("d MMM").format(s.last))
}

/** What TalkBack says for a row: the day, its average, and its lows and highs. */
private fun stripDescription(s: Strip, unit: GlucoseUnit): String {
    val name = if (s.first == s.last) dayPattern("EEEE d MMMM").format(s.first).cap()
    else dayPattern("d MMMM").format(s.first) + " – " + dayPattern("d MMMM").format(s.last)
    // Day rows: the same weekday a week earlier, briefly, when it had readings.
    val ghost = s.ghostAverage?.let { tr("Last {0}: average {1}.", dayPattern("EEEE").format(s.first), Glucose.format(it, unit)) }
    val avg = s.average ?: return listOfNotNull(tr("{0}, no readings.", name), ghost).joinToString(" ")
    val parts = mutableListOf(tr("{0}, average {1}.", name, Glucose.format(avg, unit)))
    if (s.lows > 0) parts += trn(s.lows, "{0} low.", "{0} lows.")
    if (s.highs > 0) parts += trn(s.highs, "{0} high.", "{0} highs.")
    if (ghost != null) parts += ghost
    return parts.joinToString(" ")
}

/**
 * A day's readings by time of day across the shared scale: the in-range band faint behind, the
 * readings joined in grey, each a dot in its range colour (bigger when out of range). A week's
 * row lays its days over each other, one faint line each.
 */
@Composable
private fun StripRow(
    s: Strip,
    fromHour: Int,
    maxV: Double,
    isToday: Boolean,
    now: Long,
    state: AppState,
    open: Boolean,
    first: Boolean = false,
    onTap: () -> Unit,
) {
    val zone = state.zone
    val unit = state.unit
    val targets = state.targets
    val band = C.InRange.copy(alpha = 0.12f)
    val line = C.TextSecondary
    val ghostLine = C.TextMuted.copy(alpha = 0.45f)
    val nowLine = C.AccentText
    val colors = Status.entries.associateWith { C.status(it) }
    val avg = s.average
    val desc = stripDescription(s, unit)
    val canOpen = s.readings.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .ruleTop(!first)
            .then(if (canOpen) Modifier.tap(onClick = onTap) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = desc
                if (canOpen) {
                    if (open) collapse { onTap(); true } else expand { onTap(); true }
                }
            }
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stripLabel(s),
            style = mono(
                12.sp,
                if (isToday || open) FontWeight.SemiBold else FontWeight.Normal,
                if (isToday || open) C.Text else C.TextSecondary,
            ).copy(lineHeight = 15.sp),
            modifier = Modifier.width(if (s.week) 76.dp else 64.dp).clearAndSetSemantics {},
        )
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(30.dp)) {
            val pad = 4.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            val spanSec = (24 - fromHour) * 3600f
            fun frac(at: Long): Float {
                val sec = Instant.ofEpochMilli(at).atZone(zone).toLocalTime().toSecondOfDay() - fromHour * 3600
                return sec / spanSec
            }
            fun xOf(at: Long): Float = pad + w * frac(at).coerceIn(0f, 1f)
            // Readings outside the row's hours (before its start hour, or past midnight) are left
            // out rather than pinned to an edge, where they used to draw a spike.
            fun inRow(at: Long): Boolean = frac(at) in 0f..1f
            fun yOf(m: Double): Float = pad + h * (1f - ((m - SPARK_MIN) / (maxV - SPARK_MIN)).toFloat().coerceIn(0f, 1f))
            drawRect(band, Offset(0f, yOf(Glucose.TIR_HIGH)), Size(size.width, yOf(Glucose.LOW) - yOf(Glucose.TIR_HIGH)))
            // The same weekday last week, faint and dashed behind the day's own line (no dots).
            if (!s.week && s.ghost.size >= 2) {
                val gp = Path()
                s.ghost.filter { inRow(it.at) }.forEachIndexed { i, r ->
                    if (i == 0) gp.moveTo(xOf(r.at), yOf(r.mmol)) else gp.lineTo(xOf(r.at), yOf(r.mmol))
                }
                drawPath(
                    gp,
                    ghostLine,
                    style = Stroke(
                        1.dp.toPx(),
                        cap = StrokeCap.Butt,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            val shown = s.readings.filter { inRow(it.at) }
            val runs = if (s.week) shown.groupBy { it.date(zone) }.values.toList() else listOf(shown)
            runs.forEach { rs ->
                if (rs.size >= 2) {
                    val path = Path()
                    rs.forEachIndexed { i, r ->
                        if (i == 0) path.moveTo(xOf(r.at), yOf(r.mmol)) else path.lineTo(xOf(r.at), yOf(r.mmol))
                    }
                    drawPath(
                        path,
                        if (s.week) line.copy(alpha = 0.4f) else line,
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            // Today's row: a thin ink line at the time it is now.
            if (isToday && !s.week) {
                val f = frac(now)
                if (f in 0f..1f) {
                    val x = pad + w * f
                    drawLine(nowLine, Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
                }
            }
            shown
                .map { it to Glucose.status(it.mmol, it.tag, targets) }
                .sortedBy { if (it.second == Status.IN_RANGE) 0 else 1 }
                .forEach { (r, st) ->
                    val rad = if (st == Status.IN_RANGE) 2.5.dp.toPx() else 3.5.dp.toPx()
                    drawCircle(colors.getValue(st), rad, Offset(xOf(r.at), yOf(r.mmol)))
                }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            avg?.let { Glucose.format(it, unit) } ?: "—",
            style = numeral(17.sp, if (avg != null) C.Text else C.TextMuted),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(44.dp).clearAndSetSemantics {},
        )
    }
}

/** More detail: the spread, variability and estimated A1C in a couple of quiet lines. */
@Composable
private fun MoreNumbers(r: Report, unit: GlucoseUnit) {
    val style = mono(12.sp, color = C.TextSecondary).copy(lineHeight = 18.sp)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val parts = listOfNotNull(
            r.readings.minOfOrNull { it.mmol }?.let { tr("Lowest {0}", Glucose.format(it, unit)) },
            r.readings.maxOfOrNull { it.mmol }?.let { tr("Highest {0}", Glucose.format(it, unit)) },
            r.cv?.let { tr("Variability {0}% (goal 36% or less)", it.roundToInt()) },
        )
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = style)
        val g = r.gmiPercent
        val mm = r.gmiMmolMol
        Text(
            if (g != null && mm != null) tr("Estimated A1C {0}% ({1} mmol/mol).", oneDecimal(g), mm.roundToInt())
            else tr("Estimated A1C needs a week of readings."),
            style = style,
        )
    }
}

/** Average for each moment of the day, as short bars on a shared scale with the value at the end. */
@Composable
private fun ByMoment(state: AppState, r: Report) {
    val unit = state.unit
    val maxV = maxOf(12.0, r.byTag.maxOf { it.average } + 1.0)
    Column(Modifier.fillMaxWidth()) {
        RuleHeader(tr("By time of day"))
        r.byTag.forEach { t ->
            val s = Glucose.status(t.average, t.tag, state.targets)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(t.tag.icon(), C.TextMuted, 13.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(t.tag.label, style = inter(13.sp, FontWeight.SemiBold, C.Text), modifier = Modifier.weight(1f))
                    Text(trn(t.count, "{0} reading", "{0} readings"), style = mono(12.sp, color = C.TextMuted))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth((t.average / maxV).toFloat().coerceIn(0.04f, 1f))
                                .height(8.dp)
                                .background(C.status(s))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Glucose.format(t.average, unit), style = numeral(17.sp, C.Text))
                }
                Text(s.label, style = mono(12.sp, color = C.TextMuted))
            }
        }
    }
}

/** The same numbers as a table: one row per day. */
@Composable
private fun DailyTable(r: Report, unit: GlucoseUnit) {
    var open by remember { mutableStateOf(false) }
    val df = dayPattern("EEE d MMM")
    val head = mono(12.sp, FontWeight.Medium, C.TextMuted)
    val cell = mono(12.sp, color = C.TextSecondary)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleHeader(tr("Daily averages"), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                caps(if (open) tr("Hide") else tr("Show table")),
                style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f),
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).tap { open = !open }.padding(vertical = 15.dp),
            )
        }
        Fold(open) { Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 6.dp)) {
                Text(tr("Day"), style = head, modifier = Modifier.weight(1.4f))
                Text(tr("Readings"), style = head, modifier = Modifier.weight(1f))
                Text(tr("Average"), style = head, modifier = Modifier.weight(1f))
                Text(tr("Range"), style = head, modifier = Modifier.weight(1.2f))
            }
            r.byDay.reversed().forEach { d ->
                Row(Modifier.fillMaxWidth().ruleTop().padding(vertical = 8.dp)) {
                    Text(caps(df.format(d.day)), style = cell.copy(color = C.Text), modifier = Modifier.weight(1.4f))
                    Text("${d.count}", style = cell, modifier = Modifier.weight(1f))
                    Text(Glucose.format(d.average, unit), style = cell.copy(color = C.Text, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("${Glucose.format(d.min, unit)}–${Glucose.format(d.max, unit)}", style = cell, modifier = Modifier.weight(1.2f))
                }
            }
        } }
    }
}
