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
import app.dropi.R
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
    val summary = Stats.summary(todays)
    val streak = Stats.streak(all, today, zone)
    val latest = all.lastOrNull()
    val earlier = all.filter { it.date(zone) != today }.takeLast(30).reversed()

    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        // A readable column on tablets and unfolded phones, centred.
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // Content starts below the sticky header and scrolls under its blur.
            top = LocalHeaderHeight.current + HEADER_FADE,
            // Room for the floating log button over the navigation bar.
            bottom = bars.calculateBottomPadding() + 124.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "banner") {
            Column {
                UpdateBanner()
                CrashCard(state)
            }
        }
        item(key = "hero") {
            // Live mode with a fresh sensor value: the live number and trace lead the screen.
            if (state.liveOn && app.dropi.data.Trend.fresh(state.sensor, now)) {
                LiveHero(state, now, fmt)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Hero(state, latest, fmt, now, streak)
                    if (state.liveOn) LiveWaiting(state, now, fmt)
                }
            }
        }
        // Sundays and Mondays: last week at a glance (the same as the Sunday recap).
        val dow = today.dayOfWeek
        if ((dow == java.time.DayOfWeek.SUNDAY || dow == java.time.DayOfWeek.MONDAY) && all.size >= 3) {
            item(key = "week") { WeekCard(state, today) }
        }
        item(key = "sentence") { DaySentence(state, summary) }
        item(key = "checks") { ChecksCard(state, now, fmt) }
        item(key = "today-label") {
            Text("Today", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        if (todays.isEmpty()) {
            item(key = "today-empty") {
                Text(
                    if (all.isEmpty()) "Your readings will appear here. Tap Log reading to add your first one."
                    else "Nothing logged yet today.",
                    style = inter(14.sp, color = C.TextSecondary),
                    modifier = Modifier.fillMaxWidth().card().padding(18.dp),
                )
            }
        } else {
            item(key = "today-list") { ReadingsCard(state, todays.reversed()) { fmt.time(it.at) } }
        }
        if (earlier.isNotEmpty()) {
            item(key = "earlier-label") {
                Text("Earlier", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            item(key = "earlier-list") { ReadingsCard(state, earlier) { fmt.dayTime(it.at, now) } }
        }
        if (all.isNotEmpty()) {
            item(key = "history") {
                GhostButton("See all readings", Modifier.fillMaxWidth(), color = C.AccentText) { state.openHistory() }
            }
        }
        item(key = "footer") {
            Text(
                "Dropi is a log, not a medical device. Follow the plan you agreed with your care team.",
                style = inter(11.sp, color = C.TextMuted),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The latest reading, huge and centred under the aurora, with where it sits on the range bar.
 */
@Composable
private fun Hero(state: AppState, latest: Reading?, fmt: Fmt, now: Long, streak: Int) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (latest == null) {
            Spacer(Modifier.height(24.dp))
            Ic(R.drawable.lc_droplet, C.AccentText, 36.dp)
            Spacer(Modifier.height(10.dp))
            Text("No readings yet", style = heading(30.sp))
            Text("Tap + to log your first one.", style = inter(15.sp, FontWeight.Medium, C.TextSecondary))
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val status = state.status(latest)
        Text("${latest.tag.label} · ${fmt.ago(latest.at, now)}", style = inter(14.sp, FontWeight.Medium, C.TextSecondary))
        // A gentle pop each time a new number lands.
        val pop = remember { Animatable(1f) }
        LaunchedEffect(latest.id) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
        Text(
            Glucose.format(latest.mmol, state.unit),
            style = numeral(if (state.unit == app.dropi.data.GlucoseUnit.MGDL) 116.sp else 136.sp).copy(lineHeight = 140.sp),
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Text(
            "${state.unit.label} · ${status.label.lowercase()}",
            style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary),
        )
        if (streak >= 2) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.High.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_flame, C.High, 13.dp)
                Spacer(Modifier.width(4.dp))
                Text("$streak-day logging streak", style = inter(12.sp, FontWeight.SemiBold, C.High))
            }
        }
        Spacer(Modifier.height(12.dp))
        RangeRuler(latest.mmol, state.unit)
    }
}

/** Today in one sentence, with the numbers lit in aurora mint (and lows in their own colour). */
@Composable
private fun DaySentence(state: AppState, s: app.dropi.data.Summary) {
    if (s.count == 0) return
    val mint = SpanStyle(color = C.AccentText)
    val text = buildAnnotatedString {
        append(if (s.count == 1) "1 reading today" else "${s.count} readings today")
        s.average?.let {
            append(if (s.count == 1) ", " else ", averaging ")
            withStyle(mint) { append(Glucose.format(it, state.unit)) }
        }
        s.inRangePct?.let {
            append(", and ")
            withStyle(mint) { append("$it%") }
            append(" in range")
        }
        append(".")
        if (s.lows > 0) {
            append(" ")
            withStyle(SpanStyle(color = C.Low)) { append(if (s.lows == 1) "1 low." else "${s.lows} lows.") }
        }
    }
    Text(text, style = heading(21.sp).copy(lineHeight = 28.sp), modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun WeekCard(state: AppState, today: java.time.LocalDate) {
    val r = androidx.compose.runtime.remember(state.readings, today) { app.dropi.data.Analytics.report(state.readings, 7, today, state.zone) }
    if (r.count == 0) return
    Column(
        Modifier
            .fillMaxWidth()
            .card()
            .tap {
                state.trendsDays = 7
                state.tab = Tab.TRENDS
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Your week", style = SectionLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WeekStat(r.bands?.let { "${Math.round(it.inRange)}%" } ?: "—", "in range")
            WeekStat(if (r.lows == 0) "0" else "${r.lows}", if (r.lows == 1) "low" else "lows")
            WeekStat("${r.daysLogged}/7", "days logged")
        }
        Text("See this week in Trends", style = inter(12.sp, FontWeight.SemiBold, C.AccentText))
    }
}

@Composable
private fun WeekStat(value: String, label: String) {
    Column {
        Text(value, style = numeral(22.sp, C.Text))
        Text(label, style = inter(11.sp, color = C.TextMuted))
    }
}

/**
 * Today's planned checks on a line through the day: done ones filled in their range colour,
 * the due one lit, the rest dashed. Labels that would collide take turns above and below.
 */
@Composable
private fun ChecksCard(state: AppState, now: Long, fmt: Fmt) {
    val checks = Schedule.today(state.slots, state.readings, now, state.zone)
    val next = Schedule.next(state.slots, state.readings, now, state.zone)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today's checks", style = SectionLabel, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text(
                "Edit",
                style = inter(13.sp, FontWeight.SemiBold, C.AccentText),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .tap { state.scheduleOpen = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (checks.isEmpty()) {
            Text("No check times yet. Plan a few and Dropi can remind you.", style = inter(14.sp, color = C.TextSecondary), modifier = Modifier.padding(horizontal = 4.dp))
            Chip("Plan check times", icon = R.drawable.lc_clock, selected = true) { state.scheduleOpen = true }
            return@Column
        }
        DayLine(checks, now, state, fmt)
        if (next != null) {
            val mins = (next.at - now) / 60_000
            val whenText = when {
                next.state == CheckState.DUE -> "due now"
                mins < 60 -> "in $mins min"
                mins < 24 * 60 -> "in ${mins / 60} h ${if (mins % 60 >= 10) "${mins % 60} min" else ""}".trim()
                else -> "tomorrow"
            }
            Text(
                "Next: ${next.slot.name} at ${fmt.time(next.at)}, $whenText",
                style = inter(13.sp, FontWeight.Medium, if (next.state == CheckState.DUE) C.AccentText else C.TextSecondary),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (!state.remindersOn) {
            Text("Reminders are off. Tap Edit to turn them on.", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun DayLine(checks: List<Check>, now: Long, state: AppState, fmt: Fmt) {
    val zone = state.zone
    fun hourOf(at: Long): Float {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return t.hour + t.minute / 60f
    }
    val hours = checks.map { hourOf(it.at) }
    val from = minOf(6f, (hours.minOrNull() ?: 6f) - 1f).coerceAtLeast(0f)
    val to = maxOf(23f, (hours.maxOrNull() ?: 23f) + 1f).coerceAtMost(24f)
    val nowH = hourOf(now)
    val line = C.Border
    val accent = C.AccentText
    val aurora = C.Aurora
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pad = 24.dp
        val span = maxWidth - pad * 2
        fun xOf(h: Float) = pad + span * ((h - from) / (to - from)).coerceIn(0f, 1f)
        // Labels go below the line; one too close to the label before it goes above instead.
        // The line only moves down to make room when some label actually needs to be above.
        val above = run {
            var last = -1000.dp
            hours.map { h ->
                val x = xOf(h)
                val below = x - last >= 54.dp
                if (below) last = x
                !below
            }
        }
        val lineY = if (above.any { it }) 44.dp else 14.dp
        Box(Modifier.fillMaxWidth().height(lineY + 52.dp))
        // The day's line, brighter up to now.
        Canvas(Modifier.fillMaxWidth().height(lineY + 52.dp)) {
            val y = lineY.toPx()
            drawLine(line, Offset(pad.toPx(), y), Offset(size.width - pad.toPx(), y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            val nx = xOf(nowH).toPx()
            if (nx > pad.toPx()) {
                drawLine(
                    Brush.horizontalGradient(listOf(aurora[0], aurora[1]), startX = pad.toPx(), endX = nx),
                    Offset(pad.toPx(), y), Offset(nx, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        checks.forEachIndexed { i, c ->
            val x = xOf(hours[i])
            val below = !above[i]
            val done = c.reading
            val color = done?.let { C.status(state.status(it)) }
            val due = c.state == CheckState.DUE
            Box(
                Modifier
                    .offset(x = x - 22.dp, y = lineY - 22.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .tap { if (done != null) state.editing = done else state.startNew(c.slot.tag) }
                    .semantics {
                        contentDescription = "${c.slot.name} at ${fmt.time(c.at)}: " + when {
                            done != null -> "${Glucose.format(done.mmol, state.unit)}, done"
                            due -> "due now"
                            c.state == CheckState.MISSED -> "missed"
                            else -> "coming up"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    color != null -> Box(Modifier.size(18.dp).drawBehind { drawCircle(color.copy(alpha = 0.3f), radius = size.minDimension * 0.85f) }.clip(CircleShape).background(color))
                    due -> Box(Modifier.size(20.dp).clip(CircleShape).background(C.Accent).border(3.dp, C.AccentGlow.copy(alpha = 0.5f), CircleShape))
                    c.state == CheckState.MISSED -> Box(Modifier.size(12.dp).clip(CircleShape).background(C.TextMuted.copy(alpha = 0.5f)))
                    else -> Canvas(Modifier.size(18.dp)) {
                        drawCircle(
                            accent, radius = size.minDimension / 2 - 1.dp.toPx(),
                            style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                }
            }
            Column(
                Modifier.offset(x = x - 28.dp, y = if (below) lineY + 14.dp else lineY - 44.dp).width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val value = when {
                    done != null -> Glucose.format(done.mmol, state.unit)
                    due -> "due"
                    c.state == CheckState.MISSED -> "missed"
                    else -> "later"
                }
                if (below) {
                    Text(fmt.time(c.at), style = inter(11.sp, color = C.TextMuted), maxLines = 1)
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                } else {
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                    Text(fmt.time(c.at), style = inter(11.sp, color = C.TextMuted), maxLines = 1)
                }
            }
        }
    }
}

/** The range a reading is in: its colour as a dot, the words in text colour (readable in both themes). */
@Composable
fun StatusPill(status: Status) {
    val c = C.status(status)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(status.label, style = inter(12.sp, FontWeight.Bold, C.Text))
    }
}

/** A run of readings in one glass card, a hairline between each. */
@Composable
fun ReadingsCard(state: AppState, readings: List<Reading>, fill: Color = C.Card, time: (Reading) -> String) {
    Column(Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp), fill)) {
        readings.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(C.Border.copy(alpha = 0.7f)))
            ReadingRow(state, r, time(r))
        }
    }
}

/** One reading: the moment and details on the left, the number on a badge of its range colour. */
@Composable
fun ReadingRow(state: AppState, r: Reading, time: String) {
    val status = state.status(r)
    val c = C.status(status)
    Row(
        Modifier
            .fillMaxWidth()
            .tap { state.editing = r }
            .semantics(mergeDescendants = true) { contentDescription = "${r.tag.label}, $time, ${Glucose.format(r.mmol, state.unit)}, ${status.label}" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold, C.Text))
            Text(
                listOf(time, r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = inter(12.sp, color = C.TextMuted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            Glucose.format(r.mmol, state.unit),
            style = numeral(20.sp, C.onStatus(c)),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c).padding(horizontal = 12.dp, vertical = 3.dp),
        )
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
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .border(1.dp, C.Low.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Dropi crashed last time", style = inter(14.sp, FontWeight.Bold, C.Low))
        Text(
            text.lines().filter { it.isNotBlank() }.take(8).joinToString("\n"),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 10.sp,
                color = C.TextSecondary,
                lineHeight = 13.sp,
            ),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Copy details", icon = R.drawable.lc_bookmark) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Dropi crash", text))
            }
            Chip("Dismiss") { state.dismissCrash() }
        }
    }
}
