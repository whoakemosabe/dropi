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

        // ---- Carbs ----
        val pg = m.perGram
        if (pg != null) {
            LearntLine(
                tr("50 g of carbs"),
                tr("about {0}", Foods.delta(pg * 50, unit)),
                hint = trn(m.perGramMeals, "From {0} meal of 15 g or more.", "From {0} meals of 15 g or more."),
            )
            listOf("Breakfast", "Lunch", "Dinner").forEach { name ->
                m.byName[name]?.let { (v, count) ->
                    LearntLine(
                        tr("{0}, 50 g", MealEntry.displayName(name)),
                        tr("about {0}", Foods.delta(v * 50, unit)),
                        hint = trn(count, "{0} meal", "{0} meals"),
                    )
                }
            }
        } else {
            LearntLine(tr("50 g of carbs"), tr("not yet"), hint = tr("Needs 4 meals of 15 g or more with a reading before and after. Until then Dropi uses a typical rise."))
        }

        // ---- Peak time ----
        val peak = m.peakMin
        if (peak != null) {
            LearntLine(
                tr("Your usual peak"),
                tr("{0} after eating", MealCurve.duration(peak)),
                hint = trn(m.peakMeals, "From your sensor, {0} meal.", "From your sensor, {0} meals."),
            )
            SettingsToggleRow(
                tr("After-eating check at your peak"),
                if (state.checkAtPeak) tr("Reminders come {0} after eating (later for rich meals).", MealCurve.duration(peak.coerceIn(45, 180)))
                else tr("Off: reminders use your set time."),
                state.checkAtPeak,
            ) { state.changeCheckAtPeak(it) }
        } else {
            LearntLine(tr("Your usual peak"), tr("not yet"), hint = tr("Found from a sensor around 3 or more meals. Finger-pricks are too far apart to time a peak."))
        }

        // ---- Walks and sleep (Health Connect, if turned on) ----
        val walk = androidx.compose.runtime.remember(state.measured, state.steps) { Learning.walkEffect(state.measured, state.steps) }
        walk?.let { (d, n) ->
            LearntLine(
                tr("A walk after eating"),
                if (d < 0) tr("rise {0} smaller", Foods.delta(-d, unit).removePrefix("+")) else tr("rise {0} bigger", Foods.delta(d, unit).removePrefix("+")),
                hint = trn(n, "{0} meal with 1,500+ steps in the hour after, against meals with hardly any.", "{0} meals with 1,500+ steps in the hour after, against meals with hardly any."),
            )
        }
        val night = androidx.compose.runtime.remember(state.readings, state.sleep) { Learning.sleepEffect(state.readings, state.sleep) }
        night?.let { (d, n) ->
            LearntLine(
                tr("After a short night"),
                if (d > 0) tr("waking {0} higher", Foods.delta(d, unit).removePrefix("+")) else tr("waking {0} lower", Foods.delta(-d, unit).removePrefix("+")),
                hint = trn(n, "{0} morning after under 6 h asleep, against nights of 7 h or more.", "{0} mornings after under 6 h asleep, against nights of 7 h or more."),
            )
        }
        if (!state.activityOn) {
            LearntLine(tr("Walks and sleep"), tr("off"), hint = tr("Turn on Steps and sleep in Settings › Health Connect to learn what they do for you."))
        }

        // ---- Sensor vs meter ----
        val offset = androidx.compose.runtime.remember(state.readings, state.sensor) {
            Learning.sensorOffset(state.readings, state.sensor, System.currentTimeMillis())
        }
        if (offset != null) {
            val (d, pairs) = offset
            LearntLine(
                tr("Sensor vs meter"),
                when {
                    kotlin.math.abs(d) < 0.3 -> tr("about the same")
                    d > 0 -> tr("sensor {0} higher", Foods.delta(d, unit).removePrefix("+"))
                    else -> tr("sensor {0} lower", Foods.delta(-d, unit).removePrefix("+"))
                },
                hint = trn(pairs, "From {0} finger-prick within 5 min of a sensor value, last 14 days.", "From {0} finger-pricks within 5 min of a sensor value, last 14 days.") +
                    if (kotlin.math.abs(d) >= 1.0) " " + tr("A gap this size is worth a look: check your sensor's calibration or placement.") else "",
            )
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
private fun LearntLine(label: String, value: String, first: Boolean = false, hint: String? = null) {
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
