package app.dropi.work

import android.content.Context
import android.net.Uri
import app.dropi.data.Packet
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Packet barcodes: read on the phone (ML Kit, bundled), then looked up in Open Food Facts.
 * Only the barcode number leaves the phone. A packet seen before, or one whose numbers the
 * person typed or corrected, is answered from the phone without asking again.
 */
object Barcodes {
    sealed interface Result {
        data class Ok(val found: Packet.Found, val code: String, val saved: Boolean) : Result
        data class NotListed(val code: String) : Result
        data class Offline(val code: String) : Result
        data object NoBarcode : Result
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("barcodes", Context.MODE_PRIVATE)

    /** The barcode number on a photo, or null when none could be read. */
    suspend fun read(context: Context, uri: Uri): String? {
        val image = Scan.image(context, uri) ?: return null
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        )
        return try {
            suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        cont.resume(codes.mapNotNull { it.rawValue?.trim() }.firstOrNull { Packet.valid(it) })
                    }
                    .addOnFailureListener { e -> Diag.log(context, "Barcode: reader failed (${e.message?.take(160)})"); cont.resume(null) }
            }
        } finally { scanner.close() }
    }

    /** The packet for a barcode: from the phone if seen before, otherwise from Open Food Facts. */
    suspend fun lookup(context: Context, code: String): Result {
        prefs(context).getString(code, null)?.let { Packet.fromJson(it) }?.let { return Result.Ok(it, code, saved = true) }
        return withContext(Dispatchers.IO) {
            try {
                val c = URL(Packet.url(code)).openConnection() as HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 10_000
                // Open Food Facts asks apps to name themselves.
                c.setRequestProperty("User-Agent", "Dropi/1.0 (Android; github.com/whoakemosabe/dropi)")
                try {
                    if (c.responseCode == 404) return@withContext Result.NotListed(code)
                    if (c.responseCode !in 200..299) {
                        Diag.log(context, "Barcode: Open Food Facts answered ${c.responseCode}")
                        return@withContext Result.Offline(code)
                    }
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val f = Packet.parse(body)
                    if (f == null) Result.NotListed(code)
                    else { remember(context, code, f); Result.Ok(f, code, saved = false) }
                } finally { c.disconnect() }
            } catch (e: Exception) {
                Diag.log(context, "Barcode: lookup failed (${e.javaClass.simpleName}: ${e.message?.take(120)})")
                Result.Offline(code)
            }
        }
    }

    /** Keeps a packet's numbers on the phone (as found, or as the person corrected them). */
    fun remember(context: Context, code: String, f: Packet.Found) {
        if (f.per100.carbs == null) return
        val p = prefs(context)
        val e = p.edit().putString(code, Packet.toJson(f))
        // At most 300 packets; the oldest go first.
        val order = (p.getString("_order", "").orEmpty().split(',').filter { it.isNotBlank() && it != code } + code)
        order.dropLast(300).forEach { e.remove(it) }
        e.putString("_order", order.takeLast(300).joinToString(",")).apply()
    }
}
