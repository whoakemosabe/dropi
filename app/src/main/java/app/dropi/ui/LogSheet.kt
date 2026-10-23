package app.dropi.ui

import androidx.activity.compose.BackHandler
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
fun BoxScope.LogSheetHost(state: AppState) {
    val editing = state.editing
    // Keep the last reading on screen while the sheet slides away.
    var last by remember { mutableStateOf(editing) }
    if (editing != null) last = editing

    AnimatedVisibility(
        visible = editing != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.editing = null }
        )
    }
    AnimatedVisibility(
        visible = editing != null,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        last?.let { r ->
            // A fresh form for each reading opened.
            androidx.compose.runtime.key(r.id, r.at) { LogSheet(state, r) }
        }
    }
    if (editing != null) BackHandler { state.editing = null }
}

@Composable
private fun LogSheet(state: AppState, initial: Reading) {
    val context = LocalContext.current
    val view = LocalView.current
    val fmt = remember { Fmt(context, state.zone) }
    val isNew = initial.id == 0L
    val unit = state.unit
    var text by remember { mutableStateOf(if (isNew) "" else Glucose.format(initial.mmol, unit)) }
    var tag by remember { mutableStateOf(initial.tag) }
    var note by remember { mutableStateOf(initial.note) }
    var offsetMin by remember { mutableStateOf(0) }
    val baseAt = remember { mutableLongStateOf(initial.at) }
    var confirmDelete by remember { mutableStateOf(false) }

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

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .statusBarsPadding()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(C.Card)
            // Swallow taps so they don't reach the scrim behind.
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            // The keyboard (for the note) lifts the sheet; padding goes before the scroll.
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(C.Border))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "New reading" else "Edit reading", style = inter(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (!isNew) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (confirmDelete) C.Low.copy(alpha = 0.16f) else Color.Transparent)
                        .tap {
                            if (confirmDelete) state.delete(initial) else confirmDelete = true
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
                Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceLight).tap { state.editing = null },
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

        // When: now, or a little earlier for a reading taken before opening the app.
        val at = baseAt.longValue - offsetMin * 60_000L
        if (isNew) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_clock, C.TextMuted, 16.dp)
                listOf(0 to "Now", 15 to "15 min ago", 30 to "30 min ago", 60 to "1 h ago", 120 to "2 h ago").forEach { (m, label) ->
                    Chip(label, selected = offsetMin == m) {
                        offsetMin = m
                        // Moving the time can change the likely moment (e.g. back into fasting).
                        tag = app.dropi.data.Tagging.suggest(baseAt.longValue - m * 60_000L, state.readings, state.zone)
                    }
                }
            }
        } else {
            Row(Modifier.padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.lc_clock, C.TextMuted, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Taken ${fmt.dayTime(initial.at)}", style = inter(13.sp, FontWeight.Medium, C.TextSecondary))
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
            state.save(initial.copy(at = if (isNew) at else initial.at, mmol = v, tag = tag, note = note.trim()))
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
