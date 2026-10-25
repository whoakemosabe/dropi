package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.tr
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.Reading
import app.dropi.data.Status

/**
 * Right after a low or very high reading: the standard steps, in plain words. The low steps
 * are the 15-15 rule used by the ADA and NHS; everything defers to the person's own care plan.
 */
@Composable
fun GuidanceDialog(r: Reading, status: Status, unit: GlucoseUnit, onRecheck: (() -> Long)?, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var recheckAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val low = status.isLow
    val c = C.status(status)
    val value = "${Glucose.format(r.mmol, unit)} ${unit.label}"
    val lowLine = "${Glucose.format(Glucose.LOW, unit)} ${unit.label}"
    val steps: List<String> = if (low) listOfNotNull(
        tr("Take 15 g of fast-acting sugar now: 4 glucose tablets, or about 150 ml of fruit juice or regular soft drink."),
        tr("Wait 15 minutes, then check again."),
        tr("Still under {0}? Repeat both steps.", lowLine),
        tr("Once you're back above it, have a snack or your meal if it's more than an hour away."),
        if (status == Status.URGENT_LOW) tr("If you feel confused, very drowsy or can't swallow safely, call 112 now. Don't drive.") else null,
    ) else listOf(
        tr("Drink some water and check again in a couple of hours."),
        tr("If you use insulin, your care plan may ask you to check for ketones, especially if you feel sick."),
        tr("Get medical help if you're vomiting, very thirsty and drowsy, or it keeps rising."),
    )

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(Corner.XL)
        // A panel edged in the range colour, with a band of it across the top: the colour says
        // which kind of reading this is; the words say what to do.
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(C.Card)
                .border(1.dp, c.copy(alpha = 0.6f), shape)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(c))
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RangeSquare(c)
                            Spacer(Modifier.width(8.dp))
                            Text(caps(status.label), style = mono(12.sp, FontWeight.SemiBold, C.Text, 0.08f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(value, style = numeral(34.sp, c))
                    }
                    Ic(if (low) R.drawable.lc_circle_alert else R.drawable.lc_heart_pulse, c, 24.dp)
                }
                Column {
                    steps.forEachIndexed { i, s ->
                        val urgent = status == Status.URGENT_LOW && i == steps.lastIndex
                        Row(Modifier.fillMaxWidth().ruleTop().padding(vertical = 10.dp)) {
                            Text("${i + 1}", style = mono(13.sp, FontWeight.SemiBold, if (urgent) c else C.TextSecondary), modifier = Modifier.width(24.dp))
                            Text(
                                s,
                                style = inter(15.sp, if (urgent) FontWeight.SemiBold else FontWeight.Normal, C.Text).copy(lineHeight = 20.sp),
                            )
                        }
                    }
                }
                Text(
                    tr("General guidance only. Follow the plan you agreed with your care team."),
                    style = inter(13.sp, color = C.TextMuted),
                )
                if (low && onRecheck != null) {
                    val at = recheckAt
                    if (at == null) {
                        PrimaryButton(tr("Remind me to re-check in 15 min"), Modifier.fillMaxWidth(), icon = R.drawable.lc_clock) {
                            recheckAt = onRecheck()
                        }
                        GhostButton(tr("Got it"), Modifier.fillMaxWidth()) { onClose() }
                    } else {
                        Text(
                            tr("I'll remind you at {0}.", Fmt(context, java.time.ZoneId.systemDefault()).time(at)),
                            style = inter(13.sp, FontWeight.SemiBold, C.Text),
                        )
                        PrimaryButton(tr("Got it"), Modifier.fillMaxWidth()) { onClose() }
                    }
                } else {
                    PrimaryButton(tr("Got it"), Modifier.fillMaxWidth()) { onClose() }
                }
            }
        }
    }
}
