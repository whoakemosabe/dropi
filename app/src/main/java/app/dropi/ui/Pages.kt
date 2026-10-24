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
import app.dropi.tr
import app.dropi.work.MealWork
import java.time.Instant
import java.time.LocalDate

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
    val dayName = remember { dayPattern("EEEE d MMMM") }

    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "draft") { MealDraftCard() }
        item(key = "log") {
            // One big way in (the camera), then the other four as a compact row.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(tr("Snap a meal"), Modifier.fillMaxWidth(), icon = R.drawable.lc_camera) { Launch.mealAction.value = "camera" }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniTile(tr("Say it"), R.drawable.lc_mic, Modifier.weight(1f)) { Launch.mealAction.value = "words" }
                    MiniTile(tr("Barcode"), R.drawable.lc_scan_barcode, Modifier.weight(1f)) { Launch.mealAction.value = "barcode" }
                    MiniTile(tr("Label"), R.drawable.lc_scan_text, Modifier.weight(1f)) { Launch.mealAction.value = "label" }
                    MiniTile(tr("Type it"), R.drawable.lc_pencil, Modifier.weight(1f)) { MealWork.manual() }
                }
            }
        }
        item(key = "totals") { TotalsCard(state, today) }
        item(key = "patterns") { PatternsCard(state) }
        item(key = "plate") { FoodsCard(state) }
        if (byDay.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("No meals yet. Log one with a photo, in words or from a label. It's saved at the time you ate, and joined to the readings before and after it."),
                    style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().card().padding(16.dp),
                )
            }
        }
        byDay.forEach { (day, meals) ->
            item(key = "day-$day") {
                val title = when (day) {
                    today -> tr("Today")
                    today.minusDays(1) -> tr("Yesterday")
                    else -> dayName.format(day).cap()
                }
                MealsCard(state, meals.sortedByDescending { it.at }, now, fmt, "$title · " + tr("{0} g carbs", app.dropi.data.Grams.text(meals.sumOf { it.carbs })))
                if (day != today) {
                    Text(
                        app.dropi.data.DayTotals.text(app.dropi.data.DayTotals.of(day, meals)),
                        style = inter(13.sp, color = C.TextMuted),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
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
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Eaten today"), style = SectionLabel)
        if (todays.isEmpty()) {
            Text(tr("Nothing logged yet today."), style = inter(15.sp, color = C.TextSecondary))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(tr("Carbs") to t.carbs, tr("Sugars") to t.sugars, tr("Fibre") to t.fibre, tr("Protein") to t.protein, tr("Fat") to t.fat).forEach { (label, v) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    ) {
                        val shown = v?.let { app.dropi.data.Grams.text(it) }
                        Text(shown ?: "–", style = numeral(22.sp), modifier = Modifier.semantics { contentDescription = if (shown == null) tr("{0} not known", label) else tr("{0} grams {1}", shown, label) })
                        Text(if (v == null) label else tr("{0} g", label), style = inter(13.sp, color = C.TextMuted))
                    }
                }
            }
        }
        if (week != null) {
            val line = when {
                last == null -> tr("This week: about {0} g carbs a day.", week)
                week < last -> tr("This week: about {0} g carbs a day, down from {1} g the week before.", week, last)
                week > last -> tr("This week: about {0} g carbs a day, up from {1} g the week before.", week, last)
                else -> tr("This week: about {0} g carbs a day, the same as the week before.", week)
            }
            Text(line, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
        }
    }
}

/** What the person's own meals show, once there's enough to say it. */
@Composable
private fun PatternsCard(state: AppState) {
    val patterns = remember(state.measured, state.readings, state.meals, state.unit) {
        app.dropi.data.MealPatterns.fromMeasured(state.measured, state.readings, state.meals, state.unit)
    }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("What your meals show"), style = SectionLabel)
        if (patterns.isEmpty()) {
            Text(
                tr("After a few weeks of meals with readings before and after, this shows what makes a difference for you: fibre, fat and protein, fast or slow carbs, a walk after, veg first, sugary drinks and alcohol."),
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        }
        patterns.forEach { p ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 8.dp).size(8.dp).clip(RoundedCornerShape(50))
                        .background(if (p.good) C.InRange else C.AccentText),
                )
                Spacer(Modifier.width(12.dp))
                Text(p.text, style = inter(15.sp).copy(lineHeight = 20.sp))
            }
        }
        if (patterns.isNotEmpty()) Text(tr("From your own log. Patterns, not advice."), style = inter(13.sp, color = C.TextMuted))
    }
}

/** More: everything else, one tap away. */
@Composable
fun MorePage(state: AppState) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    val items = listOf(
        Triple(tr("All readings"), R.drawable.lc_history) { state.openHistory() },
        Triple(tr("Report for your care team"), R.drawable.lc_file_text) { state.exportOpen = true },
        Triple(tr("Ask Dropi"), R.drawable.lc_message_circle) { state.askOpen = true },
        Triple(tr("Check times and reminders"), R.drawable.lc_clock) { state.scheduleOpen = true },
        Triple(tr("Live sensor"), R.drawable.lc_activity) { state.liveOpen = true },
        Triple(tr("Backup"), R.drawable.lc_archive) { state.backupOpen = true },
        Triple(tr("Settings"), R.drawable.lc_settings) { state.settingsOpen = true },
        Triple(tr("What's changed"), R.drawable.lc_sparkles) { state.changelogOpen = true },
        Triple(tr("Report a problem"), R.drawable.lc_circle_alert) { state.reportOpen = true },
    )
    LazyColumn(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 672.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalHeaderHeight.current + HEADER_FADE, bottom = bars.calculateBottomPadding() + 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(2).forEachIndexed { i, pair ->
            item(key = "row-$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (title, icon, go) -> PageTile(title, icon, Modifier.weight(1f), onClick = go) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item(key = "note") {
            Text(
                tr("Dropi is a log, not a medical device. Your readings stay on this phone."),
                style = inter(13.sp, color = C.TextMuted),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

/** A square-ish tile: an icon on a soft square, and a short name under it. */
/** A small square way in: icon over a one- or two-word label. */
@Composable
private fun MiniTile(title: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Column(
        modifier
            .heightIn(min = 72.dp)
            .pressed(press)
            .clip(RoundedCornerShape(Corner.M))
            .background(C.Card)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(Corner.M))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ic(icon, C.AccentText, 20.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = inter(13.sp, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun PageTile(title: String, icon: Int, modifier: Modifier, lit: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    Column(
        modifier
            .heightIn(min = 104.dp)
            .pressed(press)
            .clip(RoundedCornerShape(Corner.L))
            .background(C.Card)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(Corner.L))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Corner.S))
                .then(if (lit) Modifier.background(Brush.linearGradient(C.Aurora)) else Modifier.background(C.SurfaceLight)),
            contentAlignment = Alignment.Center,
        ) { Ic(icon, if (lit) C.OnAccent else C.AccentText, 20.dp) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = inter(15.sp, FontWeight.Bold).copy(lineHeight = 20.sp))
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
            .clip(RoundedCornerShape(Corner.M))
            .background(C.AccentText.copy(alpha = 0.12f))
            .border(1.dp, C.AccentText.copy(alpha = 0.45f), RoundedCornerShape(Corner.M))
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
                tr("Discard"),
                style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary),
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).semantics { role = Role.Button }
                    .tap { MealWork.discard() }.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
        Text(
            tr("Open"),
            style = inter(15.sp, FontWeight.Bold, C.OnAccent),
            modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(C.Accent).semantics { role = Role.Button }
                .tap { MealWork.open = true }.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}
