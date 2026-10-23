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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import app.dropi.ui.AppState
import app.dropi.ui.C
import app.dropi.ui.DropiRoot
import app.dropi.ui.DropiTheme
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
                "dark" -> true
                else -> systemDark
            }
            C.dark = dark
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
                            "dark" -> R.style.Theme_Dropi
                            else -> R.style.Theme_Dropi_System
                        }
                    )
                }
            }
            DropiTheme { DropiRoot(s) }
        }
    }

    /** Dark or light, from the saved theme setting, before any screen has loaded. */
    private fun startsDark(): Boolean = when (Prefs(this).theme) {
        "light" -> false
        "dark" -> true
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
     * our own boolean "open updates" flag from the update notification.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_UPDATES, false)) {
            intent.removeExtra(Alerts.EXTRA_OPEN_UPDATES)
            UpdateWatch.load(this)
            UpdateWatch.whatsNewOpen.value = true
        }
    }
}
