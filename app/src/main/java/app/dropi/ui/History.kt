package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.Glucose
import app.dropi.data.MealTag
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun HistorySheet(state: AppState) {
    if (!state.historyOpen) return
    SlideSheet(onDismiss = { state.historyOpen = false }) { close -> HistoryContent(state, close) }
}

/**
 * Every reading ever logged, newest first, as ledger rows under a ruled header per day with the
 * day's count and average. A filter grid (pinned with the title) narrows it to one moment. Tap a
 * reading to edit it.
 */
@Composable
private fun ColumnScope.HistoryContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    var filter by remember { mutableStateOf<MealTag?>(null) }
    val all = state.history
    val shown = remember(all, filter) { all.filter { filter == null || it.tag == filter }.reversed() }
    val byDay = remember(shown) { shown.groupBy { it.date(state.zone) } }
    // Short day names so the ruled header fits one line; the year only when it isn't this one.
    val dayName = remember { dayPattern("EEE d MMM") }
    val dayYear = remember { dayPattern("EEE d MMM yyyy") }
    val thisYear = remember(state.zone) { java.time.LocalDate.now(state.zone).year }
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerDp = with(density) { headerPx.toDp() }
    val nav = WindowInsets.navigationBars.asPaddingValues()

    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        LazyColumn(
            Modifier.fillMaxWidth().layerBackdrop(backdrop).background(C.Sheet),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerDp + 20.dp, bottom = nav.calculateBottomPadding() + 24.dp),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (all.isEmpty()) tr("No readings yet.") else tr("No {0} readings yet.", filter?.label?.lowercase(L.locale).orEmpty()),
                        style = inter(15.sp, color = C.TextSecondary),
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            byDay.forEach { (day, rs) ->
                item(key = "d$day") {
                    val avg = rs.map { it.mmol }.average()
                    RuleHeader(
                        (if (day.year == thisYear) dayName else dayYear).format(day),
                        trn(rs.size, "{0} reading · avg {1}", "{0} readings · avg {1}", Glucose.format(avg, state.unit)),
                        Modifier.padding(top = 4.dp),
                    )
                }
                item(key = "l$day") { ReadingsCard(state, rs) { fmt.time(it.at) } }
            }
        }
        GlassHeader(backdrop, headerDp + 16.dp, Modifier.align(Alignment.TopCenter), fade = 16.dp, base = C.Sheet)
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            SheetTitle(tr("All readings"), close)
            Text(
                caps(trn(all.size, "{0} reading in total", "{0} readings in total")),
                style = mono(12.sp, color = C.TextMuted, tracking = 0.06f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val options = listOf<MealTag?>(null) + MealTag.entries
            ChoiceGrid(
                options.map { if (it == null) Choice(tr("All")) else Choice(it.short) },
                options.indexOf(filter),
                columns = 4,
                compact = true,
            ) { filter = options[it] }
            Spacer(Modifier.height(4.dp))
        }
    }
}
