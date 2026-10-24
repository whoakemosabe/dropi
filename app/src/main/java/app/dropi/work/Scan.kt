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
        // Photos left over from a day ago (a job that never finished) are cleared out.
        try { dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() } } catch (e: Exception) { }
        val f = File(dir, name).apply { delete() }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    private fun photoFile(context: Context) = File(File(context.cacheDir, "scans"), "meter.jpg")

    /** Deletes a meal photo once Gemini Nano has looked at it. */
    fun forgetPlate(context: Context) {
        try { File(File(context.cacheDir, "scans"), "plate.jpg").delete() } catch (e: Exception) { }
    }

    /** The file behind [photoUri] for [name], to check the camera actually saved something. */
    fun shotFile(context: Context, name: String) = File(File(context.cacheDir, "scans"), name)

    /** True when the camera saved a photo, whatever result code it returned. */
    fun saved(context: Context, name: String) = shotFile(context, name).let { it.exists() && it.length() > 0 }

    /**
     * The photo as ML Kit input: upright, and no bigger than 2400 px on its long side (a 50 MP
     * photo decoded whole runs the phone out of memory, and the text is just as readable).
     */
    internal fun image(context: Context, uri: Uri): InputImage? {
        val bmp = Nano.loadScaled(context, uri, 2400) ?: return null
        return InputImage.fromBitmap(bmp, 0)
    }

    /** The text the last label scan found, line by line, to show the person what was read. */
    @Volatile var lastText: List<String> = emptyList()
        private set

    /** Why the last scan read nothing, for the screen (null when it worked). */
    @Volatile var problem: String? = null
        private set

    /** The reading in mmol/L, or null if no plausible number was found. */
    suspend fun read(context: Context, uri: Uri, unit: GlucoseUnit): Double? = withContext(Dispatchers.Default) {
        val pieces = try { recognise(context, uri) } catch (e: Throwable) { problem = "Couldn't open the photo."; Diag.log(context, "Meter: couldn't open the photo (${e.javaClass.simpleName})"); emptyList() }
        try { photoFile(context).delete() } catch (e: Exception) { }
        MeterScan.pick(pieces, unit).also { if (it == null) Diag.log(context, "Meter: no reading found among ${pieces.size} pieces of text (${pieces.take(12).joinToString(" | ") { p -> p.text }})") }
    }

    /** Every line of text on a photo, top to bottom (nutrition labels). The photo is then deleted. */
    suspend fun lines(context: Context, uri: Uri): List<String> = withContext(Dispatchers.Default) {
        problem = null
        val image = try { image(context, uri) } catch (e: Throwable) { null }
        if (image == null) { problem = "Couldn't open the photo."; return@withContext emptyList() }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        // Rows of a label table: lines grouped by height on the photo and read left to
                        // right, so a value in the right-hand column lands next to its name. Then
                        // the lines as ML Kit found them, as a second chance for a tilted photo.
                        val all = text.textBlocks.flatMap { it.lines }
                        // Each line's place and slant, laid out into table rows (straightened
                        // first, so a tilted photo still lines names up with their numbers).
                        val placed = all.mapNotNull { l ->
                            val b = l.boundingBox ?: return@mapNotNull null
                            app.dropi.data.Label.TextLine(l.text, b.exactCenterX(), b.exactCenterY(), b.left.toFloat(), b.height().toFloat(), l.angle)
                        }
                        val out = ArrayList<String>()
                        out += app.dropi.data.Label.rows(placed)
                        all.forEach { out += it.text }
                        lastText = all.map { it.text }
                        if (out.isEmpty()) problem = "No text found on the photo."
                        cont.resume(out)
                    }
                    .addOnFailureListener { e -> problem = "The text reader failed: ${e.message?.take(80)}"; Diag.log(context, "Scan: text reader failed (${e.message?.take(160)})"); cont.resume(emptyList()) }
            }
        } catch (e: Throwable) {
            problem = "The text reader failed."
            emptyList()
        } finally {
            recognizer.close()
            try { File(File(context.cacheDir, "scans"), "label.jpg").delete() } catch (e: Exception) { }
        }
    }

    private suspend fun recognise(context: Context, uri: Uri): List<MeterScan.Piece> {
        problem = null
        val image = image(context, uri) ?: run { problem = "Couldn't open the photo."; return emptyList() }
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
                        if (out.isEmpty()) problem = "No text found on the photo."
                        cont.resume(out)
                    }
                    .addOnFailureListener { e -> problem = "The text reader failed: ${e.message?.take(80)}"; Diag.log(context, "Scan: text reader failed (${e.message?.take(160)})"); cont.resume(emptyList()) }
            }
        } finally {
            recognizer.close()
        }
    }
}
