package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        "Take 15 g of fast-acting sugar now: 4 glucose tablets, or about 150 ml of fruit juice or regular soft drink.",
        "Wait 15 minutes, then check again.",
        "Still under $lowLine? Repeat both steps.",
        "Once you're back above it, have a snack or your meal if it's more than an hour away.",
        if (status == Status.URGENT_LOW) "If you feel confused, very drowsy or can't swallow safely, call 112 now. Don't drive." else null,
    ) else listOf(
        "Drink some water and check again in a couple of hours.",
        "If you use insulin, your care plan may ask you to check for ketones, especially if you feel sick.",
        "Get medical help if you're vomiting, very thirsty and drowsy, or it keeps rising.",
    )

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(Corner.XL)
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(C.Card)
                .border(1.dp, c.copy(alpha = 0.5f), shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(Corner.S)).background(c.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(if (low) R.drawable.lc_circle_alert else R.drawable.lc_heart_pulse, c, 20.dp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(status.label, style = SectionLabel)
                    Text(value, style = numeral(26.sp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { i, s ->
                    Row {
                        Text("${i + 1}", style = inter(13.sp, FontWeight.Bold, C.TextMuted), modifier = Modifier.width(20.dp))
                        Text(s, style = inter(15.sp, color = C.Text).copy(lineHeight = 20.sp))
                    }
                }
            }
            Text(
                "General guidance only. Follow the plan you agreed with your care team.",
                style = inter(13.sp, color = C.TextMuted),
            )
            if (low && onRecheck != null) {
                val at = recheckAt
                if (at == null) {
                    PrimaryButton("Remind me to re-check in 15 min", Modifier.fillMaxWidth(), icon = R.drawable.lc_clock) {
                        recheckAt = onRecheck()
                    }
                    GhostButton("Got it", Modifier.fillMaxWidth()) { onClose() }
                } else {
                    Text(
                        "I'll remind you at ${Fmt(context, java.time.ZoneId.systemDefault()).time(at)}.",
                        style = inter(13.sp, FontWeight.SemiBold, C.Text),
                    )
                    PrimaryButton("Got it", Modifier.fillMaxWidth()) { onClose() }
                }
            } else {
                PrimaryButton("Got it", Modifier.fillMaxWidth()) { onClose() }
            }
        }
    }
}
