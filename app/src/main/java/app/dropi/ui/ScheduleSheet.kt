package app.dropi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.dropi.L
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.MealTag
import app.dropi.data.Slot
import app.dropi.work.Alerts
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleSheet(state: AppState) {
    if (!state.scheduleOpen) return
    SlideSheet(onDismiss = { state.scheduleOpen = false }) { close -> ScheduleContent(state, close) }
}

/** The daily check times, and whether Dropi reminds you of them. */
@Composable
private fun ColumnScope.ScheduleContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var editingTime by remember { mutableStateOf<Slot?>(null) }
    var canNotify by remember { mutableStateOf(Alerts.canNotify(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = Alerts.canNotify(context)
        state.changeReminders(granted)
    }
    // Notification permission alone (for the recap), without switching check reminders on.
    val askOnly = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = Alerts.canNotify(context)
    }
    fun turnOnReminders() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else state.changeReminders(true)
        canNotify = Alerts.canNotify(context)
    }
    val clock = remember {
        DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", if (L.icelandic) L.locale else Locale.ENGLISH)
    }

    StickySheetBody(tr("Check times"), close) {
        Text(
            tr("Plan when you check each day. A check counts as done when you log within about an hour of it."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )

        // Reminders.
        Column(Modifier.fillMaxWidth()) {
            RuleHeader(tr("Reminders"))
            SettingsToggleRow(tr("Remind me at these times"), tr("A notification with Log and Snooze"), state.remindersOn) { on ->
                Haptics.toggle(view, on)
                if (on) turnOnReminders() else state.changeReminders(false)
            }
            if (state.remindersOn && !canNotify) {
                SettingsActionRow(
                    tr("Notifications are off for Dropi, so reminders can't show."),
                    action = tr("Turn on"),
                    labelColor = C.High,
                ) {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(i) } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    }
                }
            }
            SettingsToggleRow(
                tr("Remind me after eating"),
                tr("A nudge for the after-eating check, counted from when you ate"),
                state.afterMealOn,
            ) { on ->
                Haptics.toggle(view, on)
                state.changeAfterMeal(on)
                if (on && !state.remindersOn) turnOnReminders()
            }
            if (state.afterMealOn) {
                val options = listOf(60, 90, 120)
                SlidingSegments(
                    listOf(tr("1 h"), tr("1.5 h"), tr("2 h")),
                    options.indexOf(state.afterMealMinutes).coerceAtLeast(0),
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) { state.changeAfterMeal(true, options[it]) }
            }
            SettingsToggleRow(tr("Weekly recap"), tr("Sunday at 19:00: your week's time in range, lows and logging"), state.recapOn) { on ->
                Haptics.toggle(view, on)
                state.changeRecap(on)
                if (on && Build.VERSION.SDK_INT >= 33 && !canNotify) askOnly.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // The checks themselves: one ruled row each.
        Column(Modifier.fillMaxWidth()) {
            RuleHeader(tr("Your checks"), trailing = trn(state.slots.count { it.enabled }, "{0} check a day", "{0} checks a day"))
            state.slots.forEach { s ->
                SlotRow(
                    s,
                    clock.format(LocalTime.of(s.hour, s.min)),
                    onTime = { editingTime = s },
                    onChange = { n -> state.changeSlots(state.slots.map { if (it.id == s.id) n else it }) },
                    onDelete = { state.changeSlots(state.slots.filter { it.id != s.id }) },
                )
            }
        }
        if (state.slots.size < 10) {
            PrimaryButton(tr("Add a check time"), Modifier.fillMaxWidth(), icon = R.drawable.lc_plus) {
                val taken = state.slots.map { it.minute }.toSet()
                // The next free whole hour after the last check, or 09:00.
                var m = ((state.slots.maxOfOrNull { it.minute } ?: (8 * 60)) / 60 + 1) * 60
                while (m in taken) m += 60
                m %= 24 * 60
                val tag = when (m / 60) {
                    in 0..4 -> MealTag.OVERNIGHT
                    in 5..9 -> MealTag.FASTING
                    in 21..23 -> MealTag.BEDTIME
                    else -> MealTag.BEFORE_MEAL
                }
                val n = Slot("s" + System.currentTimeMillis().toString(36), m, tag)
                state.changeSlots(state.slots + n)
                editingTime = n
            }
        }
    }

    editingTime?.let { s ->
        TimeDialog(s.minute, onDismiss = { editingTime = null }) { minute ->
            editingTime = null
            state.changeSlots(state.slots.map { if (it.id == s.id) it.copy(minute = minute) else it })
        }
    }
}

/**
 * One check: the time in mono (tap to change it), its name, on/off and remove on one ruled line;
 * what kind of check it is and how long it still counts for underneath.
 */
@Composable
private fun SlotRow(s: Slot, time: String, onTime: () -> Unit, onChange: (Slot) -> Unit, onDelete: () -> Unit) {
    val label = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.08f)
    // Slot.name is built in English ("Before lunch"); the lookup shows the known names in the app's language.
    val name = tr(s.name)
    Column(
        Modifier.fillMaxWidth().ruleTop().padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Corner.S))
                    .tap(onClick = onTime)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    time,
                    style = mono(17.sp, FontWeight.SemiBold, if (s.enabled) C.Text else C.TextMuted),
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 64.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    name,
                    style = inter(15.sp, FontWeight.SemiBold, if (s.enabled) C.Text else C.TextMuted),
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
            Switch(
                checked = s.enabled,
                onCheckedChange = { onChange(s.copy(enabled = it)) },
                colors = dropiSwitchColors(),
                modifier = Modifier.semantics { contentDescription = name },
            )
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(Corner.S)).tap(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_trash, C.TextMuted, 18.dp, contentDescription = tr("Remove")) }
        }
        val tags = MealTag.entries.filter { it != MealTag.OTHER }
        ChoiceGrid(tags.map { Choice(it.label, it.icon()) }, tags.indexOf(s.tag), columns = 3, compact = true) {
            onChange(s.copy(tag = tags[it], window = if (s.window == Slot.defaultWindow(s.tag)) Slot.defaultWindow(tags[it]) else s.window))
        }
        Text(caps(tr("Still counts for")), style = label, modifier = Modifier.padding(top = 4.dp))
        val ws = Slot.WINDOWS
        ChoiceGrid(ws.map { Choice(if (it < 60) tr("{0} min", it) else if (it % 60 == 0) tr("{0} h", it / 60) else tr("{0}½ h", it / 60)) }, ws.indexOf(s.window).takeIf { it >= 0 }, columns = 5, compact = true) {
            onChange(s.copy(window = ws[it]))
        }
        if (s.tag == MealTag.AFTER_MEAL) {
            Text(
                tr("Follows your meal: when you log a meal (or a before-eating reading), this check moves to after it."),
                style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val st = rememberTimePickerState(minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .card(RoundedCornerShape(Corner.XL))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Check time"), style = heading(20.sp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = st,
                    colors = scheduleClockColors(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(tr("Cancel"), Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(tr("Set"), Modifier.weight(1f)) { onPick(st.hour * 60 + st.minute) }
            }
        }
    }
}

/**
 * The clock in the instrument palette: a quiet dial, the hand and selected number in the
 * signature colour with the card's colour on top (readable in all three themes), hairline edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun scheduleClockColors() = TimePickerDefaults.colors(
    clockDialColor = C.SurfaceLight,
    clockDialSelectedContentColor = C.Card,
    clockDialUnselectedContentColor = C.Text,
    selectorColor = C.AccentText,
    containerColor = C.Card,
    periodSelectorBorderColor = C.Border,
    periodSelectorSelectedContainerColor = C.AccentText.copy(alpha = 0.22f),
    periodSelectorUnselectedContainerColor = Color.Transparent,
    periodSelectorSelectedContentColor = C.Text,
    periodSelectorUnselectedContentColor = C.TextSecondary,
    timeSelectorSelectedContainerColor = C.AccentText.copy(alpha = 0.22f),
    timeSelectorUnselectedContainerColor = C.SurfaceLight,
    timeSelectorSelectedContentColor = C.Text,
    timeSelectorUnselectedContentColor = C.TextSecondary,
)
