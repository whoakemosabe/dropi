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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.dropi.work.Scan
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.dropi.L
import app.dropi.R
import app.dropi.tr
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
    // What's typed, always with a point ("6.4"); shown with the app's decimal mark (L.decimal).
    var text by remember { mutableStateOf(if (isNew) "" else Glucose.plain(initial.mmol, unit)) }
    var tag by remember { mutableStateOf(initial.tag) }
    var note by remember { mutableStateOf(initial.note) }
    // When it was taken: now, a quick "15 min ago", or any day and time in the last week.
    var offsetMin by remember { mutableStateOf<Int?>(if (isNew) 0 else null) }
    var at by remember { mutableLongStateOf(initial.at) }
    var picking by remember { mutableStateOf(false) }
    val openedAt = remember { initial.at }
    var confirmDelete by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    // Optional details, edited in their own window.
    var details by remember { mutableStateOf(Details(initial.carbs, initial.insulin, initial.insulinKind, initial.meds, initial.exercise, initial.foods, initial.meal)) }

    // Scan the meter: the camera app takes a photo (launched app-wide, see MealLaunchers), and
    // the number read on the phone comes back here.
    var scanNote by remember { mutableStateOf<String?>(null) }
    val scan = state.meterScan
    LaunchedEffect(scan) {
        if (scan == null || !isNew) return@LaunchedEffect
        when {
            scan.busy -> scanNote = tr("Reading the number…")
            scan.value != null -> {
                text = Glucose.plain(scan.value, unit)
                scanNote = tr("Scanned. Check it matches your meter.")
                Haptics.confirm(view)
                state.meterScan = null
            }
            else -> {
                scanNote = scan.problem?.let {
                    tr("Couldn't read the number ({0}). Try again closer, straight on and without glare, or type it.", it)
                } ?: tr("Couldn't read the number. Try again closer, straight on and without glare, or type it.")
                Haptics.reject(view)
                state.meterScan = null
            }
        }
    }

    val mmol = Glucose.parse(text, unit)
    val status = mmol?.let { Glucose.status(it, tag, state.targets) }
    val outOfRange = text.isNotEmpty() && !text.endsWith(".") && mmol == null && text.toDoubleOrNull() != null

    // A tiny shake when a key can't be added.
    val shake = remember { Animatable(0f) }
    var shakes by remember { mutableIntStateOf(0) }
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
            .padding(horizontal = 16.dp),
    ) {
        SheetTitle(if (isNew) tr("New reading") else tr("Edit reading"), close) {
            if (isNew) {
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(Corner.S))
                        .border(1.dp, C.Border, RoundedCornerShape(Corner.S))
                        .tap { Launch.mealAction.value = "meter" }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(R.drawable.lc_camera, C.TextSecondary, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Scan meter"), style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
                }
            } else {
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(Corner.S))
                        .border(1.dp, if (confirmDelete) C.Low.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(Corner.S))
                        .tap {
                            if (confirmDelete) { state.delete(initial); close() } else confirmDelete = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ic(R.drawable.lc_trash, C.Low, 16.dp, contentDescription = if (confirmDelete) null else tr("Delete reading"))
                    if (confirmDelete) {
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Tap to delete"), style = inter(13.sp, FontWeight.SemiBold, C.Low))
                    }
                }
            }
        }

        // The readout: the number big and left, coloured by where it lands as you type; under it
        // the range in words with a small scale, or what to do.
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val color = status?.let { C.status(it) } ?: if (text.isEmpty()) C.TextMuted.copy(alpha = 0.5f) else C.Text
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.graphicsLayer { translationX = shake.value * 10.dp.toPx() * (if (shakes % 2 == 0) 1f else -1f) },
            ) {
                Text(
                    text.ifEmpty { if (unit == GlucoseUnit.MMOL) "0.0" else "0" }.replace('.', L.decimal),
                    style = numeral(64.sp, color).copy(lineHeight = 68.sp),
                )
                Spacer(Modifier.width(8.dp))
                Text(unit.label, style = mono(13.sp, color = C.TextSecondary), modifier = Modifier.padding(bottom = 12.dp))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    status != null -> {
                        RangeSquare(C.status(status))
                        Spacer(Modifier.width(6.dp))
                        Text(caps(status.label), style = mono(12.sp, FontWeight.SemiBold, C.Text, 0.06f), modifier = Modifier.weight(1f))
                        mmol?.let { MicroScale(it, width = 64.dp) }
                    }
                    outOfRange -> Text(tr("Meters read {0}", Glucose.rangeLabel(unit)), style = inter(13.sp, FontWeight.Medium, C.High))
                    else -> Text(tr("Type the number on your meter"), style = inter(13.sp, color = C.TextMuted))
                }
            }
            scanNote?.let { Text(it, style = inter(13.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.padding(top = 6.dp)) }
            if (initial.imported) {
                Text(caps(tr("From Health Connect")), style = mono(12.sp, color = C.TextMuted, tracking = 0.04f), modifier = Modifier.padding(top = 6.dp))
            }
        }

        // When: now, a little earlier, or a forgotten one from hours or days ago.
        fun moveTo(t: Long) {
            at = t
            // A new reading's moment follows its time (an 07:00 one is fasting, a 22:00 one bedtime).
            if (isNew) tag = state.suggestTag(t)
        }

        // Moment, time and note as three ruled cells in one row, each opening a menu, so the keypad
        // and Save always fit on screen without scrolling.
        var momentOpen by remember { mutableStateOf(false) }
        var whenOpen by remember { mutableStateOf(false) }
        val rule = C.Border
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = 12.dp)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = w)
                    drawLine(rule, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), strokeWidth = w)
                },
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Pill(tag.label, tr("Moment"), Modifier.fillMaxHeight(), first = true) { momentOpen = true }
                DropdownMenu(
                    expanded = momentOpen,
                    onDismissRequest = { momentOpen = false },
                    modifier = Modifier.background(C.Card),
                ) {
                    MealTag.entries.forEach { t ->
                        MenuRow(t.label, t.icon(), t == tag) {
                            tag = t
                            momentOpen = false
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val whenLabel = when (offsetMin) {
                    null -> fmt.dayTime(at)
                    0 -> tr("Now")
                    else -> if (offsetMin!! < 60) tr("{0} min ago", offsetMin) else tr("{0} h ago", offsetMin!! / 60)
                }
                Pill(whenLabel, tr("Time"), Modifier.fillMaxHeight()) { whenOpen = true }
                DropdownMenu(
                    expanded = whenOpen,
                    onDismissRequest = { whenOpen = false },
                    modifier = Modifier.background(C.Card),
                ) {
                    if (isNew) {
                        listOf(0 to tr("Now"), 15 to tr("{0} min ago", 15), 30 to tr("{0} min ago", 30), 60 to tr("{0} h ago", 1), 120 to tr("{0} h ago", 2)).forEach { (m, label) ->
                            MenuRow(label, R.drawable.lc_clock, offsetMin == m) {
                                offsetMin = m
                                moveTo(openedAt - m * 60_000L)
                                whenOpen = false
                            }
                        }
                    }
                    MenuRow(if (isNew) tr("Pick a day and time…") else tr("Change the time…"), R.drawable.lc_pencil, offsetMin == null && isNew) {
                        whenOpen = false
                        picking = true
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val summary = details.summary()
                val shown = when {
                    summary.isNotBlank() && note.isNotBlank() -> summary + " · " + tr("note")
                    summary.isNotBlank() -> summary
                    note.isNotBlank() -> tr("Note added")
                    else -> tr("Add food, carbs…")
                }
                Pill(shown, tr("Details"), Modifier.fillMaxHeight()) { detailsOpen = true }
            }
        }
        if (picking) {
            WhenPicker(at, state.zone, onDismiss = { picking = false }) { t ->
                picking = false
                offsetMin = null
                moveTo(t)
            }
        }

        if (detailsOpen) {
            DetailsDialog(details, note, onDismiss = { detailsOpen = false }) { d, n ->
                details = d
                note = n
                detailsOpen = false
            }
        }

        // Keys size themselves to the screen so the whole sheet fits (taller on big phones).
        val screenH = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val keyH = ((screenH * 0.9f - 456.dp) / 4).coerceIn(44.dp, 60.dp)
        Keypad(
            decimal = unit == GlucoseUnit.MMOL,
            keyHeight = keyH,
            modifier = Modifier.padding(top = 16.dp),
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
            if (isNew) tr("Save reading") else tr("Save changes"),
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            icon = R.drawable.lc_check,
            enabled = mmol != null,
        ) {
            val v = mmol ?: return@PrimaryButton
            Haptics.confirm(view)
            state.save(
                initial.copy(
                    at = at, mmol = v, tag = tag, note = note.trim(),
                    carbs = details.carbs, insulin = details.insulin,
                    insulinKind = if (details.insulin != null) details.kind else null,
                    meds = details.meds, exercise = details.exercise,
                    foods = details.foods,
                    // The breakdown stays while it still describes the foods on the reading.
                    meal = details.meal?.takeIf { m -> m.foods.toSet() == details.foods.toSet() },
                )
            )
            close()
        }
    }
}

/**
 * 1–9, then point, 0 and backspace, as squared, outlined instrument keys with mono figures. The
 * point is greyed out for mg/dL (whole numbers). The key
 * shows the app's decimal mark (a comma in Icelandic) but always sends '.'.
 */
@Composable
private fun Keypad(decimal: Boolean, keyHeight: Dp, modifier: Modifier = Modifier, onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", ".0⌫")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val enabled = k != '.' || decimal
                    val press = rememberPress()
                    Box(
                        Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .pressed(press)
                            .clip(RoundedCornerShape(Corner.S))
                            .border(1.dp, if (enabled) C.Border else C.Border.copy(alpha = 0.35f), RoundedCornerShape(Corner.S))
                            .clickable(press.source, LocalIndication.current, enabled = enabled) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '⌫') {
                            Ic(R.drawable.lc_delete, C.TextSecondary, 22.dp, contentDescription = tr("Delete"))
                        } else {
                            Text(
                                if (k == '.') L.decimal.toString() else k.toString(),
                                style = mono(24.sp, FontWeight.Medium, if (enabled) C.Text else C.TextMuted.copy(alpha = 0.3f)),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A ruled cell that opens a menu: what it sets on top in mono capitals (MOMENT, TIME, DETAILS)
 * and the current value in full underneath, wrapping to a second line rather than cutting off.
 * Cells after the first have a hairline on their left.
 */
@Composable
private fun Pill(text: String, label: String, modifier: Modifier = Modifier, first: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    val rule = C.Border
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (!first) drawLine(rule, Offset(0f, 10.dp.toPx()), Offset(0f, size.height - 10.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            .pressed(press)
            .semantics { contentDescription = tr("{0}: {1}. Tap to change.", label, text) }
            .tap(press, onClick = onClick)
            .padding(start = if (first) 0.dp else 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Text(
            caps(label),
            style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.02f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(text, style = inter(15.sp, FontWeight.SemiBold, C.Text).copy(lineHeight = 20.sp), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Ic(R.drawable.lc_chevron_down, C.TextMuted, 12.dp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = inter(15.sp, if (selected) FontWeight.Bold else FontWeight.Medium, C.Text)) },
        onClick = onClick,
        leadingIcon = { Ic(icon, if (selected) C.AccentText else C.TextMuted, 18.dp) },
        trailingIcon = { if (selected) Ic(R.drawable.lc_check, C.AccentText, 16.dp) },
    )
}
