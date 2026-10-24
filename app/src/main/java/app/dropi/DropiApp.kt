package app.dropi

import android.app.Application
import android.content.res.Configuration

/**
 * Runs first in every process start, including the ones with no screen (background update
 * checks). The crash recorder goes here so a crash anywhere is shown on the next launch.
 */
class DropiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // The app's language (English or Icelandic), before any text is made.
        L.init(this)
        // Sensor broadcasts sent to "any app" reach Dropi while it's running.
        app.dropi.work.LiveCgm.listen(this)
    }

    // A language change in the phone's settings (or Dropi's own) arrives here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L.refresh()
    }
}
