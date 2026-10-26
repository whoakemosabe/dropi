package app.dropi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.Check
import app.dropi.data.CheckState
import app.dropi.data.Glucose
import app.dropi.data.Schedule
import app.dropi.data.Reading
import app.dropi.data.Stats
import app.dropi.data.Status
import java.time.Instant

/** The home screen: the latest reading under the aurora, today in a sentence, checks on a day line, and the log. */
@Composable
fun TodayPage(state: AppState) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val zone = state.zone
    val fmt = remember(zone) { Fmt(context, zone) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val all = state.readings
    val todays = Stats.onDay(all, today, zone)
    val streak = Stats.streak(all, today, zone)
    val latest = all.lastOrNull()

    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        // A readable column on tablets and unfolded phones, centred.
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            // Content starts below the sticky header and scrolls under its blur.
            top = LocalHeaderHeight.current + HEADER_FADE,
            // Room for the floating log button over the navigation bar.
            bottom = bars.calculateBottomPadding() + 124.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "banner") {
            Column {
                UpdateBanner()
                if (state.demo) DemoBanner(state)
                MealDraftCard()
                AiBanner(state)
                CrashCard(state)
            }
        }
        item(key = "hero") {
            // Live mode with a fresh sensor value: the live number and trace lead the screen.
            if (state.liveOn && app.dropi.data.Trend.fresh(state.sensor, now)) {
                LiveHero(state, now, fmt)
            } else {
                Column {
                    Readout(state, latest, fmt, now, streak)
                    if (state.liveOn) LiveWaiting(state, now, fmt)
                }
            }
        }
        val checks = Schedule.today(state.slots, state.readings, now, state.zone, state.afterMealMinutes, state.meals)
        val meals = state.meals.filter { Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate() == today }
        item(key = "strip") { DayStrip(state, checks, todays, meals, now, fmt) }
        // Sundays and Mondays: last week at a glance (the same as the Sunday recap).
        val dow = today.dayOfWeek
        if ((dow == java.time.DayOfWeek.SUNDAY || dow == java.time.DayOfWeek.MONDAY) && all.size >= 3) {
            item(key = "week") { WeekCard(state, today) }
        }
        item(key = "move") { MoveNote(state, now, fmt) }
        item(key = "ledger") {
            Column {
                val counts = listOfNotNull(
                    trn(todays.size, "{0} reading", "{0} readings"),
                    meals.takeIf { it.isNotEmpty() }?.let { trn(it.size, "{0} meal", "{0} meals") },
                ).joinToString(" · ")
                RuleHeader(tr("Today"), counts)
                if (todays.isEmpty() && meals.isEmpty() && checks.isEmpty()) {
                    Text(
                        if (all.isEmpty()) tr("Your readings will appear here. Tap + to add your first one.") else tr("Nothing logged yet today."),
                        style = inter(15.sp, color = C.TextSecondary),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Ledger(state, todays, meals, checks, now, fmt)
                if (checks.isEmpty()) {
                    Text(
                        tr("No check times yet. Plan a few and Dropi can remind you."),
                        style = inter(13.sp, color = C.TextMuted),
                        modifier = Modifier.ruleTop().padding(top = 12.dp),
                    )
                } else if (!state.remindersOn) {
                    Text(tr("Reminders are off."), style = inter(13.sp, color = C.TextMuted), modifier = Modifier.ruleTop().padding(top = 12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (all.isNotEmpty()) MonoLink(tr("All readings")) { state.openHistory() }
                    MonoLink(if (checks.isEmpty()) tr("Plan check times") else tr("Check times")) { state.scheduleOpen = true }
                }
            }
        }
    }
}

/**
 * Learnt from the last two weeks: a check usually done at another time, as a note with two
 * answers.
 */
@Composable
private fun MoveNote(state: AppState, now: Long, fmt: Fmt) {
    val move = remember(state.readings, state.slots, state.dismissedMoves) { state.moveSuggestion(now) } ?: return
    val clock = { m: Int -> fmt.time(java.time.LocalDate.now(state.zone).atStartOfDay(state.zone).plusMinutes(m.toLong()).toInstant().toEpochMilli()) }
    Column(Modifier.fillMaxWidth().ruleTop().padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text(caps(tr("Note")), style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.08f), modifier = Modifier.width(56.dp).padding(top = 2.dp))
            Text(
                tr("You usually do your {0} check at about {1} ({2} of the last 14 days). Move it there?", move.slot.name.lowercase(L.locale), clock(move.minute), move.days),
                style = inter(15.sp, FontWeight.Medium).copy(lineHeight = 20.sp),
            )
        }
        Row(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Move to {0}", clock(move.minute)), icon = R.drawable.lc_clock, selected = true) { state.acceptMove(move) }
            Chip(tr("Keep {0}", clock(move.slot.minute))) { state.dismissMove(move) }
        }
    }
}

@Composable
private fun WeekCard(state: AppState, today: java.time.LocalDate) {
    val r = androidx.compose.runtime.remember(state.readings, today) { app.dropi.data.Analytics.report(state.readings, 7, today, state.zone) }
    if (r.count == 0) return
    Column(Modifier.fillMaxWidth()) {
        RuleHeader(tr("Your week"))
        Row(Modifier.fillMaxWidth()) {
            WeekStat(r.bands?.let { "${Math.round(it.inRange)}%" } ?: "—", tr("in range"), Modifier.weight(1f), first = true)
            WeekStat("${r.lows}", trn(r.lows, "low reading", "low readings"), Modifier.weight(1f))
            WeekStat("${r.daysLogged}/7", tr("days logged"), Modifier.weight(1f))
        }
        MonoLink(tr("See this week in Trends")) {
            state.trendsDays = 7
            state.tab = Tab.TRENDS
        }
    }
}

/** One number of the week, in a column divided from the next by a hairline. */
@Composable
private fun WeekStat(value: String, label: String, modifier: Modifier, first: Boolean = false) {
    val line = C.Border
    Column(
        modifier
            .drawBehind { if (!first) drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
            .padding(start = if (first) 0.dp else 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(caps(label), style = mono(12.sp, color = C.TextMuted, tracking = 0.06f), maxLines = 1)
        Text(value, style = numeral(26.sp, C.Text))
    }
}

/** A rise in one word ("Gentle"), in the app's language. */
internal fun riseShort(r: app.dropi.data.Rise): String = when (r) {
    app.dropi.data.Rise.GENTLE -> tr("Gentle")
    app.dropi.data.Rise.MODERATE -> tr("Moderate")
    app.dropi.data.Rise.BIG -> tr("Big")
}

/** While demo data is on: says so plainly, with the way back. */
@Composable
private fun DemoBanner(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .card()
            .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(caps(tr("Demo data")), style = mono(12.sp, FontWeight.SemiBold, C.AccentText, 0.08f))
            Text(tr("Made-up readings. Your real log is safe."), style = inter(13.sp, color = C.TextSecondary))
        }
        GhostButton(tr("Leave"), color = C.Text) { state.leaveDemo() }
    }
}

/** The range a reading is in: its colour as a dot, the words in text colour (readable in both themes). */
@Composable
fun StatusPill(status: Status) {
    val c = C.status(status)
    Row(
        Modifier
            .border(1.dp, c.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RangeSquare(c)
        Spacer(Modifier.width(8.dp))
        Text(caps(status.label), style = mono(12.sp, FontWeight.SemiBold, C.Text, 0.06f))
    }
}

/** A run of readings as ledger rows: a hairline above each, time · moment · number. */
@Composable
fun ReadingsCard(state: AppState, readings: List<Reading>, time: (Reading) -> String) {
    Column(Modifier.fillMaxWidth()) {
        readings.forEachIndexed { i, r -> ReadingRow(state, r, time(r), first = i == 0) }
    }
}

/**
 * One reading as a ledger row (the same as Today's): the time in mono, the moment and details,
 * and the number in its range colour over a small scale.
 */
@Composable
fun ReadingRow(state: AppState, r: Reading, time: String, first: Boolean = false) {
    val status = state.status(r)
    // "2 h after breakfast": which meal this reading belongs to.
    val link = remember(r, state.meals, state.readings) { state.linkText(r) }
    val c = C.status(status)
    val sub = listOf(link.orEmpty(), r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .ruleTop(!first)
            .tap { state.editing = r }
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(r.tag.label, time, link, Glucose.format(r.mmol, state.unit), status.label).joinToString(", ")
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = mono(13.sp, color = C.TextSecondary), modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold, C.Text))
            if (sub.isNotEmpty()) {
                Text(sub, style = inter(13.sp, color = C.TextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(Glucose.format(r.mmol, state.unit), style = numeral(22.sp, c))
            MicroScale(r.mmol)
        }
    }
}

/** After a crash: what went wrong, with a Copy button so it can be pasted to Claude. */
@Composable
fun CrashCard(state: AppState) {
    val text = state.lastCrash ?: return
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Dropi crashed last time"), style = inter(15.sp, FontWeight.Bold))
        Text(
            text.lines().filter { it.isNotBlank() }.take(8).joinToString("\n"),
            style = mono(12.sp, color = C.TextSecondary).copy(lineHeight = 16.sp),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Copy details"), icon = R.drawable.lc_bookmark) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Dropi crash", text))
            }
            Chip(tr("Dismiss")) { state.dismissCrash() }
        }
    }
}
