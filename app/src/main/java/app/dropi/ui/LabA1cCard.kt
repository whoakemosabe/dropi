package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.data.A1c
import app.dropi.data.LabA1c
import app.dropi.data.Report
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v)

/**
 * Lab A1C results next to the estimate from readings. They rarely match exactly (the estimate
 * comes from fingersticks, and A1C reflects about three months), so the gap is shown plainly.
 */
@Composable
fun LabA1cCard(state: AppState, report: Report) {
    var adding by remember { mutableStateOf(false) }
    val df = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val list = state.labA1c
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LAB A1C", style = SectionLabel, modifier = Modifier.weight(1f))
            Chip("Add result", icon = R.drawable.lc_plus) { adding = true }
        }
        val last = list.lastOrNull()
        if (last == null) {
            Text(
                "Add the A1C from your lab results to see it next to the estimate from your readings.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(pct(last.percent), style = numeral(28.sp, C.Text))
                    Text("${last.mmolMol.roundToInt()} mmol/mol · ${df.format(last.date)}", style = inter(12.sp, color = C.TextSecondary))
                }
                report.gmiPercent?.let { g ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Estimate now", style = inter(11.sp, color = C.TextMuted))
                        Text(pct(g), style = inter(18.sp, FontWeight.Bold, C.Text).copy(fontFeatureSettings = ""))
                    }
                }
            }
            val g = report.gmiPercent
            if (g != null) {
                val d = g - last.percent
                Text(
                    when {
                        kotlin.math.abs(d) < 0.3 -> "Your estimate is close to your last lab result."
                        d > 0 -> "Your estimate is ${String.format(Locale.ROOT, "%.1f", d)} points above your last lab result."
                        else -> "Your estimate is ${String.format(Locale.ROOT, "%.1f", -d)} points below your last lab result."
                    },
                    style = inter(12.sp, color = C.TextMuted),
                )
            }
            // Earlier results, newest first; long-press-free removal with a small ×.
            list.reversed().drop(1).take(5).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(df.format(r.date), style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
                    Text("${pct(r.percent)} · ${r.mmolMol.roundToInt()}", style = inter(13.sp, FontWeight.SemiBold, C.Text))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).tap { state.removeLabA1c(r) },
                        contentAlignment = Alignment.Center,
                    ) { Ic(R.drawable.lc_x, C.TextMuted, 14.dp, contentDescription = "Remove") }
                }
            }
            GhostButton("Remove the latest result", Modifier.fillMaxWidth()) { state.removeLabA1c(last) }
        }
    }
    if (adding) {
        AddA1cDialog(list.lastOrNull()?.percent ?: report.gmiPercent ?: 6.5, onDismiss = { adding = false }) {
            state.addLabA1c(it)
            adding = false
        }
    }
}

/** The value with − and +, in % or mmol/mol (whichever the lab printed), and the test date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddA1cDialog(start: Double, onDismiss: () -> Unit, onSave: (LabA1c) -> Unit) {
    var percent by remember { mutableDoubleStateOf((start * 10).roundToInt() / 10.0) }
    var unit by remember { mutableIntStateOf(0) } // 0 = %, 1 = mmol/mol
    val today = LocalDate.now()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.Card)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Lab A1C result", style = heading(20.sp), modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SlidingSegments(listOf("%", "mmol/mol"), unit, Modifier.fillMaxWidth()) { unit = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip("−") {
                        percent = if (unit == 0) percent - 0.1 else A1c.fromMmolMol(A1c.toMmolMol(percent).roundToInt() - 1.0)
                        percent = percent.coerceIn(4.0, 15.0)
                    }
                    Text(
                        if (unit == 0) pct(percent) else "${A1c.toMmolMol(percent).roundToInt()} mmol/mol",
                        style = numeral(30.sp, C.Text),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Chip("+") {
                        percent = if (unit == 0) percent + 0.1 else A1c.fromMmolMol(A1c.toMmolMol(percent).roundToInt() + 1.0)
                        percent = percent.coerceIn(4.0, 15.0)
                    }
                }
                Text(
                    if (unit == 0) "${A1c.toMmolMol(percent).roundToInt()} mmol/mol" else pct(percent),
                    style = inter(12.sp, color = C.TextMuted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Text("Test date", style = SectionLabel, modifier = Modifier.padding(horizontal = 20.dp))
            DatePicker(
                state = dateState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = C.Card,
                    selectedDayContainerColor = C.Accent,
                    todayDateBorderColor = C.AccentText,
                ),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton("Save", Modifier.weight(1f)) {
                    val ms = dateState.selectedDateMillis ?: today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    onSave(LabA1c(date, (percent * 10).roundToInt() / 10.0))
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
