package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
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
private fun ColumnScope.ExportContent(state: AppState, close: () -> Unit) {
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

    StickySheetBody(tr("Share a report"), close) {
        Text(
            tr("Made on your phone and shared however you choose: email, Messenger, Drive, a print-out."),
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
        if (state.demo) {
            // A real warning, so a real object: a quiet panel with the range colour's square.
            Row(
                Modifier.fillMaxWidth().card(RoundedCornerShape(Corner.M)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RangeSquare(C.High, Modifier.padding(top = 6.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    tr("Demo data is on: a report now is made from made-up readings. Don't send it to your care team."),
                    style = inter(13.sp, FontWeight.SemiBold, C.High).copy(lineHeight = 19.sp),
                )
            }
        }
        RuleHeader(tr("Period"), modifier = Modifier.padding(bottom = 0.dp))
        SlidingSegments(listOf(tr("14 days"), tr("30 days"), tr("90 days")), period, Modifier.fillMaxWidth()) { period = it }
        Text(trn(inPeriod.size, "{0} reading in this period", "{0} readings in this period"), style = mono(12.sp, FontWeight.Medium, C.TextMuted))
        RuleHeader(tr("Share"), modifier = Modifier.padding(bottom = 0.dp))
        PrimaryButton(
            tr("PDF report for your doctor"),
            Modifier.fillMaxWidth(),
            icon = R.drawable.lc_share,
            enabled = !busy && inPeriod.isNotEmpty(),
        ) {
            busy = true
            scope.launch {
                try {
                    val f = Export.writePdf(context, state.readings, days, today, state.zone, state.unit, state.targets)
                    Export.share(context, f, "application/pdf", tr("Blood glucose report"))
                    note = null
                } catch (e: Exception) {
                    note = tr("Couldn't make the report.")
                }
                busy = false
            }
        }
        GhostButton(tr("Readings as a spreadsheet (CSV)"), Modifier.fillMaxWidth(), color = C.AccentText) {
            if (busy || inPeriod.isEmpty()) return@GhostButton
            busy = true
            scope.launch {
                try {
                    val f = Export.writeCsv(
                        context, inPeriod, state.zone, today,
                        state.meals.filter { !java.time.Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate().isBefore(from) },
                    )
                    Export.share(context, f, "text/csv", tr("Blood glucose readings"))
                    note = null
                } catch (e: Exception) {
                    note = tr("Couldn't make the file.")
                }
                busy = false
            }
        }
        note?.let { Text(it, style = inter(13.sp, FontWeight.Medium, C.High)) }
    }
}
