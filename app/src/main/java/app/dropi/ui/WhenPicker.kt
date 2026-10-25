package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "When did you take it?": a day (today back to a week ago) and a time on the clock, for a
 * reading logged late or one whose time needs fixing. Times in the future can't be saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenPicker(initial: Long, zone: ZoneId, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    val today = LocalDate.now(zone)
    var day by remember { mutableStateOf(start.toLocalDate()) }
    val time = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    val picked = day.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
    val future = picked > System.currentTimeMillis() + 60_000
    val dayName = remember { dayPattern("EEE d") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(Corner.XL)
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(C.Card)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("When did you take it?"), style = heading(20.sp), modifier = Modifier.padding(horizontal = 24.dp))
            // Today and the seven days before, as a 4 × 2 grid.
            val days = (0..7).map { today.minusDays(it.toLong()) }
            ChoiceGrid(
                days.mapIndexed { back, d ->
                    Choice(
                        when (back) {
                            0 -> tr("Today")
                            1 -> tr("Yesterday")
                            else -> dayName.format(d).cap()
                        }
                    )
                },
                selected = days.indexOf(day).takeIf { it >= 0 },
                columns = 4,
                compact = true,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { day = days[it] }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = time,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = C.SurfaceLight,
                        selectorColor = C.AccentText,
                        timeSelectorSelectedContainerColor = C.AccentText.copy(alpha = 0.22f),
                        timeSelectorUnselectedContainerColor = C.SurfaceLight,
                        periodSelectorSelectedContainerColor = C.AccentText.copy(alpha = 0.22f),
                        clockDialSelectedContentColor = C.OnAccent,
                    ),
                )
            }
            if (future) {
                Text(
                    tr("That's in the future. Pick an earlier time."),
                    style = inter(13.sp, FontWeight.Medium, C.High),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(tr("Cancel"), Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(tr("Set time"), Modifier.weight(1f), enabled = !future) { onPick(picked) }
            }
        }
    }
}
