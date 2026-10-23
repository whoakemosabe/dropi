package app.dropi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.dropi.work.Scan
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealTag
import app.dropi.data.Reading

/**
 * The log sheet, sliding up over Today: a big number, the moment (pre-picked), when, an
 * optional note, and a keypad. New readings and edits share it; an edit can also be deleted.
 */
@Composable
fun LogSheetHost(state: AppState) {
    val editing = state.editing ?: return
    // A fresh form for each reading opened.
    key(editing.id, editing.at) {
        SlideSheet(onDismiss = { state.editing = null }) { close -> LogSheet(state, editing, close) }
    }
}

@Composable
private fun LogSheet(state: AppState, initial: Reading, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val fmt = remember { Fmt(context, state.zone) }
    val isNew = initial.id == 0L
    val unit = state.unit
    var text by remember { mutableStateOf(if (isNew) "" else Glucose.format(initial.mmol, unit)) }
    var tag by remember { mutableStateOf(initial.tag) }
    var note by remember { mutableStateOf(initial.note) }
    // When it was taken: now, a quick "15 min ago", or any day and time in the last week.
    var offsetMin by remember { mutableStateOf<Int?>(if (isNew) 0 else null) }
    var at by remember { mutableLongStateOf(initial.at) }
    var picking by remember { mutableStateOf(false) }
    val openedAt = remember { initial.at }
    var confirmDelete by remember { mutableStateOf(false) }

    // Scan the meter: the camera app takes a photo, the number is read on the phone.
    val scope = rememberCoroutineScope()
    var scanUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = scanUri
        if (ok && u != null) {
            scanNote = "Reading the number…"
            scope.launch {
                val v = Scan.read(context, u, unit)
                if (v != null) {
                    text = Glucose.format(v, unit)
                    scanNote = "Scanned. Check it matches your meter."
                    Haptics.confirm(view)
                } else {
                    scanNote = "Couldn't read the number. Type it instead."
                    Haptics.reject(view)
                }
            }
        }
    }

    val mmol = Glucose.parse(text, unit)
    val status = mmol?.let { Glucose.status(it, tag, state.targets) }
    val outOfRange = text.isNotEmpty() && !text.endsWith(".") && mmol == null && text.toDoubleOrNull() != null

    // A tiny shake when a key can't be added.
    val shake = remember { Animatable(0f) }
    var shakes by remember { mutableStateOf(0) }
    LaunchedEffect(shakes) {
        if (shakes > 0) {
            shake.snapTo(1f)
            shake.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 900f))
        }
    }

    // Scrolls inside the sheet; at the top, a downward swipe pulls the sheet closed.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "New reading" else "Edit reading", style = inter(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (isNew) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(C.SurfaceLight)
                        .tap {
                            val u = Scan.photoUri(context)
                            scanUri = u
                            try { camera.launch(u) } catch (e: android.content.ActivityNotFoundException) { scanNote = "No camera app found." }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(R.drawable.lc_camera, C.TextSecondary, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Scan meter", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                }
                Spacer(Modifier.width(8.dp))
            }
            if (!isNew) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (confirmDelete) C.Low.copy(alpha = 0.16f) else Color.Transparent)
                        .tap {
                            if (confirmDelete) { state.delete(initial); close() } else confirmDelete = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(R.drawable.lc_trash, C.Low, 16.dp)
                    if (confirmDelete) {
                        Spacer(Modifier.width(6.dp))
                        Text("Tap to delete", style = inter(13.sp, FontWeight.SemiBold, C.Low))
                    }
                }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceLight).tap { close() },
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_x, C.TextSecondary, 18.dp, contentDescription = "Close") }
        }

        // The number, coloured by where it lands as you type.
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val color = status?.let { C.status(it) } ?: if (text.isEmpty()) C.TextMuted.copy(alpha = 0.5f) else C.Text
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.graphicsLayer { translationX = shake.value * 10.dp.toPx() * (if (shakes % 2 == 0) 1f else -1f) },
            ) {
                Text(
                    text.ifEmpty { if (unit == GlucoseUnit.MMOL) "0.0" else "0" },
                    style = inter(64.sp, FontWeight.ExtraBold, color, -0.03f),
                )
                Spacer(Modifier.width(8.dp))
                Text(unit.label, style = inter(15.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.padding(bottom = 14.dp))
            }
            when {
                status != null -> StatusPill(status)
                outOfRange -> Text("Meters read ${Glucose.rangeLabel(unit)}", style = inter(13.sp, FontWeight.Medium, C.High))
                else -> Text("Type the number on your meter", style = inter(13.sp, color = C.TextMuted))
            }
            scanNote?.let { Text(it, style = inter(12.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.padding(top = 6.dp)) }
            if (initial.imported) {
                Text("From Health Connect", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.padding(top = 6.dp))
            }
        }

        // The moment: pre-picked from the time of day and your last reading.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealTag.entries.forEach { t ->
                Chip(t.label, icon = t.icon(), selected = t == tag) { tag = t }
            }
        }

        // When: now, a little earlier, or a forgotten one from hours or days ago.
        fun moveTo(t: Long) {
            at = t
            // A new reading's moment follows its time (an 07:00 one is fasting, a 22:00 one bedtime).
            if (isNew) tag = app.dropi.data.Tagging.suggest(t, state.readings, state.zone)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_clock, C.TextMuted, 16.dp)
            if (isNew) {
                listOf(0 to "Now", 15 to "15 min ago", 30 to "30 min ago", 60 to "1 h ago").forEach { (m, label) ->
                    Chip(label, selected = offsetMin == m) {
                        offsetMin = m
                        moveTo(openedAt - m * 60_000L)
                    }
                }
            }
            Chip(
                if (offsetMin == null) fmt.dayTime(at) else "Pick a time…",
                icon = R.drawable.lc_pencil,
                selected = offsetMin == null,
            ) { picking = true }
        }
        if (picking) {
            WhenPicker(at, state.zone, onDismiss = { picking = false }) { t ->
                picking = false
                offsetMin = null
                moveTo(t)
            }
        }

        // An optional note: what you ate, how you feel, a missed dose.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.SurfaceLight)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (note.isEmpty()) Text("Add a note (optional)", style = inter(14.sp, color = C.TextMuted))
            BasicTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                singleLine = true,
                textStyle = inter(14.sp, color = C.Text),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Keypad(
            decimal = unit == GlucoseUnit.MMOL,
            modifier = Modifier.padding(top = 14.dp),
        ) { key ->
            val next = Glucose.type(text, key, unit)
            if (next == text && key != '⌫') {
                shakes++
                Haptics.reject(view)
            } else {
                Haptics.tap(view)
            }
            text = next
        }

        PrimaryButton(
            if (isNew) "Save reading" else "Save changes",
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
            icon = R.drawable.lc_check,
            enabled = mmol != null,
        ) {
            val v = mmol ?: return@PrimaryButton
            Haptics.confirm(view)
            state.save(initial.copy(at = at, mmol = v, tag = tag, note = note.trim()))
            close()
        }
    }
}

/** 1–9, then point, 0 and backspace. The point is greyed out for mg/dL (whole numbers). */
@Composable
private fun Keypad(decimal: Boolean, modifier: Modifier = Modifier, onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", ".0⌫")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val enabled = k != '.' || decimal
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(C.SurfaceLight)
                            .clickable(enabled = enabled) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '⌫') {
                            Ic(R.drawable.lc_delete, C.TextSecondary, 22.dp, contentDescription = "Delete")
                        } else {
                            Text(
                                k.toString(),
                                style = inter(24.sp, FontWeight.SemiBold, if (enabled) C.Text else C.TextMuted.copy(alpha = 0.3f)),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
