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
                if (state.demo) DemoBanner(state)
                AiBanner(state)
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
        val meals = state.meals.filter { Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate() == today }
        if (meals.isNotEmpty()) item(key = "meals") { MealsCard(state, meals, now, fmt, "Meals today") }
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
    val mint = SpanStyle(color = C.Text, fontWeight = FontWeight.ExtraBold)
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
    Text(text, style = heading(21.sp, C.TextSecondary).copy(lineHeight = 28.sp), modifier = Modifier.padding(horizontal = 4.dp))
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
    val checks = Schedule.today(state.slots, state.readings, now, state.zone, state.afterMealMinutes, state.meals)
    val next = Schedule.next(state.slots, state.readings, now, state.zone, state.afterMealMinutes, state.meals)
    val move = remember(state.readings, state.slots, state.dismissedMoves) { state.moveSuggestion(now) }
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
        // Learnt from the last two weeks: a check usually done at another time.
        if (move != null) {
            val clock = { m: Int -> fmt.time(java.time.LocalDate.now(state.zone).atStartOfDay(state.zone).plusMinutes(m.toLong()).toInstant().toEpochMilli()) }
            Column(
                Modifier.fillMaxWidth().card().padding(14.dp).semantics(mergeDescendants = false) {},
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "You usually do your ${move.slot.name.lowercase()} check at about ${clock(move.minute)} (${move.days} of the last 14 days). Move it there?",
                    style = inter(14.sp, FontWeight.Medium).copy(lineHeight = 20.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Move to ${clock(move.minute)}", icon = R.drawable.lc_clock, selected = true) { state.acceptMove(move) }
                    Chip("Keep ${clock(move.slot.minute)}") { state.dismissMove(move) }
                }
            }
        }
        if (next != null) {
            val mins = (next.at - now) / 60_000
            val whenText = when {
                next.state == CheckState.DUE -> "due until ${fmt.time(next.until)}"
                mins < 60 -> "in $mins min"
                mins < 24 * 60 -> "in ${mins / 60} h ${if (mins % 60 >= 10) "${mins % 60} min" else ""}".trim()
                else -> "tomorrow"
            }
            Text(
                "Next: ${next.slot.name} at ${fmt.time(next.at)}" + (next.meal?.let { " (after your ${it.name.lowercase()} at ${fmt.time(it.at)})" }
                    ?: next.anchor?.let { " (${state.afterMealMinutes / 60} h after your ${fmt.time(it.at)} reading)" } ?: "") + ", $whenText",
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
    // A done check sits where the reading actually was, at its exact time; the rest at the plan.
    val hours = checks.map { hourOf(it.reading?.at ?: it.at) }
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
        val day0 = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todaysMeals = state.meals.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == day0 }
        // Each meal is joined to the reading before it and the one after it.
        val links = todaysMeals.map { m ->
            val before = app.dropi.data.MealLink.before(m, state.readings)
            val after = state.readings.filter { it.at - m.at in 45 * 60_000L..240 * 60_000L }.maxByOrNull { it.mmol }
            Triple(m, before, after)
        }
        // The day's line, brighter up to now.
        Canvas(Modifier.fillMaxWidth().height(lineY + 52.dp)) {
            val y = lineY.toPx()
            drawLine(line, Offset(pad.toPx(), y), Offset(size.width - pad.toPx(), y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            // Each open check's window: from its time until it would count as missed.
            checks.forEach { c ->
                if (c.reading != null) return@forEach
                val x0 = xOf(hourOf(c.at)).toPx()
                val x1 = xOf(hourOf(c.until)).toPx()
                if (x1 > x0) drawLine(accent.copy(alpha = 0.28f), Offset(x0, y), Offset(x1, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            }
            // Meal links: a soft bar from the reading before, through the meal, to the one after.
            links.forEach { (m, b, a) ->
                val xs = listOfNotNull(b?.at, m.at, a?.at).map { xOf(hourOf(it)).toPx() }
                if (xs.size > 1) drawLine(
                    accent.copy(alpha = 0.45f), Offset(xs.min(), y + 9.dp.toPx()), Offset(xs.max(), y + 9.dp.toPx()),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            val nx = xOf(nowH).toPx()
            if (nx > pad.toPx()) {
                drawLine(
                    Brush.horizontalGradient(listOf(aurora[0], aurora[1]), startX = pad.toPx(), endX = nx),
                    Offset(pad.toPx(), y), Offset(nx, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // Readings that answered no check still sit on the line, at their exact time.
        val answered = checks.mapNotNull { it.reading }.toSet()
        state.readings.filter { it.date(zone) == day0 && it !in answered }.forEach { r ->
            val x = xOf(hourOf(r.at))
            val col = C.status(state.status(r))
            Box(
                Modifier
                    .offset(x = x - 22.dp, y = lineY - 22.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .tap { state.editing = r }
                    .semantics { contentDescription = "Extra reading at ${fmt.time(r.at)}: ${Glucose.format(r.mmol, state.unit)}" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(10.dp).clip(CircleShape).background(col)) }
        }
        // Meals sit on the line as small squares at the time they were eaten.
        todaysMeals.forEach { m ->
            val x = xOf(hourOf(m.at))
            Box(
                Modifier
                    .offset(x = x - 22.dp, y = lineY - 22.dp + 9.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .tap { app.dropi.work.MealWork.edit(m) }
                    .semantics { contentDescription = "${m.name} at ${fmt.time(m.at)}: ${m.foods.joinToString(", ")}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(C.AccentText)
                        .border(2.dp, C.Bg, RoundedCornerShape(3.dp)),
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
                            done != null -> "${Glucose.format(done.mmol, state.unit)}, done at ${fmt.time(done.at)}"
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
                val shownAt = done?.at ?: c.at
                if (below) {
                    Text(fmt.time(shownAt), style = inter(11.sp, color = C.TextMuted), maxLines = 1)
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                } else {
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                    Text(fmt.time(shownAt), style = inter(11.sp, color = C.TextMuted), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Meals: name, time and carbs, the foods, where the starting number came from, and how it went
 * (the highest reading 45 min–3 h after eating, compared with the start). Tap to change one.
 */
@Composable
fun MealsCard(state: AppState, meals: List<app.dropi.data.MealEntry>, now: Long, fmt: Fmt, title: String?, dayTimes: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = SectionLabel, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().card()) {
            meals.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(C.GlassBorder))
                MealRow(state, m, now, fmt, dayTimes)
            }
        }
    }
}

@Composable
private fun MealRow(state: AppState, m: app.dropi.data.MealEntry, now: Long, fmt: Fmt, dayTimes: Boolean) {
    val out = app.dropi.data.MealHistory.outcome(m, state.readings, state.sensor, state.meals)
    val rise = out?.let { app.dropi.data.Rise.of(it.rise) }
    val before = app.dropi.data.MealLink.before(m, state.readings)
    val foods = m.meal.items.joinToString(", ") { app.dropi.data.Foods.display(it.name) }
    val time = if (dayTimes) fmt.dayTime(m.at, now) else fmt.time(m.at)
    val from = before?.let { "Before: your ${fmt.time(it.at)} reading (${Glucose.format(it.mmol, state.unit)})" }
        ?: "No reading before it, so no rise to show"
    val gap = app.dropi.data.Schedule.gapFor(m, state.afterMealMinutes)
    val result = when {
        out != null -> "Peaked ${Glucose.format(out.peak, state.unit)} (${app.dropi.data.Foods.delta(out.rise, state.unit)}) at ${fmt.time(out.peakAt)}"
        before == null -> null
        now - m.at < 4 * 3_600_000L -> "After-eating check at about ${fmt.time(m.at + gap * 60_000L)}"
        else -> "No after-eating reading"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${m.name} at $time: $foods, ${m.carbs} grams of carbs. $from." +
                    (result?.let { " $it" } ?: "") + (rise?.let { ", ${it.word.lowercase()}" } ?: "")
            }
            .tap { app.dropi.work.MealWork.edit(m) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.SurfaceLight), contentAlignment = Alignment.Center) {
            Ic(R.drawable.lc_utensils, C.AccentText, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${m.name} · $time · ${m.carbs} g carbs", style = inter(15.sp, FontWeight.SemiBold))
            Text(foods, style = inter(13.sp, color = C.TextSecondary), maxLines = 2)
            Text(from, style = inter(12.sp, color = C.TextMuted))
            if (result != null) Text(result, style = inter(12.sp, color = C.TextMuted))
        }
        if (rise != null && out != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(app.dropi.data.Foods.delta(out.rise, state.unit), style = numeral(18.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(riseColor(rise)))
                    Spacer(Modifier.width(5.dp))
                    Text(rise.word.removeSuffix(" rise"), style = inter(12.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

/** While demo data is on: says so plainly, with the way back. */
@Composable
private fun DemoBanner(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.High.copy(alpha = 0.14f))
            .border(1.dp, C.High.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Demo data", style = inter(14.sp, FontWeight.Bold))
            Text("Made-up readings. Your real log is safe.", style = inter(12.sp, color = C.TextSecondary))
        }
        Text(
            "Leave",
            style = inter(13.sp, FontWeight.Bold, C.Text),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, C.Border, RoundedCornerShape(50))
                .tap { state.leaveDemo() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
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
    // "2 h after breakfast": which meal this reading belongs to.
    val link = remember(r, state.meals, state.readings) { state.linkText(r) }
    val c = C.status(status)
    Row(
        Modifier
            .fillMaxWidth()
            .tap { state.editing = r }
            .semantics(mergeDescendants = true) {
                contentDescription = "${r.tag.label}, $time" + (link?.let { ", $it" } ?: "") + ", ${Glucose.format(r.mmol, state.unit)}, ${status.label}"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold, C.Text))
            Text(
                listOf(time, link.orEmpty(), r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · "),
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
