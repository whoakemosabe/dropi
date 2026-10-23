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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.data.Foods
import app.dropi.data.Glucose
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
        StickySheetBody("What you ate", close) { MealBody(state, d) { close() } }
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
                Text("You can close this. Dropi lets you know when it's done.", style = inter(13.sp, color = C.TextMuted))
            }
        }

        is MealWork.Status.Failed -> {
            Text(s.message, style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp))
            PrimaryButton("Say what you ate", Modifier.fillMaxWidth(), icon = R.drawable.lc_mic) { MealWork.words() }
            GhostButton("Add the foods myself", Modifier.fillMaxWidth()) { MealWork.manual() }
        }

        is MealWork.Status.NeedsGrams -> LabelGrams(s.per100)

        is MealWork.Status.Ready -> Breakdown(state, d.meal ?: Meal(emptyList()), d.forReading, close)
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

/** After a label is read: what it is and how much was eaten. */
@Composable
private fun LabelGrams(p: app.dropi.data.Label.Per100) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf(100) }
    Text(
        "Label read: ${p.carbs?.let { Glucose.formatNumber(it) } ?: "?"} g carbs per 100 g" +
            (p.sugars?.let { ", of which ${Glucose.formatNumber(it)} g sugars" } ?: "") + ".",
        style = inter(15.sp, FontWeight.SemiBold).copy(lineHeight = 22.sp),
    )
    FieldBox(name, "What is it? (e.g. granola)", onChange = { name = it.take(40) })
    Text("How much did you eat?", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
    val options = listOf(25, 50, 100, 150)
    ChoiceGrid(options.map { Choice("$it g") }, options.indexOf(grams).takeIf { it >= 0 }, columns = 4, compact = true) { grams = options[it] }
    NumberRow("Grams eaten", grams, step = 5, max = 2000) { grams = it }
    val carbs = Math.round((p.carbs ?: 0.0) * grams / 100.0)
    Text("That's $carbs g carbs.", style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    PrimaryButton("Next", Modifier.fillMaxWidth()) { MealWork.labelGrams(Foods.normalize(name), grams) }
}

@Composable
private fun Breakdown(state: AppState, meal: Meal, forReading: Long?, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val unit = state.unit
    val scores = remember(state.readings, state.sensor) { Foods.scores(state.readings, state.sensor).associateBy { it.food } }
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val usual = remember(meal) { MealWork.usual(context) }

    // Where meals like this usually take the person, from their own log.
    val start = forReading?.let { id -> state.readings.firstOrNull { it.id == id } } ?: state.startForMeal()
    val est = start?.let { MealHistory.estimate(it.mmol, meal.foods, state.readings, state.sensor) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            start == null -> Text(
                "Log your before-meal reading too, and Dropi shows where meals like this usually take you.",
                style = inter(14.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
            )
            est == null -> {
                Text("Now ${Glucose.format(start.mmol, unit)} ${unit.label}", style = inter(16.sp, FontWeight.SemiBold))
                Text("Not enough meals with these foods yet to say where you'll peak. It learns from each one.", style = inter(13.sp, color = C.TextMuted))
            }
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("Now", style = inter(12.sp, color = C.TextMuted))
                        Text(Glucose.format(est.start, unit), style = numeral(28.sp))
                    }
                    Text("→", style = inter(22.sp, FontWeight.SemiBold, C.AccentText), modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Meals like this usually peak at", style = inter(12.sp, color = C.TextMuted))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("about " + Glucose.format(est.peak, unit), style = numeral(28.sp, C.status(Glucose.status(est.peak, app.dropi.data.MealTag.AFTER_MEAL, state.targets))))
                            Spacer(Modifier.width(6.dp))
                            Text(unit.label, style = inter(12.sp, color = C.TextSecondary), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
                Text(
                    "From your own readings after meals with ${est.foods.take(3).joinToString(" or ") { Foods.display(it) }}, ${est.meals} meals. Not advice.",
                    style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
                )
            }
        }
    }

    // The total, read out when it changes.
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("${meal.carbs}", style = numeral(44.sp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.padding(bottom = 4.dp)) {
            Text("g carbs", style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary))
            val parts = listOfNotNull(meal.sugars?.let { "$it g sugars" }, meal.fibre?.let { "$it g fibre" })
            if (parts.isNotEmpty()) Text("of which " + parts.joinToString(" · "), style = inter(13.sp, color = C.TextSecondary))
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Card)) {
        meal.items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.GlassBorder))
            FoodRow(item, scores[item.key], unit) { editing = i }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .tap { adding = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_plus, C.AccentText, 16.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (meal.items.isEmpty()) "Add a food" else "Add a food it missed", style = inter(15.sp, FontWeight.SemiBold, C.AccentText))
        }
    }

    if (Ai.usable && meal.items.isNotEmpty()) {
        Text("Not quite right? Say the change", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
        ChangeBox { MealWork.change(context, it) }
        if (MealWork.failedChange) Text("Couldn't apply that. Tap the food to change it instead.", style = inter(13.sp, color = C.High))
    }

    if (meal.items.isNotEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(C.Card)
                .border(1.dp, if (meal.rich) C.High.copy(alpha = 0.5f) else C.GlassBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_clock, if (meal.rich) C.High else C.TextMuted, 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (meal.rich) "Rich meal: the rise may come later" else "Rich meal?", style = inter(15.sp, FontWeight.SemiBold))
                Text(
                    if (meal.rich) "High in fat or protein, so the after-meal check moves to 3 h."
                    else "Switch on for fatty or creamy meals. The after-meal check then waits 3 h.",
                    style = inter(12.sp, color = C.TextSecondary).copy(lineHeight = 17.sp),
                )
            }
            Switch(
                checked = meal.rich,
                onCheckedChange = { Haptics.toggle(view, it); MealWork.update(meal.copy(rich = it)) },
                colors = dropiSwitchColors(),
                modifier = Modifier.semantics { contentDescription = "Rich meal, check at 3 hours" },
            )
        }
    }

    PrimaryButton(
        if (meal.items.isEmpty()) "Add a food first" else "Use ${meal.carbs} g carbs",
        Modifier.fillMaxWidth(),
        icon = R.drawable.lc_check,
        enabled = meal.items.isNotEmpty(),
    ) {
        Haptics.confirm(view)
        state.useMeal(meal, forReading)
        MealWork.discard()
        close()
    }
    GhostButton("Start again", Modifier.fillMaxWidth()) { MealWork.discard(); close() }
    SheetHint("Carbs and sugars are estimates to check against labels and your usual amounts. Dropi doesn't suggest doses.")

    val open = editing
    if (open != null && open in meal.items.indices) {
        FoodEditDialog(meal.items[open], isNew = false, score = scores[meal.items[open].key], unit = unit,
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
        FoodEditDialog(MealItem("", "1 portion", carbs = 0), isNew = true, score = null, unit = unit, remembered = false,
            onDismiss = { adding = false }, onRemove = { adding = false },
        ) { item, remember ->
            val withUsual = usual[item.key]?.takeIf { item.carbs == 0 }?.let { u -> item.copy(portion = u.portion, carbs = u.carbs, sugars = u.sugars, usual = true) } ?: item
            if (remember) MealWork.remember(context, withUsual)
            MealWork.update(meal.copy(items = meal.items + withUsual.copy(usual = remember || withUsual.usual)))
            adding = false
        }
    }
}

/** One food: name, portion, carbs and sugars, how it went last time. One sentence for TalkBack. */
@Composable
private fun FoodRow(item: MealItem, score: Foods.Score?, unit: app.dropi.data.GlucoseUnit, onClick: () -> Unit) {
    val rise = score?.takeIf { it.ranked }?.let { Rise.of(it.rise) }
    val spoken = buildString {
        append("${Foods.display(item.name)}, ${item.portionText()}, ${item.carbs} grams of carbs")
        item.sugars?.let { append(", $it grams sugars") }
        if (item.usual) append(", your usual amount")
        if (rise != null) append(", ${rise.word.lowercase()} last time")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = spoken }
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Foods.display(item.name), style = inter(16.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f, fill = false))
                if (item.usual) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your usual",
                        style = inter(11.sp, FontWeight.SemiBold, C.AccentText),
                        modifier = Modifier.border(1.dp, C.AccentText.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.portionText(),
                    style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(C.SurfaceLight).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                if (rise != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(riseColor(rise)))
                    Spacer(Modifier.width(5.dp))
                    Text("${rise.word}, ${score.meals} meals", style = inter(12.sp, color = C.TextMuted))
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${item.carbs}", style = numeral(22.sp))
                Text(" g", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.padding(bottom = 3.dp))
            }
            item.sugars?.let { Text("$it g sugars", style = inter(11.sp, color = C.TextMuted)) }
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
private fun FieldBox(value: String, hint: String, numeric: Boolean = false, onChange: (String) -> Unit) {
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
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
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

/** Change one food: name, how much, its carbs (typed or stepped), and whether to remember it. */
@Composable
private fun FoodEditDialog(
    initial: MealItem,
    isNew: Boolean,
    score: Foods.Score?,
    unit: app.dropi.data.GlucoseUnit,
    remembered: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onDone: (MealItem, Boolean) -> Unit,
) {
    val view = LocalView.current
    var item by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf(Foods.display(initial.name)) }
    var portion by remember { mutableStateOf(initial.portion) }
    var keep by remember { mutableStateOf(remembered || initial.usual) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isNew) "Add a food" else Foods.display(initial.name), style = heading(22.sp))
            if (isNew) FieldBox(name, "Food, e.g. banana") { name = it.take(40) }
            FieldBox(portion, "One portion, e.g. 1 cup") { portion = it.take(30) }

            Text("How much?", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
            val qtys = listOf(0.5, 1.0, 1.5, 2.0)
            ChoiceGrid(qtys.map { Choice(app.dropi.data.Meal.qtyText(it)) }, qtys.indexOf(item.qty).takeIf { it >= 0 }, columns = 4, compact = true) {
                Haptics.tap(view)
                item = item.withQty(qtys[it])
            }

            Text("Carbs (g)", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
            NumberRow("Carbs in grams", item.carbs, step = 5, max = 400) { item = item.copy(carbs = it) }
            val parts = listOfNotNull(item.sugars?.let { "$it g sugars" }, item.fibre?.let { "$it g fibre" })
            if (parts.isNotEmpty()) Text("of which " + parts.joinToString(" · "), style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.align(Alignment.CenterHorizontally))
            if (!isNew && initial.carbs != item.carbs) {
                Text("Guessed ${initial.carbs} g. You changed it to ${item.carbs} g.", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remember as my ${name.ifBlank { "food" }.lowercase()}", style = inter(15.sp, FontWeight.SemiBold))
                    Text("Next time it starts at ${Math.round(item.carbs / item.qty.coerceAtLeast(0.1))} g for $portion", style = inter(12.sp, color = C.TextMuted))
                }
                Switch(checked = keep, onCheckedChange = { Haptics.toggle(view, it); keep = it }, colors = dropiSwitchColors(),
                    modifier = Modifier.semantics { contentDescription = "Remember this amount" })
            }

            if (!isNew) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp).semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("After ${Foods.display(initial.name).lowercase()}, last time", style = inter(14.sp, FontWeight.SemiBold))
                    if (score == null || !score.ranked) {
                        Text(
                            if (score == null) "No measured meals with it yet." else "1 meal so far: ${Foods.delta(score.rise, unit)} ${unit.label}. Two make a pattern.",
                            style = inter(13.sp, color = C.TextMuted),
                        )
                    } else {
                        val r = Rise.of(score.rise)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Foods.delta(score.rise, unit), style = numeral(28.sp))
                            Spacer(Modifier.width(8.dp))
                            Text("${unit.label} on average, ${score.meals} meals", style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(riseColor(r)))
                            Spacer(Modifier.width(6.dp))
                            Text(r.word, style = inter(13.sp, FontWeight.SemiBold))
                        }
                        Text("From before the meal to the highest reading 45 min to 3 h after. Your own log, not advice.", style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(if (isNew) "Cancel" else "Remove", Modifier.weight(1f), color = if (isNew) C.TextSecondary else C.Low) { onRemove() }
                PrimaryButton("Done", Modifier.weight(1f), enabled = name.isNotBlank()) {
                    onDone(item.copy(name = Foods.normalize(name), portion = portion.ifBlank { "1 portion" }), keep)
                }
            }
        }
    }
}
