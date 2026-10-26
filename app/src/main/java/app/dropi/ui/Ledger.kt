package app.dropi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
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
    // Which way the odometer rolls: up for a higher number than the last one shown, down for lower.
    val roll = remember { Roll(latest.mmol) }
    if (roll.last != latest.mmol) {
        roll.up = latest.mmol > roll.last
        roll.last = latest.mmol
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(caps("${latest.tag.label} · ${fmt.ago(latest.at, now)}"), style = mono(12.sp, color = C.TextSecondary, tracking = 0.04f), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Odometer(
                    Glucose.format(latest.mmol, state.unit),
                    up = roll.up,
                    style = numeral(if (state.unit == GlucoseUnit.MGDL) 92.sp else 104.sp).copy(lineHeight = 100.sp),
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

/** The last value the readout showed, and whether the newest one is higher. Not state: read only when a roll starts. */
private class Roll(var last: Double, var up: Boolean = true)

/**
 * The big number as an odometer: each character rolls vertically from the old value's character
 * to the new one, left to right a beat apart. Positions are counted from the right, so the last
 * digit stays the last digit when the number gets longer (9,8 → 10,2). Only characters that
 * changed move. Read out as the whole number, not character by character.
 */
@Composable
fun Odometer(text: String, up: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            key(n - 1 - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val delay = i * 45
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(340, delay, FastOutSlowInEasing)) { h -> h * dir } + fadeIn(tween(220, delay))) togetherWith
                            (slideOutVertically(tween(340, delay, FastOutSlowInEasing)) { h -> -h * dir } + fadeOut(tween(180, delay))) using
                            SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), style = style, maxLines = 1, softWrap = false)
                }
            }
        }
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
    val anim = animateFloatAsState(frac(mmol), spring(dampingRatio = 0.75f, stiffness = 140f), label = "tape")
    val pos by anim
    // A light tick each time the gliding needle passes 3.9, 10 or 13.9 (an overshoot that comes
    // back over a mark ticks again, like a detent). Nothing on first show: there's no glide then.
    val view = LocalView.current
    LaunchedEffect(Unit) {
        val marks = listOf(Glucose.LOW, Glucose.TIR_HIGH, Glucose.VERY_HIGH).map { frac(it) }
        var prev = anim.value
        snapshotFlow { anim.value }.collect { p ->
            if (marks.any { m -> (prev < m) != (p < m) }) Haptics.segment(view)
            prev = p
        }
    }
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
    val view = LocalView.current
    // Press and hold, then drag: a cursor follows the finger and names the nearest reading or meal.
    // It listens on the way down (Initial pass) so that once the hold is recognised it can take
    // the gesture away from the dots and rings under it (no tap on release) and from the page's
    // scroll. A quick tap, or a drag that starts before the hold, goes through untouched.
    var scrubX by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var over = false
                        while (!over) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            over = c == null || !c.pressed || c.isConsumed ||
                                (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                        }
                        true
                    }
                    if (endedEarly != null) return@awaitEachGesture
                    try {
                        scrubX = down.position.x.coerceIn(0f, size.width.toFloat())
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            ev.changes.forEach { it.consume() }
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            scrubX = c.position.x.coerceIn(0f, size.width.toFloat())
                        }
                    } finally {
                        scrubX = null
                    }
                }
            },
    ) {
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
        // The scrub: the nearest reading or meal to the finger, a cursor, and its label above the strip.
        val sx = scrubX
        val wPx = constraints.maxWidth.toFloat()
        val near = if (sx == null) null else {
            fun pxOf(at: Long) = ((hourOf(at) - from) / (to - from)).coerceIn(0f, 1f) * wPx
            val r = readings.minByOrNull { abs(pxOf(it.at) - sx) }
            val m = meals.minByOrNull { abs(pxOf(it.at) - sx) }
            val rd = if (r != null) abs(pxOf(r.at) - sx) else Float.MAX_VALUE
            val md = if (m != null) abs(pxOf(m.at) - sx) else Float.MAX_VALUE
            when {
                r != null && rd <= md -> ScrubItem("r${r.id}@${r.at}", "${fmt.time(r.at)} · ${Glucose.format(r.mmol, state.unit)}")
                m != null -> ScrubItem("m${m.id}@${m.at}", "${mealName(m.name)} · ${tr("{0} g", app.dropi.data.Grams.text(m.carbs))}")
                else -> null
            }
        }
        LaunchedEffect(near?.key) { if (near != null) Haptics.tick(view) }
        if (sx != null) {
            val ink = C.Text
            Box(
                Modifier
                    .offset { IntOffset((sx - 0.5.dp.toPx()).roundToInt(), 0) }
                    .width(1.dp)
                    .height(lineY + 12.dp)
                    .background(ink)
                    .clearAndSetSemantics {},
            )
            if (near != null) {
                Text(
                    near.label,
                    style = mono(12.sp, FontWeight.Medium, C.Text),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // Centred on the cursor, kept inside the strip's width, sitting just above it.
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(p.width, p.height) {
                                val x = (sx - p.width / 2f).roundToInt().coerceIn(0, (wPx.roundToInt() - p.width).coerceAtLeast(0))
                                p.place(x, -p.height - 4.dp.roundToPx())
                            }
                        }
                        .background(C.Bg)
                        .border(1.dp, C.Border)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

/** What the day strip's scrub is over: a key to notice a change, and its label. */
private class ScrubItem(val key: String, val label: String)

private sealed class Entry(val at: Long) {
    class R(val r: Reading) : Entry(r.at)
    class M(val m: MealEntry) : Entry(m.at)
    class P(val c: Check) : Entry(c.at)
}

/**
 * The day's ledger as list items: readings, meals and the checks still planned, oldest first,
 * with a NOW line. A long day folds its older entries into one "N earlier" row ([expanded] is
 * kept by the caller, [onExpand] opens it). Every row has a stable key and animates its
 * placement, so when a planned check slips into the past the NOW line and the rows glide.
 * Goes straight under a [RuleHeader] item: its first row has no line above it.
 */
fun LazyListScope.ledgerItems(
    state: AppState,
    readings: List<Reading>,
    meals: List<MealEntry>,
    checks: List<Check>,
    now: Long,
    fmt: Fmt,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    val entries = (readings.map { Entry.R(it) } + meals.map { Entry.M(it) } + checks.filter { it.reading == null }.map { Entry.P(it) })
        .sortedBy { it.at }
    val past = entries.filter { it.at <= now }
    val future = entries.filter { it.at > now }
    val fold = past.size > 8 && !expanded
    val shown = if (fold) past.takeLast(6) else past
    // Nothing logged yet today: the planned rows show an empty value, a day waiting to be filled.
    val waiting = readings.isEmpty() && meals.isEmpty()
    // Keys must be unique in the list: a repeat (two unsaved readings at id 0, say) gets a suffix.
    val used = HashSet<String>()
    fun keyOf(e: Entry): String {
        val base = when (e) {
            is Entry.R -> "ledger:r:" + (if (e.r.id != 0L) "${e.r.id}" else "t${e.r.at}")
            is Entry.M -> "ledger:m:" + (if (e.m.id != 0L) "${e.m.id}" else "t${e.m.at}")
            is Entry.P -> "ledger:p:${e.c.slot.id}"
        }
        var k = base
        var n = 1
        while (!used.add(k)) k = "$base#${n++}"
        return k
    }
    fun typeOf(e: Entry): String = when (e) {
        is Entry.R -> "ledger-reading"
        is Entry.M -> "ledger-meal"
        is Entry.P -> "ledger-planned"
    }
    if (fold) {
        val hidden = past.size - shown.size
        item(key = "ledger:fold", contentType = "ledger-fold") { FoldRow(hidden, Modifier.animateItem(), onExpand) }
    }
    // Lines only between rows: the first one shown (the fold row, else the first entry, else
    // the NOW line) sits right under the section header with none above it.
    shown.forEachIndexed { i, e ->
        val first = i == 0 && !fold
        item(key = keyOf(e), contentType = typeOf(e)) {
            Box(Modifier.animateItem()) { LedgerRow(state, e, now, fmt, first, waiting) }
        }
    }
    if (entries.isNotEmpty()) {
        val first = shown.isEmpty() && !fold
        item(key = "ledger:now", contentType = "ledger-now") { NowRow(fmt.time(now), first, Modifier.animateItem()) }
    }
    future.forEach { e ->
        item(key = keyOf(e), contentType = typeOf(e)) {
            Box(Modifier.animateItem()) { LedgerRow(state, e, now, fmt, false, waiting) }
        }
    }
}

/** "6 EARLIER ↑": the older part of a long day, folded; tap to show it all. */
@Composable
private fun FoldRow(hidden: Int, modifier: Modifier, onExpand: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { stateDescription = tr("Collapsed") }
            .tap { onExpand() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(caps(trn(hidden, "{0} earlier", "{0} earlier")), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f), modifier = Modifier.weight(1f))
        Text("↑", style = mono(13.sp, color = C.TextSecondary))
    }
}

@Composable
private fun LedgerRow(state: AppState, e: Entry, now: Long, fmt: Fmt, first: Boolean = false, waiting: Boolean = false) {
    when (e) {
        is Entry.R -> ReadingLine(state, e.r, fmt, first)
        is Entry.M -> MealLine(state, e.m, now, fmt, first)
        is Entry.P -> PlannedLine(state, e.c, now, fmt, first, waiting)
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
    first: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).ruleTop(!first).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = mono(13.sp, color = if (timeMuted) C.TextMuted else C.TextSecondary), modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) { middle() }
        Spacer(Modifier.width(12.dp))
        end()
    }
}

@Composable
private fun ReadingLine(state: AppState, r: Reading, fmt: Fmt, first: Boolean) {
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
        first = first,
        end = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(Glucose.format(r.mmol, state.unit), style = numeral(22.sp, c))
                MicroScale(r.mmol)
            }
        },
    )
}

@Composable
private fun MealLine(state: AppState, m: MealEntry, now: Long, fmt: Fmt, first: Boolean) {
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
        first = first,
        end = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("{0} g", app.dropi.data.Grams.text(m.carbs)), style = mono(15.sp, FontWeight.SemiBold, C.Text))
                if (note != null) Text(note, style = mono(12.sp, color = if (rise != null) riseColor(rise) else C.TextSecondary))
            }
        },
    )
}

@Composable
private fun PlannedLine(state: AppState, c: Check, now: Long, fmt: Fmt, first: Boolean, waiting: Boolean = false) {
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
        first = first,
        end = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // An empty day: the value still to come, in the column where readings put theirs.
                if (waiting) {
                    Text("—", style = mono(15.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.clearAndSetSemantics {})
                    Spacer(Modifier.width(10.dp))
                }
                PlannedRing(if (due) C.AccentText else C.TextMuted)
            }
        },
    )
}

@Composable
private fun NowRow(time: String, first: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().ruleTop(!first).padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = mono(13.sp, FontWeight.SemiBold, C.AccentText), modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(1.dp).background(C.AccentText))
        Spacer(Modifier.width(10.dp))
        Text(caps(tr("now")), style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.08f))
    }
}
