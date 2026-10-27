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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import app.dropi.tr
import app.dropi.trn
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
        StickySheetBody(tr("Meal"), close, footer = footer) { MealBody(state, d) { close() } }
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
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Say what you ate"))
        try { launcher.launch(i) } catch (e: android.content.ActivityNotFoundException) { }
    }
}

@Composable
private fun ColumnScope.MealBody(state: AppState, d: MealWork.Draft, close: () -> Unit) {
    val context = LocalContext.current
    when (val s = d.status) {
        is MealWork.Status.Words -> WordsInput(
            hint = tr("For example: two slices of toast with jam and a small glass of orange juice"),
            button = tr("Work it out"),
        ) { MealWork.fromWords(context, it) }

        is MealWork.Status.Working -> Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = C.AccentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(Ai.warmUp ?: s.what, style = inter(17.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            }
            // Foods appear as they're named, before the rest is worked out: a receipt filling in.
            if (s.found.isNotEmpty()) {
                Text(
                    tr("Found so far: {0}", s.found.joinToString(", ") { Foods.display(it) }),
                    style = mono(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                    modifier = Modifier.fillMaxWidth().ruleTop().padding(vertical = 10.dp),
                )
            }
            Text(
                tr("You can close this. Dropi lets you know when it's done."),
                style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 18.sp),
                modifier = Modifier.fillMaxWidth().ruleTop().padding(top = 10.dp),
            )
        }

        is MealWork.Status.Failed -> {
            Text(s.message, style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp))
            PrimaryButton(tr("Say what you ate"), Modifier.fillMaxWidth(), icon = R.drawable.lc_mic) { MealWork.words() }
            GhostButton(tr("Add the foods myself"), Modifier.fillMaxWidth()) { MealWork.manual() }
        }

        is MealWork.Status.NeedsGrams -> LabelGrams(s.per100, s.read, s.why, s.text, s.code, s.source, s.portion)

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
            .clip(RoundedCornerShape(Corner.M))
            .border(1.dp, C.AccentText.copy(alpha = 0.5f), RoundedCornerShape(Corner.M))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).heightIn(min = 72.dp)) {
            if (text.isEmpty()) Text(hint, style = inter(17.sp, color = C.TextMuted).copy(lineHeight = 23.sp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                textStyle = inter(17.sp, FontWeight.Medium, C.Text).copy(lineHeight = 24.sp),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("What you ate") },
            )
        }
        if (voice != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(C.Accent)
                    .semantics { role = Role.Button; contentDescription = tr("Say it") }
                    .tap { voice() },
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_mic, C.OnAccent, 26.dp) }
        }
    }
    SheetHint(
        if (voice != null) tr("Tap the microphone and say it, or type it. Speech is turned into text by your phone.")
        else tr("Type it, or use your keyboard's microphone.")
    )
    PrimaryButton(button, Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { onDone(text) }
}

/**
 * After a label photo: the per-100 g numbers it found (or empty boxes to type them from the
 * label when it couldn't read them), what it is, and how much was eaten.
 */
@Composable
private fun LabelGrams(
    found: app.dropi.data.Label.Per100, read: Boolean, why: String? = null, seen: List<String> = emptyList(),
    code: String? = null, source: String = "label", portion: Double? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun fmt(d: Double?) = d?.let { Glucose.formatNumber(Math.round(it * 10) / 10.0) }.orEmpty()
    // US labels give their numbers per serving; European ones per 100 g.
    val us = found.us
    val per = if (us) tr("per serving") else tr("per 100 g")
    var name by remember { mutableStateOf(found.name) }
    var carbs by remember { mutableStateOf(fmt(found.carbs)) }
    var sugars by remember { mutableStateOf(fmt(found.sugars)) }
    var fibre by remember { mutableStateOf(fmt(found.fibre)) }
    var protein by remember { mutableStateOf(fmt(found.protein)) }
    var fat by remember { mutableStateOf(fmt(found.fat)) }
    var serving by remember { mutableStateOf(fmt(found.serving)) }
    val packetG = portion?.let { Math.round(it).toInt() }?.takeIf { !us && it in 5..2000 }
    var grams by remember { mutableStateOf(found.serving?.let { Math.round(it).toInt() }?.takeIf { us } ?: packetG ?: 100) }
    fun num(t: String) = t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..(if (us) 400.0 else 100.0) }
    Text(
        when {
            source == "off" -> tr("Found in Open Food Facts. Check the numbers match the packet's \"{0}\" column.", per)
            source == "saved" -> tr("A packet you've scanned before, with the numbers you checked.")
            source == "barcode" -> why ?: tr("Type the numbers from the label's \"{0}\" column.", per)
            read -> tr("Read from the label. Check the numbers match the \"{0}\" column.", per)
            else -> tr("Couldn't read the numbers clearly. Type them from the label's \"{0}\" column, or take the photo again straight on.", per)
        },
        style = inter(15.sp, FontWeight.SemiBold).copy(lineHeight = 22.sp),
    )
    if (!read && why != null && source != "barcode") Text(why, style = inter(13.sp, color = C.TextMuted))
    // The label's numbers, ruled like the label itself.
    RuleHeader(per)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Carbs {0}", per))
            FieldBox(carbs, tr("e.g. 60,5"), numeric = true, label = tr("Carbs {0}", per)) { carbs = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Sugars {0}", per))
            FieldBox(sugars, tr("optional"), numeric = true, label = tr("Sugars {0}, optional", per)) { sugars = it.take(5) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Fibre"))
            FieldBox(fibre, tr("optional"), numeric = true, label = tr("Fibre {0}, optional", per)) { fibre = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Protein"))
            FieldBox(protein, tr("optional"), numeric = true, label = tr("Protein {0}, optional", per)) { protein = it.take(5) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Fat"))
            FieldBox(fat, tr("optional"), numeric = true, label = tr("Fat {0}, optional", per)) { fat = it.take(5) }
        }
    }
    if (us) {
        Text(
            tr("This looks like a US label: its numbers are per serving, and its carbs include fibre (Dropi takes the fibre off, as European labels already do). Check the serving size below."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("Serving size on the label (g)"))
            FieldBox(serving, tr("e.g. 40"), numeric = true, label = tr("Serving size in grams")) { serving = it.take(5) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(tr("What is it?"))
        FieldBox(name, tr("What is it? (e.g. granola)"), label = tr("What is it?"), onChange = { name = it.take(40) })
    }
    RuleHeader(tr("How much did you eat?"))
    val servingG = serving.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..2000.0 }
    val options = when {
        us && servingG != null -> listOf(1.0, 1.5, 2.0).map { Math.round(servingG * it).toInt() }.distinct()
        // A packet's serving first (e.g. a 170 g skyr pot), then the usual amounts.
        packetG != null -> (listOf(packetG) + listOf(25, 50, 100, 150).filter { it != packetG }).take(4)
        else -> listOf(25, 50, 100, 150)
    }
    ChoiceGrid(
        options.map { g -> Choice(if (us && servingG != null) "${app.dropi.data.Meal.qtyText(g / servingG)} (${g} g)" else "$g g") },
        options.indexOf(grams).takeIf { it >= 0 }, columns = options.size, compact = true,
    ) { grams = options[it] }
    NumberRow(tr("Grams eaten"), grams, step = 5, max = 2000) { grams = it }
    val c = num(carbs)
    val raw = found.copy(carbs = c, sugars = num(sugars), fibre = num(fibre), protein = num(protein), fat = num(fat), serving = servingG)
    // Per 100 g from here on (a US label's per-serving numbers are converted by its serving size).
    val checked = raw.toPer100()
    val ready = c != null && (!us || servingG != null)
    Text(
        when {
            c == null -> tr("Type the carbs {0} to work it out.", per)
            us && servingG == null -> tr("Type the serving size to work it out.")
            else -> tr("That's {0} g carbs.", app.dropi.data.Grams.text((checked.netCarbs ?: 0.0) * grams / 100.0))
        },
        style = inter(15.sp, FontWeight.SemiBold),
        modifier = Modifier.fillMaxWidth().ruleTop().padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
    PrimaryButton(tr("Next"), Modifier.fillMaxWidth(), enabled = ready) {
        MealWork.labelGrams(context, Foods.normalize(name), grams, checked, code, packetG?.toDouble())
    }
    if (source == "label") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(tr("Take it again"), Modifier.weight(1f)) { Launch.mealAction.value = "label" }
        GhostButton(tr("Pick from photos"), Modifier.weight(1f)) { Launch.mealAction.value = "label-pick" }
    }
    else if (!read) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(tr("Scan the label"), Modifier.weight(1f), color = C.AccentText) { Launch.mealAction.value = "label" }
            GhostButton(tr("Barcode again"), Modifier.weight(1f)) { Launch.mealAction.value = "barcode" }
        }
        GhostButton(tr("Pick a barcode photo"), Modifier.fillMaxWidth()) { Launch.mealAction.value = "barcode-pick" }
    }
    if (code != null) Text(
        if (source == "off") tr("Barcode {0} · data from Open Food Facts (ODbL)", code) else tr("Barcode {0}", code),
        style = mono(12.sp, color = C.TextMuted),
    )
    // What the camera actually read, so a label that won't work can be checked (and sent to fix it).
    if (seen.isNotEmpty()) {
        var show by remember { mutableStateOf(false) }
        GhostButton(if (show) tr("Hide what Dropi read") else tr("What Dropi read from the photo"), Modifier.fillMaxWidth(), color = C.AccentText) { show = !show }
        if (show) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    seen.joinToString("\n"),
                    style = mono(12.sp, color = C.TextSecondary).copy(lineHeight = 18.sp),
                    modifier = Modifier.fillMaxWidth().card(RoundedCornerShape(Corner.S)).padding(12.dp),
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
            meal.items.isEmpty() -> tr("Add a food first")
            waiting -> tr("Waiting for a food's numbers…")
            f.old != null -> tr("Save changes")
            else -> saveMealText(f.name)
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
 * The meal screen: its name and time at the top, the meal as a receipt (each food with a dotted
 * leader to its carbs, a torn line, the totals), the likely peak, and everything else folded
 * under "More". Save is pinned below.
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
    // Carbs and the likely rise up front; sugars, fibre, protein and fat one tap away.
    var nutrition by remember { mutableStateOf(false) }
    val usual = remember(meal) { MealWork.usual(context) }
    val f = MealFacts(state, d)
    val start = f.start

    // ---- Name and time ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Corner.S))
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = tr("{0}. Change the meal's name or time", mealNameShown(f.name)) }
                .tap { picking = true },
            contentAlignment = Alignment.CenterStart,
        ) { Text(mealNameShown(f.name), style = heading(26.sp)) }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Corner.S))
                .border(1.dp, C.Border, RoundedCornerShape(Corner.S))
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = tr("Ate at {0}. Change it", fmt.time(f.ateAt)) }
                .tap { picking = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_clock, C.TextSecondary, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(caps(tr("Ate {0}", fmt.time(f.ateAt))), style = mono(13.sp, FontWeight.Medium, C.Text, 0.04f), maxLines = 1)
        }
    }
    Text(tr("Tap the name or time to change them"), style = mono(12.sp, color = C.TextMuted))
    if (picking) MealWhenDialog(f.ateAt, f.name, fmt, state.zone) { picking = false }

    // ---- The meal as a receipt: each food and its carbs, a torn line, then the totals ----
    RuleHeader(tr("What you ate"), if (meal.items.isEmpty()) null else trn(meal.items.size, "{0} food", "{0} foods"))
    // A photo can't show how much was on the plate: one quick question instead of a silent guess.
    if (meal.source == "photo" && meal.items.any { !it.usual } && f.old == null) PortionPick(meal)
    // Foods whose guesses were scaled from the person's earlier corrections, said once.
    val fixes = remember(meal.source) { app.dropi.Prefs(context).carbFixes }
    val fixed = if (meal.source == "photo" || meal.source == "words") meal.items.filter { !it.usual && app.dropi.data.MealAi.fixFor(it.name, fixes) != null } else emptyList()
    if (fixed.isNotEmpty()) {
        Text(
            tr("Carbs for {0} adjusted to how you've corrected it before.", fixed.joinToString(", ") { Foods.display(it.name).lowercase(app.dropi.L.locale) }),
            style = mono(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    val paper = C.Card
    val edge = C.Border.copy(alpha = 0.7f)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val s = 1.dp.toPx()
                val corner = CornerRadius(3.dp.toPx())
                drawRoundRect(paper, cornerRadius = corner)
                drawRoundRect(edge, topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s), cornerRadius = corner, style = Stroke(s))
            }
            .padding(top = 6.dp, bottom = 14.dp),
    ) {
        meal.items.forEachIndexed { i, item -> FoodRow(item, scores[item.key], nutrition) { editing = i } }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .tap { adding = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_plus, C.AccentText, 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(caps(tr("Add a food")), style = mono(13.sp, FontWeight.SemiBold, C.AccentText, 0.06f))
        }
        MealTear(Modifier.padding(top = 2.dp))

        // ---- Totals ----
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(app.dropi.data.Grams.text(meal.carbs), style = numeral(44.sp))
                Spacer(Modifier.width(8.dp))
                Text(tr("g carbs"), style = mono(13.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.padding(bottom = 8.dp).weight(1f))
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Corner.S))
                        .semantics(mergeDescendants = true) { role = Role.Button; stateDescription = if (nutrition) tr("Shown") else tr("Hidden") }
                        .tap { nutrition = !nutrition }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(caps(tr("Nutrition")), style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.06f))
                    Spacer(Modifier.width(4.dp))
                    Ic(if (nutrition) R.drawable.lc_chevron_up else R.drawable.lc_chevron_down, C.AccentText, 16.dp)
                }
            }
            if (nutrition) Column(Modifier.padding(end = 8.dp)) {
                listOf(tr("sugars") to meal.sugars, tr("fibre") to meal.fibre, tr("protein") to meal.protein, tr("fat") to meal.fat).forEach { (label, v) ->
                    LeaderLine(label, v?.let { tr("{0} g", app.dropi.data.Grams.text(it)) } ?: "–")
                }
            }
            val gapMin = app.dropi.data.Schedule.gapFor(app.dropi.data.MealEntry(at = 0, name = "", meal = meal), state.afterMealMinutes)
            val badges = listOfNotNull(
                meal.speed?.let { it.word to C.AccentText },
                if (meal.hasSugaryDrink) tr("Sugary drink") to C.High else null,
                when {
                    meal.slowDigesting -> tr("Rich: check at {0}", app.dropi.data.MealCurve.duration(gapMin)) to C.High
                    meal.someFatProtein -> tr("Some fat and protein: check at {0}", app.dropi.data.MealCurve.duration(gapMin)) to C.TextSecondary
                    else -> null
                },
            )
            if (badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { (text, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RangeSquare(color)
                            Spacer(Modifier.width(6.dp))
                            Text(text, style = mono(12.sp, FontWeight.Medium, C.TextSecondary))
                        }
                    }
                }
            }
        }
    }

    // ---- Likely peak ----
    // All from the meals measured once in AppState, leaving this meal out of its own estimate.
    val othersMeasured = remember(state.measured, f.old?.id) { state.measured.filter { it.entryId != f.old?.id } }
    val est = remember(start?.mmol, meal.foods, othersMeasured) { start?.let { MealHistory.estimate(it.mmol, meal.foods, othersMeasured) } }
    // Breakfast, lunch and dinner each learn their own factor once there are enough of them.
    val perGram = remember(othersMeasured, state.meals, f.name) { app.dropi.data.Learning.perGramFor(othersMeasured, state.meals, f.name) }
    val learnt = state.learning
    val curve = remember(meal, perGram, est, learnt.peakMin) { app.dropi.data.MealCurve.predict(meal, perGram, est, learnt.peakMin) }
    val actual = f.old?.let { o -> state.measuredById[o.id]?.after }.orEmpty()
    RuleHeader(if (curve != null && start == null) tr("Likely rise") else tr("Likely peak"))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (start == null) {
            Text(
                if (f.last != null) tr("No reading just before this meal. Your last one was at {0} ({1}).", fmt.time(f.last.at), Glucose.format(f.last.mmol, unit))
                else tr("No reading before this meal. Take one before you eat to see where it's likely to take you."),
                style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.last != null) Chip(tr("Use {0}", fmt.time(f.last.at)), icon = R.drawable.lc_check, selected = true) { MealWork.setBefore(f.last.id) }
                // Only while the reading would still count as "before" (up to 15 min after eating).
                if (f.ateAt > System.currentTimeMillis() - 15 * 60_000L) {
                    Chip(tr("Take one now"), icon = R.drawable.lc_plus, selected = f.last == null) {
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
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr("about {0}", if (s0 != null) Glucose.format(s0 + curve.rise, unit) else Foods.delta(curve.rise, unit)),
                    style = numeral(36.sp, if (s0 != null) C.status(Glucose.status(s0 + curve.rise, app.dropi.data.MealTag.AFTER_MEAL, state.targets)) else C.Text),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("around {0} after eating", app.dropi.data.MealCurve.duration(curve.peakMin)),
                    style = mono(13.sp, color = C.TextSecondary),
                    modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Bottom),
                )
            }
            // A range from how far off past guesses were: narrower as Dropi learns.
            learnt.spread?.let { sp ->
                val lo = (if (s0 != null) s0 + curve.rise else curve.rise) - sp
                val hi = (if (s0 != null) s0 + curve.rise else curve.rise) + sp
                Text(
                    if (s0 != null) tr("likely {0} to {1}", Glucose.format(lo.coerceAtLeast(s0), unit), Glucose.format(hi, unit))
                    else tr("likely a rise of {0} to {1}", Foods.delta(lo.coerceAtLeast(0.0), unit), Foods.delta(hi, unit)),
                    style = mono(13.sp, FontWeight.Medium, C.TextSecondary),
                )
            }
            if (start != null) Text(tr("from your {0} reading, {1}", fmt.time(start.at), Glucose.format(start.mmol, unit)), style = mono(12.sp, color = C.TextMuted))
            ExpectedCurve(curve, s0, actual, unit)
            val outcome = actual.filter { it.first in 30..240 }.maxByOrNull { it.second }
            if (outcome != null && s0 != null) {
                // Expected vs actual: what Dropi guessed from the meals before this one.
                val expected = f.old?.let { learnt.hindcast(it.id)?.expected } ?: (s0 + curve.rise)
                val got = s0 + outcome.second
                Column(Modifier.fillMaxWidth().ruleTop().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("How it went: peaked {0} at {1}.", Glucose.format(got, unit), app.dropi.data.MealCurve.duration(outcome.first)),
                        style = inter(15.sp, FontWeight.SemiBold),
                    )
                    Text(
                        if (kotlin.math.abs(got - expected) <= app.dropi.data.Learning.CLOSE) tr("Dropi expected {0}: close. This meal is now part of what it learns from.", Glucose.format(expected, unit))
                        else tr("Dropi expected {0}, so it was off by {1}. This meal is now part of what it learns from.", Glucose.format(expected, unit), Foods.delta(kotlin.math.abs(got - expected), unit).removePrefix("+")),
                        style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().ruleTop(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (curve.personal) tr("An estimate from this meal and your past meals. Not advice.") else tr("An estimate from this meal's numbers. Not advice."),
                    style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                Text(
                    caps(if (why) tr("Hide") else tr("Why?")),
                    style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.06f),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(Corner.S))
                        .semantics { role = Role.Button }
                        .tap { why = !why }
                        .padding(horizontal = 8.dp),
                )
            }
            if (why) {
                // What shaped the estimate, one reason a line, like the items on a receipt.
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // What a walk after usually does for this person, learnt from their steps.
                    app.dropi.data.Learning.walkEffect(state.measured, state.steps)?.takeIf { it.first < 0 }?.let { (d, _) ->
                        Text(tr("A walk after eating usually takes about {0} off your rise.", Foods.delta(-d, unit).removePrefix("+")), style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
                    }
                    if (curve.reasons.isNotEmpty()) {
                        Text(tr("Shaped by: {0}.", curve.reasons.joinToString(", ")), style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
                    }
                    Text(tr("Back near the start by about {0}.", app.dropi.data.MealCurve.duration(curve.backMin)), style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp))
                    if (!curve.personal) {
                        Text(tr("It learns your own pattern after a few meals with readings before and after."), style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 19.sp))
                    }
                }
            }
        } else if (start != null) {
            Text(tr("Starts from your {0} reading ({1}). Add the foods to see the likely peak.", fmt.time(start.at), Glucose.format(start.mmol, unit)), style = inter(15.sp, color = C.TextSecondary))
        }
    }

    // ---- More, folded ----
    if (meal.items.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .ruleTop()
                    .semantics(mergeDescendants = true) { role = Role.Button; stateDescription = if (more) tr("Expanded") else tr("Collapsed") }
                    .tap { more = !more }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("More: veg first, a walk after, rich meal, say a change"), style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                FoldChevron(more, C.TextSecondary)
            }
            Fold(more) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(tr("Anything else with it?"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        app.dropi.data.MealTags.ALL.forEach { t ->
                            val on = t.key in meal.tags
                            Chip(t.label, selected = on) {
                                Haptics.tap(view)
                                MealWork.update(meal.copy(tags = if (on) meal.tags - t.key else meal.tags + t.key))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).ruleTop().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Rich meal"), style = inter(15.sp, FontWeight.SemiBold))
                            Text(tr("Fatty or creamy: the rise comes later, so the after-eating check waits 3 h."), style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 17.sp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = meal.rich,
                            onCheckedChange = { Haptics.toggle(view, it); MealWork.update(meal.copy(rich = it)) },
                            colors = dropiSwitchColors(),
                            modifier = Modifier.semantics { contentDescription = tr("Rich meal, check at 3 hours") },
                        )
                    }
                    if (Ai.usable) {
                        Column(Modifier.fillMaxWidth().ruleTop().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldLabel(tr("Not quite right? Say the change"))
                            ChangeBox { MealWork.change(context, it) }
                            if (MealWork.failedChange) Text(tr("Couldn't apply that. Tap the food to change it instead."), style = inter(13.sp, color = C.High))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.55f)))
        }
    }

    if (f.old != null) {
        var sure by remember { mutableStateOf(false) }
        GhostButton(if (sure) tr("Tap again to delete this meal") else tr("Delete this meal"), Modifier.fillMaxWidth(), color = C.Low) {
            if (sure) { state.deleteMeal(f.old); MealWork.discard(); close() } else sure = true
        }
    } else {
        GhostButton(tr("Start again"), Modifier.fillMaxWidth()) { MealWork.discard(); close() }
    }
    SheetHint(tr("Carbs, sugars, protein, fat and how fast carbs hit are estimates to check against labels and your usual amounts. Dropi doesn't suggest doses."))

    val open = editing
    if (open != null && open in meal.items.indices) {
        FoodEditDialog(meal.items[open], score = scores[meal.items[open].key], unit = unit,
            remembered = meal.items[open].key in usual,
            onDismiss = { editing = null },
            onRemove = { MealWork.update(meal.copy(items = meal.items.filterIndexed { k, _ -> k != open })); editing = null },
        ) { item, remember ->
            // A changed guess teaches the next one (photo and words meals).
            MealWork.correct(context, meal, meal.items[open], item)
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
    val presets = listOf(0 to tr("Now"), 15 to tr("{0} min ago", 15), 30 to tr("{0} min ago", 30), 60 to tr("{0} h ago", 1))
    val onPreset = presets.firstOrNull { kotlin.math.abs(it.first - minsAgo) <= 2 }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.XL))
                .background(C.Sheet)
                .border(1.dp, C.Border, RoundedCornerShape(Corner.XL))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Which meal?"), style = heading(20.sp), modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Names are saved in English; only how they look is translated.
                app.dropi.data.MealEntry.NAMES.forEach { n -> Chip(mealNameShown(n), selected = n == name) { MealWork.setName(n) } }
            }
            Text(
                tr("Ate at {0}", fmt.time(ateAt)),
                style = heading(20.sp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).ruleTop().padding(top = 12.dp).semantics { heading() },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { (m, label) -> Chip(label, selected = onPreset?.first == m) { MealWork.setWhen(now - m * 60_000L) } }
                Chip(if (onPreset == null) fmt.time(ateAt) else tr("Other time"), icon = R.drawable.lc_clock, selected = onPreset == null) { pickingTime = true }
            }
            PrimaryButton(tr("Done"), Modifier.fillMaxWidth()) { onDone() }
        }
    }
    if (pickingTime) WhenPicker(ateAt, zone, onDismiss = { pickingTime = false }) { t -> MealWork.setWhen(t); pickingTime = false }
}

/** One food on the receipt: its name, a dotted leader to its carbs, and a mono line of portion and what's in it. */
@Composable
private fun FoodRow(item: MealItem, score: Foods.Score?, nutrition: Boolean, onClick: () -> Unit) {
    val rise = score?.takeIf { it.ranked }?.let { Rise.of(it.rise) }
    val looking = item.key in MealWork.filling
    val g = { v: Double -> app.dropi.data.Grams.text(v) }
    val details = when {
        looking -> listOf(item.portionShown(), tr("looking up its numbers…"))
        else -> listOfNotNull(
            item.portionShown(),
            if (item.sugaryDrink) tr("sugary drink") else item.giValue?.takeIf { item.carbs > 0 }?.let { app.dropi.data.Gi.speed(it).word.lowercase() },
            item.protein?.takeIf { it > 0 && nutrition }?.let { tr("{0} g protein", g(it)) },
            item.fat?.takeIf { it > 0 && nutrition }?.let { tr("{0} g fat", g(it)) },
        )
    }
    val spoken = buildString {
        append(tr("{0}, {1} grams of carbs, {2}", Foods.display(item.name), g(item.carbs), details.joinToString(", ")))
        if (item.usual) append(tr(", your usual amount"))
        if (rise != null) append(tr(", {0} last time", rise.word.lowercase()))
    }
    // A line of the receipt: "Toast ······ 28 g", then the amount and what's in it in mono.
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val dots = C.Border
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = spoken }
            .tap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                Foods.display(item.name),
                style = inter(15.sp, FontWeight.SemiBold),
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
                        val from = l.getLineRight(last) + 8.dp.toPx()
                        val to = size.width - 8.dp.toPx()
                        if (to - from < 4.dp.toPx()) return@drawBehind
                        val y = l.getLineBottom(last) - 6.dp.toPx()
                        drawLine(
                            dots, Offset(from, y), Offset(to, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.dp.toPx())),
                        )
                    },
            )
            Text(
                if (looking) "…" else tr("{0} g", g(item.carbs)),
                style = mono(14.sp, FontWeight.SemiBold, C.Text),
                maxLines = 1,
                modifier = Modifier.alignBy(LastBaseline),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rise != null) {
                RangeSquare(riseColor(rise))
                Spacer(Modifier.width(6.dp))
            }
            if (item.usual) {
                Text(caps(tr("Your usual")), style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.04f), maxLines = 1)
                Text(" · ", style = mono(12.sp, color = C.TextMuted))
            }
            Text(
                details.joinToString(" · "),
                style = mono(12.sp, color = if (looking) C.AccentText else C.TextMuted).copy(lineHeight = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** A dashed tear across the meal's receipt, above its totals. */
@Composable
private fun MealTear(modifier: Modifier = Modifier) {
    val dash = C.Border
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        drawLine(
            dash, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** "sugars ········ 12 g": a receipt line in mono with a dotted leader between. */
@Composable
private fun LeaderLine(label: String, value: String) {
    val dots = C.Border
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = mono(13.sp, color = C.TextSecondary), maxLines = 1)
        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp)) {
            val y = size.height - 1.dp.toPx()
            drawLine(
                dots, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.dp.toPx())),
            )
        }
        Text(value, style = mono(13.sp, FontWeight.SemiBold, C.Text), maxLines = 1)
    }
}

/** A field's small label, in mono capitals. */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(caps(text), style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f), modifier = modifier)
}

/**
 * A meal's name as shown. Names are saved in English ("Breakfast"…), so only Dropi's own
 * defaults are translated; a name the person typed is shown as it is.
 */
private fun mealNameShown(name: String): String = when (name) {
    "Breakfast" -> tr("Breakfast")
    "Lunch" -> tr("Lunch")
    "Dinner" -> tr("Dinner")
    "Snack" -> tr("Snack")
    else -> name
}

/** "Save breakfast": a whole phrase per default name, so Icelandic can use the right case. */
private fun saveMealText(name: String): String = when (name) {
    "Breakfast" -> tr("Save breakfast")
    "Lunch" -> tr("Save lunch")
    "Dinner" -> tr("Save dinner")
    "Snack" -> tr("Save snack")
    else -> tr("Save {0}", name.lowercase())
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
            .clip(RoundedCornerShape(Corner.M))
            .border(1.dp, C.Border, RoundedCornerShape(Corner.M))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) Text(tr("e.g. I only ate half the rice"), style = inter(15.sp, color = C.TextMuted))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                textStyle = inter(15.sp, color = C.Text),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("Change to the meal") },
            )
        }
        val icon = if (text.isBlank() && voice != null) R.drawable.lc_mic else R.drawable.lc_send
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics { role = Role.Button; contentDescription = if (icon == R.drawable.lc_mic) tr("Say the change") else tr("Apply the change") }
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
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Corner.M))
            .border(1.dp, C.Border, RoundedCornerShape(Corner.M))
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
        StepButton(R.drawable.lc_minus, tr("{0} less", step)) { onChange((value - step).coerceAtLeast(0)) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
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
                modifier = Modifier.widthIn(min = 80.dp, max = 140.dp).semantics { contentDescription = tr("{0}, {1}. Edit to type a number", label, value) },
            )
            Text("g", style = mono(15.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.padding(bottom = 10.dp).clearAndSetSemantics {})
        }
        StepButton(R.drawable.lc_plus, tr("{0} more", step)) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(Corner.M))
            .border(1.dp, C.Border, RoundedCornerShape(Corner.M))
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
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
                        .clip(RoundedCornerShape(Corner.M))
                        .border(if (highlight) 1.5.dp else 1.dp, if (highlight) C.AccentText else C.Border, RoundedCornerShape(Corner.M))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("–", style = numeral(22.sp, C.TextMuted))
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = tr("{0} in grams, {1}. Decimals allowed", label, value?.let { app.dropi.data.Grams.text(it) } ?: tr("not set"))
            },
        )
    }
}

/** Carbs, sugars, protein, fat and fibre in two columns of boxes. */
@Composable
private fun NumbersGrid(item: MealItem, onChange: (MealItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DecimalBox(tr("Carbs"), item.carbs, Modifier.weight(1f), highlight = true) { onChange(item.copy(carbs = it ?: 0.0)) }
            DecimalBox(tr("of which sugars"), item.sugars, Modifier.weight(1f)) { onChange(item.copy(sugars = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DecimalBox(tr("Protein"), item.protein, Modifier.weight(1f)) { onChange(item.copy(protein = it)) }
            DecimalBox(tr("Fat"), item.fat, Modifier.weight(1f)) { onChange(item.copy(fat = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DecimalBox(tr("Fibre"), item.fibre, Modifier.weight(1f)) { onChange(item.copy(fibre = it)) }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The five numbers for a food as one tidy row, for the add-a-food preview. */
@Composable
private fun NumbersPreview(item: MealItem?) {
    Row(Modifier.fillMaxWidth()) {
        listOf(tr("carbs") to item?.carbs, tr("sugars") to item?.sugars, tr("fibre") to item?.fibre, tr("protein") to item?.protein, tr("fat") to item?.fat).forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (item == null) Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(26.dp).clip(RoundedCornerShape(2.dp)).background(C.SurfaceLight))
                else Text(v?.let { app.dropi.data.Grams.text(it) } ?: "–", style = numeral(22.sp), maxLines = 1)
                // Inter here, not mono: five Icelandic words ("kolvetni") have to fit across a narrow dialog.
                Text(label, style = inter(13.sp, color = C.TextSecondary), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
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
                .clip(RoundedCornerShape(Corner.XL))
                .background(C.Sheet)
                .border(1.dp, C.Border, RoundedCornerShape(Corner.XL))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
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
            portion == null && u == null && known != null -> { base = known; from = tr("Looked up before") }
            portion != null && known != null -> { base = known; from = tr("Looked up before") }
            portion == null && u != null -> {
                base = MealItem(key, u.portion, carbs = u.carbs, sugars = u.sugars, protein = u.protein, fat = u.fat, usual = true); from = tr("Your usual amount")
            }
            portion == null && basic != null -> { base = basic.copy(name = key); from = tr("Found: {0}, from Dropi's food list", app.dropi.data.FoodNames.shown(basic.name)) }
            app.dropi.work.Ai.usable -> {
                base = null
                if (askNow == 0) kotlinx.coroutines.delay(700) // wait for a pause in typing
                looking = true
                val found = MealWork.lookup(context, key, portion)
                looking = false
                base = found?.copy(name = key, portion = portion ?: found.portion)
                from = if (found != null) tr("Estimated by the on-device AI. Check it if you know better.") else ""
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
        Text(tr("Add a food"), style = heading(24.sp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(tr("What is it?"))
            FieldBox(name, tr("e.g. hard boiled egg"), label = tr("What is it?"), onDone = { askNow++ }) { name = it.take(40) }
        }

        if (key.length >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(tr("How much?"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val b = base
                    if (b != null && !other) {
                        listOf(1.0, 2.0, 3.0).forEach { q ->
                            Chip(b.withQty(q).portionShown(), selected = qty == q) { Haptics.tap(view); qty = q; edited = null }
                        }
                    }
                    Chip(tr("Other amount"), selected = other || b == null) { other = !other || b == null; edited = null }
                }
                if (other || base == null) FieldBox(otherPortion, tr("e.g. 150 g, 1 bowl, a handful"), label = tr("How much"), onDone = { askNow++ }) { otherPortion = it.take(30); other = true }
            }

            Column(
                Modifier.fillMaxWidth().ruleTop().padding(top = 14.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = C.AccentText, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Not in Dropi's list. Working it out on your phone…"), style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary))
                    }
                    edited != null -> FieldLabel(tr("Your numbers"))
                    preview != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.lc_check, C.AccentText, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(from, style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                    }
                    app.dropi.work.Ai.usable -> Text(tr("Looks it up when you stop typing."), style = inter(13.sp, color = C.TextSecondary))
                    else -> Text(tr("Not in Dropi's list. Type the numbers from the packet or your own amounts."), style = inter(13.sp, color = C.TextSecondary))
                }
                NumbersPreview(if (looking || !known) null else current)
                if (known) Text(tr("Grams for {0}.", current.portionShown()), style = mono(12.sp, color = C.TextMuted))
                if (looking) Text(tr("You can add it now; the numbers land on the meal when they're ready."), style = inter(13.sp, color = C.TextMuted))
                if (!numbersOpen) {
                    GhostButton(tr("Change the numbers"), color = C.AccentText) { numbersOpen = true }
                }
            }
            if (numbersOpen) NumbersGrid(current) { edited = it }
        }

        PrimaryButton(
            when {
                key.length < 2 -> tr("Add")
                known -> tr("Add {0} · {1} g carbs", Foods.display(key), app.dropi.data.Grams.text(current.carbs))
                !app.dropi.work.Ai.usable -> tr("Add {0} with 0 g carbs", Foods.display(key))
                else -> tr("Add {0}", Foods.display(key))
            },
            Modifier.fillMaxWidth(),
            enabled = key.length >= 2,
        ) { onAdd(current.copy(name = key), known) }
        GhostButton(tr("Cancel"), Modifier.fillMaxWidth()) { onDismiss() }
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
            Spacer(Modifier.width(12.dp))
            Text(item.portionShown(), style = mono(13.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.padding(bottom = 5.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0, 3.0).forEach { q ->
                Chip(initial.withQty(q).portionShown(), selected = item.qty == q) {
                    Haptics.tap(view)
                    item = item.withQty(q)
                }
            }
        }
        Text(tr("Grams for the whole amount. Decimals are fine: 0,8 or 0.8."), style = inter(13.sp, color = C.TextMuted))
        NumbersGrid(item) { item = it }
        if (initial.carbs != item.carbs && initial.qty == item.qty) {
            Text(tr("Guessed {0} g carbs. You changed it to {1} g.", app.dropi.data.Grams.text(initial.carbs), app.dropi.data.Grams.text(item.carbs)), style = inter(13.sp, color = C.TextMuted))
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).ruleTop().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(tr("Remember as my {0}", Foods.display(initial.name).lowercase()), style = inter(15.sp, FontWeight.SemiBold))
                Text(tr("Next time: {0} g carbs for {1}", app.dropi.data.Grams.text(item.carbs / item.qty.coerceAtLeast(0.1)), app.dropi.data.Portions.shown(item.portion)), style = mono(12.sp, color = C.TextMuted))
            }
            Switch(checked = keep, onCheckedChange = { Haptics.toggle(view, it); keep = it }, colors = dropiSwitchColors(),
                modifier = Modifier.semantics { contentDescription = tr("Remember this amount") })
        }

        if (score != null) {
            Column(
                Modifier.fillMaxWidth().ruleTop().padding(top = 12.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tr("After {0}, last time", Foods.display(initial.name).lowercase()), style = inter(15.sp, FontWeight.SemiBold))
                if (!score.ranked) {
                    Text(tr("1 meal so far: {0} {1}. Two make a pattern.", Foods.delta(score.rise, unit), unit.label), style = mono(12.sp, color = C.TextMuted))
                } else {
                    val r = Rise.of(score.rise)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Foods.delta(score.rise, unit), style = numeral(24.sp))
                        Spacer(Modifier.width(8.dp))
                        RangeSquare(riseColor(r))
                        Spacer(Modifier.width(6.dp))
                        Text(trn(score.meals, "{1}, {0} meal", "{1}, {0} meals", r.word), style = mono(12.sp, color = C.TextSecondary))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton(tr("Remove"), Modifier.weight(1f), color = C.Low) { onRemove() }
            PrimaryButton(tr("Done"), Modifier.weight(2f)) { onDone(item, keep) }
        }
    }
}

/**
 * The expected rise as a soft curve over the hours after eating, with the start as a baseline
 * and, for a logged meal, what actually happened as dots on top.
 */
@Composable
private fun ExpectedCurve(curve: app.dropi.data.MealCurve.Curve, start: Double?, actual: List<Pair<Int, Double>>, unit: app.dropi.data.GlucoseUnit) {
    // The shared chart style: the expected curve is the main (ink) line over a grey spread.
    val line = Chart.ink
    val fill = Chart.spreadMost
    val base = C.Border
    val dot = C.Text
    val muted = C.TextMuted
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = mono(12.sp, color = muted)
    val endMin = maxOf(curve.backMin + 30, actual.maxOfOrNull { it.first } ?: 0, 180).coerceAtMost(300)
    val top = maxOf(curve.rise, actual.maxOfOrNull { it.second } ?: 0.0, 1.0) * 1.15
    val bottom = minOf(0.0, actual.minOfOrNull { it.second } ?: 0.0) - 0.2
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics {
                val d = Foods.delta(curve.rise, unit)
                val peak = app.dropi.data.MealCurve.duration(curve.peakMin)
                contentDescription =
                    if (actual.isNotEmpty()) tr("Expected curve: rises about {0} {1}, peaking around {2} after eating, with your actual readings shown as dots", d, unit.label, peak)
                    else tr("Expected curve: rises about {0} {1}, peaking around {2} after eating", d, unit.label, peak)
            },
    ) {
        val labelH = 18.dp.toPx()
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
                Chart.lineW.toPx(), cap = StrokeCap.Round,
                pathEffect = if (actual.isNotEmpty()) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            ),
        )
        actual.filter { it.first in 0..endMin }.forEach { (mm, v) ->
            with(Chart) { readingDot(dot, androidx.compose.ui.geometry.Offset(x(mm.toDouble()), y(v)), C.Sheet) }
        }
        // Hour marks along the bottom.
        var hr = 0
        while (hr * 60 <= endMin) {
            val t = measurer.measure(if (hr == 0) tr("ate") else tr("{0} h", hr), labelStyle)
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


/**
 * "Portion: Smaller · As guessed · Bigger" over a photo meal: scales every guessed food (not the
 * person's usual amounts) by ¾ or 1⅓. Picking again re-scales from the last pick, never compounding.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PortionPick(meal: app.dropi.data.Meal) {
    val view = androidx.compose.ui.platform.LocalView.current
    var factor by remember { mutableStateOf(1.0) }
    val options = listOf(0.75 to tr("Smaller"), 1.0 to tr("As guessed"), 1.33 to tr("Bigger"))
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("How big was the portion?"), style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (q, label) ->
                Chip(label, selected = factor == q) {
                    if (factor == q) return@Chip
                    Haptics.segment(view)
                    val r = q / factor
                    fun sc(v: Double?) = v?.let { app.dropi.data.Grams.round(it * r) }
                    MealWork.update(meal.copy(items = meal.items.map { i ->
                        if (i.usual) i else i.copy(carbs = app.dropi.data.Grams.round(i.carbs * r), sugars = sc(i.sugars), fibre = sc(i.fibre), protein = sc(i.protein), fat = sc(i.fat))
                    }))
                    factor = q
                }
            }
        }
    }
}
