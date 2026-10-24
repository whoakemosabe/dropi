package app.dropi

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Keeps the last crash on disk so the next launch can show it (with a Copy button), since
 * there's no logcat on a phone. Chains to the system handler, so the app still closes normally.
 */
object CrashLog {
    private fun file(context: Context) = File(context.applicationContext.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    private class Handler(
        private val app: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (x: Exception) { "?" }
                file(app).writeText(
                    "Dropi $version · Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n" +
                        "${Instant.now()} · thread ${t.name}\n\n" + sw.toString().take(12_000)
                )
            } catch (x: Throwable) { }
            previous?.uncaughtException(t, e)
        }
    }

    fun read(context: Context): String? = try { file(context).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }

    /** Dismissed from the screen, but kept (as the last seen crash) for a problem report. */
    fun clear(context: Context) {
        try {
            val f = file(context)
            if (f.exists()) f.copyTo(seen(context), overwrite = true)
            f.delete()
        } catch (e: Exception) { }
    }

    private fun seen(context: Context) = File(context.applicationContext.filesDir, "last-crash-seen.txt")
    fun readSeen(context: Context): String? = try { seen(context).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }
}
