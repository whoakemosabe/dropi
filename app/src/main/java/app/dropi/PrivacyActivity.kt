package app.dropi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.ui.C
import app.dropi.ui.DropiTheme
import app.dropi.ui.PrimaryButton
import app.dropi.ui.inter

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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Dropi and your health data", style = inter(24.sp, FontWeight.ExtraBold))
                    listOf(
                        "Dropi only asks Health Connect for blood glucose. Nothing else.",
                        "Reading: blood glucose other apps saved (a sensor or another meter app) is copied into Dropi so " +
                            "your log, trends and reports are complete.",
                        "Writing: readings you log in Dropi are saved to Health Connect so your other health apps can use them.",
                        "Everything stays on your phone. Dropi has no account and no servers, and never sends your " +
                            "readings anywhere. The only time it goes online is to check GitHub for app updates.",
                        "Reports you share (PDF or CSV) go only where you send them.",
                        "Turn sync off any time in Dropi's Settings, or remove its access in Health Connect.",
                    ).forEach { Text(it, style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp)) }
                    PrimaryButton("Done", Modifier.padding(top = 8.dp)) { finish() }
                }
            }
        }
    }
}
