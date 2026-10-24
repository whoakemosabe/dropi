package app.dropi.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import app.dropi.data.Plate
import app.dropi.data.PlateGuess
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * Gemini Nano on the phone, through ML Kit's GenAI Prompt API and Android's AICore. Photos and
 * questions are processed on the device and never uploaded. Phones without it (most older or
 * budget ones) simply don't show the features that need it.
 */
object Nano {
    enum class State { CHECKING, READY, NEEDS_DOWNLOAD, DOWNLOADING, UNAVAILABLE }

    var state by mutableStateOf(State.CHECKING)
        private set

    private var model: GenerativeModel? = null
    private val lock = Mutex()

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    /** Asks AICore whether Gemini Nano is here, downloadable, or not on this phone. */
    suspend fun refresh(): State {
        state = try {
            when (client().checkStatus()) {
                FeatureStatus.AVAILABLE -> State.READY
                FeatureStatus.DOWNLOADABLE -> State.NEEDS_DOWNLOAD
                FeatureStatus.DOWNLOADING -> State.DOWNLOADING
                else -> State.UNAVAILABLE
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            State.UNAVAILABLE
        }
        return state
    }

    /** Gets the model ready, downloading it (once, over the network, by AICore) if needed. */
    suspend fun prepare(): Boolean {
        if (state == State.CHECKING) refresh()
        if (state == State.READY) return true
        if (state == State.UNAVAILABLE) return false
        state = State.DOWNLOADING
        return try {
            val end = withTimeoutOrNull(15 * 60_000L) {
                client().download().first { it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed }
            }
            refresh() == State.READY && end !is DownloadStatus.DownloadFailed
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            refresh()
            false
        }
    }

    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /** Starts the one-time download in the background, so it carries on if the screen closes. */
    fun startDownload() {
        if (state == State.READY || state == State.UNAVAILABLE) return
        bg.launch { try { prepare() } catch (e: Throwable) { } }
    }

    /** A guess at the foods and carbs in a meal photo, or null if the model couldn't tell. */
    suspend fun plate(context: Context, uri: Uri): PlateGuess? = lock.withLock {
        if (!prepare()) return@withLock null
        Scan.settled(context, uri)
        val bitmap = withContext(Dispatchers.IO) { loadScaled(context, uri, 768) } ?: return@withLock null
        try {
            val res = client().generateContent(
                generateContentRequest(ImagePart(bitmap), TextPart(Plate.PROMPT)) {
                    temperature = 0.2f
                }
            )
            Plate.parse(res.candidates.firstOrNull()?.text)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** The model's raw reply to a photo and a prompt (meal breakdowns). */
    suspend fun imageText(context: Context, uri: Uri, prompt: String): String? = lock.withLock {
        if (!prepare()) return@withLock null
        Scan.settled(context, uri)
        val bitmap = withContext(Dispatchers.IO) { loadScaled(context, uri, 768) } ?: return@withLock null
        try {
            val res = client().generateContent(generateContentRequest(ImagePart(bitmap), TextPart(prompt)) { temperature = 0.2f })
            res.candidates.firstOrNull()?.text
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** The model's raw reply to a text prompt (meals said in words, changes to a meal). */
    suspend fun text(prompt: String): String? = lock.withLock {
        if (!prepare()) return@withLock null
        try {
            client().generateContent(generateContentRequest(TextPart(prompt)) { temperature = 0.2f }).candidates.firstOrNull()?.text
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** A short answer to a typed question, from [prompt] (question plus the facts). */
    suspend fun ask(prompt: String): String? = lock.withLock {
        if (!prepare()) return@withLock null
        try {
            val res = client().generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.3f
                }
            )
            res.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /** The photo, upright and no bigger than [max] px on its long side (the model needs no more). */
    /** Why the last photo couldn't be opened, for problem reports. */
    @Volatile var lastLoadProblem: String? = null

    /**
     * A photo, upright and no bigger than [max] px on its long side. Decoded straight to about that
     * size (a 50–200 MP camera photo decoded whole runs the app out of memory), first with
     * Android's ImageDecoder (reads HEIF and Ultra HDR, turns the photo upright itself), then with
     * the older BitmapFactory. Never throws: what went wrong is kept in [lastLoadProblem].
     */
    internal fun loadScaled(context: Context, uri: Uri, max: Int): Bitmap? {
        lastLoadProblem = null
        val cr = context.contentResolver
        val problems = ArrayList<String>()
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                    // Halving keeps the shape whichever way the photo is turned.
                    var sample = 1
                    while (maxOf(info.size.width, info.size.height) / (sample * 2) >= max) sample *= 2
                    d.setTargetSampleSize(sample)
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
                return shrink(bmp, max, 0f)
            } catch (e: Throwable) {
                problems += "ImageDecoder ${e.javaClass.simpleName}: ${e.message?.take(100)}"
            }
        }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val big = maxOf(bounds.outWidth, bounds.outHeight)
            // Size unknown: decode small rather than risk the whole photo.
            var sample = if (big <= 0) 8 else 1
            while (big / (sample * 2) >= max) sample *= 2
            val decoded = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            if (decoded == null) {
                problems += "BitmapFactory read ${bounds.outWidth}x${bounds.outHeight} ${bounds.outMimeType ?: "unknown type"}, gave nothing"
            } else {
                val rotation = try {
                    cr.openInputStream(uri)?.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                } catch (e: Exception) { 0f }
                return shrink(decoded, max, rotation)
            }
        } catch (e: Throwable) {
            problems += "BitmapFactory ${e.javaClass.simpleName}: ${e.message?.take(100)}"
        }
        lastLoadProblem = problems.joinToString("; ").ifEmpty { "nothing to read" }
        return null
    }

    /** Scales a decoded photo down to [max] px and turns it upright. */
    private fun shrink(decoded: Bitmap, max: Int, rotation: Float): Bitmap {
        val scale = max.toFloat() / maxOf(decoded.width, decoded.height)
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        if (m.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it != decoded) decoded.recycle() }
    }
}
