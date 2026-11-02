package app.dropi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.data.Foods
import app.dropi.work.Ai
import app.dropi.work.MealWork
import app.dropi.work.Scan
import kotlinx.coroutines.launch

/**
 * The + panel: a reading, a photo of the meal, the meal in words, a packet's label or barcode, or
 * a photo already taken, as one ruled list; and meals logged lately, one tap to log again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPanel(state: AppState) {
    if (!state.addOpen) return
    // The + panel is the start of most meal logging: get the on-device model loading now.
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { app.dropi.work.Ai.warm(ctx) }
    SlideSheet(onDismiss = { state.addOpen = false }) { close ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            SheetTitle(tr("Add"), close)
            Spacer(Modifier.height(8.dp))
            fun go(action: String) {
                state.addOpen = false
                Launch.mealAction.value = action
            }
            // Four big ways in, two by two; the reading first, lit.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddTile(tr("Reading"), tr("Type or scan your meter"), R.drawable.lc_droplet, lead = true, modifier = Modifier.weight(1f)) {
                        state.addOpen = false
                        state.startNew()
                    }
                    AddTile(tr("Snap a meal"), tr("Photo of your plate"), R.drawable.lc_camera, modifier = Modifier.weight(1f)) { go("camera") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddTile(tr("Say what you ate"), tr("Speak or type it"), R.drawable.lc_mic, modifier = Modifier.weight(1f)) { go("words") }
                    AddTile(tr("Scan a packet"), tr("Barcode or label: exact carbs"), R.drawable.lc_scan_barcode, modifier = Modifier.weight(1f)) { go("packet") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                AddLink(tr("Pick a photo you took"), R.drawable.lc_image) { go("pick") }
                AddLink(tr("Add foods by hand"), R.drawable.lc_plus) { go("manual") }
            }

            // Quick add: saved meals, saved foods, then meals logged lately. One tap opens the
            // meal filled in, ready to check and save.
            val saved = androidx.compose.runtime.remember { MealWork.usual(ctx) }
            val myMeals = androidx.compose.runtime.remember { MealWork.savedMeals(ctx).entries.reversed().take(3) }
            val rank = androidx.compose.runtime.remember(state.meals) { Foods.recent(state.meals, 100).withIndex().associate { (i, f) -> f to i } }
            val myFoods = saved.entries.sortedWith(compareBy<Map.Entry<String, app.dropi.data.Usual>> { rank[it.key] ?: Int.MAX_VALUE }.thenBy { it.key }).take(4)
            val recent = androidx.compose.runtime.remember(state.meals) {
                state.meals.asReversed().filter { it.meal.items.isNotEmpty() }
                    .distinctBy { e -> e.meal.foods.sorted().joinToString("|") }.take(3)
            }
            if (myMeals.isNotEmpty() || myFoods.isNotEmpty() || recent.isNotEmpty()) {
                val fmt = androidx.compose.runtime.remember(state.zone) { Fmt(ctx, state.zone) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).drawBehind {
                        drawLine(C.Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(caps(tr("Quick add")), style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.06f), modifier = Modifier.weight(1f))
                    if (saved.isNotEmpty() || myMeals.isNotEmpty()) Text(
                        caps(tr("All my foods")) + " →",
                        style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.06f),
                        modifier = Modifier.heightIn(min = 40.dp).semantics { role = Role.Button }.tap {
                            state.addOpen = false
                            state.myFoodsOpen = true
                        }.padding(start = 12.dp, top = 12.dp),
                    )
                }
                if (myMeals.isNotEmpty()) {
                    QuickLabel(tr("My meals"))
                    myMeals.forEach { (name, m) ->
                        QuickRow(name, m.items.joinToString(", ") { Foods.display(it.name).lowercase(app.dropi.L.locale) }, m.carbs, saved = true) {
                            state.addOpen = false
                            MealWork.again(m)
                        }
                    }
                }
                if (myFoods.isNotEmpty()) {
                    QuickLabel(tr("My foods"))
                    myFoods.forEach { (key, u) ->
                        QuickRow(Foods.display(key), app.dropi.data.Portions.shown(u.portion), u.carbs, saved = true) {
                            state.addOpen = false
                            MealWork.withSaved(key, u)
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    QuickLabel(tr("Again · recent meals"))
                    recent.forEach { e ->
                        val m = e.meal
                        val title = m.items.take(2).joinToString(" & ") { Foods.display(it.name) } + (if (m.items.size > 2) " +${m.items.size - 2}" else "")
                        QuickRow(title, mealName(e.name) + " · " + fmt.dayTime(e.at), m.carbs, saved = false) {
                            state.addOpen = false
                            MealWork.again(m)
                        }
                    }
                }
            }
        }
    }
}

/** One of the four big ways in: an icon on top, the name and what it does under it. */
@Composable
private fun AddTile(title: String, sub: String, icon: Int, modifier: Modifier = Modifier, lead: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(Corner.L)
    Column(
        modifier
            .heightIn(min = 92.dp)
            .pressed(press)
            .clip(shape)
            .background(if (lead) C.Accent.copy(alpha = 0.16f) else C.Card)
            .border(if (lead) 1.5.dp else 1.dp, if (lead) C.AccentText.copy(alpha = 0.6f) else C.Border, shape)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ic(icon, if (lead) C.AccentText else C.TextSecondary, 20.dp)
        Column {
            Text(title, style = inter(15.sp, FontWeight.SemiBold), maxLines = 2)
            Text(sub, style = inter(12.sp, color = if (lead) C.TextSecondary else C.TextMuted).copy(lineHeight = 16.sp), maxLines = 2)
        }
    }
}

/** A small way in under the tiles. */
@Composable
private fun AddLink(text: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).semantics(mergeDescendants = true) { role = Role.Button }.tap(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, C.AccentText, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = inter(13.sp, FontWeight.SemiBold, C.AccentText))
    }
}

@Composable
private fun QuickLabel(text: String) {
    Text(caps(text), style = mono(11.sp, color = C.TextMuted, tracking = 0.04f), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** One quick-add line: name, a quiet detail, carbs; one tap opens the meal filled in. */
@Composable
private fun QuickRow(title: String, detail: String, carbs: Double, saved: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressed(press)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(if (saved) R.drawable.lc_bookmark else R.drawable.lc_clock, if (saved) C.AccentText else C.TextMuted, 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = inter(13.sp, color = C.TextMuted), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(tr("{0} g", app.dropi.data.Grams.text(carbs)), style = mono(13.sp, color = C.TextSecondary))
    }
}

/** One way to add something: a plain icon, the name and what it does, an arrow; ruled above. */
@Composable
private fun AddActionRow(title: String, sub: String?, icon: Int, lead: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (sub != null) 64.dp else 56.dp)
            .ruleTop(!lead)
            .pressed(press)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, if (lead) C.AccentText else C.TextSecondary, 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold))
            if (sub != null) Text(sub, style = inter(13.sp, color = C.TextMuted))
        }
        Spacer(Modifier.width(12.dp))
        Ic(R.drawable.lc_arrow_right, C.TextMuted, 16.dp)
    }
}

/**
 * Where the camera, photo picker and label scan start from, wherever the request came from
 * (the + panel, a reminder, a shortcut, the widget, a shared photo). Always on screen, so the
 * result lands even after the panel that asked has closed.
 */
@Composable
fun MealLaunchers(state: AppState) {
    val context = LocalContext.current
    // Saved across the camera app taking over: Android may close Dropi while the camera is open,
    // and the photo's address must survive that or the result is lost.
    var shot by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var labelShot by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var meterShot by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // What to do once the on-device model is ready (null: nothing waiting).
    var afterAi by remember { mutableStateOf<String?>(null) }

    // Some camera apps report "cancelled" even after saving the photo, so the file itself decides.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = shot
        val uri = u?.let { android.net.Uri.parse(it) }
        if (uri != null && (ok || Scan.saved(context, uri.lastPathSegment ?: ""))) MealWork.fromPhoto(context, uri)
    }
    // One photo of a packet, either side: barcode first, then the label.
    val packetCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = labelShot
        if (u != null && (ok || Scan.saved(context, "packet.jpg"))) MealWork.fromPacket(context, android.net.Uri.parse(u))
    }
    val packetPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) MealWork.fromPacket(context, uri)
    }
    val barcodeCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = labelShot
        if (u != null && (ok || Scan.saved(context, "barcode.jpg"))) MealWork.fromBarcode(context, android.net.Uri.parse(u))
    }
    val labelCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = labelShot
        if (u != null && (ok || Scan.saved(context, "label.jpg"))) MealWork.fromLabel(context, android.net.Uri.parse(u))
    }
    // Scan meter: here rather than in the log sheet, so the result still lands if Android closed
    // Dropi while the camera was open (the log sheet opens again with the number in it).
    val meterCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = meterShot
        if (u == null || !(ok || Scan.saved(context, "meter.jpg"))) return@rememberLauncherForActivityResult
        if (state.editing == null) state.startNew()
        state.meterScan = AppState.MeterScan(busy = true)
        scope.launch {
            val v = try { Scan.read(context, android.net.Uri.parse(u), state.unit) } catch (e: Throwable) { null }
            state.meterScan = AppState.MeterScan(value = v, problem = if (v == null) Scan.problem else null)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) MealWork.fromPhoto(context, uri)
    }
    // A label or barcode from a photo already taken (when the camera hand-off doesn't work).
    val labelPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) MealWork.fromLabel(context, uri)
    }
    val barcodePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) MealWork.fromBarcode(context, uri)
    }

    fun run(action: String) {
        when (action) {
            "camera" -> {
                // Load the model while the camera is open, so it's ready when the photo comes back.
                app.dropi.work.Ai.warm(context)
                // A file per photo, so a second photo never replaces one still being looked at.
                val u = Scan.photoUri(context, "plate-${System.currentTimeMillis()}.jpg")
                shot = u.toString()
                try { camera.launch(u) } catch (e: android.content.ActivityNotFoundException) { state.mealNote = tr("No camera app found.") }
            }
            "pick" -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "packet" -> {
                val u = Scan.photoUri(context, "packet.jpg")
                labelShot = u.toString()
                try { packetCamera.launch(u) } catch (e: android.content.ActivityNotFoundException) { state.mealNote = tr("No camera app found.") }
            }
            "packet-pick" -> packetPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "manual" -> MealWork.manual()
            "label-pick" -> labelPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "barcode-pick" -> barcodePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "words" -> { app.dropi.work.Ai.warm(context); MealWork.words() }
            "label" -> {
                val u = Scan.photoUri(context, "label.jpg")
                labelShot = u.toString()
                try { labelCamera.launch(u) } catch (e: android.content.ActivityNotFoundException) { state.mealNote = tr("No camera app found.") }
            }
            "barcode" -> {
                val u = Scan.photoUri(context, "barcode.jpg")
                labelShot = u.toString()
                try { barcodeCamera.launch(u) } catch (e: android.content.ActivityNotFoundException) { state.mealNote = tr("No camera app found.") }
            }
            "meter" -> {
                val u = Scan.photoUri(context, "meter.jpg")
                meterShot = u.toString()
                try { meterCamera.launch(u) } catch (e: android.content.ActivityNotFoundException) {
                    state.meterScan = AppState.MeterScan(problem = tr("No camera app found."))
                }
            }
            "draft" -> if (MealWork.draft != null) MealWork.open = true
                else state.mealNote = tr("That meal was lost when Android closed Dropi. Please log it again.")
            else -> if (action.startsWith("shared:")) MealWork.fromPhoto(context, android.net.Uri.parse(action.removePrefix("shared:")))
        }
    }

    val want = Launch.mealAction.value
    LaunchedEffect(want) {
        if (want == null) return@LaunchedEffect
        Launch.mealAction.value = null
        state.addOpen = false
        // A label or meter is read by the text scanner and works on every phone; the rest needs the model.
        val needsModel = want !in setOf("label", "barcode", "label-pick", "barcode-pick", "draft", "meter")
        if (needsModel && !Ai.usable) {
            Ai.refresh(context)
            if (!Ai.usable) { afterAi = want; return@LaunchedEffect }
        }
        run(want)
    }

    afterAi?.let { pending ->
        AiNeededDialog(
            when (pending) { "words" -> tr("Saying what you ate"); else -> tr("Meal photos") },
            when (pending) { "words" -> tr("Say it"); else -> tr("Take a photo") },
            onDismiss = { afterAi = null },
            readyIcon = if (pending == "words") R.drawable.lc_mic else R.drawable.lc_camera,
        ) {
            afterAi = null
            run(pending)
        }
    }
}
