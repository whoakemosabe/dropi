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
    val summary = Stats.summary(todays)
    val streak = Stats.streak(all, today, zone)
    val latest = all.lastOrNull()

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
        // Meals: one line here; the Meals page has the full cards.
        if (meals.isNotEmpty()) item(key = "meals") { MealsLine(state, meals, fmt) }
        item(key = "today-label") {
            Text(tr("Today"), style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        if (todays.isEmpty()) {
            item(key = "today-empty") {
                Text(
                    if (all.isEmpty()) tr("Your readings will appear here. Tap + to add your first one.")
                    else tr("Nothing logged yet today."),
                    style = inter(15.sp, color = C.TextSecondary),
                    modifier = Modifier.fillMaxWidth().card().padding(16.dp),
                )
            }
        } else {
            item(key = "today-list") { ReadingsCard(state, todays.reversed()) { fmt.time(it.at) } }
        }
        if (all.isNotEmpty()) {
            item(key = "history") {
                GhostButton(tr("See all readings"), Modifier.fillMaxWidth(), color = C.AccentText) { state.openHistory() }
            }
        }
        item(key = "footer") {
            Text(
                tr("Dropi is a log, not a medical device. Follow the plan you agreed with your care team."),
                style = inter(13.sp, color = C.TextMuted),
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
            Spacer(Modifier.height(12.dp))
            Text(tr("No readings yet"), style = heading(30.sp))
            Text(tr("Tap + to log your first one."), style = inter(15.sp, FontWeight.Medium, C.TextSecondary))
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val status = state.status(latest)
        Text("${latest.tag.label} · ${fmt.ago(latest.at, now)}", style = inter(15.sp, FontWeight.Medium, C.TextSecondary))
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
            "${state.unit.label} · ${status.label.lowercase(L.locale)}",
            style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary),
        )
        if (streak >= 2) {
            // Never 1 here; the singular form is for 21, 31… in Icelandic.
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.High.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_flame, C.High, 13.dp)
                Spacer(Modifier.width(4.dp))
                Text(trn(streak, "{0} day logging streak", "{0}-day logging streak"), style = inter(13.sp, FontWeight.SemiBold, C.High))
            }
        }
        Spacer(Modifier.height(12.dp))
        RangeRuler(latest.mmol, state.unit)
    }
}

/**
 * Appends [text], lighting each of [values] in [style] where it first appears (in whatever order
 * the translation puts them).
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendLit(text: String, values: List<String>, style: SpanStyle) {
    var rest = text
    var left = values.filter { it.isNotEmpty() }
    while (true) {
        val hit = left.map { it to rest.indexOf(it) }.filter { it.second >= 0 }.minByOrNull { it.second } ?: break
        append(rest.substring(0, hit.second))
        withStyle(style) { append(hit.first) }
        rest = rest.substring(hit.second + hit.first.length)
        left = left - hit.first
    }
    append(rest)
}

/** Today in one sentence, with the numbers lit in aurora mint (and lows in their own colour). */
@Composable
private fun DaySentence(state: AppState, s: app.dropi.data.Summary) {
    if (s.count == 0) return
    val mint = SpanStyle(color = C.Text, fontWeight = FontWeight.ExtraBold)
    // Whole sentences per case (so Icelandic word order works); the values are lit where they land.
    val avg = s.average?.let { Glucose.format(it, state.unit) }
    val pct = s.inRangePct?.let { "$it%" }
    // One reading has no average to speak of; the singular forms are still used for 21, 31… in Icelandic.
    val sentence = when {
        s.count == 1 && avg != null && pct != null -> tr("1 reading today, {0}, and {1} in range.", avg, pct)
        s.count == 1 && avg != null -> tr("1 reading today, {0}.", avg)
        avg != null && pct != null -> trn(s.count, "{0} reading today, averaging {1}, and {2} in range.", "{0} readings today, averaging {1}, and {2} in range.", avg, pct)
        avg != null -> trn(s.count, "{0} reading today, averaging {1}.", "{0} readings today, averaging {1}.", avg)
        else -> trn(s.count, "{0} reading today.", "{0} readings today.")
    }
    val lit = listOfNotNull(avg, pct)
    val text = buildAnnotatedString {
        appendLit(sentence, lit, mint)
        if (s.lows > 0) {
            append(" ")
            withStyle(SpanStyle(color = C.Low)) { append(trn(s.lows, "{0} low.", "{0} lows.")) }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Your week"), style = SectionLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeekStat(r.bands?.let { "${Math.round(it.inRange)}%" } ?: "—", tr("in range"))
            WeekStat("${r.lows}", trn(r.lows, "low reading", "low readings"))
            WeekStat("${r.daysLogged}/7", tr("days logged"))
        }
        Text(tr("See this week in Trends"), style = inter(13.sp, FontWeight.SemiBold, C.AccentText))
    }
}

@Composable
private fun WeekStat(value: String, label: String) {
    Column {
        Text(value, style = numeral(22.sp, C.Text))
        Text(label, style = inter(13.sp, color = C.TextMuted))
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
            Text(tr("Today's checks"), style = SectionLabel, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text(
                tr("Edit"),
                style = inter(13.sp, FontWeight.SemiBold, C.AccentText),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .tap { state.scheduleOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (checks.isEmpty()) {
            Text(tr("No check times yet. Plan a few and Dropi can remind you."), style = inter(15.sp, color = C.TextSecondary), modifier = Modifier.padding(horizontal = 4.dp))
            Chip(tr("Plan check times"), icon = R.drawable.lc_clock, selected = true) { state.scheduleOpen = true }
            return@Column
        }
        DayLine(checks, now, state, fmt)
        // Learnt from the last two weeks: a check usually done at another time.
        if (move != null) {
            val clock = { m: Int -> fmt.time(java.time.LocalDate.now(state.zone).atStartOfDay(state.zone).plusMinutes(m.toLong()).toInstant().toEpochMilli()) }
            Column(
                Modifier.fillMaxWidth().card().padding(16.dp).semantics(mergeDescendants = false) {},
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr("You usually do your {0} check at about {1} ({2} of the last 14 days). Move it there?", move.slot.name.lowercase(L.locale), clock(move.minute), move.days),
                    style = inter(15.sp, FontWeight.Medium).copy(lineHeight = 20.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(tr("Move to {0}", clock(move.minute)), icon = R.drawable.lc_clock, selected = true) { state.acceptMove(move) }
                    Chip(tr("Keep {0}", clock(move.slot.minute))) { state.dismissMove(move) }
                }
            }
        }
        if (next != null) {
            val mins = (next.at - now) / 60_000
            val whenText = when {
                next.state == CheckState.DUE -> tr("due until {0}", fmt.time(next.until))
                mins < 60 -> tr("in {0} min", mins)
                mins < 24 * 60 -> if (mins % 60 >= 10) tr("in {0} h {1} min", mins / 60, mins % 60) else tr("in {0} h", mins / 60)
                else -> tr("tomorrow")
            }
            val why = next.meal?.let { afterYourMealText(it.name, fmt.time(it.at)) }
                ?: next.anchor?.let { tr("{0} h after your {1} reading", state.afterMealMinutes / 60, fmt.time(it.at)) }
            Text(
                if (why != null) tr("Next: {0} at {1} ({2}), {3}", next.slot.name, fmt.time(next.at), why, whenText)
                else tr("Next: {0} at {1}, {2}", next.slot.name, fmt.time(next.at), whenText),
                style = inter(13.sp, FontWeight.Medium, if (next.state == CheckState.DUE) C.AccentText else C.TextSecondary),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (!state.remindersOn) {
            Text(tr("Reminders are off. Tap Edit to turn them on."), style = inter(13.sp, color = C.TextMuted), modifier = Modifier.padding(horizontal = 4.dp))
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
                    .semantics { contentDescription = tr("Extra reading at {0}: {1}", fmt.time(r.at), Glucose.format(r.mmol, state.unit)) },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(10.dp).clip(CircleShape).background(col)) }
        }
        // Meals sit on the line as small squares at the time they were eaten. They're marks, not
        // buttons (the meals card below opens them), so they never take a tap meant for a check.
        todaysMeals.forEach { m ->
            val x = xOf(hourOf(m.at))
            Box(
                Modifier
                    .offset(x = x - 6.dp, y = lineY - 6.dp + 9.dp)
                    .size(12.dp)
                    .semantics { contentDescription = tr("{0} at {1}: {2}", mealName(m.name), fmt.time(m.at), m.foods.joinToString(", ")) },
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
                        val how = when {
                            done != null -> tr("{0}, done at {1}", Glucose.format(done.mmol, state.unit), fmt.time(done.at))
                            due -> tr("due now")
                            c.state == CheckState.MISSED -> tr("missed")
                            else -> tr("coming up")
                        }
                        contentDescription = tr("{0} at {1}: {2}", c.slot.name, fmt.time(c.at), how)
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
                    due -> tr("due")
                    c.state == CheckState.MISSED -> tr("missed")
                    else -> tr("later")
                }
                val shownAt = done?.at ?: c.at
                if (below) {
                    Text(fmt.time(shownAt), style = inter(12.sp, color = C.TextMuted), maxLines = 1)
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                } else {
                    Text(value, style = inter(13.sp, FontWeight.Bold, if (due) C.AccentText else C.Text), maxLines = 1)
                    Text(fmt.time(shownAt), style = inter(12.sp, color = C.TextMuted), maxLines = 1)
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
    val out = state.outcome(m)
    val rise = out?.let { app.dropi.data.Rise.of(it.rise) }
    val before = app.dropi.data.MealLink.before(m, state.readings)
    val foods = m.meal.items.joinToString(", ") { app.dropi.data.Foods.display(it.name) }
    val time = if (dayTimes) fmt.dayTime(m.at, now) else fmt.time(m.at)
    val name = mealName(m.name)
    val from = before?.let { tr("Before: your {0} reading ({1})", fmt.time(it.at), Glucose.format(it.mmol, state.unit)) }
        ?: tr("No reading before it, so no rise to show")
    val gap = app.dropi.data.Schedule.gapFor(m, state.afterMealMinutes)
    val result = when {
        out != null -> tr("Peaked {0} ({1}) at {2}", Glucose.format(out.peak, state.unit), app.dropi.data.Foods.delta(out.rise, state.unit), fmt.time(out.peakAt))
        before == null -> null
        now - m.at < 4 * 3_600_000L -> tr("After-eating check at about {0}", fmt.time(m.at + gap * 60_000L))
        else -> tr("No after-eating reading")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = tr("{0} at {1}: {2}, {3} grams of carbs.", name, time, foods, app.dropi.data.Grams.text(m.carbs)) + " $from." +
                    (result?.let { " $it" } ?: "") + (rise?.let { ", ${riseWord(it).lowercase(L.locale)}" } ?: "")
            }
            .tap { app.dropi.work.MealWork.edit(m) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(Corner.S)).background(C.SurfaceLight), contentAlignment = Alignment.Center) {
            Ic(R.drawable.lc_utensils, C.AccentText, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$name · $time · " + tr("{0} g carbs", app.dropi.data.Grams.text(m.carbs)), style = inter(15.sp, FontWeight.SemiBold))
            Text(foods, style = inter(13.sp, color = C.TextSecondary), maxLines = 2)
            Text(from, style = inter(13.sp, color = C.TextMuted))
            if (result != null) Text(result, style = inter(13.sp, color = C.TextMuted))
        }
        if (rise != null && out != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(app.dropi.data.Foods.delta(out.rise, state.unit), style = numeral(18.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(riseColor(rise)))
                    Spacer(Modifier.width(4.dp))
                    Text(riseShort(rise), style = inter(13.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

/** A rise in words ("Gentle rise"), in the app's language. Rise.word stays English. */
private fun riseWord(r: app.dropi.data.Rise): String = when (r) {
    app.dropi.data.Rise.GENTLE -> tr("Gentle rise")
    app.dropi.data.Rise.MODERATE -> tr("Moderate rise")
    app.dropi.data.Rise.BIG -> tr("Big rise")
}

/** The short word under a meal's rise ("Gentle"). */
private fun riseShort(r: app.dropi.data.Rise): String = when (r) {
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
            .clip(RoundedCornerShape(Corner.M))
            .background(C.High.copy(alpha = 0.14f))
            .border(1.dp, C.High.copy(alpha = 0.6f), RoundedCornerShape(Corner.M))
            .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Demo data"), style = inter(15.sp, FontWeight.Bold))
            Text(tr("Made-up readings. Your real log is safe."), style = inter(13.sp, color = C.TextSecondary))
        }
        Text(
            tr("Leave"),
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(status.label, style = inter(13.sp, FontWeight.Bold, C.Text))
    }
}

/** A run of readings in one glass card, a hairline between each. */
@Composable
fun ReadingsCard(state: AppState, readings: List<Reading>, fill: Color = C.Card, time: (Reading) -> String) {
    Column(Modifier.fillMaxWidth().card(RoundedCornerShape(Corner.L), fill)) {
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
                contentDescription = listOfNotNull(r.tag.label, time, link, Glucose.format(r.mmol, state.unit), status.label).joinToString(", ")
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.tag.label, style = inter(15.sp, FontWeight.SemiBold, C.Text))
            Text(
                listOf(time, link.orEmpty(), r.detailsText(), r.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = inter(13.sp, color = C.TextMuted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            Glucose.format(r.mmol, state.unit),
            style = numeral(20.sp, C.onStatus(c)),
            modifier = Modifier.clip(RoundedCornerShape(Corner.S)).background(c).padding(horizontal = 12.dp, vertical = 4.dp),
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
            .clip(RoundedCornerShape(Corner.M))
            .background(C.Card)
            .border(1.dp, C.Low.copy(alpha = 0.4f), RoundedCornerShape(Corner.M))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Dropi crashed last time"), style = inter(15.sp, FontWeight.Bold, C.Low))
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
            Chip(tr("Copy details"), icon = R.drawable.lc_bookmark) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Dropi crash", text))
            }
            Chip(tr("Dismiss")) { state.dismissCrash() }
        }
    }
}


/** Today's meals in one tappable line ("Breakfast 08:10 · 45 g  ·  Lunch 12:30 · 60 g"), opening the Meals page. */
@Composable
private fun MealsLine(state: AppState, meals: List<app.dropi.data.MealEntry>, fmt: Fmt) {
    val text = meals.sortedBy { it.at }.joinToString("  ·  ") { "${mealName(it.name)} ${fmt.time(it.at)} · ${app.dropi.data.Grams.text(it.meal.carbs)} g" }
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = tr("Meals today: {0}. Opens Meals.", text) }
            .tap { state.tab = Tab.MEALS }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_utensils, C.AccentText, 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(15.sp, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(8.dp))
        Ic(R.drawable.lc_arrow_right, C.TextMuted, 16.dp)
    }
}
