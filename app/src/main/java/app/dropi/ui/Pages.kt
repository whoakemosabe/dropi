package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.R
import app.dropi.data.DayTotals
import app.dropi.data.Foods
import app.dropi.data.Glucose
import app.dropi.data.Grams
import app.dropi.data.MealEntry
import app.dropi.data.MealHistory
import app.dropi.data.MealItem
import app.dropi.data.MealLink
import app.dropi.data.Rise
import app.dropi.data.Schedule
import app.dropi.tr
import app.dropi.trn
import app.dropi.work.Ai
import app.dropi.work.MealWork
import java.time.Instant
import java.time.LocalDate

/**
 * Meals: ways to log one, what the person's meals show, today's meals as receipts, how foods move
 * their numbers, and every meal of the last two weeks by day.
 */
@Composable
fun MealsPage(state: AppState) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val today = LocalDate.now(state.zone)
    val byDay = remember(state.meals, today) {
        state.meals.filter { !Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate().isBefore(today.minusDays(13)) }
            .groupBy { Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    val todays = byDay[today].orEmpty().sortedByDescending { it.at }
    val dayName = remember { dayPattern("EEEE d MMMM") }

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
    ) {
        item(key = "draft") { MealDraftCard() }
        item(key = "log") {
            // The camera first and wider, then the other four ways in, all in one row.
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CaptureButton(tr("Snap a meal"), R.drawable.lc_camera, Modifier.weight(1.7f), filled = true) { Launch.mealAction.value = "camera" }
                CaptureButton(tr("Say it"), R.drawable.lc_mic, Modifier.weight(1f)) { Launch.mealAction.value = "words" }
                CaptureButton(tr("Barcode"), R.drawable.lc_scan_barcode, Modifier.weight(1f)) { Launch.mealAction.value = "barcode" }
                CaptureButton(tr("Label"), R.drawable.lc_scan_text, Modifier.weight(1f)) { Launch.mealAction.value = "label" }
                CaptureButton(tr("Type it"), R.drawable.lc_pencil, Modifier.weight(1f)) { MealWork.manual() }
            }
        }
        item(key = "patterns") { PatternNotes(state) }
        if (byDay.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("No meals yet. Log one with a photo, in words or from a label. It's saved at the time you ate, and joined to the readings before and after it."),
                    style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        } else {
            item(key = "today") {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    if (todays.isEmpty()) {
                        RuleHeader(tr("Today"))
                        Text(tr("Nothing logged yet today."), style = mono(12.sp, color = C.TextMuted))
                    } else {
                        RuleHeader(tr("Today"), trn(todays.size, "{0} meal", "{0} meals"))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            todays.forEach { m -> Receipt(state, m, now, fmt) }
                        }
                    }
                }
            }
        }
        item(key = "totals") { TotalsLines(state, today, todays) }
        item(key = "plate") { FoodsCard(state) }
        byDay.forEach { (day, meals) ->
            if (day == today) return@forEach
            item(key = "day-$day") {
                val title = if (day == today.minusDays(1)) tr("Yesterday") else dayName.format(day).cap()
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    RuleHeader(title, tr("{0} g", Grams.text(meals.sumOf { it.carbs })))
                    meals.sortedByDescending { it.at }.forEach { m -> Slip(state, m, now, fmt) }
                    Text(
                        DayTotals.text(DayTotals.of(day, meals)),
                        style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                        modifier = Modifier.ruleTop().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** One way to log a meal: an icon over a one- or two-word name, filled for the main one. */
@Composable
private fun CaptureButton(title: String, icon: Int, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(12.dp)
    val ink = if (filled) C.OnAccent else C.Text
    Column(
        modifier
            .pressed(press)
            .fillMaxHeight()
            .heightIn(min = 60.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(C.Accent).border(1.dp, C.AccentText.copy(alpha = 0.45f), shape) else Modifier.border(1.dp, C.Border, shape))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Ic(icon, ink, 20.dp)
        // One line that shrinks a little when a word is long ("Strikamerki" on a narrow phone).
        androidx.compose.foundation.text.BasicText(
            title,
            style = inter(12.sp, FontWeight.SemiBold, ink).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
        )
    }
}

/** What the person's own meals show, as short notes, once there's enough to say it. */
@Composable
private fun PatternNotes(state: AppState) {
    val patterns = remember(state.measured, state.readings, state.meals, state.unit) {
        app.dropi.data.MealPatterns.fromMeasured(state.measured, state.readings, state.meals, state.unit)
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (patterns.isEmpty()) {
            Note(tr("After a few weeks of meals with readings before and after, this shows what makes a difference for you: fibre, fat and protein, fast or slow carbs, a walk after, veg first, sugary drinks and alcohol."))
        }
        patterns.forEach { p -> Note(p.text, if (p.good) C.InRange else C.TextMuted) }
        if (patterns.isNotEmpty()) {
            Text(tr("From your own log. Patterns, not advice."), style = mono(12.sp, color = C.TextMuted))
        }
    }
}

/** "NOTE  When you walk after eating…": a mono label and one sentence. */
@Composable
private fun Note(text: String, label: Color = C.TextMuted) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(caps(tr("Note")), style = mono(12.sp, FontWeight.Medium, label, 0.08f), modifier = Modifier.padding(top = 1.dp))
        Text(text, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp), modifier = Modifier.weight(1f))
    }
}

/** A meal's outcome, the line under it, and what TalkBack says for it (the same as on Today). */
private class MealInfo(val out: MealHistory.Outcome?, val rise: Rise?, val line: String, val description: String)

private fun mealInfo(state: AppState, m: MealEntry, now: Long, fmt: Fmt): MealInfo {
    val out = state.outcome(m)
    val rise = out?.let { Rise.of(it.rise) }
    val before = MealLink.before(m, state.readings)
    val foods = m.meal.items.joinToString(", ") { Foods.display(it.name) }
    val time = fmt.time(m.at)
    val name = mealName(m.name)
    val from = before?.let { tr("Before: your {0} reading ({1})", fmt.time(it.at), Glucose.format(it.mmol, state.unit)) }
        ?: tr("No reading before it, so no rise to show")
    val gap = Schedule.gapFor(m, state.afterMealMinutes)
    val result = when {
        out != null -> tr("Peaked {0} ({1}) at {2}", Glucose.format(out.peak, state.unit), Foods.delta(out.rise, state.unit), fmt.time(out.peakAt))
        before == null -> null
        now - m.at < 4 * 3_600_000L -> tr("After-eating check at about {0}", fmt.time(m.at + gap * 60_000L))
        else -> tr("No after-eating reading")
    }
    val description = tr("{0} at {1}: {2}, {3} grams of carbs.", name, time, foods, Grams.text(m.carbs)) + " $from." +
        (result?.let { " $it" } ?: "") + (rise?.let { ", ${it.word.lowercase(L.locale)}" } ?: "")
    return MealInfo(out, rise, result ?: from, description)
}

/** The rise colours: gentle in range, moderate high, big very high (always with the word). */
@Composable
private fun riseInk(r: Rise): Color = when (r) {
    Rise.GENTLE -> C.InRange
    Rise.MODERATE -> C.High
    Rise.BIG -> C.VeryHigh
}

/**
 * Today's meal as a receipt: name and time, each food with its carbs and amount, a torn line,
 * then the carbs and, once the readings after it are in, a stamp with the rise.
 */
@Composable
private fun Receipt(state: AppState, m: MealEntry, now: Long, fmt: Fmt) {
    val info = mealInfo(state, m, now, fmt)
    val paper = C.Card
    val edge = C.Border.copy(alpha = 0.7f)
    val label = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.08f)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val s = 1.dp.toPx()
                val corner = CornerRadius(3.dp.toPx())
                drawRoundRect(paper, cornerRadius = corner)
                drawRoundRect(edge, topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s), cornerRadius = corner, style = Stroke(s))
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = info.description
            }
            .tap { MealWork.edit(m) }
            .padding(top = 14.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            Text(caps(mealName(m.name)), style = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(fmt.time(m.at), style = label, maxLines = 1)
        }
        m.meal.items.forEach { item -> ReceiptItem(item) }
        TearLine(Modifier.padding(top = 4.dp))
        val rise = info.rise
        val out = info.out
        if (rise != null && out != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(caps(tr("Carbs")), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f))
                    Text(tr("{0} g", Grams.text(m.carbs)), style = numeral(24.sp))
                }
                Stamp(rise.short + "\n" + Foods.delta(out.rise, state.unit), riseInk(rise), big = true)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                Text(caps(tr("Carbs")), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f), modifier = Modifier.weight(1f).alignByBaseline())
                Text(tr("{0} g", Grams.text(m.carbs)), style = numeral(24.sp), modifier = Modifier.alignByBaseline())
            }
        }
        Text(
            info.line,
            style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
    }
}

/** "Chicken wrap ······ 38 g" with its amount under it. */
@Composable
private fun ReceiptItem(item: MealItem) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val dots = C.Border
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            Foods.display(item.name),
            style = inter(14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier
                .weight(1f)
                .alignBy(LastBaseline)
                .drawBehind {
                    // The dotted leader, from the end of the name to the grams.
                    val l = layout ?: return@drawBehind
                    val last = l.lineCount - 1
                    if (last < 0) return@drawBehind
                    val start = l.getLineRight(last) + 8.dp.toPx()
                    val end = size.width - 8.dp.toPx()
                    if (end - start < 4.dp.toPx()) return@drawBehind
                    val y = l.getLineBottom(last) - 6.dp.toPx()
                    drawLine(
                        dots, Offset(start, y), Offset(end, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.dp.toPx())),
                    )
                },
        )
        Text(tr("{0} g", Grams.text(item.carbs)), style = mono(13.sp, FontWeight.SemiBold, C.Text), modifier = Modifier.alignBy(LastBaseline))
    }
    Text(
        item.portionShown(),
        style = mono(12.sp, color = C.TextMuted),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 1.dp, bottom = 8.dp),
    )
}

/**
 * A dashed tear across the receipt. (No cut-out notches: the page's glow shows through behind
 * the receipt, so a notch painted in the page colour would show as a disc.)
 */
@Composable
private fun TearLine(modifier: Modifier = Modifier) {
    val dash = C.Border
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        drawLine(
            dash, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** An earlier day's meal on one line: time, name and foods, the rise if known, and carbs. */
@Composable
private fun Slip(state: AppState, m: MealEntry, now: Long, fmt: Fmt) {
    val info = mealInfo(state, m, now, fmt)
    val foods = m.meal.items.joinToString(" · ") { Foods.display(it.name) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .ruleTop()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = info.description
            }
            .tap { MealWork.edit(m) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmt.time(m.at), style = mono(13.sp), maxLines = 1, modifier = Modifier.width(if (fmt.is24) 52.dp else 76.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(mealName(m.name), style = inter(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (foods.isNotEmpty()) Text(foods, style = inter(13.sp, color = C.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        val rise = info.rise
        val out = info.out
        if (rise != null && out != null) {
            Stamp(rise.short + " " + Foods.delta(out.rise, state.unit), riseInk(rise))
            Spacer(Modifier.width(10.dp))
        }
        Text(tr("{0} g", Grams.text(m.carbs)), style = mono(15.sp, FontWeight.SemiBold, C.Text), maxLines = 1)
    }
}

/** Today's sugars, fibre, protein and fat, and carbs a day this week against last, in mono. */
@Composable
private fun TotalsLines(state: AppState, today: LocalDate, todays: List<MealEntry>) {
    val (week, last) = remember(state.meals, today) { DayTotals.weekCarbs(state.meals, today, state.zone) }
    val lines = buildList {
        if (todays.isNotEmpty()) add(DayTotals.text(DayTotals.of(today, todays)))
        if (week != null) {
            add(
                when {
                    last == null -> tr("This week: about {0} g carbs a day.", week)
                    week < last -> tr("This week: about {0} g carbs a day, down from {1} g the week before.", week, last)
                    week > last -> tr("This week: about {0} g carbs a day, up from {1} g the week before.", week, last)
                    else -> tr("This week: about {0} g carbs a day, the same as the week before.", week)
                },
            )
        }
    }
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp)) }
    }
}

/** More: an index of everything else, each with where it stands. */
@Composable
fun MorePage(state: AppState) {
    val context = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val now = rememberNow(60_000)
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    LaunchedEffect(Unit) { Ai.refresh(context) }
    // Read again whenever the backup sheet opens or closes, so a new backup shows straight away.
    val lastBackup = remember(state.backupOpen) { app.dropi.Prefs(context).lastBackup }

    val aiStatus = when {
        Ai.usable -> tr("ready")
        Ai.checking -> tr("Checking…")
        else -> tr("not set up")
    }
    val checksOn = state.slots.count { it.enabled }
    val checks =
        if (state.remindersOn) trn(checksOn, "{0} a day · reminders on", "{0} a day · reminders on")
        else trn(checksOn, "{0} a day · reminders off", "{0} a day · reminders off")
    val backupText = when {
        lastBackup <= 0L -> tr("never")
        now - lastBackup < 12 * 3_600_000L -> fmt.ago(lastBackup, now)
        else -> {
            val days = ((now - lastBackup) / 86_400_000L).coerceAtLeast(1L)
            trn(days, "{0} day ago", "{0} days ago")
        }
    }
    val backupDue = state.readings.isNotEmpty() && (lastBackup <= 0L || now - lastBackup > 14 * 86_400_000L)
    val themeName = when (state.theme) {
        "dark" -> tr("Dark")
        "light" -> tr("Light")
        "dracula" -> "Dracula"
        else -> tr("System")
    }.lowercase(L.locale)

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
    ) {
        item(key = "log") {
            Column(Modifier.fillMaxWidth()) {
                RuleHeader(tr("Your log"))
                IndexRow(tr("All readings"), state.readings.size.toString()) { state.openHistory() }
                IndexRow(tr("Report for your care team"), "PDF · CSV") { state.exportOpen = true }
                IndexRow(tr("Ask Dropi"), aiStatus, mark = if (Ai.usable) C.InRange else null) { state.askOpen = true }
            }
        }
        item(key = "setup") {
            Column(Modifier.fillMaxWidth()) {
                RuleHeader(tr("Setup"))
                IndexRow(tr("Check times"), checks) { state.scheduleOpen = true }
                IndexRow(tr("Live sensor"), if (state.liveOn) tr("on") else tr("off")) { state.liveOpen = true }
                IndexRow(tr("Backup"), backupText, mark = if (backupDue) C.High else null) { state.backupOpen = true }
                IndexRow(tr("Settings"), "${state.unit.label} · $themeName") { state.settingsOpen = true }
                // Always in both languages, so it can be found whichever one is showing.
                IndexRow("Language · Tungumál", if (L.icelandic) "Íslenska" else "English") { state.settingsOpen = true }
            }
        }
        item(key = "dropi") {
            Column(Modifier.fillMaxWidth()) {
                RuleHeader("Dropi")
                IndexRow(tr("What's changed"), null) { state.changelogOpen = true }
                IndexRow(tr("Report a problem"), null) { state.reportOpen = true }
            }
        }
        item(key = "note") {
            Text(
                tr("Dropi is a log, not a medical device. Your readings stay on this phone."),
                style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 18.sp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).ruleTop().padding(top = 14.dp),
            )
        }
    }
}

/** One line of the index: a big name on the left, where it stands in mono on the right. */
@Composable
private fun IndexRow(title: String, status: String?, mark: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .ruleTop()
            .tap(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = heading(21.sp), modifier = Modifier.weight(1f))
        if (status != null) {
            Spacer(Modifier.width(12.dp))
            if (mark != null) {
                RangeSquare(mark)
                Spacer(Modifier.width(6.dp))
            }
            Text(status, style = mono(12.sp, color = C.TextMuted), maxLines = 1)
        }
    }
}

/**
 * A meal being worked out or waiting to be checked, after its screen was closed: one tap back to
 * it, so a swipe never loses it.
 */
@Composable
fun MealDraftCard() {
    val d = MealWork.draft ?: return
    if (MealWork.open) return
    // A logged meal opened just to look at it, or an empty start, isn't anything waiting.
    if (d.forMeal != null && d.meal == d.forMeal.meal) return
    if (d.status is MealWork.Status.Ready && d.meal?.items.isNullOrEmpty()) return
    val (title, sub) = when (val s = d.status) {
        is MealWork.Status.Working -> tr("Working out your meal…") to tr("Dropi lets you know when it's done.")
        is MealWork.Status.Ready -> {
            val m = d.meal
            if (m == null || m.items.isEmpty()) tr("A meal isn't saved yet") to tr("Add its foods and save it.")
            else tr("Your meal is ready to check") to tr("About {0} g carbs. Not saved yet.", app.dropi.data.Grams.text(m.carbs))
        }
        is MealWork.Status.Failed -> tr("Couldn't make out your meal") to tr("Say what you ate, or add the foods yourself.")
        else -> tr("A meal isn't finished") to tr("Pick up where you left off.")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            // A real object (a meal waiting), so a quiet panel: small corners, a hairline.
            .card(RoundedCornerShape(Corner.M))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_utensils, C.AccentText, 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold))
            Text(sub, style = inter(13.sp, color = C.TextSecondary))
        }
        if (d.status !is MealWork.Status.Working) {
            Text(
                caps(tr("Discard")),
                style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f),
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(Corner.S)).semantics { role = Role.Button }
                    .tap { MealWork.discard() }.padding(horizontal = 10.dp, vertical = 16.dp),
            )
        }
        BannerAction(tr("Open")) { MealWork.open = true }
    }
}
