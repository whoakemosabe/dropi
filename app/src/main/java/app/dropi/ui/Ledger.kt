package app.dropi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.data.Check
import app.dropi.data.CheckState
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealEntry
import app.dropi.data.Reading
import app.dropi.tr
import app.dropi.trn
import java.time.Instant

/*
 * Today in 1.1: the readout (the latest number, its range on a vertical tape, the streak in
 * tallies), the day strip (06–24 with readings, meals, now and the planned checks), and one
 * ledger for the day: readings, meals and planned checks in time order, a NOW line between what's
 * happened and what's planned.
 */

/** The latest reading, left-aligned, with the range tape beside it. */
@Composable
fun Readout(state: AppState, latest: Reading?, fmt: Fmt, now: Long, streak: Int) {
    if (latest == null) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("No readings yet"), style = heading(30.sp))
            Text(tr("Tap + to log your first one."), style = inter(15.sp, FontWeight.Medium, C.TextSecondary))
        }
        return
    }
    val status = state.status(latest)
    val color = C.status(status)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(latest.id) {
        pop.snapTo(0.92f)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(caps("${latest.tag.label} · ${fmt.ago(latest.at, now)}"), style = mono(12.sp, color = C.TextSecondary, tracking = 0.04f), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Glucose.format(latest.mmol, state.unit),
                    style = numeral(if (state.unit == GlucoseUnit.MGDL) 92.sp else 104.sp).copy(lineHeight = 100.sp),
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.unit.label, style = mono(13.sp, color = C.TextSecondary))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RangeSquare(color)
                        Spacer(Modifier.width(6.dp))
                        Text(status.label.lowercase(L.locale), style = inter(13.sp, FontWeight.SemiBold))
                    }
                }
            }
            if (streak >= 2) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tally(streak, C.High)
                    if (streak <= 15) Spacer(Modifier.width(10.dp))
                    // Never 1 here; the singular is for 21, 31… in Icelandic.
                    Text(trn(streak, "{0} day logged in a row", "{0} days logged in a row"), style = mono(12.sp, color = C.TextSecondary))
                }
            }
        }
        RangeTape(latest.mmol, state.unit, Modifier.width(80.dp).height(150.dp))
    }
}

/**
 * The consensus bands as a short vertical tape (very high at the top, low at the bottom), with a
 * needle that glides to each new value. 3.9, 10 and 13.9 are labelled in the person's unit.
 */
@Composable
fun RangeTape(mmol: Double, unit: GlucoseUnit, modifier: Modifier = Modifier) {
    val lo = 2.0
    val hi = 16.0
    fun frac(v: Double) = ((hi - v) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    val pos by animateFloatAsState(frac(mmol), spring(dampingRatio = 0.75f, stiffness = 140f), label = "tape")
    val low = C.Low
    val inRange = C.InRange
    val high = C.High
    val veryHigh = C.VeryHigh
    val ink = C.Text
    val tick = C.Border
    BoxWithConstraints(modifier.semantics { contentDescription = tr("On the range bar: {0} {1}", Glucose.format(mmol, unit), unit.label) }) {
        val h = maxHeight
        // Needle on the left, the tape, then the labels to its right: nothing sits on the tape.
        val tapeX = 30.dp
        Canvas(Modifier.fillMaxSize()) {
            val x = tapeX.toPx()
            val bar = 3.dp.toPx()
            fun y(v: Double) = frac(v) * size.height
            listOf(hi to Glucose.VERY_HIGH to veryHigh, Glucose.VERY_HIGH to Glucose.TIR_HIGH to high,
                Glucose.TIR_HIGH to Glucose.LOW to inRange, Glucose.LOW to lo to low).forEach { (r, c) ->
                drawLine(c, Offset(x, y(r.first)), Offset(x, y(r.second)), strokeWidth = bar)
            }
            listOf(Glucose.VERY_HIGH, Glucose.TIR_HIGH, Glucose.LOW).forEach { v ->
                drawLine(tick, Offset(x + 3.dp.toPx(), y(v)), Offset(x + 8.dp.toPx(), y(v)), strokeWidth = 1.dp.toPx())
            }
            // The needle: a hairline from the left edge to a small arrowhead touching the tape.
            val ny = pos * size.height
            drawLine(ink, Offset(0f, ny), Offset(x - 10.dp.toPx(), ny), strokeWidth = 1.dp.toPx())
            val p = Path().apply {
                moveTo(x - 3.dp.toPx(), ny)
                lineTo(x - 12.dp.toPx(), ny - 6.dp.toPx())
                lineTo(x - 12.dp.toPx(), ny + 6.dp.toPx())
                close()
            }
            drawPath(p, ink)
        }
        val labelStyle = mono(12.sp, color = C.TextMuted)
        listOf(Glucose.VERY_HIGH, Glucose.TIR_HIGH, Glucose.LOW).forEach { v ->
            Text(
                Glucose.format(v, unit), style = labelStyle, maxLines = 1, softWrap = false,
                modifier = Modifier.offset(x = tapeX + 11.dp, y = h * frac(v) - 8.dp),
            )
        }
    }
}

/**
 * The day on one line, 06–24 (earlier when something happened earlier): readings as dots in
 * their range colour, meals as short marks above the line, planned checks as hollow rings, and a
 * "now" hairline. Dots and rings are tappable.
 */
@Composable
fun DayStrip(state: AppState, checks: List<Check>, readings: List<Reading>, meals: List<MealEntry>, now: Long, fmt: Fmt) {
    val zone = state.zone
    fun hourOf(at: Long): Float {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return t.hour + t.minute / 60f
    }
    val earliest = (readings.map { it.at } + meals.map { it.at } + checks.map { it.at }).minOfOrNull { hourOf(it) } ?: 6f
    val from = if (earliest < 6f) kotlin.math.floor(earliest / 3f) * 3f else 6f
    val to = 24f
    val line = C.Border
    val accent = C.AccentText
    val markColor = C.TextSecondary
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        val w = maxWidth
        fun xOf(h: Float): Dp = w * ((h - from) / (to - from)).coerceIn(0f, 1f)
        val lineY = 26.dp
        Canvas(Modifier.fillMaxSize()) {
            val y = lineY.toPx()
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            var hh = from
            while (hh <= to + 0.01f) {
                val x = ((hh - from) / (to - from)) * size.width
                val major = hh.toInt() % 6 == 0
                val t = (if (major) 5 else 4).dp.toPx()
                drawLine(line, Offset(x.coerceIn(0.5f, size.width - 0.5f), y - t), Offset(x.coerceIn(0.5f, size.width - 0.5f), y + t), strokeWidth = 1.dp.toPx())
                hh += 3f
            }
            meals.forEach { m ->
                val x = ((hourOf(m.at) - from) / (to - from)) * size.width
                drawLine(markColor, Offset(x, y - 16.dp.toPx()), Offset(x, y - 6.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            val nx = ((hourOf(now) - from) / (to - from)).coerceIn(0f, 1f) * size.width
            drawLine(accent, Offset(nx, y - 20.dp.toPx()), Offset(nx, y + 10.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        // Hour labels under the line, every six hours.
        var hh = kotlin.math.ceil(from / 6f) * 6f
        while (hh <= to) {
            val x = xOf(hh)
            val label = if (hh >= 24f) "24" else "%02d".format(hh.toInt())
            val dx = when {
                hh <= from -> 0.dp
                hh >= to -> (-16).dp
                else -> (-8).dp
            }
            Text(label, style = mono(12.sp, color = C.TextMuted), modifier = Modifier.offset(x = x + dx, y = lineY + 10.dp))
            hh += 6f
        }
        val nowX = xOf(hourOf(now))
        Text(tr("now"), style = mono(12.sp, color = C.AccentText), modifier = Modifier.offset(x = (nowX + 4.dp).coerceAtMost(w - 28.dp), y = 0.dp))
        // Planned checks not done yet: hollow rings (the due one in ink), tap to log it.
        checks.filter { it.reading == null }.forEach { c ->
            val x = xOf(hourOf(c.at))
            val how = when (c.state) {
                CheckState.DUE -> tr("due now")
                CheckState.MISSED -> tr("missed")
                else -> tr("coming up")
            }
            Box(
                Modifier
                    .offset(x = x - 22.dp, y = lineY - 22.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .tap { state.startNew(c.slot.tag) }
                    .semantics { contentDescription = tr("{0} at {1}: {2}", c.slot.name, fmt.time(c.at), how) },
                contentAlignment = Alignment.Center,
            ) {
                when (c.state) {
                    CheckState.MISSED -> Box(Modifier.size(7.dp).clip(CircleShape).background(C.TextMuted.copy(alpha = 0.6f)))
                    CheckState.DUE -> Box(Modifier.size(12.dp).clip(CircleShape).background(C.Bg).border(2.dp, C.AccentText, CircleShape))
                    else -> Box(Modifier.size(11.dp).clip(CircleShape).background(C.Bg).border(2.dp, C.TextMuted, CircleShape))
                }
            }
        }
        // Readings: dots in their range colour, tap to edit.
        readings.forEach { r ->
            val x = xOf(hourOf(r.at))
            Box(
                Modifier
                    .offset(x = x - 22.dp, y = lineY - 22.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .tap { state.editing = r }
                    .semantics { contentDescription = tr("{0} at {1}: {2}", r.tag.label, fmt.time(r.at), Glucose.format(r.mmol, state.unit)) },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(11.dp).clip(CircleShape).background(C.status(state.status(r)))) }
        }
    }
}

private sealed class Entry(val at: Long) {
    class R(val r: Reading) : Entry(r.at)
    class M(val m: MealEntry) : Entry(m.at)
    class P(val c: Check) : Entry(c.at)
}

/**
 * The day's ledger: readings, meals and the checks still planned, oldest first, with a NOW line.
 * A long day folds its older entries into one "N earlier" row.
 */
@Composable
fun Ledger(state: AppState, readings: List<Reading>, meals: List<MealEntry>, checks: List<Check>, now: Long, fmt: Fmt) {
    val entries = remember(readings, meals, checks) {
        (readings.map { Entry.R(it) } + meals.map { Entry.M(it) } + checks.filter { it.reading == null }.map { Entry.P(it) })
            .sortedBy { it.at }
    }
    val past = entries.filter { it.at <= now }
    val future = entries.filter { it.at > now }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val fold = past.size > 8 && !expanded
    val shown = if (fold) past.takeLast(6) else past
    Column(Modifier.fillMaxWidth()) {
        if (fold) {
            val hidden = past.size - shown.size
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .ruleTop()
                    .semantics(mergeDescendants = true) { stateDescription = tr("Collapsed") }
                    .tap { expanded = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(caps(trn(hidden, "{0} earlier", "{0} earlier")), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f), modifier = Modifier.weight(1f))
                Text("↑", style = mono(13.sp, color = C.TextSecondary))
            }
        }
        shown.forEach { LedgerRow(state, it, now, fmt) }
        if (future.isNotEmpty() || past.isNotEmpty()) NowRow(fmt.time(now))
        future.forEach { LedgerRow(state, it, now, fmt) }
    }
}

@Composable
private fun LedgerRow(state: AppState, e: Entry, now: Long, fmt: Fmt) {
    when (e) {
        is Entry.R -> ReadingLine(state, e.r, fmt)
        is Entry.M -> MealLine(state, e.m, now, fmt)
        is Entry.P -> PlannedLine(state, e.c, now, fmt)
    }
}

/** Time · what · value: the three columns every ledger row shares. */
@Composable
private fun LedgerCells(
    time: String,
    timeMuted: Boolean,
    modifier: Modifier,
    middle: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).ruleTop().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = mono(13.sp, color = if (timeMuted) C.TextMuted else C.TextSecondary), modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) { middle() }
        Spacer(Modifier.width(12.dp))
        end()
    }
}

@Composable
private fun ReadingLine(state: AppState, r: Reading, fmt: Fmt) {
    val status = state.status(r)
    val c = C.status(status)
    val link = remember(r, state.meals, state.readings) { state.linkText(r) }
    val time = fmt.time(r.at)
    val sub = listOf(link.orEmpty(), r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · ")
    LedgerCells(
        time, false,
        Modifier
            .tap { state.editing = r }
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(r.tag.label, time, link, Glucose.format(r.mmol, state.unit), status.label).joinToString(", ")
            },
        middle = {
            Column {
                Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold))
                if (sub.isNotEmpty()) Text(sub, style = inter(13.sp, color = C.TextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        end = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(Glucose.format(r.mmol, state.unit), style = numeral(22.sp, c))
                MicroScale(r.mmol)
            }
        },
    )
}

@Composable
private fun MealLine(state: AppState, m: MealEntry, now: Long, fmt: Fmt) {
    val out = state.outcome(m)
    val rise = out?.let { app.dropi.data.Rise.of(it.rise) }
    val name = mealName(m.name)
    val time = fmt.time(m.at)
    val foods = m.meal.items.joinToString(" · ") { app.dropi.data.Foods.display(it.name) }
    val gap = app.dropi.data.Schedule.gapFor(m, state.afterMealMinutes)
    val note = when {
        out != null && rise != null -> "${app.dropi.data.Foods.delta(out.rise, state.unit)} ${riseShort(rise).lowercase(L.locale)}"
        now - m.at < 4 * 3_600_000L -> tr("check {0}", fmt.time(m.at + gap * 60_000L))
        else -> null
    }
    LedgerCells(
        time, false,
        Modifier
            .tap { app.dropi.work.MealWork.edit(m) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = tr("{0} at {1}: {2}, {3} grams of carbs.", name, time, foods, app.dropi.data.Grams.text(m.carbs)) +
                    (note?.let { " $it" } ?: "")
            },
        middle = {
            Column {
                Text(name, style = inter(15.sp, FontWeight.SemiBold))
                if (foods.isNotEmpty()) Text(foods.lowercase(L.locale), style = inter(13.sp, color = C.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        end = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("{0} g", app.dropi.data.Grams.text(m.carbs)), style = mono(15.sp, FontWeight.SemiBold, C.Text))
                if (note != null) Text(note, style = mono(12.sp, color = if (rise != null) riseColor(rise) else C.TextSecondary))
            }
        },
    )
}

@Composable
private fun PlannedLine(state: AppState, c: Check, now: Long, fmt: Fmt) {
    val time = fmt.time(c.at)
    val mins = (c.at - now) / 60_000
    val sub = when {
        c.state == CheckState.DUE -> tr("due until {0}", fmt.time(c.until))
        c.state == CheckState.MISSED -> tr("missed")
        mins < 60 -> tr("in {0} min", mins.coerceAtLeast(1))
        mins < 24 * 60 -> if (mins % 60 >= 10) tr("in {0} h {1} min", mins / 60, mins % 60) else tr("in {0} h", mins / 60)
        else -> tr("tomorrow")
    }
    val why = c.meal?.let { afterYourMealText(it.name, fmt.time(it.at)) }
    val due = c.state == CheckState.DUE
    LedgerCells(
        time, !due,
        Modifier
            .tap { state.startNew(c.slot.tag) }
            .semantics(mergeDescendants = true) { contentDescription = tr("{0} at {1}: {2}", c.slot.name, time, sub) },
        middle = {
            Column {
                Text(c.slot.name, style = inter(15.sp, FontWeight.SemiBold, if (due) C.Text else C.TextSecondary))
                Text(
                    listOfNotNull(sub, why).joinToString(" · "),
                    style = inter(13.sp, color = if (due) C.AccentText else C.TextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        },
        end = { PlannedRing(if (due) C.AccentText else C.TextMuted) },
    )
}

@Composable
private fun NowRow(time: String) {
    Row(
        Modifier.fillMaxWidth().ruleTop().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = mono(13.sp, FontWeight.SemiBold, C.AccentText), modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(1.dp).background(C.AccentText))
        Spacer(Modifier.width(10.dp))
        Text(caps(tr("now")), style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.08f))
    }
}
