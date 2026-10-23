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
import app.dropi.data.Plate
import app.dropi.data.PlateGuess
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
    private const val IDLE_MS = 3 * 60_000L

    sealed interface State {
        data object Checking : State
        data class Unsupported(val why: String) : State
        data object NotDownloaded : State
        data object WaitingForWifi : State
        data class Downloading(val done: Long) : State
        data object Verifying : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Checking)
        private set

    /** True while the model sits in memory (the first answer after a while takes a few seconds more). */
    var loaded by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
                state = State.Verifying
                val good = withContext(Dispatchers.IO) { f.length() == SIZE && sha256(f) == SHA256 }
                p.edit { remove("download_id"); putBoolean("verified", good) }
                if (good) State.Ready
                else {
                    f.delete()
                    State.Failed("The download didn't check out, so it was deleted. Try again.")
                }
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
                state = State.Failed("Needs about ${gb(SIZE + SPARE)} GB free; this phone has ${gb(free)} GB.")
                return@withLock
            }
            val req = DownloadManager.Request(Uri.parse(URL))
                .setTitle("Dropi on-device AI")
                .setDescription("Gemma 4 model, 2.6 GB")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, FILE)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false)
            val id = ctx.getSystemService(DownloadManager::class.java).enqueue(req)
            p.edit { putLong("download_id", id); putBoolean("verified", false) }
            state = State.Downloading(0)
        }
        refresh(ctx)
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

    /** A guess at the foods and carbs in a meal photo, or null if the model couldn't tell. */
    suspend fun plate(context: Context, uri: Uri): PlateGuess? {
        val png = withContext(Dispatchers.IO) {
            val bitmap = Nano.loadScaled(context, uri, 768) ?: return@withContext null
            try {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            } finally {
                bitmap.recycle()
            }
        } ?: return null
        val text = generate(context, 0.2) { Contents.of(Content.ImageBytes(png), Content.Text(Plate.PROMPT)) }
        return Plate.parse(text)
    }

    /** A short answer to a typed question, from [prompt] (question plus the facts). */
    suspend fun ask(context: Context, prompt: String): String? =
        generate(context, 0.3) { Contents.of(Content.Text(prompt)) }?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun generate(context: Context, temperature: Double, contents: () -> Contents): String? {
        if (state != State.Ready && refresh(context) != State.Ready) return null
        val f = file(context) ?: return null
        return lock.withLock {
            idle?.cancel()
            try {
                withContext(Dispatchers.Default) {
                    val e = engine ?: open(context, f) ?: return@withContext null
                    e.createConversation(
                        ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature))
                    ).use { it.sendMessage(contents()).toString() }
                }
            } catch (e: Throwable) {
                // A native failure can leave the engine unusable; start fresh next time.
                closeEngine()
                null
            } finally {
                idle = scope.launch {
                    delay(IDLE_MS)
                    lock.withLock { closeEngine() }
                }
            }
        }
    }

    /** Loads the model, on the GPU when the phone allows and on the CPU otherwise. */
    private fun open(context: Context, f: File): Engine? {
        for (gpu in listOf(true, false)) {
            val backend: Backend = if (gpu) Backend.GPU() else Backend.CPU()
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
                e.initialize()
                engine = e
                loaded = true
                return e
            } catch (t: Throwable) {
                // Try the next backend.
            }
        }
        return null
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

    fun gb(bytes: Long): String = "%.1f".format(bytes / 1_000_000_000.0)
}
