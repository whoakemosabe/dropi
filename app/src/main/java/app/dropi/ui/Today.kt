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

/** The home screen: the latest reading on its range-coloured slab, today's numbers, checks on a day line, and the log on a rail. */
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
        item(key = "checks") { ChecksCard(state, now, fmt) }
        item(key = "stats") { StatRow(state, summary) }
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
            val list = todays.reversed()
            itemsIndexed(list, key = { _, r -> "r${r.id}" }) { i, r -> ReadingRow(state, r, fmt.time(r.at), first = i == 0, last = i == list.lastIndex) }
        }
        if (earlier.isNotEmpty()) {
            item(key = "earlier-label") {
                Text("Earlier", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            itemsIndexed(earlier, key = { _, r -> "r${r.id}" }) { i, r -> ReadingRow(state, r, fmt.dayTime(r.at, now), first = i == 0, last = i == earlier.lastIndex) }
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
 * The latest reading on a slab of its own range colour: green water when in range, amber when
 * high, pink-red when low. The number is set huge on it, with a wave along the bottom edge.
 */
@Composable
private fun Hero(state: AppState, latest: Reading?, fmt: Fmt, now: Long, streak: Int) {
    val status = latest?.let { state.status(it) }
    val base = status?.let { C.status(it) }
    val ink = base?.let { C.onStatus(it) } ?: C.OnAccent
    val brush = if (base != null) {
        Brush.linearGradient(listOf(lerp(base, Color.White, 0.35f), base, lerp(base, Color.Black, 0.12f)))
    } else {
        Brush.linearGradient(listOf(C.AccentGlow, C.Accent))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(SlabShape)
            .background(brush)
            .semantics(mergeDescendants = true) {}
    ) {
        Wave(Color.White.copy(alpha = 0.2f), Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp))
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 30.dp)) {
            if (latest == null) {
                Ic(R.drawable.lc_droplet, ink, 28.dp)
                Spacer(Modifier.height(10.dp))
                Text("No readings yet", style = heading(28.sp, ink))
                Text("Tap + to log your first one.", style = inter(15.sp, FontWeight.Medium, ink.copy(alpha = 0.85f)))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(latest.tag.icon(), ink.copy(alpha = 0.8f), 15.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${latest.tag.label} · ${fmt.ago(latest.at, now)}",
                    style = inter(14.sp, FontWeight.SemiBold, ink.copy(alpha = 0.8f)),
                    modifier = Modifier.weight(1f),
                )
                if (streak >= 2) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ic(R.drawable.lc_flame, ink, 13.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("$streak days", style = inter(12.sp, FontWeight.Bold, ink))
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                // A gentle pop each time a new number lands.
                val pop = remember { Animatable(1f) }
                LaunchedEffect(latest.id) {
                    pop.snapTo(0.9f)
                    pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
                }
                Text(
                    Glucose.format(latest.mmol, state.unit),
                    style = numeral(if (state.unit == app.dropi.data.GlucoseUnit.MGDL) 104.sp else 116.sp, ink).copy(lineHeight = 112.sp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.7f)
                    },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 20.dp)) {
                    Text(state.unit.label, style = inter(15.sp, FontWeight.SemiBold, ink.copy(alpha = 0.85f)))
                    Text(status!!.label, style = inter(15.sp, FontWeight.Bold, ink))
                }
            }
        }
    }
}

/** The hero's shape: round, with one squarer corner where the wave runs out. */
val SlabShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 30.dp, bottomStart = 30.dp)

/** A soft wave, like the edge of water. */
@Composable
fun Wave(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val step = w / 6
        val p = Path().apply {
            moveTo(0f, h * 0.5f)
            var x = 0f
            var up = true
            while (x < w) {
                quadraticTo(x + step / 2, if (up) 0f else h, x + step, h * 0.5f)
                x += step
                up = !up
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(p, color)
    }
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
    BoxWithConstraints(Modifier.fillMaxWidth().height(92.dp)) {
        val pad = 24.dp
        val span = maxWidth - pad * 2
        fun xOf(h: Float) = pad + span * ((h - from) / (to - from)).coerceIn(0f, 1f)
        val lineY = 40.dp
        // The day's line, brighter up to now.
        Canvas(Modifier.fillMaxSize()) {
            val y = lineY.toPx()
            drawLine(line, Offset(pad.toPx(), y), Offset(size.width - pad.toPx(), y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            val nx = xOf(nowH).toPx()
            drawLine(accent.copy(alpha = 0.5f), Offset(pad.toPx(), y), Offset(nx, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        // Labels below the line, unless the one before is too close: then above.
        var lastBelow = -1000.dp
        checks.forEachIndexed { i, c ->
            val x = xOf(hours[i])
            val below = x - lastBelow >= 54.dp
            if (below) lastBelow = x
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
                    color != null -> Box(Modifier.size(18.dp).clip(CircleShape).background(color))
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
                Modifier.offset(x = x - 28.dp, y = if (below) lineY + 16.dp else 0.dp).width(56.dp),
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

/** Today's numbers in a line of three, divided by hairlines rather than boxed in cards. */
@Composable
private fun StatRow(state: AppState, s: app.dropi.data.Summary) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Stat(s.average?.let { Glucose.format(it, state.unit) } ?: "—", "average today", Modifier.weight(1f))
        Hairline()
        Stat(s.inRangePct?.let { "$it%" } ?: "—", "in range", Modifier.weight(1f).padding(start = 14.dp))
        Hairline()
        Stat(
            "${s.count}", if (s.lows > 0) "${s.lows} low" else if (s.count == 1) "reading" else "readings",
            Modifier.weight(1f).padding(start = 14.dp), subColor = if (s.lows > 0) C.Low else null,
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(C.Border))
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, subColor: Color? = null) {
    Column(modifier) {
        Text(value, style = numeral(30.sp), maxLines = 1)
        Text(label, style = inter(12.sp, FontWeight.Medium, subColor ?: C.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A reading on the day's rail: time on the left, a dot in its range colour on the line, then
 * the moment and details, and the number. [first] and [last] end the line at the dot.
 */
@Composable
fun ReadingRow(
    state: AppState, r: Reading, time: String, first: Boolean = false, last: Boolean = false,
    /** Half the list's spacing, so the rail meets the next row's. */
    halfGap: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val status = state.status(r)
    val c = C.status(status)
    val rail = C.Border
    val bg = C.Bg
    Row(
        Modifier
            .fillMaxWidth()
            .tap { state.editing = r }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = inter(13.sp, FontWeight.Medium, C.TextMuted), modifier = Modifier.width(52.dp), maxLines = 2)
        Box(
            Modifier
                .width(22.dp)
                .height(40.dp)
                .drawBehind {
                    // The rail runs on through the row's padding and the list's gap to the next row.
                    val x = size.width / 2
                    val gap = (8.dp + halfGap).toPx()
                    val top = if (first) size.height / 2 else -gap
                    val bottom = if (last) size.height / 2 else size.height + gap
                    drawLine(rail, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                    drawCircle(bg, radius = 9.dp.toPx(), center = Offset(x, size.height / 2))
                    drawCircle(c, radius = 6.dp.toPx(), center = Offset(x, size.height / 2))
                },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold, C.Text))
            val sub = listOf(r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = inter(12.sp, color = C.TextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Glucose.format(r.mmol, state.unit), style = numeral(26.sp))
            Text(status.label, style = inter(11.sp, FontWeight.Medium, C.TextMuted))
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
