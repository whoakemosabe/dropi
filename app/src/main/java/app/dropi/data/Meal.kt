package app.dropi.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One food in a meal: a portion of it, and its carbs, sugars and fibre for that portion. [qty]
 * scales the base portion (½, 1, 1½, 2…); [carbs] etc. are for the whole of [qty].
 */
data class MealItem(
    val name: String,
    val portion: String,
    val qty: Double = 1.0,
    val carbs: Int,
    val sugars: Int? = null,
    val fibre: Int? = null,
    /** The carbs came from the person's own "usual" for this food, not a guess. */
    val usual: Boolean = false,
) {
    val key get() = Foods.normalize(name)

    /** "1 cup", "½ × 1 cup", "2 × 1 slice". */
    fun portionText(): String = if (qty == 1.0) portion else "${Meal.qtyText(qty)} × $portion"

    /** The same food at another amount, everything scaled from one portion. */
    fun withQty(q: Double): MealItem {
        val per = if (qty > 0) 1.0 / qty else 1.0
        fun s(v: Int?) = v?.let { (it * per * q).roundToInt() }
        return copy(qty = q, carbs = (carbs * per * q).roundToInt(), sugars = s(sugars), fibre = s(fibre))
    }
}

/**
 * What was eaten with a reading: the foods and their numbers, and whether it was rich (high fat
 * or protein, so the rise may come later). [source] is "photo", "words", "label" or "typed".
 */
data class Meal(val items: List<MealItem>, val rich: Boolean = false, val source: String = "typed") {
    val carbs get() = items.sumOf { it.carbs }
    val sugars: Int? get() = items.mapNotNull { it.sugars }.takeIf { it.isNotEmpty() }?.sum()
    val fibre: Int? get() = items.mapNotNull { it.fibre }.takeIf { it.isNotEmpty() }?.sum()
    val foods: List<String> get() = items.map { it.key }.filter { it.isNotEmpty() }.distinct()

    fun toJson(): String = JSONObject().apply {
        put("rich", rich)
        put("source", source)
        put("items", JSONArray().apply {
            items.forEach { i ->
                put(JSONObject().apply {
                    put("name", i.name); put("portion", i.portion); put("qty", i.qty); put("carbs", i.carbs)
                    i.sugars?.let { put("sugars", it) }
                    i.fibre?.let { put("fibre", it) }
                    if (i.usual) put("usual", true)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(s: String?): Meal? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val a = o.optJSONArray("items") ?: return null
                val items = (0 until a.length()).mapNotNull { k ->
                    val i = a.optJSONObject(k) ?: return@mapNotNull null
                    MealItem(
                        name = i.optString("name").take(40),
                        portion = i.optString("portion", "1 portion").take(30),
                        qty = i.optDouble("qty", 1.0).takeIf { it in 0.1..20.0 } ?: 1.0,
                        carbs = i.optInt("carbs", 0).coerceIn(0, 400),
                        sugars = if (i.has("sugars")) i.optInt("sugars").coerceIn(0, 400) else null,
                        fibre = if (i.has("fibre")) i.optInt("fibre").coerceIn(0, 200) else null,
                        usual = i.optBoolean("usual", false),
                    )
                }.filter { it.name.isNotBlank() }
                if (items.isEmpty()) null else Meal(items, o.optBoolean("rich", false), o.optString("source", "typed"))
            } catch (e: Exception) {
                null
            }
        }

        /** ½, 1, 1½, 2, 0.3… */
        fun qtyText(q: Double): String {
            val whole = q.toInt()
            val frac = q - whole
            val half = kotlin.math.abs(frac - 0.5) < 0.01
            return when {
                frac < 0.01 -> "$whole"
                half && whole == 0 -> "½"
                half -> "$whole½"
                else -> String.format(Locale.ROOT, "%.1f", q)
            }
        }
    }
}

/** The person's own amount for a food: carbs (and sugars) in one [portion]. */
data class Usual(val portion: String, val carbs: Int, val sugars: Int? = null)

/** Prompts for the on-device model, and reading its replies. */
object MealAi {
    private const val SHAPE =
        "Reply with only JSON, no other text, in exactly this shape:\n" +
            "{\"items\":[{\"food\":\"basmati rice\",\"portion\":\"1 cup\",\"carbs\":45,\"sugars\":0,\"fibre\":1}],\"rich\":false}\n" +
            "One item per food, short everyday names, a simple household portion (cup, slice, piece, bowl, glass, spoon), " +
            "and whole grams of carbohydrate, sugars and fibre for that portion. " +
            "Set rich to true when the meal is high in fat or protein (fried food, cream, cheese, pizza, curry, big meat portions)."

    val PHOTO = "This is a photo of a meal. Identify each food and estimate how much of it is on the plate. $SHAPE"

    fun words(text: String) = "Someone describes what they ate: \"${text.take(300)}\". Work out each food and the amount they said " +
        "(assume a normal portion when they didn't say). $SHAPE"

    fun change(current: Meal, change: String): String {
        val list = current.items.joinToString("; ") { "${it.name}, ${it.portionText()}, ${it.carbs} g carbs" }
        return "A meal was logged as: $list. The person says: \"${change.take(200)}\". Apply the change and give the whole meal again. $SHAPE"
    }

    /** JSON schema for models that can be held to it (Gemma through LiteRT-LM). */
    const val SCHEMA = """{"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{"food":{"type":"string"},"portion":{"type":"string"},"carbs":{"type":"integer"},"sugars":{"type":"integer"},"fibre":{"type":"integer"}},"required":["food","portion","carbs"]}},"rich":{"type":"boolean"}},"required":["items","rich"]}"""

    /**
     * Reads the model's reply. Tolerant: finds the JSON object in any surrounding words, and
     * falls back to the older "FOODS: …  CARBS: …" lines. Null when nothing usable came back.
     */
    fun parse(text: String?, source: String): Meal? {
        if (text.isNullOrBlank()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            try {
                val o = JSONObject(text.substring(start, end + 1))
                val a = o.optJSONArray("items")
                if (a != null) {
                    val items = (0 until a.length()).mapNotNull { k ->
                        val i = a.optJSONObject(k) ?: return@mapNotNull null
                        val name = Foods.normalize(i.optString("food", i.optString("name")).trimEnd('.'))
                        if (name.length !in 2..40 || name == "unknown" || name == "none") return@mapNotNull null
                        MealItem(
                            name = name,
                            portion = i.optString("portion", "1 portion").trim().ifEmpty { "1 portion" }.take(30),
                            carbs = num(i, "carbs")?.coerceIn(0, 300) ?: 0,
                            sugars = num(i, "sugars")?.coerceIn(0, 300),
                            fibre = num(i, "fibre")?.coerceIn(0, 100),
                        )
                    }.distinctBy { it.key }.take(10)
                    if (items.isNotEmpty()) return Meal(items, o.optBoolean("rich", false), source)
                }
            } catch (e: Exception) { }
        }
        // The older two-line reply.
        val old = Plate.parse(text) ?: return null
        if (old.foods.isEmpty()) return null
        val each = old.carbs?.let { it / old.foods.size } ?: 0
        return Meal(old.foods.map { MealItem(it, "1 portion", carbs = each) }, false, source)
    }

    private fun num(o: JSONObject, k: String): Int? {
        if (!o.has(k)) return null
        val v = o.opt(k)
        return when (v) {
            is Number -> v.toDouble().roundToInt()
            is String -> Regex("\\d{1,3}").find(v)?.value?.toIntOrNull()
            else -> null
        }
    }

    /** Puts the person's usual amounts in place of guesses, scaled to the portions seen. */
    fun applyUsual(meal: Meal, usual: Map<String, Usual>): Meal = meal.copy(items = meal.items.map { i ->
        val u = usual[i.key] ?: return@map i
        i.copy(portion = u.portion, carbs = (u.carbs * i.qty).roundToInt(), sugars = u.sugars?.let { (it * i.qty).roundToInt() } ?: i.sugars, usual = true)
    })
}

/**
 * A nutrition label's carbs, sugars and fibre per 100 g (or ml), read from the text ML Kit finds
 * on a photo of the packet. Understands English and Icelandic labels ("Kolvetni", "þar af
 * sykurtegundir", "Trefjar").
 */
object Label {
    data class Per100(val carbs: Double?, val sugars: Double?, val fibre: Double?)

    private val carbWords = listOf("carbohydrate", "carbohydrates", "carbs", "kolvetni")
    private val sugarWords = listOf("sugars", "sugar", "sykurtegundir", "sykur")
    private val fibreWords = listOf("fibre", "fiber", "trefjar")

    /** [lines] in reading order. The first number on (or right after) a matching line is per 100 g. */
    fun read(lines: List<String>): Per100? {
        val clean = lines.map { it.lowercase(Locale.ROOT).replace(',', '.') }
        fun find(words: List<String>, avoid: List<String> = emptyList()): Double? {
            for ((k, l) in clean.withIndex()) {
                if (words.none { l.contains(it) } || avoid.any { l.contains(it) }) continue
                val here = number(l) ?: clean.getOrNull(k + 1)?.let { if (it.any(Char::isLetter) && number(it) == null) null else number(it) }
                if (here != null) return here
            }
            return null
        }
        val carbs = find(carbWords, avoid = sugarWords)
        val sugars = find(sugarWords)
        val fibre = find(fibreWords)
        if (carbs == null && sugars == null) return null
        return Per100(carbs, sugars, fibre)
    }

    private fun number(l: String): Double? =
        Regex("(\\d{1,3}(?:\\.\\d)?)\\s*g").find(l)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it <= 100.0 }

    /** The item for [grams] eaten of a product with [p] per 100 g. */
    fun item(name: String, grams: Int, p: Per100): MealItem = MealItem(
        name = name.ifBlank { "packet food" },
        portion = "$grams g",
        carbs = ((p.carbs ?: 0.0) * grams / 100.0).roundToInt(),
        sugars = p.sugars?.let { (it * grams / 100.0).roundToInt() },
        fibre = p.fibre?.let { (it * grams / 100.0).roundToInt() },
    )
}

/** How much a food or meal tends to lift the person's numbers, in words that don't need colour. */
enum class Rise(val word: String) {
    GENTLE("Gentle rise"), MODERATE("Moderate rise"), BIG("Big rise");

    companion object {
        /** The same steps as the food scores: under 2.0, up to 3.0, over 3.0 mmol/L. */
        fun of(mmol: Double) = when {
            mmol < 2.0 -> GENTLE
            mmol <= 3.0 -> MODERATE
            else -> BIG
        }
    }
}

/** What the person's own past meals say about this one. */
object MealHistory {
    data class Estimate(val start: Double, val rise: Double, val meals: Int, val foods: List<String>) {
        val peak get() = start + rise
    }

    /**
     * The usual peak after meals sharing a food with [foods]: the median rise of those past meals
     * on top of [start]. Null with fewer than two such meals (one meal is a story, two a pattern).
     */
    fun estimate(start: Double, foods: List<String>, readings: List<Reading>, sensor: List<SensorPoint>): Estimate? {
        if (foods.isEmpty()) return null
        val wanted = foods.toSet()
        val past = Foods.meals(readings, sensor).filter { m -> m.rise != null && m.foods.any { it in wanted } }
        if (past.size < Foods.MIN_MEALS) return null
        val shared = past.flatMap { m -> m.foods.filter { it in wanted } }.distinct()
        return Estimate(start, Foods.median(past.mapNotNull { it.rise }), past.size, shared)
    }

    /** How a logged meal went: its start, peak and when, from what came after (null until then). */
    data class Outcome(val start: Double, val peak: Double, val peakAt: Long) {
        val rise get() = peak - start
    }

    fun outcome(r: Reading, readings: List<Reading>, sensor: List<SensorPoint>): Outcome? {
        val m = Foods.meals(readings, sensor).firstOrNull { it.foods == r.foods && kotlin.math.abs(it.at - r.at) <= 3 * 3_600_000L } ?: return null
        val best = m.after.filter { it.first in 45..180 }.maxByOrNull { it.second } ?: return null
        return Outcome(m.start, m.start + best.second, m.at + best.first * 60_000L)
    }
}
