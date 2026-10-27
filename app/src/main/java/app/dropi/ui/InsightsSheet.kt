package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Experiment
import app.dropi.data.Learning
import app.dropi.data.MealPatterns
import app.dropi.tr
import app.dropi.trn

/**
 * What your meals show: everything Dropi learns from meals, in one page off the Meals page
 * (and More), so Meals itself stays about logging and today. Patterns, Your plate, what Dropi
 * has learnt, and the try-it week.
 */
@Composable
fun InsightsSheet(state: AppState) {
    if (!state.insightsOpen) return
    SlideSheet(onDismiss = { state.insightsOpen = false }) { close -> InsightsContent(state, close) }
}

@Composable
private fun ColumnScope.InsightsContent(state: AppState, close: () -> Unit) {
    StickySheetBody(tr("What your meals show"), close) {
        PatternNotes(state)
        FoodsCard(state)
        LearntCard(state)
        ExperimentCard(state)
    }
}

/**
 * Meals page: one row that opens What your meals show, with the two things worth knowing at a
 * glance (how close the guesses have been, how many patterns there are).
 */
@Composable
fun InsightsRow(state: AppState) {
    val m = state.learning
    val patterns = remember(state.measured, state.readings, state.meals, state.unit) {
        MealPatterns.fromMeasured(state.measured, state.readings, state.meals, state.unit)
    }
    val parts = listOfNotNull(
        if (m.recent.size >= Learning.MIN_TRACK) trn(m.recent.size, "{1} of {0} guesses close", "{1} of {0} guesses close", m.closeCount) else null,
        if (patterns.isNotEmpty()) trn(patterns.size, "{0} pattern", "{0} patterns") else null,
        state.foodScores.count { it.ranked }.takeIf { it > 0 }?.let { trn(it, "{0} food scored", "{0} foods scored") },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 56.dp)
            .ruleTop()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap { state.insightsOpen = true }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("What your meals show"), style = inter(15.sp, FontWeight.SemiBold))
            Text(
                if (parts.isEmpty()) tr("Patterns, your plate and what Dropi has learnt") else parts.joinToString(" · "),
                style = mono(12.sp, color = C.TextMuted),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("→", style = mono(15.sp, FontWeight.Medium, C.TextSecondary))
    }
}

/**
 * A try-it week that's running (or done and waiting to be seen): a slim banner on Today and Meals,
 * only while that's so. Opens What your meals show.
 */
@Composable
fun ExperimentBanner(state: AppState) {
    val run = state.experiment ?: return
    val now = System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .ruleTop()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap { state.insightsOpen = true }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RangeSquare(C.AccentText)
        Spacer(Modifier.width(10.dp))
        Text(
            if (run.done(now)) tr("Your {0}-day try is done: {1}. Tap to see how it went.", Experiment.DAYS, run.kind.title.lowercase(app.dropi.L.locale))
            else tr("Day {0} of {1} · {2}", run.day(now), Experiment.DAYS, run.kind.title.lowercase(app.dropi.L.locale)),
            style = inter(13.sp, FontWeight.Medium, C.TextSecondary).copy(lineHeight = 18.sp),
            modifier = Modifier.weight(1f),
        )
        Text("→", style = mono(15.sp, FontWeight.Medium, C.TextSecondary))
    }
}
