package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Experiment
import app.dropi.data.Foods
import app.dropi.tr
import app.dropi.trn

/**
 * Meals › Try something for 5 days. Pick one change; while it runs, a line says which day it is
 * and what to do; after, the meals with it are compared with the usual ones from the four weeks
 * before, and the result says plainly when there's no clear difference.
 */
@Composable
fun ExperimentCard(state: AppState) {
    val view = LocalView.current
    val now = System.currentTimeMillis()
    val unit = state.unit
    val run = state.experiment
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (run == null) {
            RuleHeader(tr("Try something for {0} days", Experiment.DAYS))
            Text(
                tr("Pick one change. Afterwards Dropi compares those meals with your usual ones from the four weeks before, from your own readings."),
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
            if (!picking) {
                MonoLink(tr("Choose one"), Modifier.padding(top = 8.dp)) { picking = true }
            } else {
                Fold(true) {
                    SettingsRows {
                        Experiment.Kind.entries.forEachIndexed { i, k ->
                            SettingsActionRow(k.title, hint = k.how, action = tr("Start"), first = i == 0) {
                                Haptics.confirm(view)
                                state.startExperiment(k)
                                picking = false
                            }
                        }
                    }
                }
            }
            return@Column
        }

        val result = remember(run, state.measured, state.meals, state.steps) {
            Experiment.result(run, state.measured, state.meals, state.zone, state.steps, now)
        }
        if (!run.done(now)) {
            RuleHeader(tr("Trying: {0}", run.kind.title), tr("Day {0} of {1}", run.day(now), Experiment.DAYS))
            Text(run.kind.how, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
            Text(
                trn(result.triedMeals, "{0} meal with it so far, with a reading before and after.", "{0} meals with it so far, with a reading before and after."),
                style = mono(12.sp, color = C.TextMuted),
                modifier = Modifier.padding(top = 6.dp),
            )
            MonoLink(tr("Stop"), Modifier.padding(top = 8.dp)) { state.endExperiment() }
            return@Column
        }

        RuleHeader(tr("How it went: {0}", run.kind.title))
        val d = result.clearDiff
        Text(
            when {
                !result.enough -> tr(
                    "Not enough meals to tell: {0} with it and {1} as usual. Each side needs {2} with a reading before and 1–3 h after.",
                    result.triedMeals, result.usualMeals, Experiment.MIN_EACH,
                )
                d != null && d < 0 -> tr("Your rise was {0} smaller with it ({1} meals) than usual ({2} meals).", Foods.delta(-d, unit).removePrefix("+"), result.triedMeals, result.usualMeals)
                d != null -> tr("Your rise was {0} bigger with it ({1} meals) than usual ({2} meals).", Foods.delta(d, unit).removePrefix("+"), result.triedMeals, result.usualMeals)
                else -> tr(
                    "No clear difference: {0} apart, under the {1} that would count ({2} meals with it, {3} as usual).",
                    Foods.delta(kotlin.math.abs((result.tried ?: 0.0) - (result.usual ?: 0.0)), unit).removePrefix("+"),
                    Foods.delta(Experiment.CLEAR, unit).removePrefix("+"), result.triedMeals, result.usualMeals,
                )
            },
            style = inter(15.sp, FontWeight.Medium).copy(lineHeight = 21.sp),
        )
        Text(tr("A few days of your own meals: a hint, not proof, and not advice."), style = mono(12.sp, color = C.TextMuted), modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MonoLink(tr("Try another")) { state.endExperiment(); picking = true }
            MonoLink(tr("Done")) { state.endExperiment() }
        }
    }
}
