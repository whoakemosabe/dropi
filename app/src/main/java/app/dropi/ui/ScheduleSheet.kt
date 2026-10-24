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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.dropi.R
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
        DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.ENGLISH)
    }

    StickySheetBody("Check times", close) {
        Text(
            "Plan when you check each day. A check counts as done when you log within about an hour of it.",
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )

        // Reminders.
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow("Remind me at these times", "A notification with Log and Snooze", state.remindersOn) { on ->
                Haptics.toggle(view, on)
                if (on) turnOnReminders() else state.changeReminders(false)
            }
            if (state.remindersOn && !canNotify) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notifications are off for Dropi, so reminders can't show.",
                        style = inter(12.sp, FontWeight.Medium, C.High),
                        modifier = Modifier.weight(1f),
                    )
                    Chip("Turn on") {
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(i) } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                        }
                    }
                }
            }
            ToggleRow(
                "Remind me after eating",
                "A nudge for the after-eating check, counted from when you ate",
                state.afterMealOn,
            ) { on ->
                Haptics.toggle(view, on)
                state.changeAfterMeal(on)
                if (on && !state.remindersOn) turnOnReminders()
            }
            if (state.afterMealOn) {
                val options = listOf(60, 90, 120)
                SlidingSegments(
                    listOf("1 h", "1.5 h", "2 h"),
                    options.indexOf(state.afterMealMinutes).coerceAtLeast(0),
                    Modifier.fillMaxWidth(),
                ) { state.changeAfterMeal(true, options[it]) }
            }
            ToggleRow("Weekly recap", "Sunday at 19:00: your week's time in range, lows and logging", state.recapOn) { on ->
                Haptics.toggle(view, on)
                state.changeRecap(on)
                if (on && Build.VERSION.SDK_INT >= 33 && !canNotify) askOnly.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // The checks themselves.
        state.slots.forEach { s ->
            SlotCard(
                s,
                clock.format(LocalTime.of(s.hour, s.min)),
                onTime = { editingTime = s },
                onChange = { n -> state.changeSlots(state.slots.map { if (it.id == s.id) n else it }) },
                onDelete = { state.changeSlots(state.slots.filter { it.id != s.id }) },
            )
        }
        if (state.slots.size < 10) {
            PrimaryButton("Add a check time", Modifier.fillMaxWidth(), icon = R.drawable.lc_plus) {
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

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold))
            Text(sub, style = inter(12.sp, color = C.TextMuted))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = dropiSwitchColors(),
        )
    }
}

@Composable
private fun SlotCard(s: Slot, time: String, onTime: () -> Unit, onChange: (Slot) -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().card(RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).tap(onClick = onTime)) {
                Text(time, style = numeral(26.sp, if (s.enabled) C.Text else C.TextMuted))
                Text(s.name, style = inter(13.sp, FontWeight.Medium, C.TextSecondary))
            }
            Switch(
                checked = s.enabled,
                onCheckedChange = { onChange(s.copy(enabled = it)) },
                colors = dropiSwitchColors(),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).tap(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_trash, C.TextMuted, 18.dp, contentDescription = "Remove") }
        }
        val tags = MealTag.entries.filter { it != MealTag.OTHER }
        ChoiceGrid(tags.map { Choice(it.label, it.icon()) }, tags.indexOf(s.tag), columns = 3, compact = true) {
            onChange(s.copy(tag = tags[it], window = if (s.window == Slot.defaultWindow(s.tag)) Slot.defaultWindow(tags[it]) else s.window))
        }
        Text("Still counts for", style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary))
        val ws = Slot.WINDOWS
        ChoiceGrid(ws.map { Choice(if (it < 60) "$it min" else if (it % 60 == 0) "${it / 60} h" else "${it / 60}½ h") }, ws.indexOf(s.window).takeIf { it >= 0 }, columns = 5, compact = true) {
            onChange(s.copy(window = ws[it]))
        }
        if (s.tag == MealTag.AFTER_MEAL) {
            Text(
                "Follows your meal: when you log a meal (or a before-eating reading), this check moves to after it.",
                style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
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
                .clip(RoundedCornerShape(24.dp))
                .background(C.Card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Check time", style = heading(20.sp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = st,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = C.SurfaceLight,
                        selectorColor = C.Accent,
                        timeSelectorSelectedContainerColor = C.Accent.copy(alpha = 0.22f),
                        timeSelectorUnselectedContainerColor = C.SurfaceLight,
                        periodSelectorSelectedContainerColor = C.Accent.copy(alpha = 0.22f),
                        clockDialSelectedContentColor = C.OnAccent,
                    ),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton("Set", Modifier.weight(1f)) { onPick(st.hour * 60 + st.minute) }
            }
        }
    }
}
