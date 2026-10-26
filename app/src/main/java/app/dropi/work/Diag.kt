package app.dropi.work

import android.content.Context
import android.os.Build
import app.dropi.CrashLog
import app.dropi.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A small diary of what went wrong (a label that read nothing, an AI reply that couldn't be read,
 * the model failing to load…), kept on the phone so "Report a problem" can say exactly why
 * something didn't work. Never holds readings or meals: only what failed and why.
 */
object Diag {
    private const val MAX = 60
    private const val FILE = "diag"
    private val time = DateTimeFormatter.ofPattern("d MMM HH:mm:ss").withZone(ZoneId.systemDefault())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Notes one event ("Label: no carbs found in 14 lines"). Cheap, safe from any thread. */
    @Synchronized
    fun log(context: Context, what: String) {
        try {
            val p = prefs(context)
            val lines = p.getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }
            val line = time.format(Instant.now()) + "  " + what.replace('\n', ' ').take(400)
            p.edit().putString("log", (lines + line).takeLast(MAX).joinToString("\n")).apply()
        } catch (e: Exception) { }
    }

    fun entries(context: Context): List<String> =
        prefs(context).getString("log", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun clear(context: Context) = prefs(context).edit().remove("log").apply()

    /**
     * Everything a problem report holds, as plain text: the person's note, the app and phone, the
     * on-device AI's state, the settings that change behaviour, the last label scan, recent
     * problems and the last crash. No readings, meals or notes.
     */
    fun report(context: Context, note: String): String {
        val ctx = context.applicationContext
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        val p = Prefs(ctx)
        val ai = ctx.getSharedPreferences("dropi_ai", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        sb.append("Dropi problem report\n")
        sb.append("Sent ").append(time.format(Instant.now())).append("\n\n")
        if (note.isNotBlank()) sb.append("What happened:\n").append(note.trim()).append("\n\n")
        sb.append("App: Dropi ").append(version).append('\n')
        sb.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.let { val m = android.app.ActivityManager.MemoryInfo().also { mi -> it.getMemoryInfo(mi) }; sb.append("Memory: ").append(m.totalMem / 1_000_000).append(" MB total, ").append(m.availMem / 1_000_000).append(" MB free\n") }
        sb.append('\n')

        sb.append("On-device AI\n")
        sb.append("  Gemini Nano: ").append(Nano.state).append('\n')
        sb.append("  Gemma: ").append(Gemma.state.toString()).append(if (Gemma.loaded) " (loaded)" else " (not loaded)").append('\n')
        sb.append("  Runs on: ").append(Gemma.backendName ?: Gemma.lastBackend(ctx) ?: "not known yet").append('\n')
        sb.append("  Speculative decoding: ").append(if (ai.getBoolean("spec_off", false)) "off (failed before)" else "on").append('\n')
        sb.append("  Picked: ").append(Ai.pick).append(if (Ai.canPick) "" else " (no choice on this phone)").append('\n')
        sb.append("  In use for meals: ").append(if (Ai.usable) Ai.name else "none").append('\n')
        sb.append("  Foods remembered: ").append(p.foodCache.size).append(", your usual amounts: ").append(p.usualFoods.size).append("\n\n")

        sb.append("Settings\n")
        sb.append("  Unit ").append(p.unit).append(", theme ").append(p.theme)
            .append(", reminders ").append(if (p.remindersOn) "on" else "off")
            .append(", after-eating reminder ").append(if (p.afterMealOn) "${p.afterMealMinutes} min" else "off")
            .append(", Health Connect ").append(if (p.healthSync) "on" else "off")
            .append(", live sensor ").append(if (p.liveOn) "on" else "off")
            .append(if (p.demo) ", DEMO DATA ON" else "").append('\n')
        sb.append("  Notifications allowed: ").append(Alerts.canNotify(ctx)).append('\n')
        sb.append("  Pending reminders: ").append(p.oneOffs.size).append("\n\n")

        val label = Scan.lastText
        sb.append("Last label scan\n")
        if (label.isEmpty()) sb.append("  (none since Dropi opened)\n")
        else {
            Scan.problemEn?.let { sb.append("  Problem: ").append(it).append('\n') }
            sb.append("  Text read from the photo:\n")
            label.take(80).forEach { sb.append("    ").append(it).append('\n') }
        }
        sb.append('\n')

        val log = entries(ctx)
        sb.append("Recent problems (").append(log.size).append(")\n")
        if (log.isEmpty()) sb.append("  none\n") else log.forEach { sb.append("  ").append(it).append('\n') }
        sb.append('\n')

        val crash = CrashLog.read(ctx) ?: CrashLog.readSeen(ctx)
        sb.append("Last crash\n").append(crash?.let { it.take(6_000) } ?: "  none").append('\n')
        return sb.toString()
    }
}
