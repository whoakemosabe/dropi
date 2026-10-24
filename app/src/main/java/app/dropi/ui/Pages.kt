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
                MealsCard(state, meals.sortedByDescending { it.at }, now, fmt, "$title · ${meals.sumOf { it.carbs }} g carbs")
            }
        }
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
