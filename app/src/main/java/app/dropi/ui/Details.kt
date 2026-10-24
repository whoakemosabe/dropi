package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.dropi.R
import app.dropi.L
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.InsulinKind
import java.util.Locale

/** The optional details on a reading. Nothing here is required, and nothing is ever suggested. */
data class Details(
    val carbs: Int? = null,
    val insulin: Double? = null,
    val kind: InsulinKind? = null,
    val meds: Boolean = false,
    val exercise: Int? = null,
    val foods: List<String> = emptyList(),
    /** The meal breakdown behind [foods] and [carbs], when there is one. */
    val meal: app.dropi.data.Meal? = null,
) {
    fun summary(): String = listOfNotNull(
        foods.takeIf { it.isNotEmpty() }?.let { f -> if (f.size <= 2) f.joinToString(", ") { app.dropi.data.Foods.display(it) } else trn(f.size, "{0} food", "{0} foods") },
        carbs?.let { tr("{0} g", it) },
        insulin?.let { tr("{0} u", fmt(it)) },
        if (meds) tr("Tablets") else null,
        exercise?.let { tr("{0} min", it) },
    ).joinToString(" · ")

    companion object {
        /** "4" or "4.5" ("4,5" in Icelandic), for showing. */
        fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.ROOT, "%.1f", d).replace('.', L.decimal)
    }
}

/**
 * Carbs, insulin, tablets, exercise and a note, all with steppers and switches so there's
 * nothing to type except the note. The window scrolls only if the keyboard is up for the note.
 */
@Composable
fun DetailsDialog(
    initial: Details,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (Details, String) -> Unit,
) {
    val view = LocalView.current
    var d by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf(initialNote) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(Corner.XL))
                .background(C.Card)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Details"), style = heading(20.sp))
            Text(
                tr("All optional. Meals are logged on their own with the + button, at the time you ate."),
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp),
            )

            Stepper(tr("Carbs"), d.carbs?.let { tr("{0} g", it) }, R.drawable.lc_utensils,
                onMinus = { d = d.copy(carbs = d.carbs?.let { (it - 5).takeIf { v -> v > 0 } }) },
                onPlus = { d = d.copy(carbs = ((d.carbs ?: 0) + 5).coerceAtMost(400)) },
            )
            Stepper(tr("Insulin"), d.insulin?.let { u -> trn(if (u % 1.0 == 0.0) u.toInt() else 2, "{1} unit", "{1} units", Details.fmt(u)) }, R.drawable.lc_droplet,
                onMinus = { d = d.copy(insulin = d.insulin?.let { (it - 0.5).takeIf { v -> v > 0.01 } }) },
                onPlus = { d = d.copy(insulin = ((d.insulin ?: 0.0) + 0.5).coerceAtMost(150.0), kind = d.kind ?: InsulinKind.RAPID) },
            )
            if (d.insulin != null) {
                val kinds = InsulinKind.entries
                ChoiceGrid(kinds.map { Choice(it.label) }, kinds.indexOf(d.kind ?: InsulinKind.RAPID), columns = 3, compact = true) {
                    d = d.copy(kind = kinds[it])
                }
            }
            Stepper(tr("Exercise"), d.exercise?.let { tr("{0} min", it) }, R.drawable.lc_flame,
                onMinus = { d = d.copy(exercise = d.exercise?.let { (it - 15).takeIf { v -> v > 0 } }) },
                onPlus = { d = d.copy(exercise = ((d.exercise ?: 0) + 15).coerceAtMost(600)) },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.lc_check, C.TextMuted, 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Took my tablets"), style = inter(15.sp, FontWeight.SemiBold))
                    Text(tr("Diabetes medicine other than insulin"), style = inter(13.sp, color = C.TextMuted))
                }
                Switch(
                    checked = d.meds,
                    onCheckedChange = { Haptics.toggle(view, it); d = d.copy(meds = it) },
                    colors = dropiSwitchColors(),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corner.M))
                    .background(C.SurfaceLight)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (note.isEmpty()) Text(tr("Note: how you feel, anything unusual…"), style = inter(15.sp, color = C.TextMuted))
                BasicTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    textStyle = inter(15.sp, color = C.Text),
                    cursorBrush = SolidColor(C.AccentText),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(tr("Clear all"), Modifier.weight(1f)) {
                    d = Details()
                    note = ""
                }
                PrimaryButton(tr("Done"), Modifier.weight(1f)) { onSave(d, note.trim()) }
            }
        }
    }
}

/** − value + on one row; the value shows "Add" until it's set. */
@Composable
private fun Stepper(label: String, value: String?, icon: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, C.TextMuted, 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = inter(15.sp, FontWeight.SemiBold))
            Text(value ?: tr("Not logged"), style = inter(13.sp, FontWeight.Medium, if (value != null) C.Text else C.TextMuted))
        }
        RoundButton("−", tr("Less {0}", label), enabled = value != null, action = onMinus)
        Spacer(Modifier.width(8.dp))
        RoundButton("+", tr("More {0}", label), enabled = true, action = onPlus)
    }
}

/** Tap to step once; hold to keep stepping (so 75 g of carbs isn't 15 taps). */
@Composable
private fun RoundButton(symbol: String, description: String, enabled: Boolean, action: () -> Unit) {
    val view = LocalView.current
    var down by remember { mutableStateOf(false) }
    val enabledNow = rememberUpdatedState(enabled)
    val scale by animateFloatAsState(if (down) 0.94f else 1f, tween(100), label = "step")
    val latest = rememberUpdatedState(action)
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) C.SurfaceLight else C.SurfaceLight.copy(alpha = 0.4f))
            .semantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { latest.value(); true }
            }
            .pointerInput(Unit) {
                // Keyed on nothing: the gesture keeps running when the button disables itself
                // (stepping down to "Not logged"), and the repeat stops with it.
                detectTapGestures(onPress = {
                    if (!enabledNow.value) return@detectTapGestures
                    down = true
                    try {
                        coroutineScope {
                            Haptics.tap(view)
                            latest.value()
                            val repeat = launch {
                                delay(450)
                                var n = 0
                                while (enabledNow.value && n < 120) {
                                    latest.value()
                                    if (n % 3 == 0) Haptics.segment(view)
                                    n++
                                    delay(110)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    } finally {
                        down = false
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = inter(22.sp, FontWeight.SemiBold, if (enabled) C.Text else C.TextMuted))
    }
}
