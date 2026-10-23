package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.work.Export
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ExportSheet(state: AppState) {
    if (!state.exportOpen) return
    SlideSheet(onDismiss = { state.exportOpen = false }) { close -> ExportContent(state, close) }
}

/** A PDF for a doctor's appointment, or the readings as a spreadsheet, through the share sheet. */
@Composable
private fun ExportContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val periods = listOf(14, 30, 90)
    var period by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val days = periods[period]
    val today = LocalDate.now(state.zone)
    val from = today.minusDays((days - 1).toLong())
    val inPeriod = state.readings.filter { !it.date(state.zone).isBefore(from) }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetTitle("Share a report", close)
        Text(
            "Made on your phone and shared however you choose: email, Messenger, Drive, a print-out.",
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        SlidingSegments(listOf("14 days", "30 days", "90 days"), period, Modifier.fillMaxWidth()) { period = it }
        Text("${inPeriod.size} readings in this period", style = inter(13.sp, FontWeight.Medium, C.TextMuted))
        PrimaryButton(
            "PDF report for your doctor",
            Modifier.fillMaxWidth(),
            icon = R.drawable.lc_share,
            enabled = !busy && inPeriod.isNotEmpty(),
        ) {
            busy = true
            scope.launch {
                try {
                    val f = Export.writePdf(context, state.readings, days, today, state.zone, state.unit, state.targets)
                    Export.share(context, f, "application/pdf", "Blood glucose report")
                    note = null
                } catch (e: Exception) {
                    note = "Couldn't make the report."
                }
                busy = false
            }
        }
        GhostButton("Readings as a spreadsheet (CSV)", Modifier.fillMaxWidth(), color = C.AccentText) {
            if (busy || inPeriod.isEmpty()) return@GhostButton
            busy = true
            scope.launch {
                try {
                    val f = Export.writeCsv(context, inPeriod, state.zone, today)
                    Export.share(context, f, "text/csv", "Blood glucose readings")
                    note = null
                } catch (e: Exception) {
                    note = "Couldn't make the file."
                }
                busy = false
            }
        }
        note?.let { Text(it, style = inter(13.sp, FontWeight.Medium, C.High)) }
    }
}
