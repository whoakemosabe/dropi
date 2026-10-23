package app.dropi.work

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.dropi.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks this repo's GitHub Releases for a newer dropi.apk, downloads it and hands it to the
 * system installer. The repo is public, so no token is needed (same as F1 Pro, Afli and Ljós).
 */
object Updater {
    const val REPO = "whoakemosabe/dropi"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"

    data class Release(val version: String, val assetUrl: String, val sizeBytes: Long, val notes: List<String> = emptyList(), val behind: Int = 1)

    /**
     * The bullets from a release's notes: the hand-written "Highlights" when the release has
     * them (written for people, not developers), else the commit titles under "What's new".
     */
    fun whatsNew(body: String): List<String> {
        val sections = HashMap<String, MutableList<String>>()
        var current: String? = null
        for (line in body.lines()) {
            val t = line.trim()
            if (t.startsWith("###")) {
                current = when {
                    t.contains("Highlights", ignoreCase = true) -> "h"
                    t.contains("What's new", ignoreCase = true) -> "w"
                    else -> null
                }
                continue
            }
            if (t.startsWith("**")) current = null
            if (current != null && t.startsWith("- ")) sections.getOrPut(current) { ArrayList() } += t.removePrefix("- ").trim()
        }
        return (sections["h"] ?: sections["w"]).orEmpty().take(8)
    }

    /**
     * Everything new since [installed], newest release first: someone who skipped a few
     * versions sees all of their highlights, not just the last one's.
     */
    fun notesSince(releasesJson: String, installed: String): List<String> {
        val arr = org.json.JSONArray(releasesJson)
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            if (!isNewer(r.optString("tag_name"), installed)) continue
            out += whatsNew(r.optString("body"))
        }
        return out.take(10)
    }

    /** One release in the change log. [date] is "2026-10-23". */
    data class Logged(val version: String, val date: String, val notes: List<String>)

    /** Every published release, newest first, with its highlights. */
    fun changelog(releasesJson: String): List<Logged> {
        val arr = org.json.JSONArray(releasesJson)
        val out = ArrayList<Logged>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val notes = whatsNew(r.optString("body")).ifEmpty { listOf("Fixes and improvements") }
            out += Logged(r.optString("tag_name").removePrefix("v"), r.optString("published_at").take(10), notes)
        }
        return out.sortedWith { a, b -> if (isNewer(a.version, b.version)) -1 else if (isNewer(b.version, a.version)) 1 else 0 }
    }

    /** The change log from GitHub, or null when it can't be reached. */
    suspend fun fetchChangelog(): List<Logged>? = withContext(Dispatchers.IO) {
        try {
            val r = open("https://api.github.com/repos/$REPO/releases?per_page=50", "application/vnd.github+json")
            val body = if (r.responseCode in 200..299) r.inputStream.bufferedReader().use { it.readText() } else null
            r.disconnect()
            body?.let { changelog(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** How many published releases are newer than [installed] (capped by what the page holds). */
    fun countSince(releasesJson: String, installed: String): Int {
        val arr = org.json.JSONArray(releasesJson)
        var n = 0
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (!r.optBoolean("draft") && !r.optBoolean("prerelease") && isNewer(r.optString("tag_name"), installed)) n++
        }
        return n
    }

    sealed class Check {
        data object UpToDate : Check()
        data class Available(val release: Release) : Check()
        data class Failed(val message: String) : Check()
    }

    fun installedVersion(context: Context): String =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "0"

    /** "v2.0.12" / "2.0.12" → [2, 0, 12]. */
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val c = open("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404 || code == 401 || code == 403) {
                c.disconnect()
                return@withContext Check.Failed("GitHub can't see the releases (is the repo public?).")
            }
            if (code !in 200..299) throw IOException("GitHub returned $code")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) asset = a
            }
            val apk = asset ?: return@withContext Check.Failed("The latest release has no APK yet.")
            if (!isNewer(tag, installedVersion(context))) return@withContext Check.UpToDate
            val installed = installedVersion(context)
            val notes = try {
                val r = open("https://api.github.com/repos/$REPO/releases?per_page=30", "application/vnd.github+json")
                val body = if (r.responseCode in 200..299) r.inputStream.bufferedReader().use { it.readText() } else null
                r.disconnect()
                body?.let { notesSince(it, installed) to countSince(it, installed) }
            } catch (e: Exception) { null }
            Check.Available(Release(
                tag.removePrefix("v"), apk.getString("browser_download_url"), apk.optLong("size"),
                notes?.first?.takeIf { it.isNotEmpty() } ?: whatsNew(json.optString("body")),
                behind = notes?.second?.coerceAtLeast(1) ?: 1,
            ))
        } catch (e: Exception) {
            Check.Failed("Couldn't reach GitHub.")
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // GitHub answers with redirects to signed storage; follow them by hand (cross-host).
            var conn = open(release.assetUrl, "application/octet-stream").apply { instanceFollowRedirects = false }
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Download link missing")
                conn.disconnect()
                hops++
                conn = open(next, "application/octet-stream").apply { instanceFollowRedirects = false }
            }
            if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "dropi.apk")
            // Written to a part file first: a half-finished download never looks ready.
            val part = File(dir, "dropi-part.apk").apply { delete() }
            var done = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && done != total) {
                part.delete()
                throw IOException("Download incomplete ($done of $total bytes)")
            }
            verify(context, part, release.version)
            out.delete()
            if (!part.renameTo(out)) throw IOException("Couldn't save the update")
            out
        }

    /**
     * Checks the downloaded file is a whole APK of this app and the version it should be,
     * before the system installer sees it (it would only say "problem parsing the package").
     * The installer still checks the signature itself.
     */
    private fun verify(context: Context, apk: File, expected: String) {
        val info = try {
            context.packageManager.getPackageArchiveInfo(apk.path, 0)
        } catch (e: Exception) { null }
        if (info == null) {
            apk.delete()
            throw IOException("The download is damaged")
        }
        if (info.packageName != context.packageName) {
            apk.delete()
            throw IOException("The download isn't Dropi")
        }
        val v = info.versionName.orEmpty()
        if (isNewer(expected, v) || !isNewer(v, installedVersion(context))) {
            apk.delete()
            throw IOException("The download is the wrong version ($v)")
        }
    }

    /** True if the installer opened; false if the app first needs "install unknown apps". */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Dropi-updater")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return c
    }
}

/** Where the one-tap update is: shared by the top banner and Settings → Updates. */
sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Downloading(val version: String) : UpdateUi()
    data class Ready(val file: File, val version: String, val note: String? = null) : UpdateUi()
    /** Found a newer version; waiting for "Download" (Settings asks before downloading). */
    data class Available(val release: Updater.Release) : UpdateUi()
    data class Error(val message: String) : UpdateUi()
}

/**
 * Notices new releases on its own, like F1 Pro: the background check asks GitHub about every
 * 6 hours and posts one quiet notification per version; opening the app asks at most every
 * 10 minutes. A waiting version shows as the banner across the top. Its Update button opens
 * the "What's new" window, which downloads (progress in the window and the banner) and then
 * installs.
 */
object UpdateWatch {
    private const val BACKGROUND_EVERY = 6 * 60 * 60_000L
    private const val OPEN_EVERY = 10 * 60_000L

    /** The newer version waiting, if any. */
    var waitingVersion by mutableStateOf<String?>(null)
        private set

    /** The version whose banner was closed with ×. */
    var bannerHidden by mutableStateOf<String?>(null)

    /** The "What's new" window is open (from the banner, the notification or Settings). */
    val whatsNewOpen = mutableStateOf(false)

    /** Set when the app should open Settings → Updates (e.g. from the notification). */
    val openUpdates = mutableStateOf(false)

    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
    var progress by mutableFloatStateOf(0f)

    fun waiting(context: Context): String? {
        val v = Prefs(context).availableVersion
        return v.takeIf { it.isNotBlank() && Updater.isNewer(it, Updater.installedVersion(context)) }
    }

    fun load(context: Context) {
        waitingVersion = waiting(context)
    }

    /** The waiting version's highlights, as saved with it. */
    fun notes(context: Context): List<String> =
        Prefs(context).availableNotes.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** How many updates the waiting version is ahead of this one (1 = just the latest). */
    fun behind(context: Context): Int = Prefs(context).availableBehind.coerceAtLeast(1)

    /** Asks GitHub if it's been long enough (or [force]); returns the newer version waiting. */
    suspend fun check(context: Context, background: Boolean, force: Boolean = false): String? {
        val p = Prefs(context)
        val now = System.currentTimeMillis()
        val every = if (background) BACKGROUND_EVERY else OPEN_EVERY
        if (force || now - p.lastUpdateCheck >= every) {
            p.lastUpdateCheck = now
            when (val r = Updater.check(context)) {
                is Updater.Check.Available -> {
                    p.availableVersion = r.release.version
                    p.availableNotes = r.release.notes.joinToString("\n")
                    p.availableBehind = r.release.behind
                }
                Updater.Check.UpToDate -> p.availableVersion = ""
                is Updater.Check.Failed -> {}
            }
        }
        val w = waiting(context)
        waitingVersion = w
        return w
    }

    /** Background: check, and notify once per new version. */
    suspend fun checkAndNotify(context: Context) {
        val v = check(context, background = true) ?: return
        val p = Prefs(context)
        if (p.notifiedVersion == v) return
        p.notifiedVersion = v
        Alerts.postUpdate(context, v)
    }

    /**
     * Settings → Check for updates: only checks. A newer version becomes [UpdateUi.Available]
     * and Settings asks before downloading it.
     */
    suspend fun checkOnly(context: Context) {
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) return
        ui = UpdateUi.Checking
        ui = when (val r = Updater.check(context)) {
            is Updater.Check.Available -> {
                Prefs(context).availableVersion = r.release.version
                Prefs(context).availableNotes = r.release.notes.joinToString("\n")
                Prefs(context).availableBehind = r.release.behind
                waitingVersion = waiting(context)
                UpdateUi.Available(r.release)
            }
            Updater.Check.UpToDate -> {
                Prefs(context).availableVersion = ""
                waitingVersion = null
                UpdateUi.UpToDate
            }
            is Updater.Check.Failed -> UpdateUi.Error(r.message)
        }
    }

    /** "Not now" on the confirm: back to idle; the banner still offers it. */
    fun notNow() {
        if (ui is UpdateUi.Available) ui = UpdateUi.Idle
    }

    private suspend fun download(context: Context, release: Updater.Release): Boolean {
        ui = UpdateUi.Downloading(release.version)
        progress = 0f
        return try {
            val f = Updater.download(context, release) { progress = it }
            ui = UpdateUi.Ready(f, release.version)
            true
        } catch (e: Exception) {
            ui = UpdateUi.Error("Download failed. Tap to try again.")
            false
        }
    }

    /**
     * One tap: check → download → ready; a tap when ready installs. Returns true when the
     * download finished (for a confirm haptic).
     */
    suspend fun tap(context: Context): Boolean {
        when (val st = ui) {
            is UpdateUi.Ready -> {
                if (!Updater.install(context, st.file)) ui = st.copy(note = "Allow Dropi to install updates, then tap again.")
                return false
            }
            is UpdateUi.Available -> return download(context, st.release)
            is UpdateUi.Checking, is UpdateUi.Downloading -> return false
            else -> {}
        }
        ui = UpdateUi.Checking
        return when (val r = Updater.check(context)) {
            is Updater.Check.Available -> download(context, r.release)
            Updater.Check.UpToDate -> {
                Prefs(context).availableVersion = ""
                waitingVersion = null
                ui = UpdateUi.UpToDate
                false
            }
            is Updater.Check.Failed -> {
                ui = UpdateUi.Error(r.message)
                false
            }
        }
    }
}
