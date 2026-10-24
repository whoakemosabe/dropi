package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.work.MealWork
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Meals: ways to log one, how foods move the person's numbers, and every meal of the last two
 * weeks by day, each with where its numbers came from.
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
    val dayName = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH) }

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "draft") { MealDraftCard() }
        item(key = "log") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PageTile("Snap a meal", R.drawable.lc_camera, Modifier.weight(1f), lit = true) { Launch.mealAction.value = "camera" }
                    PageTile("Say what you ate", R.drawable.lc_mic, Modifier.weight(1f)) { Launch.mealAction.value = "words" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PageTile("Scan a label", R.drawable.lc_scan_text, Modifier.weight(1f)) { Launch.mealAction.value = "label" }
                    PageTile("Add foods myself", R.drawable.lc_pencil, Modifier.weight(1f)) { MealWork.manual() }
                }
            }
        }
        item(key = "totals") { TotalsCard(state, today) }
        item(key = "patterns") { PatternsCard(state) }
        item(key = "plate") { FoodsCard(state) }
        if (byDay.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No meals yet. Log one with a photo, in words or from a label. It's saved at the time you ate, " +
                        "and joined to the readings before and after it.",
                    style = inter(14.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().card().padding(18.dp),
                )
            }
        }
        byDay.forEach { (day, meals) ->
            item(key = "day-$day") {
                val title = when (day) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> dayName.format(day)
                }
                MealsCard(state, meals.sortedByDescending { it.at }, now, fmt, "$title · ${app.dropi.data.Grams.text(meals.sumOf { it.carbs })} g carbs")
                if (day != today) {
                    Text(
                        app.dropi.data.DayTotals.text(app.dropi.data.DayTotals.of(day, meals)),
                        style = inter(12.sp, color = C.TextMuted),
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Today's totals (carbs, sugars, fibre, protein, fat) and carbs a day this week against last. */
@Composable
private fun TotalsCard(state: AppState, today: LocalDate) {
    val todays = state.meals.filter { Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate() == today }
    val t = app.dropi.data.DayTotals.of(today, todays)
    val (week, last) = remember(state.meals, today) { app.dropi.data.DayTotals.weekCarbs(state.meals, today, state.zone) }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Eaten today", style = SectionLabel)
        if (todays.isEmpty()) {
            Text("Nothing logged yet today.", style = inter(14.sp, color = C.TextSecondary))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Carbs" to t.carbs, "Sugars" to t.sugars, "Fibre" to t.fibre, "Protein" to t.protein, "Fat" to t.fat).forEach { (label, v) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    ) {
                        val shown = v?.let { app.dropi.data.Grams.text(it) }
                        Text(shown ?: "–", style = numeral(22.sp), modifier = Modifier.semantics { contentDescription = if (shown == null) "$label not known" else "$shown grams $label" })
                        Text(if (v == null) label else "$label g", style = inter(11.sp, color = C.TextMuted))
                    }
                }
            }
        }
        if (week != null) {
            val trend = when {
                last == null -> ""
                week < last -> ", down from $last g the week before"
                week > last -> ", up from $last g the week before"
                else -> ", the same as the week before"
            }
            Text("This week: about $week g carbs a day$trend.", style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
        }
    }
}

/** What the person's own meals show, once there's enough to say it. */
@Composable
private fun PatternsCard(state: AppState) {
    val patterns = remember(state.measured, state.readings, state.meals, state.unit) {
        app.dropi.data.MealPatterns.fromMeasured(state.measured, state.readings, state.meals, state.unit)
    }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What your meals show", style = SectionLabel)
        if (patterns.isEmpty()) {
            Text(
                "After a few weeks of meals with readings before and after, this shows what makes a difference for you: " +
                    "fibre, fat and protein, fast or slow carbs, a walk after, veg first, sugary drinks and alcohol.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        }
        patterns.forEach { p ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 6.dp).size(8.dp).clip(RoundedCornerShape(50))
                        .background(if (p.good) C.InRange else C.AccentText),
                )
                Spacer(Modifier.width(10.dp))
                Text(p.text, style = inter(14.sp).copy(lineHeight = 20.sp))
            }
        }
        if (patterns.isNotEmpty()) Text("From your own log. Patterns, not advice.", style = inter(12.sp, color = C.TextMuted))
    }
}

/** More: everything else, one tap away. */
@Composable
fun MorePage(state: AppState) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    val items = listOf(
        Triple("All readings", R.drawable.lc_history) { state.openHistory() },
        Triple("Report for your care team", R.drawable.lc_file_text) { state.exportOpen = true },
        Triple("Ask Dropi", R.drawable.lc_message_circle) { state.askOpen = true },
        Triple("Check times and reminders", R.drawable.lc_clock) { state.scheduleOpen = true },
        Triple("Live sensor", R.drawable.lc_activity) { state.liveOpen = true },
        Triple("Backup", R.drawable.lc_archive) { state.backupOpen = true },
        Triple("Settings", R.drawable.lc_settings) { state.settingsOpen = true },
        Triple("What's changed", R.drawable.lc_sparkles) { state.changelogOpen = true },
    )
    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.chunked(2).forEachIndexed { i, pair ->
            item(key = "row-$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (title, icon, go) -> PageTile(title, icon, Modifier.weight(1f), onClick = go) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item(key = "note") {
            Text(
                "Dropi is a log, not a medical device. Your readings stay on this phone.",
                style = inter(11.sp, color = C.TextMuted),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

/** A square-ish tile: an icon on a soft square, and a short name under it. */
@Composable
private fun PageTile(title: String, icon: Int, modifier: Modifier, lit: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    Column(
        modifier
            .heightIn(min = 104.dp)
            .pressed(press)
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (lit) Modifier.background(Brush.linearGradient(C.Aurora)) else Modifier.background(C.SurfaceLight)),
            contentAlignment = Alignment.Center,
        ) { Ic(icon, if (lit) C.OnAccent else C.AccentText, 20.dp) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = inter(15.sp, FontWeight.Bold).copy(lineHeight = 19.sp))
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
    val (title, sub) = when (val s = d.status) {
        is MealWork.Status.Working -> "Working out your meal…" to "Dropi lets you know when it's done."
        is MealWork.Status.Ready -> {
            val m = d.meal
            if (m == null || m.items.isEmpty()) "A meal isn't saved yet" to "Add its foods and save it."
            else "Your meal is ready to check" to "About ${app.dropi.data.Grams.text(m.carbs)} g carbs. Not saved yet."
        }
        is MealWork.Status.Failed -> "Couldn't make out your meal" to "Say what you ate, or add the foods yourself."
        else -> "A meal isn't finished" to "Pick up where you left off."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.AccentText.copy(alpha = 0.12f))
            .border(1.dp, C.AccentText.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_utensils, C.AccentText, 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold))
            Text(sub, style = inter(13.sp, color = C.TextSecondary))
        }
        if (d.status !is MealWork.Status.Working) {
            Text(
                "Discard",
                style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary),
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).semantics { role = Role.Button }
                    .tap { MealWork.discard() }.padding(horizontal = 10.dp, vertical = 14.dp),
            )
        }
        Text(
            "Open",
            style = inter(14.sp, FontWeight.Bold, C.OnAccent),
            modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(C.Accent).semantics { role = Role.Button }
                .tap { MealWork.open = true }.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
