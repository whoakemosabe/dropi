package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Foods
import app.dropi.data.Learning
import app.dropi.data.MealCurve
import app.dropi.data.MealEntry
import app.dropi.tr
import app.dropi.trn

/**
 * Meals › What Dropi has learnt: the track record of its guesses, the person's rise per 50 g of
 * carbs (overall and by meal), and their usual peak time, with the switch that lets the
 * after-eating check follow it. Every line says how much it's based on.
 */
@Composable
fun LearntCard(state: AppState) {
    val m = state.learning
    val unit = state.unit
    Column(Modifier.fillMaxWidth()) {
        RuleHeader(tr("What Dropi has learnt"))

        // ---- Track record ----
        val n = m.recent.size
        if (n >= Learning.MIN_TRACK) {
            LearntLine(
                tr("Its guesses"),
                trn(n, "{1} of the last {0} within {2}", "{1} of the last {0} within {2}", m.closeCount, Foods.delta(Learning.CLOSE, unit).removePrefix("+")),
                first = true,
            )
            m.typicalMiss?.let { LearntLine(tr("Usually off by"), Foods.delta(it, unit).removePrefix("+")) }
            m.bias?.let { b ->
                LearntLine(
                    tr("Tends to guess"),
                    if (b > 0) tr("{0} too low", Foods.delta(b, unit).removePrefix("+")) else tr("{0} too high", Foods.delta(-b, unit).removePrefix("+")),
                )
            }
        } else {
            LearntLine(
                tr("Its guesses"),
                trn(Learning.MIN_TRACK - n, "after {0} more meal", "after {0} more meals"),
                first = true,
                hint = tr("A meal counts once it has a reading just before and one 1–3 h after."),
            )
        }

        // ---- Carbs: the overall factor; meal times and walks fold under More ----
        val pg = m.perGram
        if (pg != null) {
            LearntLine(
                tr("50 g of carbs"),
                tr("about {0}", Foods.delta(pg * 50, unit)),
                hint = trn(m.perGramMeals, "From {0} meal of 15 g or more.", "From {0} meals of 15 g or more."),
            )
        } else {
            LearntLine(tr("50 g of carbs"), tr("not yet"), hint = tr("Needs 4 meals of 15 g or more with a reading before and after. Until then Dropi uses a typical rise."))
        }

        // ---- Peak time (the reminder switch lives in Check times and reminders) ----
        val peak = m.peakMin
        if (peak != null) {
            LearntLine(
                tr("Your usual peak"),
                tr("{0} after eating", MealCurve.duration(peak)),
                hint = trn(m.peakMeals, "From your sensor, {0} meal.", "From your sensor, {0} meals.") + " " +
                    if (state.checkAtPeak) tr("Your after-eating check follows it (Check times and reminders).") else tr("Your after-eating check uses your set time (Check times and reminders)."),
            )
        } else {
            LearntLine(tr("Your usual peak"), tr("not yet"), hint = tr("Found from a sensor around 3 or more meals. Finger-pricks are too far apart to time a peak."))
        }

        // ---- More: meal-time factors and walks ----
        var more by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .ruleTop()
                .semantics(mergeDescendants = true) { stateDescription = if (more) tr("Expanded") else tr("Collapsed") }
                .tap { more = !more }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("More: by meal time, and walks"), style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            FoldChevron(more, C.TextSecondary)
        }
        Fold(more) {
            Column(Modifier.fillMaxWidth()) {
                val any = listOf("Breakfast", "Lunch", "Dinner").mapNotNull { name -> m.byName[name]?.let { name to it } }
                any.forEach { (name, vc) ->
                    LearntLine(
                        tr("{0}, 50 g", MealEntry.displayName(name)),
                        tr("about {0}", Foods.delta(vc.first * 50, unit)),
                        hint = trn(vc.second, "{0} meal", "{0} meals"),
                    )
                }
                if (any.isEmpty()) {
                    LearntLine(tr("By meal time"), tr("not yet"), hint = tr("Breakfast, lunch and dinner each get their own once there are 4 of that meal with a reading before and after."))
                }
                val walk = androidx.compose.runtime.remember(state.measured, state.steps) { Learning.walkEffect(state.measured, state.steps) }
                when {
                    walk != null -> LearntLine(
                        tr("A walk after eating"),
                        if (walk.first < 0) tr("rise {0} smaller", Foods.delta(-walk.first, unit).removePrefix("+")) else tr("rise {0} bigger", Foods.delta(walk.first, unit).removePrefix("+")),
                        hint = trn(walk.second, "{0} meal with 1,500+ steps in the hour after, against meals with hardly any.", "{0} meals with 1,500+ steps in the hour after, against meals with hardly any."),
                    )
                    !state.activityOn -> LearntLine(tr("Walks and sleep"), tr("off"), hint = tr("Turn on Steps and sleep in Settings › Health Connect to learn what they do for you."))
                    else -> LearntLine(tr("A walk after eating"), tr("not yet"), hint = tr("Needs 3 meals with a walk after and 3 without, with readings before and after."))
                }
            }
        }

        Text(
            tr("From your own readings. Estimates, not advice."),
            style = mono(12.sp, color = C.TextMuted),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** "Its guesses ······ 7 of the last 10 within 1,0": a label, a value, and what it's based on. */
@Composable
fun LearntLine(label: String, value: String, first: Boolean = false, hint: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .ruleTop(!first)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, style = mono(13.sp, FontWeight.Medium, C.Text), textAlign = TextAlign.End)
        }
        if (hint != null) Text(hint, style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 18.sp))
    }
}


/**
 * The sensor compared with the meter (Live sensor): finger-pricks within 5 minutes of a sensor
 * value over 14 days, the usual gap, and a nudge when it's 1 mmol/L or more.
 */
@Composable
fun SensorVsMeter(state: AppState) {
    val unit = state.unit
    val offset = androidx.compose.runtime.remember(state.readings, state.sensor) {
        Learning.sensorOffset(state.readings, state.sensor, System.currentTimeMillis())
    } ?: return
    val (d, pairs) = offset
    LearntLine(
        tr("Sensor vs meter"),
        when {
            kotlin.math.abs(d) < 0.3 -> tr("about the same")
            d > 0 -> tr("sensor {0} higher", Foods.delta(d, unit).removePrefix("+"))
            else -> tr("sensor {0} lower", Foods.delta(-d, unit).removePrefix("+"))
        },
        first = true,
        hint = trn(pairs, "From {0} finger-prick within 5 min of a sensor value, last 14 days.", "From {0} finger-pricks within 5 min of a sensor value, last 14 days.") +
            if (kotlin.math.abs(d) >= 1.0) " " + tr("A gap this size is worth a look: check your sensor's calibration or placement.") else "",
    )
}
