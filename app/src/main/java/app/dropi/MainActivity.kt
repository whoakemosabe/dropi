package app.dropi

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import app.dropi.ui.AppState
import app.dropi.ui.C
import app.dropi.ui.DropiRoot
import app.dropi.ui.DropiTheme
import app.dropi.ui.Launch
import app.dropi.data.MealTag
import app.dropi.work.Alerts
import app.dropi.work.Scheduler
import app.dropi.work.UpdateWatch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System bar icons for the saved theme from the first frame.
        barStyle(startsDark())
        Alerts.createChannels(this)
        Scheduler.ensure(this)
        UpdateWatch.load(this)
        handleIntent(intent)
        setContent {
            val scope = rememberCoroutineScope()
            val s = remember { AppState(this, scope) }
            // Theme: follow the phone, or Dark / Light. Set before anything reads the palette.
            val systemDark = isSystemInDarkTheme()
            val dark = when (s.theme) {
                "light" -> false
                "dark", "dracula" -> true
                else -> systemDark
            }
            C.dark = dark
            C.dracula = s.theme == "dracula"
            LaunchedEffect(dark) {
                barStyle(dark)
                window.setBackgroundDrawable(C.Bg.toArgb().toDrawable())
            }
            // Android 13+ remembers which launch screen to show next time, so it matches the theme.
            LaunchedEffect(s.theme) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    splashScreen.setSplashScreenTheme(
                        when (s.theme) {
                            "light" -> R.style.Theme_Dropi_Light
                            "dark", "dracula" -> R.style.Theme_Dropi
                            else -> R.style.Theme_Dropi_System
                        }
                    )
                }
            }
            // Very narrow phones get the same layout scaled down a touch rather than wrapping, and
            // the system text size is followed up to 1.5x (past that, rows would start to clip).
            val base = LocalDensity.current
            val widthDp = LocalWindowInfo.current.containerSize.width / base.density
            val scale = (widthDp / 380f).coerceIn(0.9f, 1f)
            CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale.coerceAtMost(1.5f))) {
                DropiTheme { DropiRoot(s) }
            }
        }
    }

    /** Dark or light, from the saved theme setting, before any screen has loaded. */
    private fun startsDark(): Boolean = when (Prefs(this).theme) {
        "light" -> false
        "dark", "dracula" -> true
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun barStyle(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * This screen is the launcher entry, so other apps can open it: the only thing followed is
     * our own "open updates" flag and a log-sheet tag from our reminders and widget.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_UPDATES, false)) {
            intent.removeExtra(Alerts.EXTRA_OPEN_UPDATES)
            UpdateWatch.load(this)
            UpdateWatch.whatsNewOpen.value = true
        }
        // "On-device AI is ready" opens Ask Dropi.
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_ASK, false)) {
            intent.removeExtra(Alerts.EXTRA_OPEN_ASK)
            app.dropi.ui.Launch.openAsk.value = true
        }
        // The weekly recap opens Trends.
        if (intent.getStringExtra(Alerts.EXTRA_OPEN_TAB) == "trends") {
            intent.removeExtra(Alerts.EXTRA_OPEN_TAB)
            Launch.openTrends.value = true
        }
        // A reminder or the widget's +: open the log sheet. Only a known tag name (or "new") is
        // followed; anything else another app might send is dropped.
        intent.getStringExtra(Alerts.EXTRA_LOG_TAG)?.let { t ->
            intent.removeExtra(Alerts.EXTRA_LOG_TAG)
            if (t == "new" || MealTag.entries.any { it.name == t }) Launch.logTag.value = t
        }
    }
}
