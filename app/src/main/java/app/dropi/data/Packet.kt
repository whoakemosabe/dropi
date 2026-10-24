package app.dropi.data

import org.json.JSONObject

/**
 * A packet found by its barcode in Open Food Facts (the free, open product database), turned
 * into the same per-100 g numbers a scanned label gives. Only the barcode number is sent.
 */
object Packet {
    /** What a barcode lookup gave: the numbers, the name and the packet's serving in grams (if listed). */
    data class Found(val per100: Label.Per100, val serving: Double?)

    /** The address asked for one barcode: just the fields Dropi uses, so the answer is small. */
    fun url(code: String) =
        "https://world.openfoodfacts.org/api/v2/product/$code.json" +
            "?fields=product_name,product_name_is,product_name_en,generic_name,brands,nutriments,serving_quantity,countries_tags"

    /** EAN-13, EAN-8, UPC-A and UPC-E: the barcodes on food packets. */
    fun valid(code: String) = code.length in setOf(8, 12, 13, 14) && code.all { it.isDigit() }

    /**
     * Reads Open Food Facts' answer. Null when the product isn't in it or has no carbs listed.
     * US products list carbs with the fibre in; that's taken off here, so the numbers always
     * mean the same as a European label's.
     */
    fun parse(json: String): Found? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        if (root.optInt("status", 0) != 1) return null
        val p = root.optJSONObject("product") ?: return null
        val n = p.optJSONObject("nutriments") ?: return null
        fun num(key: String): Double? {
            if (!n.has(key) || n.isNull(key)) return null
            val v = n.opt(key)
            val d = when (v) {
                is Number -> v.toDouble()
                is String -> v.replace(',', '.').trim().toDoubleOrNull()
                else -> null
            }
            return d?.takeIf { it >= 0 && it <= 100 }
        }
        val carbs = num("carbohydrates_100g") ?: return null
        val fibre = num("fiber_100g")
        val us = p.optJSONArray("countries_tags")?.let { a ->
            val tags = (0 until a.length()).map { a.optString(it) }
            tags.isNotEmpty() && tags.all { it == "en:united-states" || it == "en:canada" }
        } ?: false
        val net = if (us && fibre != null) (carbs - fibre).coerceAtLeast(0.0) else carbs
        val name = listOf("product_name_is", "product_name", "product_name_en", "generic_name")
            .map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() }
            ?: p.optString("brands").substringBefore(',').trim()
        val serving = p.opt("serving_quantity").let {
            when (it) { is Number -> it.toDouble(); is String -> it.replace(',', '.').toDoubleOrNull(); else -> null }
        }?.takeIf { it in 1.0..2000.0 }
        return Found(
            Label.Per100(net, num("sugars_100g"), fibre, name.take(40), num("proteins_100g"), num("fat_100g")),
            serving,
        )
    }

    /** The numbers as saved on the phone for a barcode seen before (or typed by the person). */
    fun toJson(f: Found): String = JSONObject().apply {
        put("name", f.per100.name)
        f.per100.carbs?.let { put("carbs", it) }; f.per100.sugars?.let { put("sugars", it) }
        f.per100.fibre?.let { put("fibre", it) }; f.per100.protein?.let { put("protein", it) }
        f.per100.fat?.let { put("fat", it) }; f.serving?.let { put("serving", it) }
    }.toString()

    fun fromJson(s: String): Found? = try {
        val o = JSONObject(s)
        fun d(k: String) = if (o.has(k)) o.getDouble(k) else null
        Found(Label.Per100(d("carbs"), d("sugars"), d("fibre"), o.optString("name"), d("protein"), d("fat")), d("serving"))
    } catch (e: Exception) { null }
}
