package app.dropi.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.data.Foods
import app.dropi.data.Glucose
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import app.dropi.data.Meal
import app.dropi.data.MealHistory
import app.dropi.data.MealItem
import app.dropi.data.Rise
import app.dropi.work.Ai
import app.dropi.work.MealWork

/**
 * What you ate: the meal broken into foods, each with its portion, carbs and sugars and how it
 * went last time; where meals like it usually take the person; and ways to fix it by tapping,
 * typing or saying the change. Everything comes from the phone and the person's own log.
 */
@Composable
fun MealSheet(state: AppState) {
    val d = MealWork.draft
    if (!MealWork.open || d == null) return
    SlideSheet(onDismiss = { MealWork.open = false }) { close ->
        val footer: (@Composable () -> Unit)? =
            if (d.status is MealWork.Status.Ready) ({ MealSaveBar(state, d) { close() } }) else null
        StickySheetBody("Meal", close, footer = footer) { MealBody(state, d) { close() } }
    }
}

/** The speech-to-text screen Android provides (on the phone where it can be), or null without one. */
@Composable
private fun rememberVoice(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    val available = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }
    if (!available) return null
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you ate")
        try { launcher.launch(i) } catch (e: android.content.ActivityNotFoundException) { }
    }
}

@Composable
private fun ColumnScope.MealBody(state: AppState, d: MealWork.Draft, close: () -> Unit) {
    val context = LocalContext.current
    when (val s = d.status) {
        is MealWork.Status.Words -> WordsInput(
            hint = "For example: two slices of toast with jam and a small glass of orange juice",
            button = "Work it out",
        ) { MealWork.fromWords(context, it) }

        is MealWork.Status.Working -> Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), color = C.AccentText, strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Ai.warmUp ?: s.what, style = inter(16.sp, FontWeight.SemiBold))
                // Foods appear as they're named, before the rest is worked out.
                if (s.found.isNotEmpty()) {
                    Text("Found so far: " + s.found.joinToString(", ") { Foods.display(it) }, style = inter(14.sp, color = C.TextSecondary))
                }
                Text("You can close this. Dropi lets you know when it's done.", style = inter(13.sp, color = C.TextMuted))
            }
        }

        is MealWork.Status.Failed -> {
            Text(s.message, style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp))
            PrimaryButton("Say what you ate", Modifier.fillMaxWidth(), icon = R.drawable.lc_mic) { MealWork.words() }
            GhostButton("Add the foods myself", Modifier.fillMaxWidth()) { MealWork.manual() }
        }

        is MealWork.Status.NeedsGrams -> LabelGrams(s.per100, s.read, s.why, s.text)

        is MealWork.Status.Ready -> Breakdown(state, d.meal ?: Meal(emptyList()), d, close)
    }
}

/** A box for the meal in words, with a big microphone. */
@Composable
private fun WordsInput(hint: String, button: String, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val voice = rememberVoice { said -> text = said; onDone(said) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Card)
            .border(1.dp, C.AccentText.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).heightIn(min = 72.dp)) {
            if (text.isEmpty()) Text(hint, style = inter(16.sp, color = C.TextMuted).copy(lineHeight = 23.sp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                textStyle = inter(17.sp, FontWeight.Medium, C.Text).copy(lineHeight = 24.sp),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What you ate" },
            )
        }
        if (voice != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(C.Aurora))
                    .semantics { role = Role.Button; contentDescription = "Say it" }
                    .tap { voice() },
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_mic, C.OnAccent, 26.dp) }
        }
    }
    SheetHint(
        if (voice != null) "Tap the microphone and say it, or type it. Speech is turned into text by your phone."
        else "Type it, or use your keyboard's microphone."
    )
    PrimaryButton(button, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { onDone(text) }
}

/**
 * After a label photo: the per-100 g numbers it found (or empty boxes to type them from the
 * label when it couldn't read them), what it is, and how much was eaten.
 */
@Composable
private fun LabelGrams(found: app.dropi.data.Label.Per100, read: Boolean, why: String? = null, seen: List<String> = emptyList()) {
    fun fmt(d: Double?) = d?.let { Glucose.formatNumber(Math.round(it * 10) / 10.0) }.orEmpty()
    // US labels give their numbers per serving; European ones per 100 g.
    val us = found.us
    val per = if (us) "per serving" else "per 100 g"
    var name by remember { mutableStateOf(found.name) }
    var carbs by remember { mutableStateOf(fmt(found.carbs)) }
    var sugars by remember { mutableStateOf(fmt(found.sugars)) }
    var fibre by remember { mutableStateOf(fmt(found.fibre)) }
    var protein by remember { mutableStateOf(fmt(found.protein)) }
    var fat by remember { mutableStateOf(fmt(found.fat)) }
    var serving by remember { mutableStateOf(fmt(found.serving)) }
    var grams by remember { mutableStateOf(found.serving?.let { Math.round(it).toInt() }?.takeIf { us } ?: 100) }
    fun num(t: String) = t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..(if (us) 400.0 else 100.0) }
    Text(
        if (read) "Read from the label. Check the numbers match the \"$per\" column."
        else "Couldn't read the numbers clearly. Type them from the label's \"$per\" column, or take the photo again straight on.",
        style = inter(15.sp, FontWeight.SemiBold).copy(lineHeight = 22.sp),
    )
    if (!read && why != null) Text(why, style = inter(13.sp, color = C.TextMuted))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Carbs $per", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(carbs, "e.g. 60,5", numeric = true, label = "Carbs $per") { carbs = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sugars $per", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(sugars, "optional", numeric = true, label = "Sugars $per, optional") { sugars = it.take(5) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fibre", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(fibre, "optional", numeric = true, label = "Fibre $per, optional") { fibre = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Protein", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(protein, "optional", numeric = true, label = "Protein $per, optional") { protein = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fat", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(fat, "optional", numeric = true, label = "Fat $per, optional") { fat = it.take(5) }
        }
    }
    if (us) {
        Text(
            "This looks like a US label: its numbers are per serving, and its carbs include fibre (Dropi takes the fibre off, " +
                "as European labels already do). Check the serving size below.",
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Serving size on the label (g)", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(serving, "e.g. 40", numeric = true, label = "Serving size in grams") { serving = it.take(5) }
        }
    }
    FieldBox(name, "What is it? (e.g. granola)", label = "What is it?", onChange = { name = it.take(40) })
    Text("How much did you eat?", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
    val servingG = serving.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..2000.0 }
    val options = if (us && servingG != null) listOf(1.0, 1.5, 2.0).map { Math.round(servingG * it).toInt() }.distinct() else listOf(25, 50, 100, 150)
    ChoiceGrid(
        options.map { g -> Choice(if (us && servingG != null) "${app.dropi.data.Meal.qtyText(g / servingG)} (${g} g)" else "$g g") },
        options.indexOf(grams).takeIf { it >= 0 }, columns = options.size, compact = true,
    ) { grams = options[it] }
    NumberRow("Grams eaten", grams, step = 5, max = 2000) { grams = it }
    val c = num(carbs)
    val raw = found.copy(carbs = c, sugars = num(sugars), fibre = num(fibre), protein = num(protein), fat = num(fat), serving = servingG)
    // Per 100 g from here on (a US label's per-serving numbers are converted by its serving size).
    val checked = raw.toPer100()
    val ready = c != null && (!us || servingG != null)
    Text(
        when {
            c == null -> "Type the carbs $per to work it out."
            us && servingG == null -> "Type the serving size to work it out."
            else -> "That's ${app.dropi.data.Grams.text((checked.netCarbs ?: 0.0) * grams / 100.0)} g carbs."
        },
        style = inter(15.sp, FontWeight.SemiBold),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    PrimaryButton("Next", Modifier.fillMaxWidth(), enabled = ready) {
        MealWork.labelGrams(Foods.normalize(name), grams, checked)
    }
    GhostButton("Take the photo again", Modifier.fillMaxWidth()) { Launch.mealAction.value = "label" }
    // What the camera actually read, so a label that won't work can be checked (and sent to fix it).
    if (seen.isNotEmpty()) {
        var show by remember { mutableStateOf(false) }
        Text(
            if (show) "Hide what Dropi read" else "What Dropi read from the photo",
            style = inter(14.sp, FontWeight.SemiBold, C.AccentText),
            modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp)).semantics { role = Role.Button }.tap { show = !show },
        )
        if (show) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    seen.joinToString("\n"),
                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(12.dp),
                )
            }
        }
    }
}

/** The meal's time, name and starting reading, worked out from the draft. */
private class MealFacts(state: AppState, d: MealWork.Draft) {
    val old = d.forMeal
    val ateAt = d.started
    val name = d.name ?: app.dropi.data.MealEntry.nameFor(d.started, state.zone)
    val auto = state.startFor(ateAt)
    val start = d.before?.let { id -> state.readings.firstOrNull { it.id == id } } ?: auto
    val last = if (start == null) state.lastReadingBefore(ateAt)?.takeIf { ateAt - it.at < 12 * 3_600_000L } else null
}

/** The pinned Save button under the meal screen. */
@Composable
private fun MealSaveBar(state: AppState, d: MealWork.Draft, close: () -> Unit) {
    val view = LocalView.current
    val meal = d.meal ?: Meal(emptyList())
    val f = MealFacts(state, d)
    // A food still being looked up would be saved as 0 g: wait for it.
    val waiting = meal.items.any { it.key in MealWork.filling }
    PrimaryButton(
        when {
            meal.items.isEmpty() -> "Add a food first"
            waiting -> "Waiting for a food's numbers…"
            f.old != null -> "Save changes"
            else -> "Save ${f.name.lowercase()}"
        },
        Modifier.fillMaxWidth(),
        icon = R.drawable.lc_check,
        enabled = meal.items.isNotEmpty() && !waiting,
    ) {
        Haptics.confirm(view)
        state.saveMeal(meal, f.ateAt, f.name, d.before, f.old?.id ?: 0)
        MealWork.discard()
        close()
    }
}

/**
 * The meal screen: its name and time at the top, one card with the totals, one with the likely
 * peak, the foods, and everything else folded under "More". Save is pinned below.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Breakdown(state: AppState, meal: Meal, d: MealWork.Draft, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val unit = state.unit
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val scores = remember(state.foodScores) { state.foodScores.associateBy { it.food } }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(meal.tags.isNotEmpty()) }
    val usual = remember(meal) { MealWork.usual(context) }
    val f = MealFacts(state, d)
    val start = f.start

    // ---- Name and time ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            f.name,
            style = heading(26.sp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .semantics { role = Role.Button; contentDescription = "${f.name}. Change the meal's name or time" }
                .tap { picking = true },
        )
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .background(C.Card)
                .border(1.dp, C.GlassBorder, RoundedCornerShape(50))
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Ate at ${fmt.time(f.ateAt)}. Change it" }
                .tap { picking = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_clock, C.TextSecondary, 16.dp)
            Spacer(Modifier.width(8.dp))
            Text("Ate ${fmt.time(f.ateAt)}", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
        }
    }
    Text("Tap the name or time to change them", style = inter(13.sp, color = C.TextMuted), modifier = Modifier.padding(top = 0.dp))
    if (picking) MealWhenDialog(f.ateAt, f.name, fmt, state.zone) { picking = false }

    // ---- Totals ----
    Column(
        Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp)).padding(18.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(app.dropi.data.Grams.text(meal.carbs), style = numeral(52.sp))
            Spacer(Modifier.width(8.dp))
            Text("g carbs", style = inter(16.sp, FontWeight.SemiBold, C.TextSecondary), modifier = Modifier.padding(bottom = 7.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sugars" to meal.sugars, "fibre" to meal.fibre, "protein" to meal.protein, "fat" to meal.fat).forEach { (label, v) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.SurfaceLight).padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(v?.let { app.dropi.data.Grams.text(it) } ?: "–", style = numeral(20.sp), maxLines = 1)
                    Text(label, style = inter(11.sp, color = C.TextSecondary))
                }
            }
        }
        val gapMin = app.dropi.data.Schedule.gapFor(app.dropi.data.MealEntry(at = 0, name = "", meal = meal), state.afterMealMinutes)
        val badges = listOfNotNull(
            meal.speed?.let { it.word to C.AccentText },
            if (meal.hasSugaryDrink) "Sugary drink" to C.High else null,
            when {
                meal.slowDigesting -> "Rich: check at ${app.dropi.data.MealCurve.duration(gapMin)}" to C.High
                meal.someFatProtein -> "Some fat and protein: check at ${app.dropi.data.MealCurve.duration(gapMin)}" to C.TextSecondary
                else -> null
            },
        )
        if (badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { (text, color) ->
                    Text(
                        text,
                        style = inter(12.sp, FontWeight.SemiBold, color),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

    // ---- Likely peak ----
    // All from the meals measured once in AppState, leaving this meal out of its own estimate.
    val othersMeasured = remember(state.measured, f.old?.id) { state.measured.filter { it.entryId != f.old?.id } }
    val est = remember(start?.mmol, meal.foods, othersMeasured) { start?.let { MealHistory.estimate(it.mmol, meal.foods, othersMeasured) } }
    val perGram = remember(othersMeasured, state.meals) { app.dropi.data.MealCurve.perGram(othersMeasured, state.meals) }
    val curve = remember(meal, perGram, est) { app.dropi.data.MealCurve.predict(meal, perGram, est) }
    val actual = f.old?.let { o -> state.measuredById[o.id]?.after }.orEmpty()
    Column(
        Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (start == null) {
            Text(
                if (f.last != null) "No reading just before this meal. Your last one was at ${fmt.time(f.last.at)} (${Glucose.format(f.last.mmol, unit)})."
                else "No reading before this meal. Take one before you eat to see where it's likely to take you.",
                style = inter(14.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.last != null) Chip("Use ${fmt.time(f.last.at)}", icon = R.drawable.lc_check, selected = true) { MealWork.setBefore(f.last.id) }
                // Only while the reading would still count as "before" (up to 15 min after eating).
                if (f.ateAt > System.currentTimeMillis() - 15 * 60_000L) {
                    Chip("Take one now", icon = R.drawable.lc_plus, selected = f.last == null) {
                        if (meal.items.isEmpty()) return@Chip
                        state.saveMeal(meal, f.ateAt, f.name, null, f.old?.id ?: 0)
                        MealWork.discard()
                        close()
                        state.startNew(app.dropi.data.MealTag.BEFORE_MEAL)
                    }
                }
            }
        }
        if (curve != null) {
            val s0 = start?.mmol
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (s0 != null) "Likely peak" else "Likely rise", style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (start != null) Text("from your ${fmt.time(start.at)} reading, ${Glucose.format(start.mmol, unit)}", style = inter(13.sp, color = C.TextSecondary))
            }
            FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (s0 != null) "about " + Glucose.format(s0 + curve.rise, unit) else "about " + Foods.delta(curve.rise, unit),
                    style = numeral(32.sp, if (s0 != null) C.status(Glucose.status(s0 + curve.rise, app.dropi.data.MealTag.AFTER_MEAL, state.targets)) else C.Text),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "around ${app.dropi.data.MealCurve.duration(curve.peakMin)} after eating",
                    style = inter(14.sp, color = C.TextSecondary),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            ExpectedCurve(curve, s0, actual, unit)
            val outcome = actual.filter { it.first in 30..240 }.maxByOrNull { it.second }
            if (outcome != null && s0 != null) {
                Text(
                    "How it went: peaked ${Glucose.format(s0 + outcome.second, unit)} at ${app.dropi.data.MealCurve.duration(outcome.first)}.",
                    style = inter(14.sp, FontWeight.SemiBold),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (curve.personal) "An estimate from this meal and your past meals. Not advice." else "An estimate from this meal's numbers. Not advice.",
                    style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (why) "Hide" else "Why?",
                    style = inter(13.sp, FontWeight.SemiBold, C.AccentText),
                    modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).tap { why = !why }.padding(horizontal = 8.dp),
                )
            }
            if (why) {
                val reasons = if (curve.reasons.isEmpty()) "" else "Shaped by: ${curve.reasons.joinToString(", ")}. "
                Text(
                    reasons + "Back near the start by about ${app.dropi.data.MealCurve.duration(curve.backMin)}." +
                        if (curve.personal) "" else " It learns your own pattern after a few meals with readings before and after.",
                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                )
            }
        } else if (start != null) {
            Text("Starts from your ${fmt.time(start.at)} reading (${Glucose.format(start.mmol, unit)}). Add the foods to see the likely peak.", style = inter(14.sp, color = C.TextSecondary))
        }
    }

    // ---- The foods ----
    Text("What you ate", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    if (meal.items.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp))) {
            meal.items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
                FoodRow(item, scores[item.key]) { editing = i }
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, C.Border, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap { adding = true },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_plus, C.AccentText, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text("Add a food", style = inter(15.sp, FontWeight.SemiBold, C.AccentText))
    }

    // ---- More, folded ----
    if (meal.items.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp))) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button; stateDescription = if (more) "Open" else "Closed" }
                    .tap { more = !more }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("More: veg first, a walk after, rich meal, say a change", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary), modifier = Modifier.weight(1f))
                Ic(R.drawable.lc_chevron_down, C.TextMuted, 18.dp, modifier = Modifier.graphicsLayer { rotationZ = if (more) 180f else 0f })
            }
            if (more) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Anything else with it?", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        app.dropi.data.MealTags.ALL.forEach { t ->
                            val on = t.key in meal.tags
                            Chip(t.label, selected = on) {
                                Haptics.tap(view)
                                MealWork.update(meal.copy(tags = if (on) meal.tags - t.key else meal.tags + t.key))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Rich meal", style = inter(15.sp, FontWeight.SemiBold))
                            Text("Fatty or creamy: the rise comes later, so the after-eating check waits 3 h.", style = inter(12.sp, color = C.TextSecondary).copy(lineHeight = 17.sp))
                        }
                        Switch(
                            checked = meal.rich,
                            onCheckedChange = { Haptics.toggle(view, it); MealWork.update(meal.copy(rich = it)) },
                            colors = dropiSwitchColors(),
                            modifier = Modifier.semantics { contentDescription = "Rich meal, check at 3 hours" },
                        )
                    }
                    if (Ai.usable) {
                        Text("Not quite right? Say the change", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                        ChangeBox { MealWork.change(context, it) }
                        if (MealWork.failedChange) Text("Couldn't apply that. Tap the food to change it instead.", style = inter(13.sp, color = C.High))
                    }
                }
            }
        }
    }

    if (f.old != null) {
        var sure by remember { mutableStateOf(false) }
        GhostButton(if (sure) "Tap again to delete this meal" else "Delete this meal", Modifier.fillMaxWidth(), color = C.Low) {
            if (sure) { state.deleteMeal(f.old); MealWork.discard(); close() } else sure = true
        }
    } else {
        GhostButton("Start again", Modifier.fillMaxWidth()) { MealWork.discard(); close() }
    }
    SheetHint("Carbs, sugars, protein, fat and how fast carbs hit are estimates to check against labels and your usual amounts. Dropi doesn't suggest doses.")

    val open = editing
    if (open != null && open in meal.items.indices) {
        FoodEditDialog(meal.items[open], score = scores[meal.items[open].key], unit = unit,
            remembered = meal.items[open].key in usual,
            onDismiss = { editing = null },
            onRemove = { MealWork.update(meal.copy(items = meal.items.filterIndexed { k, _ -> k != open })); editing = null },
        ) { item, remember ->
            if (remember) MealWork.remember(context, item) else if (meal.items[open].key in usual) MealWork.forget(context, item.key)
            MealWork.update(meal.copy(items = meal.items.mapIndexed { k, it -> if (k == open) item.copy(usual = remember) else it }))
            editing = null
        }
    }
    if (adding) {
        AddFoodDialog(usual, onDismiss = { adding = false }) { item, known ->
            MealWork.update(meal.copy(items = meal.items + item))
            // Added before its numbers came back: they land on the meal when ready.
            if (!known) MealWork.fillIn(context, item)
            adding = false
        }
    }
}

/** The meal's name and when it was eaten: wrapping chips, never a sideways scroll. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MealWhenDialog(ateAt: Long, name: String, fmt: Fmt, zone: java.time.ZoneId, onDone: () -> Unit) {
    var pickingTime by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val minsAgo = ((now - ateAt) / 60_000L).toInt()
    val presets = listOf(0 to "Now", 15 to "15 min ago", 30 to "30 min ago", 60 to "1 h ago")
    val onPreset = presets.firstOrNull { kotlin.math.abs(it.first - minsAgo) <= 2 }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.Sheet)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Which meal?", style = heading(20.sp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                app.dropi.data.MealEntry.NAMES.forEach { n -> Chip(n, selected = n == name) { MealWork.setName(n) } }
            }
            Text("Ate at ${fmt.time(ateAt)}", style = heading(20.sp), modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { (m, label) -> Chip(label, selected = onPreset?.first == m) { MealWork.setWhen(now - m * 60_000L) } }
                Chip(if (onPreset == null) fmt.time(ateAt) else "Other time", icon = R.drawable.lc_clock, selected = onPreset == null) { pickingTime = true }
            }
            PrimaryButton("Done", Modifier.fillMaxWidth()) { onDone() }
        }
    }
    if (pickingTime) WhenPicker(ateAt, zone, onDismiss = { pickingTime = false }) { t -> MealWork.setWhen(t); pickingTime = false }
}

/** One food: its name, one line of portion and what's in it, and its carbs on the right. */
@Composable
private fun FoodRow(item: MealItem, score: Foods.Score?, onClick: () -> Unit) {
    val rise = score?.takeIf { it.ranked }?.let { Rise.of(it.rise) }
    val looking = item.key in MealWork.filling
    val g = { v: Double -> app.dropi.data.Grams.text(v) }
    val details = when {
        looking -> listOf(item.portionText(), "looking up its numbers…")
        else -> listOfNotNull(
            item.portionText(),
            if (item.sugaryDrink) "sugary drink" else item.giValue?.takeIf { item.carbs > 0 }?.let { app.dropi.data.Gi.speed(it).word.lowercase() },
            item.protein?.takeIf { it > 0 }?.let { "${g(it)} g protein" },
            item.fat?.takeIf { it > 0 }?.let { "${g(it)} g fat" },
        )
    }
    val spoken = buildString {
        append("${Foods.display(item.name)}, ${g(item.carbs)} grams of carbs, ${details.joinToString(", ")}")
        if (item.usual) append(", your usual amount")
        if (rise != null) append(", ${rise.word.lowercase()} last time")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = spoken }
            .tap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Foods.display(item.name), style = inter(16.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f, fill = false))
                if (item.usual) {
                    Spacer(Modifier.width(8.dp))
                    Text("Your usual", style = inter(11.sp, FontWeight.SemiBold, C.AccentText))
                }
                if (rise != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(riseColor(rise)))
                }
            }
            Text(details.joinToString(" · "), style = inter(13.sp, color = if (looking) C.AccentText else C.TextSecondary), maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (looking) "…" else g(item.carbs), style = numeral(20.sp))
            Text(" g", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun riseColor(r: Rise) = when (r) {
    Rise.GENTLE -> C.InRange
    Rise.MODERATE -> C.High
    Rise.BIG -> C.VeryHigh
}

/** "Make the juice a small glass": typed or said. */
@Composable
private fun ChangeBox(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val voice = rememberVoice { said -> onSend(said) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.SurfaceLight)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text("e.g. I only ate half the rice", style = inter(15.sp, color = C.TextMuted))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                textStyle = inter(15.sp, color = C.Text),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Change to the meal" },
            )
        }
        val icon = if (text.isBlank() && voice != null) R.drawable.lc_mic else R.drawable.lc_send
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics { role = Role.Button; contentDescription = if (icon == R.drawable.lc_mic) "Say the change" else "Apply the change" }
                .tap {
                    if (text.isNotBlank()) { onSend(text); text = "" } else voice?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) { Ic(icon, C.AccentText, 20.dp) }
    }
}

/** A single-line box with a hint. */
@Composable
private fun FieldBox(value: String, hint: String, numeric: Boolean = false, label: String? = null, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.SurfaceLight)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = inter(15.sp, color = C.TextMuted))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = inter(15.sp, color = C.Text),
            cursorBrush = SolidColor(C.AccentText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke(); defaultKeyboardAction(ImeAction.Done) }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label ?: hint },
        )
    }
}

/** − number + where the number itself can be tapped and typed. */
@Composable
private fun NumberRow(label: String, value: Int, step: Int, max: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(R.drawable.lc_minus, "$step less") { onChange((value - step).coerceAtLeast(0)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    val digits = v.filter(Char::isDigit).take(4)
                    text = digits
                    digits.toIntOrNull()?.let { onChange(it.coerceAtMost(max)) }
                },
                textStyle = numeral(40.sp).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                cursorBrush = SolidColor(C.AccentText),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.widthIn(min = 80.dp).semantics { contentDescription = "$label, $value. Edit to type a number" },
            )
        }
        StepButton(R.drawable.lc_plus, "$step more") { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(C.SurfaceLight)
            .semantics { role = Role.Button; contentDescription = label }
            .tap { Haptics.tap(view); onClick() },
        contentAlignment = Alignment.Center,
    ) { Ic(icon, C.Text, 20.dp) }
}

/** A number of grams typed in a box: decimals with a comma or a point; empty means not known. */
@Composable
private fun DecimalBox(label: String, value: Double?, modifier: Modifier = Modifier, highlight: Boolean = false, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let { app.dropi.data.Grams.text(it).replace('.', ',') }.orEmpty()) }
    // Changed from outside (the amount chips): show the new value.
    LaunchedEffect(value) {
        if (app.dropi.data.Grams.parse(text) != value) text = value?.let { app.dropi.data.Grams.text(it).replace('.', ',') }.orEmpty()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
        BasicTextField(
            value = text,
            onValueChange = { v ->
                val cleaned = v.filter { it.isDigit() || it == ',' || it == '.' }
                val mark = cleaned.indexOfFirst { it == ',' || it == '.' }
                val t = if (mark < 0) cleaned.take(4)
                else cleaned.substring(0, mark).take(4) + cleaned[mark] + cleaned.substring(mark + 1).filter(Char::isDigit).take(1)
                text = t
                onChange(if (t.isEmpty()) null else app.dropi.data.Grams.parse(t)?.coerceAtMost(400.0))
            },
            textStyle = numeral(22.sp),
            cursorBrush = SolidColor(C.AccentText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.SurfaceLight)
                        .border(if (highlight) 1.5.dp else 1.dp, if (highlight) C.AccentText else C.GlassBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("–", style = numeral(22.sp, C.TextMuted))
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "$label in grams, ${value?.let { app.dropi.data.Grams.text(it) } ?: "not set"}. Decimals allowed"
            },
        )
    }
}

/** Carbs, sugars, protein, fat and fibre in two columns of boxes. */
@Composable
private fun NumbersGrid(item: MealItem, onChange: (MealItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalBox("Carbs", item.carbs, Modifier.weight(1f), highlight = true) { onChange(item.copy(carbs = it ?: 0.0)) }
            DecimalBox("of which sugars", item.sugars, Modifier.weight(1f)) { onChange(item.copy(sugars = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalBox("Protein", item.protein, Modifier.weight(1f)) { onChange(item.copy(protein = it)) }
            DecimalBox("Fat", item.fat, Modifier.weight(1f)) { onChange(item.copy(fat = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalBox("Fibre", item.fibre, Modifier.weight(1f)) { onChange(item.copy(fibre = it)) }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The five numbers for a food as one tidy row, for the add-a-food preview. */
@Composable
private fun NumbersPreview(item: MealItem?) {
    Row(Modifier.fillMaxWidth()) {
        listOf("carbs" to item?.carbs, "sugars" to item?.sugars, "fibre" to item?.fibre, "protein" to item?.protein, "fat" to item?.fat).forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (item == null) Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(C.SurfaceLight))
                else Text(v?.let { app.dropi.data.Grams.text(it) } ?: "–", style = numeral(22.sp), maxLines = 1)
                Text(label, style = inter(11.sp, color = C.TextSecondary), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A dialog laid over the meal screen, sliding content in a rounded card. */
@Composable
private fun FoodDialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // A stray tap outside doesn't throw away what was typed; back or Cancel closes it.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(24.dp))
                .background(C.Sheet)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Add a food: as the name is typed its numbers show underneath, from the person's usual amounts,
 * then Dropi's food list, then (after a short pause in typing) the on-device AI. Pick how much,
 * change the numbers if needed, and the button says exactly what will be added.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddFoodDialog(usual: Map<String, app.dropi.data.Usual>, onDismiss: () -> Unit, onAdd: (MealItem, Boolean) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var name by remember { mutableStateOf("") }
    val key = Foods.normalize(name)
    // The food at one portion, and where it came from.
    var base by remember { mutableStateOf<MealItem?>(null) }
    var from by remember { mutableStateOf("") }
    var looking by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf(1.0) }
    var other by remember { mutableStateOf(false) }
    var otherPortion by remember { mutableStateOf("") }
    var numbersOpen by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf<MealItem?>(null) }

    // Get the model loading now, so it's ready by the time the name is typed.
    LaunchedEffect(Unit) { app.dropi.work.Ai.warm(context) }
    // Done on the keyboard asks straight away instead of waiting for a pause.
    var askNow by remember { mutableStateOf(0) }
    LaunchedEffect(key, if (other) otherPortion else "", askNow) {
        // A different food or amount: numbers typed for the old one don't carry over.
        edited = null
        looking = false
        if (key.length < 2) { base = null; return@LaunchedEffect }
        val portion = otherPortion.trim().takeIf { other && it.isNotEmpty() }
        val u = usual[key]
        val basic = app.dropi.data.Basics.find(key)
        val known = MealWork.cached(context, key, portion)
        when {
            portion == null && u == null && known != null -> { base = known; from = "Looked up before" }
            portion != null && known != null -> { base = known; from = "Looked up before" }
            portion == null && u != null -> {
                base = MealItem(key, u.portion, carbs = u.carbs, sugars = u.sugars, protein = u.protein, fat = u.fat, usual = true); from = "Your usual amount"
            }
            portion == null && basic != null -> { base = basic.copy(name = key); from = "Found: ${basic.name}, from Dropi's food list" }
            app.dropi.work.Ai.usable -> {
                base = null
                if (askNow == 0) kotlinx.coroutines.delay(700) // wait for a pause in typing
                looking = true
                val found = MealWork.lookup(context, key, portion)
                looking = false
                base = found?.copy(name = key, portion = portion ?: found.portion)
                from = if (found != null) "Estimated by the on-device AI. Check it if you know better." else ""
                if (found == null) numbersOpen = true
            }
            else -> { base = null; from = ""; numbersOpen = true }
        }
        if (portion == null) qty = 1.0
    }

    val preview = base?.let { b -> if (other) b.copy(qty = 1.0) else b.withQty(qty) }
    val current = edited ?: preview ?: MealItem(key, otherPortion.trim().ifEmpty { "1 portion" }, carbs = 0.0)
    val known = edited != null || preview != null

    FoodDialogFrame(onDismiss) {
        Text("Add a food", style = heading(24.sp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What is it?", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
            FieldBox(name, "e.g. hard boiled egg", label = "What is it?", onDone = { askNow++ }) { name = it.take(40) }
        }

        if (key.length >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How much?", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val b = base
                    if (b != null && !other) {
                        listOf(1.0, 2.0, 3.0).forEach { q ->
                            Chip(b.withQty(q).portionText(), selected = qty == q) { Haptics.tap(view); qty = q; edited = null }
                        }
                    }
                    Chip("Other amount", selected = other || b == null) { other = !other || b == null; edited = null }
                }
                if (other || base == null) FieldBox(otherPortion, "e.g. 150 g, 1 bowl, a handful", label = "How much", onDone = { askNow++ }) { otherPortion = it.take(30); other = true }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Card).border(1.dp, C.GlassBorder, RoundedCornerShape(20.dp)).padding(16.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = C.AccentText, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Not in Dropi's list. Working it out on your phone…", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
                    }
                    edited != null -> Text("Your numbers", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                    preview != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.lc_check, C.InRange, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(from, style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                    }
                    app.dropi.work.Ai.usable -> Text("Looks it up when you stop typing.", style = inter(13.sp, color = C.TextSecondary))
                    else -> Text("Not in Dropi's list. Type the numbers from the packet or your own amounts.", style = inter(13.sp, color = C.TextSecondary))
                }
                NumbersPreview(if (looking || !known) null else current)
                if (known) Text("Grams for ${current.portionText()}.", style = inter(12.sp, color = C.TextMuted))
                if (looking) Text("You can add it now; the numbers land on the meal when they're ready.", style = inter(12.sp, color = C.TextMuted))
                if (!numbersOpen) {
                    Text(
                        "Change the numbers",
                        style = inter(14.sp, FontWeight.SemiBold, C.AccentText),
                        modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(8.dp))
                            .semantics { role = Role.Button }.tap { numbersOpen = true },
                    )
                }
            }
            if (numbersOpen) NumbersGrid(current) { edited = it }
        }

        PrimaryButton(
            when {
                key.length < 2 -> "Add"
                known -> "Add ${Foods.display(key)} · ${app.dropi.data.Grams.text(current.carbs)} g carbs"
                !app.dropi.work.Ai.usable -> "Add ${Foods.display(key)} with 0 g carbs"
                else -> "Add ${Foods.display(key)}"
            },
            Modifier.fillMaxWidth(),
            enabled = key.length >= 2,
        ) { onAdd(current.copy(name = key), known) }
        GhostButton("Cancel", Modifier.fillMaxWidth()) { onDismiss() }
    }
}

/** Change one food: how much, its five numbers (decimals welcome), and whether to remember it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FoodEditDialog(
    initial: MealItem,
    score: Foods.Score?,
    unit: app.dropi.data.GlucoseUnit,
    remembered: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onDone: (MealItem, Boolean) -> Unit,
) {
    val view = LocalView.current
    var item by remember(initial) { mutableStateOf(initial) }
    var keep by remember(initial) { mutableStateOf(remembered || initial.usual) }
    FoodDialogFrame(onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Foods.display(initial.name), style = heading(24.sp), modifier = Modifier.weight(1f))
            Text(item.portionText(), style = inter(14.sp, color = C.TextSecondary), modifier = Modifier.padding(bottom = 3.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0, 3.0).forEach { q ->
                Chip(initial.withQty(q).portionText(), selected = item.qty == q) {
                    Haptics.tap(view)
                    item = item.withQty(q)
                }
            }
        }
        Text("Grams for the whole amount. Decimals are fine: 0,8 or 0.8.", style = inter(13.sp, color = C.TextMuted))
        NumbersGrid(item) { item = it }
        if (initial.carbs != item.carbs && initial.qty == item.qty) {
            Text("Guessed ${app.dropi.data.Grams.text(initial.carbs)} g carbs. You changed it to ${app.dropi.data.Grams.text(item.carbs)} g.", style = inter(12.sp, color = C.TextMuted))
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.GlassBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Remember as my ${Foods.display(initial.name).lowercase()}", style = inter(15.sp, FontWeight.SemiBold))
                Text("Next time: ${app.dropi.data.Grams.text(item.carbs / item.qty.coerceAtLeast(0.1))} g carbs for ${item.portion}", style = inter(12.sp, color = C.TextMuted))
            }
            Switch(checked = keep, onCheckedChange = { Haptics.toggle(view, it); keep = it }, colors = dropiSwitchColors(),
                modifier = Modifier.semantics { contentDescription = "Remember this amount" })
        }

        if (score != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("After ${Foods.display(initial.name).lowercase()}, last time", style = inter(14.sp, FontWeight.SemiBold))
                if (!score.ranked) {
                    Text("1 meal so far: ${Foods.delta(score.rise, unit)} ${unit.label}. Two make a pattern.", style = inter(13.sp, color = C.TextMuted))
                } else {
                    val r = Rise.of(score.rise)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Foods.delta(score.rise, unit), style = numeral(24.sp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(riseColor(r)))
                        Spacer(Modifier.width(6.dp))
                        Text("${r.word}, ${score.meals} meals", style = inter(13.sp, color = C.TextSecondary))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Remove", Modifier.weight(1f), color = C.Low) { onRemove() }
            PrimaryButton("Done", Modifier.weight(2f)) { onDone(item, keep) }
        }
    }
}

/**
 * The expected rise as a soft curve over the hours after eating, with the start as a baseline
 * and, for a logged meal, what actually happened as dots on top.
 */
@Composable
private fun ExpectedCurve(curve: app.dropi.data.MealCurve.Curve, start: Double?, actual: List<Pair<Int, Double>>, unit: app.dropi.data.GlucoseUnit) {
    val line = C.AccentText
    val fill = C.AccentText.copy(alpha = 0.14f)
    val base = C.Border
    val dot = C.Text
    val muted = C.TextMuted
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = muted)
    val endMin = maxOf(curve.backMin + 30, actual.maxOfOrNull { it.first } ?: 0, 180).coerceAtMost(300)
    val top = maxOf(curve.rise, actual.maxOfOrNull { it.second } ?: 0.0, 1.0) * 1.15
    val bottom = minOf(0.0, actual.minOfOrNull { it.second } ?: 0.0) - 0.2
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .semantics {
                contentDescription = "Expected curve: rises about ${Foods.delta(curve.rise, unit)} ${unit.label}, peaking around " +
                    "${app.dropi.data.MealCurve.duration(curve.peakMin)} after eating" +
                    if (actual.isNotEmpty()) ", with your actual readings shown as dots" else ""
            },
    ) {
        val labelH = 14.dp.toPx()
        val w = size.width
        val h = size.height - labelH
        fun x(min: Double) = (min / endMin * w).toFloat()
        fun y(v: Double) = (h - (v - bottom) / (top - bottom) * h).toFloat()
        drawLine(base, androidx.compose.ui.geometry.Offset(0f, y(0.0)), androidx.compose.ui.geometry.Offset(w, y(0.0)), strokeWidth = 1.dp.toPx())
        val path = androidx.compose.ui.graphics.Path()
        val area = androidx.compose.ui.graphics.Path()
        area.moveTo(0f, y(0.0))
        var m = 0.0
        while (m <= endMin) {
            val px = x(m); val py = y(curve.at(m))
            if (m == 0.0) path.moveTo(px, py) else path.lineTo(px, py)
            area.lineTo(px, py)
            m += 3.0
        }
        area.lineTo(w, y(0.0)); area.close()
        drawPath(area, fill)
        drawPath(
            path, line,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (actual.isNotEmpty()) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            ),
        )
        actual.filter { it.first in 0..endMin }.forEach { (mm, v) ->
            drawCircle(dot, 3.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x(mm.toDouble()), y(v)))
        }
        // Hour marks along the bottom.
        var hr = 0
        while (hr * 60 <= endMin) {
            val t = measurer.measure(if (hr == 0) "ate" else "${hr} h", labelStyle)
            val px = (x(hr * 60.0) - t.size.width / 2f).coerceIn(0f, w - t.size.width)
            drawText(t, topLeft = androidx.compose.ui.geometry.Offset(px, h + 2.dp.toPx()))
            hr++
        }
        if (start != null) {
            val t = measurer.measure(Glucose.format(start, unit), labelStyle)
            drawText(t, topLeft = androidx.compose.ui.geometry.Offset(w - t.size.width, y(0.0) - t.size.height - 1.dp.toPx()))
        }
    }
}
