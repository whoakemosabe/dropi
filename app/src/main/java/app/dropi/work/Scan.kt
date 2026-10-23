package app.dropi.work

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.dropi.data.GlucoseUnit
import app.dropi.data.MeterScan
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads the number off a photo of a meter, on the phone (ML Kit text recognition through Google
 * Play services; the photo never leaves the phone and is deleted after).
 */
object Scan {
    /** Where the camera app saves the photo, shared with it through our FileProvider. */
    fun photoUri(context: Context, name: String = "meter.jpg"): Uri {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val f = File(dir, name).apply { delete() }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    private fun photoFile(context: Context) = File(File(context.cacheDir, "scans"), "meter.jpg")

    /** Deletes a meal photo once Gemini Nano has looked at it. */
    fun forgetPlate(context: Context) {
        try { File(File(context.cacheDir, "scans"), "plate.jpg").delete() } catch (e: Exception) { }
    }

    /** The reading in mmol/L, or null if no plausible number was found. */
    suspend fun read(context: Context, uri: Uri, unit: GlucoseUnit): Double? = withContext(Dispatchers.Default) {
        val pieces = try { recognise(context, uri) } catch (e: Exception) { emptyList() }
        try { photoFile(context).delete() } catch (e: Exception) { }
        MeterScan.pick(pieces, unit)
    }

    private suspend fun recognise(context: Context, uri: Uri): List<MeterScan.Piece> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val out = ArrayList<MeterScan.Piece>()
                        for (block in text.textBlocks) for (line in block.lines) {
                            val lh = line.boundingBox?.height() ?: 0
                            out += MeterScan.Piece(line.text, lh)
                            for (el in line.elements) out += MeterScan.Piece(el.text, el.boundingBox?.height() ?: lh)
                        }
                        cont.resume(out)
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
        } finally {
            recognizer.close()
        }
    }
}
