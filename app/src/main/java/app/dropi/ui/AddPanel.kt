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
 * The + panel: a reading, a photo of the meal, the meal in words, or a packet's label, as four
 * big tiles; a photo already taken below them; and meals logged lately, one tap to log again.
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(tr("Add"), close)
            fun go(action: String) {
                state.addOpen = false
                Launch.mealAction.value = action
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(tr("Reading"), tr("Type or scan your meter"), R.drawable.lc_droplet, Modifier.weight(1f)) {
                    state.addOpen = false
                    state.startNew()
                }
                Tile(tr("Snap a meal"), tr("Photo of your plate"), R.drawable.lc_camera, Modifier.weight(1f)) { go("camera") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(tr("Say what you ate"), tr("Speak or type it"), R.drawable.lc_mic, Modifier.weight(1f)) { go("words") }
                Tile(tr("Scan a label"), tr("Exact carbs from a packet"), R.drawable.lc_scan_text, Modifier.weight(1f)) { go("label") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(Corner.M))
                    .border(1.dp, C.Border, RoundedCornerShape(Corner.M))
                    .semantics { role = Role.Button }
                    .tap { go("barcode") }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_scan_barcode, C.AccentText, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Scan a packet's barcode"), style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Ic(R.drawable.lc_arrow_right, C.TextMuted, 16.dp)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(Corner.M))
                    .border(1.dp, C.Border, RoundedCornerShape(Corner.M))
                    .semantics { role = Role.Button }
                    .tap { go("pick") }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_image, C.AccentText, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("Pick a photo you already took"), style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Ic(R.drawable.lc_arrow_right, C.TextMuted, 16.dp)
            }
            val again = state.recentMeals
            if (again.isNotEmpty()) {
                Text(tr("Again"), style = inter(13.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    again.forEach { m ->
                        val label = m.items.take(2).joinToString(" & ") { Foods.display(it.name) } +
                            (if (m.items.size > 2) " +${m.items.size - 2}" else "") + " · ${app.dropi.data.Grams.text(m.carbs)} g"
                        Text(
                            label,
                            style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .background(C.SurfaceLight)
                                .border(1.dp, C.GlassBorder, RoundedCornerShape(50))
                                .semantics { role = Role.Button; contentDescription = tr("Log again: {0} carbs", label) }
                                .tap {
                                    state.addOpen = false
                                    MealWork.again(m)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, sub: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Column(
        modifier
            .heightIn(min = 124.dp)
            .pressed(press)
            .clip(RoundedCornerShape(Corner.L))
            .background(C.Card)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(Corner.L))
            .semantics(mergeDescendants = true) { role = Role.Button }
            .tap(press, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(Corner.S)).background(C.Accent),
            contentAlignment = Alignment.Center,
        ) { Ic(icon, C.OnAccent, 20.dp) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = inter(17.sp, FontWeight.Bold))
        Text(sub, style = inter(13.sp, color = C.TextMuted))
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
