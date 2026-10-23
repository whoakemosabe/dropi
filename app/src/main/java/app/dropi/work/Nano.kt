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
            refresh()
            false
        }
    }

    /** A guess at the foods and carbs in a meal photo, or null if the model couldn't tell. */
    suspend fun plate(context: Context, uri: Uri): PlateGuess? = lock.withLock {
        if (!prepare()) return@withLock null
        val bitmap = withContext(Dispatchers.IO) { loadScaled(context, uri, 768) } ?: return@withLock null
        try {
            val res = client().generateContent(
                generateContentRequest(ImagePart(bitmap), TextPart(Plate.PROMPT)) {
                    temperature = 0.2f
                }
            )
            Plate.parse(res.candidates.firstOrNull()?.text)
        } catch (e: Throwable) {
            null
        } finally {
            bitmap.recycle()
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
            null
        }
    }

    /** The photo, upright and no bigger than [max] px on its long side (the model needs no more). */
    private fun loadScaled(context: Context, uri: Uri, max: Int): Bitmap? = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val rotation = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (decoded == null) null
        else {
            val scale = max.toFloat() / maxOf(decoded.width, decoded.height)
            val m = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            if (m.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it != decoded) decoded.recycle() }
        }
    } catch (e: Exception) {
        null
    }
}
