package app.dropi

import android.app.Application

/**
 * Runs first in every process start, including the ones with no screen (background update
 * checks). The crash recorder goes here so a crash anywhere is shown on the next launch.
 */
class DropiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
