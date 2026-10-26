package app.dropi.work

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import app.dropi.L
import app.dropi.data.Plate
import app.dropi.data.PlateGuess
import app.dropi.tr
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Google's open Gemma 4 E2B model, run on the phone by LiteRT-LM, for phones without Gemini Nano.
 * The model isn't in the APK: it's a 2.6 GB one-time download, only when asked for in Settings,
 * only over Wi-Fi, straight from Hugging Face (pinned to one commit and checked against its
 * SHA-256). After that, meal photos and questions are answered on the phone and never uploaded.
 */
object Gemma {
    const val FILE = "gemma-4-E2B-it.litertlm"
    private const val REPO = "litert-community/gemma-4-E2B-it-litert-lm"
    private const val COMMIT = "a790a852d433991c0c0a41c1154cf9b3850300fa"
    const val URL = "https://huggingface.co/$REPO/resolve/$COMMIT/$FILE"
    const val SIZE = 2_588_147_712L
    const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

    /** Room left over after the download, so the phone isn't filled to the brim. */
    private const val SPARE = 400L * 1024 * 1024

    /** Below this the model would starve the phone (it needs about 1–1.7 GB while answering). */
    private const val MIN_RAM = 5_000_000_000L

    /** The model is unloaded after this long unused, giving its memory back. */
    private const val IDLE_MS = 10 * 60_000L

    sealed interface State {
        data object Checking : State
        /** [why] is English (problem reports); [shownWhy] is in the app's language. */
        data class Unsupported(val why: String) : State {
            val shownWhy: String get() = when (why) {
                "it needs a 64-bit phone" -> tr("it needs a 64-bit phone")
                "it needs a phone with at least 6 GB of memory" -> tr("it needs a phone with at least 6 GB of memory")
                "the phone's storage isn't available right now" -> tr("the phone's storage isn't available right now")
                else -> why
            }
        }
        data object NotDownloaded : State
        data object WaitingForWifi : State
        data class Downloading(val done: Long) : State
        data object Verifying : State
        data object Ready : State
        /**
         * [message] is English (problem reports); [shown] is in the app's language. [need] and
         * [free] are set (bytes) when there wasn't room for the download.
         */
        data class Failed(val message: String, val need: Long = 0L, val free: Long = 0L) : State {
            val shown: String get() = when {
                need > 0L -> tr("Needs about {0} GB free; this phone has {1} GB.", Gemma.gb(need), Gemma.gb(free))
                message == "Ran out of space. Free up about 3 GB and try again." -> tr("Ran out of space. Free up about 3 GB and try again.")
                message == "The download stopped. Try again on Wi-Fi." -> tr("The download stopped. Try again on Wi-Fi.")
                message == "The download didn't check out, so it was deleted. Try again." -> tr("The download didn't check out, so it was deleted. Try again.")
                else -> message
            }

            override fun toString(): String = "Failed(message=$message)"
        }
    }

    var state by mutableStateOf<State>(State.Checking)
        private set

    /** True while the model sits in memory (the first answer after a while takes a few seconds more). */
    var loaded by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The SHA-256 check of a finished download. App-wide, so closing a screen can't cancel it. */
    private var verifyJob: Job? = null

    /** Follows a running download once a second while the app is alive, whatever screen is open. */
    private var watchJob: Job? = null
    private val lock = Mutex()
    private val fileLock = Mutex()
    private var engine: Engine? = null
    private var idle: Job? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dropi_ai", Context.MODE_PRIVATE)

    private fun dir(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    fun file(context: Context): File? = dir(context)?.let { File(it, FILE) }

    /** Why this phone can't run the model, or null if it can. */
    fun unsupported(context: Context): String? {
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) return "it needs a 64-bit phone"
        val mem = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        if (mem.totalMem in 1 until MIN_RAM) return "it needs a phone with at least 6 GB of memory"
        if (dir(context) == null) return "the phone's storage isn't available right now"
        return null
    }

    /** Works out where things stand: downloaded, downloading, waiting for Wi-Fi, or not yet. */
    suspend fun refresh(context: Context): State = fileLock.withLock {
        val ctx = context.applicationContext
        unsupported(ctx)?.let { state = State.Unsupported(it); return@withLock state }
        val f = file(ctx)!!
        val p = prefs(ctx)
        if (p.getBoolean("verified", false) && f.length() == SIZE) {
            readyBanner = !p.getBoolean("ready_seen", true)
            state = State.Ready
            return@withLock state
        }
        val id = p.getLong("download_id", -1L)
        if (id == -1L) {
            if (state !is State.Failed) state = State.NotDownloaded
            return@withLock state
        }
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val row = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) null
            else Triple(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
        state = when (row?.first) {
            null -> {
                p.edit { remove("download_id") }
                State.NotDownloaded
            }
            DownloadManager.STATUS_PENDING -> State.Downloading(row.second)
            DownloadManager.STATUS_RUNNING -> State.Downloading(row.second)
            DownloadManager.STATUS_PAUSED ->
                if (row.third == DownloadManager.PAUSED_QUEUED_FOR_WIFI || row.third == DownloadManager.PAUSED_WAITING_FOR_NETWORK) State.WaitingForWifi
                else State.Downloading(row.second)
            DownloadManager.STATUS_SUCCESSFUL -> {
                startVerify(ctx)
                State.Verifying
            }
            else -> {
                dm.remove(id)
                p.edit { remove("download_id") }
                State.Failed(
                    if (row.third == DownloadManager.ERROR_INSUFFICIENT_SPACE) "Ran out of space. Free up about 3 GB and try again."
                    else "The download stopped. Try again on Wi-Fi."
                )
            }
        }
        state
    }

    /**
     * Checks the finished file against its SHA-256 (10–30 s for 2.6 GB). Runs in Gemma's own
     * scope: a screen closing, or the state changing under a screen's loop, can't cancel it.
     */
    private fun startVerify(ctx: Context) {
        if (verifyJob?.isActive == true) return
        verifyJob = scope.launch {
            val f = file(ctx) ?: return@launch
            val good = withContext(Dispatchers.IO) { f.length() == SIZE && sha256(f) == SHA256 }
            fileLock.withLock {
                val p = prefs(ctx)
                p.edit { remove("download_id"); putBoolean("verified", good) }
                if (good) {
                    // The home screen's banner says "ready" once, until it's tapped or closed.
                    p.edit { putBoolean("ready_seen", false) }
                    readyBanner = true
                    state = State.Ready
                    Alerts.postAiReady(ctx)
                } else {
                    f.delete()
                    state = State.Failed("The download didn't check out, so it was deleted. Try again.")
                }
            }
        }
    }

    /** Waits for a running check to finish (used by the download-complete worker). */
    suspend fun awaitVerify() {
        verifyJob?.join()
    }

    /** Keeps [state] current while a download or its check is under way. */
    fun watch(context: Context) {
        val ctx = context.applicationContext
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (true) {
                val st = refresh(ctx)
                if (st !is State.Downloading && st != State.WaitingForWifi && st != State.Verifying) break
                delay(1000)
            }
        }
    }

    /** The home screen shows "on-device AI is ready" once, until it's tapped or closed. */
    var readyBanner by mutableStateOf(false)
        private set

    fun markReadySeen(context: Context) {
        prefs(context).edit { putBoolean("ready_seen", true) }
        readyBanner = false
    }

    /** Starts the one-time download (Wi-Fi only; Android's download manager shows its progress). */
    suspend fun download(context: Context) {
        val ctx = context.applicationContext
        fileLock.withLock {
            if (unsupported(ctx) != null) return@withLock
            val f = file(ctx)!!
            val p = prefs(ctx)
            if (p.getLong("download_id", -1L) != -1L) return@withLock
            f.delete()
            val free = StatFs(f.parentFile!!.path).availableBytes
            if (free < SIZE + SPARE) {
                state = State.Failed("Needs about ${gbPlain(SIZE + SPARE)} GB free; this phone has ${gbPlain(free)} GB.", SIZE + SPARE, free)
                return@withLock
            }
            val req = DownloadManager.Request(URL.toUri())
                .setTitle(tr("Dropi on-device AI"))
                .setDescription(tr("Gemma 4 model, 2.6 GB"))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, FILE)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false)
            val id = ctx.getSystemService(DownloadManager::class.java).enqueue(req)
            p.edit { putLong("download_id", id); putBoolean("verified", false) }
            state = State.Downloading(0)
        }
        watch(ctx)
    }

    /** Stops a download, or deletes the downloaded model to get the space back. */
    suspend fun remove(context: Context) {
        val ctx = context.applicationContext
        lock.withLock { closeEngine() }
        fileLock.withLock {
            val p = prefs(ctx)
            val id = p.getLong("download_id", -1L)
            if (id != -1L) ctx.getSystemService(DownloadManager::class.java).remove(id)
            file(ctx)?.delete()
            p.edit { remove("download_id"); putBoolean("verified", false) }
            state = if (unsupported(ctx) == null) State.NotDownloaded else State.Unsupported(unsupported(ctx)!!)
        }
    }

    /** The photo for the model: upright, 512 px on its long side, as JPEG (smaller and quicker than 768 px PNG). */
    private suspend fun photoBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        Scan.settled(context, uri)
        val bitmap = Nano.loadScaled(context, uri, 512) ?: return@withContext null
        try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /** A guess at the foods and carbs in a meal photo, or null if the model couldn't tell. */
    suspend fun plate(context: Context, uri: Uri): PlateGuess? {
        val img = photoBytes(context, uri) ?: return null
        val text = generate(context, 0.2, maxOut = 200) { Contents.of(Content.ImageBytes(img), Content.Text(Plate.PROMPT)) }
        return Plate.parse(text)
    }

    /** A meal breakdown from a photo, held to the meal JSON shape; [partial] sees the reply as it grows. */
    suspend fun imageJson(context: Context, uri: Uri, prompt: String, schema: String?, partial: ((String) -> Unit)? = null): String? {
        val img = photoBytes(context, uri) ?: return null
        return generate(context, 0.2, schema, maxOut = 900, partial = partial) { Contents.of(Content.ImageBytes(img), Content.Text(prompt)) }
    }

    /** A reply in words held to a JSON shape ([maxOut] tokens at most). */
    suspend fun textJson(context: Context, prompt: String, schema: String?, maxOut: Int = 700): String? =
        generate(context, 0.2, schema, maxOut = maxOut) { Contents.of(Content.Text(prompt)) }

    /** A short answer to a typed question, from [prompt] (question plus the facts). */
    suspend fun ask(context: Context, prompt: String): String? =
        generate(context, 0.3, maxOut = 300, stopAtJson = false) { Contents.of(Content.Text(prompt)) }?.trim()?.takeIf { it.isNotEmpty() }

    /** Which chip the model runs on, for Settings (null until it has loaded once). */
    var backendName by mutableStateOf<String?>(null)
        private set

    /**
     * Loads the model ahead of time (when the + panel or the camera opens), so the first answer
     * doesn't wait for it. Does nothing if it's loaded, loading, or busy answering.
     */
    fun warm(context: Context) {
        if (state != State.Ready || engine != null) return
        val ctx = context.applicationContext
        scope.launch {
            if (!lock.tryLock()) return@launch
            try {
                if (engine == null) file(ctx)?.let { open(ctx, it) }
            } catch (e: Throwable) {
            } finally {
                lock.unlock()
            }
            idleAfter()
        }
    }

    /** Lets the model go from memory now (after any answer that's running), e.g. when Nano is picked. */
    fun unload() {
        idle?.cancel()
        idle = scope.launch { lock.withLock { closeEngine() } }
    }

    private fun idleAfter() {
        idle?.cancel()
        idle = scope.launch {
            delay(IDLE_MS)
            lock.withLock { closeEngine() }
        }
    }

    /** The end of the first complete JSON object in [s], or -1 while it's still open. */
    private fun jsonEnd(s: CharSequence): Int {
        var depth = 0; var inString = false; var escaped = false; var started = false
        for (i in s.indices) {
            val ch = s[i]
            if (inString) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> { depth++; started = true }
                '}' -> { depth--; if (started && depth == 0) return i }
            }
        }
        return -1
    }

    /**
     * One answer, streamed: it stops as soon as the JSON is complete (no waiting for the model to
     * wind down), is capped at [maxOut] tokens, and a caller that stops waiting (more typing)
     * cancels the model's work instead of letting it run on and block the next question.
     */
    private suspend fun generate(
        context: Context, temperature: Double, schema: String? = null, maxOut: Int = 700,
        stopAtJson: Boolean = true, partial: ((String) -> Unit)? = null, contents: () -> Contents,
    ): String? {
        if (state != State.Ready && refresh(context) != State.Ready) return null
        val f = file(context) ?: return null
        return lock.withLock {
            idle?.cancel()
            try {
                withContext(Dispatchers.Default) {
                    val e = engine ?: open(context, f) ?: return@withContext null
                    val sampler = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                    suspend fun once(held: Boolean): String = try {
                        stream(context, e, sampler, held, schema, maxOut, stopAtJson, partial, contents)
                    } catch (x: SpecFailed) {
                        Diag.log(context, "Gemma: speculative decoding failed, turned off")
                        stream(context, e, sampler, held, schema, maxOut, stopAtJson, partial, contents)
                    }
                    if (schema != null) {
                        // Held to the JSON shape where the engine supports it; plain otherwise.
                        val held = try { once(true) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) {
                            Diag.log(context, "Gemma: held-to-shape answer failed (${t.javaClass.simpleName}: ${t.message?.take(120)}), asking plainly")
                            null
                        }
                        if (!held.isNullOrBlank()) return@withContext held
                    }
                    once(false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // The caller stopped waiting (e.g. more typing): the engine is fine, keep it.
                throw e
            } catch (e: Throwable) {
                // A native failure can leave the engine unusable; start fresh next time.
                Diag.log(context, "Gemma: answer failed (${e.javaClass.simpleName}: ${e.message?.take(160)}); model unloaded")
                closeEngine()
                null
            } finally {
                idleAfter()
            }
        }
    }

    /** Speculative decoding didn't work on this phone; it's now off, so the same answer is asked again. */
    private class SpecFailed : Exception()

    private suspend fun stream(
        context: Context, e: Engine, sampler: SamplerConfig, held: Boolean, schema: String?, maxOut: Int,
        stopAtJson: Boolean, partial: ((String) -> Unit)?, contents: () -> Contents,
    ): String {
        val spec = speculative(context)
        val config = ConversationConfig(samplerConfig = sampler, enableResponseFormat = held, enableSpeculativeDecoding = if (spec) true else null)
        val conv = try { e.createConversation(config) } catch (t: Throwable) {
            if (spec) { prefs(context).edit { putBoolean("spec_off", true) }; throw SpecFailed() }
            throw t
        }
        val text = StringBuilder()
        val done = kotlinx.coroutines.CompletableDeferred<Throwable?>()
        // Stopping and closing never overlap, and a stop is never asked from inside the model's own callback.
        val guard = Any()
        var closed = false
        var stopAsked = false
        fun stop() {
            if (stopAsked) return
            stopAsked = true
            scope.launch { synchronized(guard) { if (!closed) try { conv.cancelProcess() } catch (_: Throwable) { } } }
        }
        try {
            if (spec) markTrying(context, true)
            conv.sendMessageAsync(
                contents(),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val piece = message.toString()
                        // Chunks are usually new text; if one repeats what came before, it's the whole text so far.
                        if (text.isNotEmpty() && piece.startsWith(text.toString())) { text.setLength(0); text.append(piece) } else text.append(piece)
                        partial?.invoke(text.toString())
                        if (stopAtJson && jsonEnd(text) >= 0) stop()
                    }
                    override fun onDone() { done.complete(null) }
                    override fun onError(throwable: Throwable) { done.complete(throwable) }
                },
                maxOutputToken = maxOut,
                responseFormat = if (held && schema != null) ResponseFormat.json(schema) else null,
            )
            val err = try {
                done.await()
            } catch (c: kotlinx.coroutines.CancellationException) {
                // Stop the model, and wait for it to stop before closing (closing mid-answer can crash).
                synchronized(guard) { try { conv.cancelProcess() } catch (_: Throwable) { } }
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    kotlinx.coroutines.withTimeoutOrNull(5_000) { done.await() }
                }
                if (spec) markTrying(context, false)
                throw c
            }
            if (spec) markTrying(context, false)
            // Stopped on purpose once the JSON was complete: that's a finished answer, not an error.
            if (err != null && err !is kotlinx.coroutines.CancellationException) {
                // The model can't guess ahead on this phone: turn it off, the caller asks again without it.
                if (spec) { prefs(context).edit { putBoolean("spec_off", true) }; throw SpecFailed() }
                throw err
            }
            val s = text.toString()
            val end = if (stopAtJson) jsonEnd(s) else -1
            return if (end >= 0) s.substring(0, end + 1) else s
        } finally {
            if (done.isCompleted) synchronized(guard) { closed = true; try { conv.close() } catch (_: Throwable) { } }
        }
    }

    /**
     * Speculative decoding (a small helper model guesses ahead, so answers come out faster). Used
     * when the model has it. If an answer with it ever didn't finish (the app closed mid-way, which
     * is what a crash looks like), it's turned off for good on this phone.
     */
    private fun speculative(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean("spec_trying", false)) p.edit { putBoolean("spec_off", true); putBoolean("spec_trying", false) }
        return !p.getBoolean("spec_off", false)
    }

    private fun markTrying(context: Context, on: Boolean) = prefs(context).edit { putBoolean("spec_trying", on) }

    /** Loads the model, on the GPU when the phone allows and on the CPU otherwise. */
    private fun open(context: Context, f: File): Engine? {
        for (gpu in listOf(true, false)) {
            val backend: Backend = if (gpu) Backend.GPU() else Backend.CPU()
            var made: Engine? = null
            try {
                val e = Engine(
                    EngineConfig(
                        modelPath = f.absolutePath,
                        backend = backend,
                        visionBackend = if (gpu) Backend.GPU() else Backend.CPU(),
                        maxNumTokens = 4096,
                        maxNumImages = 1,
                        cacheDir = context.cacheDir.absolutePath,
                    )
                )
                made = e
                e.initialize()
                engine = e
                loaded = true
                backendName = if (gpu) GPU_TEXT else CPU_TEXT
                prefs(context).edit { putBoolean("last_gpu", gpu) }
                return e
            } catch (t: Throwable) {
                // Free what the failed backend took, then try the next one.
                Diag.log(context, "Gemma: couldn't load on ${if (gpu) "GPU" else "CPU"} (${t.javaClass.simpleName}: ${t.message?.take(160)})")
                try { made?.close() } catch (_: Throwable) { }
            }
        }
        return null
    }

    /** What ran last time, before this session has loaded the model. */
    fun lastBackend(context: Context): String? {
        val p = prefs(context)
        if (!p.contains("last_gpu")) return null
        return if (p.getBoolean("last_gpu", true)) GPU_TEXT else CPU_TEXT
    }

    private fun closeEngine() {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
        loaded = false
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered(1 shl 20).use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Gigabytes with one decimal, as shown (decimal comma in Icelandic). */
    fun gb(bytes: Long): String = gbPlain(bytes).replace('.', L.decimal)

    /** Gigabytes with one decimal and a point, for problem reports. */
    private fun gbPlain(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f", bytes / 1_000_000_000.0)

    private const val GPU_TEXT = "the graphics chip (GPU), the fast way"
    private const val CPU_TEXT = "the main processor (CPU). Slower: the graphics chip didn't work on this phone"

    /** [backendName] or [lastBackend] (English, also in problem reports) in the app's language. */
    fun backendShown(english: String): String = when (english) {
        GPU_TEXT -> tr("the graphics chip (GPU), the fast way")
        CPU_TEXT -> tr("the main processor (CPU). Slower: the graphics chip didn't work on this phone")
        else -> english
    }
}

/**
 * Android's download manager says a download finished, possibly while Dropi was closed. A short
 * background job checks the model file and posts "ready", so nobody has to open the app first.
 * It only looks at Dropi's own state; the broadcast's contents aren't trusted.
 */
class GemmaDownloadReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "gemma-verify",
            androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<GemmaVerifyWorker>().build(),
        )
    }
}

class GemmaVerifyWorker(context: Context, params: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Gemma.refresh(applicationContext)
        Gemma.awaitVerify()
        return Result.success()
    }
}
