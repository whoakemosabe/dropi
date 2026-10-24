package app.dropi.work

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.dropi.data.GlucoseUnit
import app.dropi.data.MeterScan
import app.dropi.tr
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
    internal suspend fun image(context: Context, uri: Uri): InputImage? {
        try { settled(context, uri) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        val bmp = withContext(Dispatchers.IO) { Nano.loadScaled(context, uri, 2400) }
        if (bmp == null) {
            Diag.log(context, "Photo: couldn't open it (${Nano.lastLoadProblem ?: "?"}; ${describe(context, uri)})")
            return null
        }
        return try { InputImage.fromBitmap(bmp, 0) } catch (e: Throwable) {
            Diag.log(context, "Photo: ML Kit wouldn't take it (${e.javaClass.simpleName}: ${e.message?.take(120)}; ${bmp.width}x${bmp.height} ${bmp.config})")
            null
        }
    }

    /** Our own photo file behind a FileProvider address, or null for a photo from elsewhere. */
    private fun fileFor(context: Context, uri: Uri): File? {
        if (uri.authority != "${context.packageName}.files" || uri.pathSegments.firstOrNull() != "scans") return null
        return File(File(context.cacheDir, "scans"), uri.lastPathSegment ?: return null)
    }

    /** The file's size and how long ago it was written, for problem reports. */
    private fun describe(context: Context, uri: Uri): String {
        val f = fileFor(context, uri) ?: return "photo from ${uri.authority}"
        return if (!f.exists()) "the camera saved no file" else "file ${f.length() / 1024} KB, written ${(System.currentTimeMillis() - f.lastModified()) / 1000} s ago, ${if (complete(f)) "whole" else "cut short"}"
    }

    /**
     * Some camera apps (OPPO's among them) say the photo is taken before they've finished
     * writing it, so it was opened while still empty or half written. This waits (up to 10 s)
     * until the file has stopped growing and, for a JPEG, has its end marker. Instant when the
     * photo is already whole.
     */
    suspend fun settled(context: Context, uri: Uri): Boolean {
        val f = fileFor(context, uri) ?: return true
        val until = System.currentTimeMillis() + 10_000
        var last = -1L
        while (true) {
            val len = if (f.exists()) f.length() else 0L
            if (len > 0 && complete(f) && (len == last || System.currentTimeMillis() - f.lastModified() > 1_500)) return true
            if (System.currentTimeMillis() > until) return len > 0
            last = len
            kotlinx.coroutines.delay(250)
        }
    }

    /** True when a JPEG ends with its end marker (FF D9, sometimes followed by a little padding); any other file counts as whole. */
    private fun complete(f: File): Boolean = try {
        java.io.RandomAccessFile(f, "r").use { r ->
            if (r.length() < 4) return@use false
            val head = ByteArray(2).also { r.readFully(it) }
            if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return@use true
            val n = minOf(r.length(), 512L).toInt()
            r.seek(r.length() - n)
            val tail = ByteArray(n).also { r.readFully(it) }
            (0 until n - 1).any { tail[it] == 0xFF.toByte() && tail[it + 1] == 0xD9.toByte() }
        }
    } catch (e: Exception) { false }

    /** The text the last label scan found, line by line, to show the person what was read. */
    @Volatile var lastText: List<String> = emptyList()
        private set

    /** Why the last scan read nothing, for the screen, in the app's language (null when it worked). */
    @Volatile var problem: String? = null
        private set

    /** The same in English, for problem reports. */
    @Volatile var problemEn: String? = null
        private set

    private fun setProblem(en: String?, shown: String?) { problemEn = en; problem = shown }

    private fun cantOpen() = setProblem("Couldn't open the photo.", tr("Couldn't open the photo."))
    private fun noText() = setProblem("No text found on the photo.", tr("No text found on the photo."))
    private fun readerFailed(e: Exception) = setProblem("The text reader failed: ${e.message?.take(80)}", tr("The text reader failed: {0}", e.message?.take(80)))

    /** The reading in mmol/L, or null if no plausible number was found. */
    suspend fun read(context: Context, uri: Uri, unit: GlucoseUnit): Double? = withContext(Dispatchers.Default) {
        val pieces = try { recognise(context, uri) } catch (e: Throwable) { cantOpen(); Diag.log(context, "Meter: couldn't open the photo (${e.javaClass.simpleName})"); emptyList() }
        try { photoFile(context).delete() } catch (e: Exception) { }
        MeterScan.pick(pieces, unit).also { if (it == null) Diag.log(context, "Meter: no reading found among ${pieces.size} pieces of text (${pieces.take(12).joinToString(" | ") { p -> p.text }})") }
    }

    /** Every line of text on a photo, top to bottom (nutrition labels). The photo is then deleted. */
    suspend fun lines(context: Context, uri: Uri): List<String> = withContext(Dispatchers.Default) {
        setProblem(null, null)
        val image = try { image(context, uri) } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Diag.log(context, "Label: opening the photo failed (${e.javaClass.simpleName}: ${e.message?.take(160)})")
            null
        }
        if (image == null) { cantOpen(); return@withContext emptyList() }
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
                        if (out.isEmpty()) noText()
                        cont.resume(out)
                    }
                    .addOnFailureListener { e -> readerFailed(e); Diag.log(context, "Scan: text reader failed (${e.message?.take(160)})"); cont.resume(emptyList()) }
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            setProblem("The text reader failed.", tr("The text reader failed."))
            Diag.log(context, "Label: the text reader failed (${e.javaClass.simpleName}: ${e.message?.take(160)})")
            emptyList()
        } finally {
            recognizer.close()
        }
    }

    private suspend fun recognise(context: Context, uri: Uri): List<MeterScan.Piece> {
        setProblem(null, null)
        val image = image(context, uri) ?: run { cantOpen(); return emptyList() }
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
                        if (out.isEmpty()) noText()
                        cont.resume(out)
                    }
                    .addOnFailureListener { e -> readerFailed(e); Diag.log(context, "Scan: text reader failed (${e.message?.take(160)})"); cont.resume(emptyList()) }
            }
        } finally {
            recognizer.close()
        }
    }
}
