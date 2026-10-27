package app.dropi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.ui.C
import app.dropi.ui.DropiTheme
import app.dropi.ui.PrimaryButton
import app.dropi.ui.RuleHeader
import app.dropi.ui.heading
import app.dropi.ui.inter
import app.dropi.ui.ruleTop

/**
 * How Dropi uses Health Connect data. Health Connect opens this from its permission screen and
 * its app settings; it only shows text, and reads nothing from the intent that opened it.
 */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val theme = Prefs(this).theme
        C.dark = when (theme) { "light" -> false; "dark", "dracula" -> true; else -> night }
        C.dracula = theme == "dracula"
        setContent {
            DropiTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(C.Bg)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(tr("Dropi and your health data"), style = heading(24.sp), modifier = Modifier.padding(bottom = 4.dp))
                    RuleHeader(tr("Health Connect"))
                    // Ruled paragraphs, like the rest of 1.1: a hairline between each, no cards.
                    listOf(
                        tr("Dropi asks Health Connect for blood glucose. Steps and sleep only if you turn them on, and only to read."),
                        tr("Reading: blood glucose other apps saved (a sensor or another meter app) is copied into Dropi so your log, trends and reports are complete."),
                        tr("Writing: readings you log in Dropi are saved to Health Connect so your other health apps can use them."),
                        tr("Steps and sleep, if you turn them on: read to learn your own patterns, like how a walk after eating or a short night changes your numbers. They're never written or shared."),
                        tr("Everything stays on your phone. Dropi has no account and no servers, and never sends your readings anywhere. It only goes online to check GitHub for app updates, to look up a barcode you scan on Open Food Facts (only the barcode number is sent) and, if you ask for it, to download the on-device AI model once."),
                        tr("Reports you share (PDF or CSV) go only where you send them."),
                        tr("Turn sync off any time in Dropi's Settings, or remove its access in Health Connect."),
                    ).forEachIndexed { i, para ->
                        Text(
                            para,
                            style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (i > 0) Modifier.ruleTop() else Modifier)
                                .padding(vertical = 12.dp),
                        )
                    }
                    PrimaryButton(tr("Done"), Modifier.fillMaxWidth().padding(top = 16.dp)) { finish() }
                }
            }
        }
    }
}
