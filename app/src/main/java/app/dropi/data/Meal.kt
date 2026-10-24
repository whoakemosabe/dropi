package app.dropi.data

import app.dropi.L
import app.dropi.tr
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
    val carbs: Double,
    val sugars: Double? = null,
    val fibre: Double? = null,
    /** The carbs came from the person's own "usual" for this food, not a guess. */
    val usual: Boolean = false,
    val protein: Double? = null,
    val fat: Double? = null,
    /** Glycaemic index (glucose = 100) when the model gave one; else looked up by name. */
    val gi: Int? = null,
    /** A drink (juice, soda, sweet coffee…), told apart because sugar in a drink hits fastest. */
    val drink: Boolean = false,
) {
    val key get() = Foods.normalize(name)

    /** How fast its carbs hit: the given index, else the built-in table (null when unknown). */
    val giValue: Int? get() = gi ?: Gi.lookup(name)

    /** Glycaemic load: carbs weighted by how fast they hit. */
    val load: Double? get() = giValue?.let { it * carbs / 100.0 }

    /** A drink with real sugar in it (not diet, zero or water). */
    val sugaryDrink: Boolean get() = (drink || Gi.isDrink(name)) && !Gi.isSugarFree(name) && ((sugars ?: carbs) >= 5.0)

    /** "1 cup", "2 eggs", "3 slices", "½ × 1 cup", "2 × 1 medium". [plain]: a point in decimals (prompts, CSV). */
    fun portionText(plain: Boolean = false): String {
        if (qty == 1.0) return portion
        val rest = portion.removePrefix("1 ")
        val word = rest.split(' ').singleOrNull()
        if (qty % 1.0 == 0.0 && portion.startsWith("1 ") && word != null && word !in sizeWords) {
            val plural = if (word.endsWith("s")) word else if (word.endsWith("ch") || word.endsWith("sh")) word + "es" else word + "s"
            return "${qty.toInt()} $plural"
        }
        return "${Meal.qtyText(qty, plain)} × $portion"
    }

    private companion object {
        val sizeWords = setOf("medium", "small", "large", "portion", "normal", "big")
    }

    /** The same food at another amount, everything scaled from one portion. */
    fun withQty(q: Double): MealItem {
        val per = if (qty > 0) 1.0 / qty else 1.0
        fun s(v: Double?) = v?.let { Grams.round(it * per * q) }
        return copy(qty = q, carbs = Grams.round(carbs * per * q), sugars = s(sugars), fibre = s(fibre), protein = s(protein), fat = s(fat))
    }
}

/**
 * What was eaten with a reading: the foods and their numbers, and whether it was rich (high fat
 * or protein, so the rise may come later). [source] is "photo", "words", "label" or "typed".
 */
data class Meal(
    val items: List<MealItem>,
    val rich: Boolean = false,
    val source: String = "typed",
    /** What else went with it: "veg_first", "walk_after", "alcohol", "ill", "stress" (see [MealTags]). */
    val tags: List<String> = emptyList(),
) {
    val carbs: Double get() = Grams.round(items.sumOf { it.carbs })
    val sugars: Double? get() = items.mapNotNull { it.sugars }.takeIf { it.isNotEmpty() }?.let { Grams.round(it.sum()) }
    val fibre: Double? get() = items.mapNotNull { it.fibre }.takeIf { it.isNotEmpty() }?.let { Grams.round(it.sum()) }
    val protein: Double? get() = items.mapNotNull { it.protein }.takeIf { it.isNotEmpty() }?.let { Grams.round(it.sum()) }
    val fat: Double? get() = items.mapNotNull { it.fat }.takeIf { it.isNotEmpty() }?.let { Grams.round(it.sum()) }

    /** Carb-weighted glycaemic index of the foods whose index is known (null when none are). */
    val gi: Int? get() {
        val known = items.filter { it.giValue != null && it.carbs > 0 }
        val c = known.sumOf { it.carbs }
        return if (c <= 0.0) null else (known.sumOf { it.giValue!! * it.carbs } / c).roundToInt()
    }
    val load: Double? get() = items.mapNotNull { it.load }.takeIf { it.isNotEmpty() }?.sum()
    val speed: Gi.Speed? get() = gi?.let { Gi.speed(it) }
    val hasSugaryDrink get() = items.any { it.sugaryDrink }

    /** High enough in fat or protein that the rise comes later: the switch, or the numbers. */
    val slowDigesting get() = rich || (fat ?: 0.0) >= 25 || (protein ?: 0.0) >= 40
    /** Some fat or protein: the peak moves a little later. */
    val someFatProtein get() = (fat ?: 0.0) >= 15 || (protein ?: 0.0) >= 25
    val foods: List<String> get() = items.map { it.key }.filter { it.isNotEmpty() }.distinct()

    fun toJson(): String = JSONObject().apply {
        put("rich", rich)
        put("source", source)
        if (tags.isNotEmpty()) put("tags", JSONArray(tags))
        put("items", JSONArray().apply {
            items.forEach { i ->
                put(JSONObject().apply {
                    put("name", i.name); put("portion", i.portion); put("qty", i.qty); put("carbs", i.carbs)
                    i.sugars?.let { put("sugars", it) }
                    i.fibre?.let { put("fibre", it) }
                    i.protein?.let { put("protein", it) }
                    i.fat?.let { put("fat", it) }
                    i.gi?.let { put("gi", it) }
                    if (i.drink) put("drink", true)
                    if (i.usual) put("usual", true)
                })
            }
        })
    }.toString()

    companion object {
        private fun d(o: JSONObject, k: String, max: Double): Double? =
            if (!o.has(k) || o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }?.coerceIn(0.0, max)

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
                        carbs = i.optDouble("carbs", 0.0).let { if (it.isNaN()) 0.0 else it }.coerceIn(0.0, 400.0),
                        sugars = d(i, "sugars", 400.0),
                        fibre = d(i, "fibre", 200.0),
                        usual = i.optBoolean("usual", false),
                        protein = d(i, "protein", 300.0),
                        fat = d(i, "fat", 300.0),
                        gi = if (i.has("gi")) i.optInt("gi").takeIf { it in 1..110 } else null,
                        drink = i.optBoolean("drink", false),
                    )
                }.filter { it.name.isNotBlank() }
                val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it in MealTags.KEYS } }.orEmpty()
                if (items.isEmpty()) null else Meal(items, o.optBoolean("rich", false), o.optString("source", "typed"), tags)
            } catch (e: Exception) {
                null
            }
        }

        /** ½, 1, 1½, 2, 0.3… (0,3 in Icelandic unless [plain]). */
        fun qtyText(q: Double, plain: Boolean = false): String {
            val whole = q.toInt()
            val frac = q - whole
            val half = kotlin.math.abs(frac - 0.5) < 0.01
            return when {
                frac < 0.01 -> "$whole"
                half && whole == 0 -> "½"
                half -> "$whole½"
                else -> String.format(Locale.ROOT, "%.1f", q).let { if (plain) it else it.replace('.', L.decimal) }
            }
        }
    }
}

/**
 * A meal as its own entry, at the time it was eaten, with or without a reading. [before] is a
 * reading the person chose as its starting value; without one, the latest reading in the 90
 * minutes before (or the 15 after) is used.
 */
data class MealEntry(
    val id: Long = 0,
    val at: Long,
    val name: String,
    val meal: Meal,
    val before: Long? = null,
) {
    val foods: List<String> get() = meal.foods
    val carbs: Double get() = meal.carbs

    /** [name] as shown: the default names in the app's language, the person's own as typed. */
    val shownName: String get() = displayName(name)

    companion object {
        /** The default names, saved on meals in English (see [displayName] for showing them). */
        val NAMES = listOf("Breakfast", "Lunch", "Dinner", "Snack")

        /** A meal name as shown: "Breakfast"… in the app's language; any other name as it was typed. */
        fun displayName(name: String): String = when (name) {
            "Breakfast" -> tr("Breakfast")
            "Lunch" -> tr("Lunch")
            "Dinner" -> tr("Dinner")
            "Snack" -> tr("Snack")
            else -> name
        }

        /** Breakfast, lunch, dinner or a snack, from the time it was eaten. */
        fun nameFor(at: Long, zone: java.time.ZoneId): String {
            val t = java.time.Instant.ofEpochMilli(at).atZone(zone)
            val m = t.hour * 60 + t.minute
            return when (m) {
                in 4 * 60 until 10 * 60 + 30 -> "Breakfast"
                in 11 * 60 until 14 * 60 + 30 -> "Lunch"
                in 17 * 60 until 21 * 60 + 30 -> "Dinner"
                else -> "Snack"
            }
        }
    }
}

/** How meals and readings are linked: the reading before a meal, and the readings after it. */
object MealLink {
    private const val MIN = 60_000L

    /** The meal's starting reading: the one chosen, else the latest in the 90 min before or the first in the 15 after. */
    fun before(e: MealEntry, readings: List<Reading>): Reading? {
        e.before?.let { id -> readings.firstOrNull { it.id == id }?.let { return it } }
        return readings.filter { it.at in e.at - 90 * MIN..e.at }.maxByOrNull { it.at }
            ?: readings.filter { it.at in e.at + 1..e.at + 15 * MIN }.minByOrNull { it.at }
    }

    /** The meal a reading came before, if it's any meal's starting reading. */
    fun mealAfter(r: Reading, meals: List<MealEntry>, readings: List<Reading>): MealEntry? =
        meals.filter { it.at >= r.at - 15 * MIN && it.at - r.at <= 6 * 60 * MIN }.minByOrNull { it.at }
            ?.takeIf { before(it, readings)?.id == r.id }

    /** The meal a reading came 45 min – 4 h after (not its own starting reading). */
    fun mealBefore(r: Reading, meals: List<MealEntry>): MealEntry? =
        meals.filter { r.at - it.at in 45 * MIN..240 * MIN }.maxByOrNull { it.at }

    /** "2 h after breakfast", "before lunch", or null. */
    fun text(r: Reading, meals: List<MealEntry>, readings: List<Reading>): String? {
        mealAfter(r, meals, readings)?.let {
            return when (it.name) {
                "Breakfast" -> tr("before breakfast")
                "Lunch" -> tr("before lunch")
                "Dinner" -> tr("before dinner")
                "Snack" -> tr("before snack")
                else -> tr("before {0}", it.name.lowercase())
            }
        }
        mealBefore(r, meals)?.let { m ->
            val mins = ((r.at - m.at) / MIN).toInt()
            val h = mins / 60; val rest = mins % 60
            val gap = when {
                h == 0 -> tr("{0} min", mins)
                rest < 10 -> tr("{0} h", h)
                rest > 50 -> tr("{0} h", h + 1)
                else -> tr("{0} h {1} min", h, rest)
            }
            return when (m.name) {
                "Breakfast" -> tr("{0} after breakfast", gap)
                "Lunch" -> tr("{0} after lunch", gap)
                "Dinner" -> tr("{0} after dinner", gap)
                "Snack" -> tr("{0} after snack", gap)
                else -> tr("{0} after {1}", gap, m.name.lowercase())
            }
        }
        return null
    }
}

/** Grams to one decimal, and written without a trailing ".0" ("12", "12.5"). */
object Grams {
    fun round(v: Double): Double = Math.round(v * 10) / 10.0
    /** For showing: "12.5" ("12,5" in Icelandic), "12". */
    fun text(v: Double): String = plain(v).let { if (app.dropi.L.decimal == '.') it else it.replace('.', app.dropi.L.decimal) }
    /** Always with a point: for CSV files, prompts and JSON. */
    fun plain(v: Double): String = round(v).let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.ROOT, "%.1f", it) }
    fun text(v: Double?, missing: String): String = v?.let { text(it) } ?: missing
    /** "12,5" or "12.5" typed in a box. */
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && it >= 0 }
}

/** The person's own amount for a food: carbs (and sugars) in one [portion]. */
data class Usual(val portion: String, val carbs: Double, val sugars: Double? = null, val protein: Double? = null, val fat: Double? = null)

/** Prompts for the on-device model, and reading its replies. */
object MealAi {
    private const val SHAPE =
        "Reply with only JSON, no other text, in exactly this shape:\n" +
            "{\"items\":[{\"food\":\"basmati rice\",\"portion\":\"1 cup\",\"carbs\":45,\"sugars\":0.1,\"fibre\":1.2,\"protein\":4.4,\"fat\":0.4,\"gi\":58,\"drink\":false}," +
            "{\"food\":\"hard boiled egg\",\"portion\":\"1 egg\",\"carbs\":0.6,\"sugars\":0.6,\"fibre\":0,\"protein\":6.3,\"fat\":5.3,\"gi\":0,\"drink\":false}],\"rich\":false,\"tags\":[]}\n" +
            "One item per food, short everyday names, a simple household portion (cup, slice, piece, bowl, glass, spoon), " +
            "and grams for that portion (one decimal is fine) of carbohydrate (not counting fibre, as on European labels), sugars, fibre, protein and fat. " +
            "List every food you can see or that was named, including ones with no carbs such as eggs, meat, bacon, fish, cheese and butter. " +
            "gi is the food's glycaemic index (glucose = 100), or 0 if it has almost no carbs. drink is true for anything drunk. " +
            "Set rich to true when the meal is high in fat or protein (fried food, cream, cheese, pizza, curry, big meat portions). " +
            "tags lists any of these the person mentioned: veg_first (ate vegetables or salad first), walk_after (walked or exercised after), " +
            "alcohol, ill (sick or unwell), stress."

    val PHOTO = "This is a photo of a meal. Identify each food and estimate how much of it is on the plate. $SHAPE"

    fun words(text: String) = "Someone describes what they ate: \"${text.take(300)}\". Work out each food and the amount they said " +
        "(assume a normal portion when they didn't say). $SHAPE"

    fun change(current: Meal, change: String): String {
        val list = current.items.joinToString("; ") { "${it.name}, ${it.portionText(plain = true)}, ${Grams.plain(it.carbs)} g carbs" } +
            (if (current.tags.isNotEmpty()) ". Tags: ${current.tags.joinToString(", ")}" else "")
        return "A meal was logged as: $list. The person says: \"${change.take(200)}\". Apply the change and give the whole meal again. $SHAPE"
    }

    /** One food: a short prompt asking only for its numbers (much quicker than the whole-meal one). */
    fun food(name: String, portion: String?) =
        "Nutrition for ${if (portion.isNullOrBlank()) "one normal portion of" else "$portion of"} ${name.take(60)}. " +
            "Reply with only JSON: {\"portion\":\"1 egg\",\"carbs\":0.6,\"sugars\":0.6,\"fibre\":0,\"protein\":6.3,\"fat\":5.3,\"gi\":0}. " +
            "Grams for that portion; carbs not counting fibre; gi is the glycaemic index, 0 if almost no carbs."

    const val FOOD_SCHEMA = """{"type":"object","properties":{"portion":{"type":"string"},"carbs":{"type":"number"},"sugars":{"type":"number"},"fibre":{"type":"number"},"protein":{"type":"number"},"fat":{"type":"number"},"gi":{"type":"integer"}},"required":["portion","carbs","protein","fat"]}"""

    /** Reads the reply to [food]. */
    fun parseFood(text: String?, name: String, portion: String?): MealItem? {
        if (text.isNullOrBlank()) return null
        val a = text.indexOf('{'); val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            val o = JSONObject(text.substring(a, b + 1))
            if (!o.has("carbs")) return null
            MealItem(
                name = Foods.normalize(name),
                portion = portion?.takeIf { it.isNotBlank() } ?: o.optString("portion", "1 portion").trim().ifEmpty { "1 portion" }.take(30),
                carbs = num(o, "carbs")?.coerceIn(0.0, 300.0) ?: 0.0,
                sugars = num(o, "sugars")?.coerceIn(0.0, 300.0),
                fibre = num(o, "fibre")?.coerceIn(0.0, 100.0),
                protein = num(o, "protein")?.coerceIn(0.0, 300.0),
                fat = num(o, "fat")?.coerceIn(0.0, 300.0),
                gi = num(o, "gi")?.roundToInt()?.takeIf { it in 1..110 },
            )
        } catch (e: Exception) { null }
    }

    /**
     * The foods finished so far in a reply that's still coming ("{"items":[{…},{…},{"fo…"): each
     * complete item object, read as a meal. Also rescues a reply cut off before its end.
     */
    fun partial(text: String?, source: String): Meal? {
        if (text.isNullOrBlank()) return null
        val start = text.indexOf("[").takeIf { it >= 0 } ?: return null
        val objects = ArrayList<String>()
        var depth = 0; var from = -1; var inString = false; var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> { if (depth == 0) from = i; depth++ }
                '}' -> { depth--; if (depth == 0 && from >= 0) { objects += text.substring(from, i + 1); from = -1 } }
                ']' -> if (depth == 0) break
            }
        }
        if (objects.isEmpty()) return null
        return parse("{\"items\":[" + objects.joinToString(",") + "]}", source)
    }

    /** JSON schema for models that can be held to it (Gemma through LiteRT-LM). */
    // Plain types only: the on-device engine's constrained decoding doesn't take type unions or enums.
    const val SCHEMA = """{"type":"object","properties":{"items":{"type":"array","items":{"type":"object","properties":{"food":{"type":"string"},"portion":{"type":"string"},"carbs":{"type":"number"},"sugars":{"type":"number"},"fibre":{"type":"number"},"protein":{"type":"number"},"fat":{"type":"number"},"gi":{"type":"integer"},"drink":{"type":"boolean"}},"required":["food","portion","carbs","protein","fat"]}},"rich":{"type":"boolean"},"tags":{"type":"array","items":{"type":"string"}}},"required":["items"]}"""

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
                            carbs = num(i, "carbs")?.coerceIn(0.0, 300.0) ?: 0.0,
                            sugars = num(i, "sugars")?.coerceIn(0.0, 300.0),
                            fibre = num(i, "fibre")?.coerceIn(0.0, 100.0),
                            protein = num(i, "protein")?.coerceIn(0.0, 300.0),
                            fat = num(i, "fat")?.coerceIn(0.0, 300.0),
                            gi = num(i, "gi")?.roundToInt()?.takeIf { it in 1..110 },
                            drink = i.optBoolean("drink", false),
                        )
                    }.distinctBy { it.key }.take(10)
                    val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) } }.orEmpty().filter { it in MealTags.KEYS }.distinct()
                    if (items.isNotEmpty()) {
                        val m = Meal(items, o.optBoolean("rich", false), source, tags)
                        // Rich by the numbers too, whatever the model said.
                        return m.copy(rich = m.rich || (m.fat ?: 0.0) >= 25 || (m.protein ?: 0.0) >= 40)
                    }
                }
            } catch (e: Exception) { }
        }
        // The older two-line reply.
        val old = Plate.parse(text) ?: return null
        if (old.foods.isEmpty()) return null
        val each = old.carbs?.let { it.toDouble() / old.foods.size } ?: 0.0
        return Meal(old.foods.map { MealItem(it, "1 portion", carbs = each) }, false, source)
    }

    private fun num(o: JSONObject, k: String): Double? {
        if (!o.has(k) || o.isNull(k)) return null
        val v = o.opt(k)
        return when (v) {
            is Number -> v.toDouble().takeIf { !it.isNaN() }?.let { Grams.round(it) }
            is String -> Regex("\\d{1,3}(?:[.,]\\d+)?").find(v)?.value?.replace(',', '.')?.toDoubleOrNull()?.let { Grams.round(it) }
            else -> null
        }
    }

    /** Puts the person's usual amounts in place of guesses, scaled to the portions seen. */
    fun applyUsual(meal: Meal, usual: Map<String, Usual>): Meal = meal.copy(items = meal.items.map { i ->
        val u = usual[i.key] ?: return@map i
        fun s(v: Double?) = v?.let { Grams.round(it * i.qty) }
        i.copy(
            portion = u.portion, carbs = Grams.round(u.carbs * i.qty), sugars = s(u.sugars) ?: i.sugars,
            protein = s(u.protein) ?: i.protein, fat = s(u.fat) ?: i.fat, usual = true,
        )
    })
}

/**
 * A nutrition label's carbs, sugars and fibre per 100 g (or ml), read from the text ML Kit finds
 * on a photo of the packet. Knows the words on Icelandic, English and other European labels
 * (EU labels all follow the same table: energy, fat, carbohydrate "of which sugars", fibre…).
 */
object Label {
    /**
     * Per 100 g. [us] is a US-style label ("Nutrition Facts", "Total Carbohydrate"), where the
     * carbs include fibre; EU and Icelandic carbs already leave fibre out.
     */
    data class Per100(
        val carbs: Double?, val sugars: Double?, val fibre: Double?, val name: String = "",
        val protein: Double? = null, val fat: Double? = null, val us: Boolean = false,
        /** A US label's serving size in grams: its numbers are per serving, not per 100 g. */
        val serving: Double? = null,
    ) {
        /** The numbers per 100 g, when they were read per serving (a US label with its serving size). */
        fun toPer100(): Per100 {
            val s = serving?.takeIf { us && it > 0 } ?: return this
            fun f(v: Double?) = v?.let { it * 100.0 / s }
            return copy(carbs = f(carbs), sugars = f(sugars), fibre = f(fibre), protein = f(protein), fat = f(fat), serving = null)
        }
        /** Carbs that raise blood sugar: fibre taken off on a US label. */
        val netCarbs: Double? get() = carbs?.let { if (us && fibre != null) (it - fibre).coerceAtLeast(0.0) else it }
    }

    private val proteinWords = listOf(
        "prótein", "protein", "eiweiß", "eiweiss", "protéines", "proteines", "proteine", "proteínas", "proteinas", "eiwitten",
        "białko", "bialko", "proteiini", "bílkoviny", "fehérje", "baltymai", "olbaltumvielas", "valk", "πρωτεΐνες", "bjelančevine",
        "beljakovine", "proteine", "белтъци",
    )
    private val fatWords = listOf(
        "fita", "fat", "fett", "matières grasses", "matieres grasses", "lipides", "grassi", "grasas", "gorduras", "vet",
        "tłuszcz", "tluszcz", "rasva", "fedt", "tuky", "zsír", "riebalai", "tauki", "λιπαρά", "masti", "maščobe", "grăsimi", "мазнини",
    )
    /** Rows about saturated fat, which come right after the fat row and mustn't be read as it. */
    private val saturatedWords = listOf(
        "mettuð", "mettað", "mettaðar", "saturate", "saturated", "gesättigt", "gesattigt", "saturés", "satures", "saturi", "saturadas",
        "saturados", "verzadigd", "nasycone", "tyydyttyneet", "mættede", "mättat", "mettet", "nasycen",
    )
    private val usMarks = listOf("nutrition facts", "total carbohydrate", "total carb", "dietary fiber", "total fat")

    // Lower case; matched inside the line, so "kohlenhydrate" also finds "Kohlenhydrate:".
    private val carbWords = listOf(
        "kolvetni", "carbohydrate", "carbohydrates", "carbs", "kohlenhydrat", "glucides", "hidratos de carbono",
        "carboidrati", "kulhydrat", "kolhydrat", "karbohydrat", "koolhydrat", "węglowodany", "weglowodany",
        "hiilihydraat", "hidratos", "sacharidy", "uhlohydráty", "szénhidrát", "angliavandeniai", "ogļhidrāti",
        "süsivesik", "υδατάνθρακες", "ugljikohidrati", "ogljikovi hidrati", "carbohidrați", "въглехидрати",
    )
    private val sugarWords = listOf(
        "sykur", "sugar", "zucker", "sucres", "sucre", "azúcar", "azucar", "açúcar", "zuccheri", "sukker",
        "socker", "suiker", "cukr", "sokeri", "cukry", "cukor", "šećer", "sladkor", "zaharuri", "захари", "σάκχαρα",
    )
    private val fibreWords = listOf(
        "trefjar", "fibre", "fiber", "ballaststoff", "fibres", "fibra", "kostfiber", "vezel", "błonnik", "blonnik",
        "ravintokuitu", "kuitu", "vláknina", "vlakna", "vlaknine", "fibre alimentare", "φυτικές ίνες",
    )

    /** A line of text on the photo: where it is (centre, left edge, height in px) and its slant in degrees. */
    data class TextLine(val text: String, val cx: Float, val cy: Float, val left: Float, val h: Float, val angle: Float)

    /**
     * Table rows from the lines on a photo: turned straight by the lines' typical slant, grouped by
     * height, and read left to right, so "Kolvetni" and its "54 g" end up on one row even when the
     * photo is tilted.
     */
    fun rows(lines: List<TextLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val slant = lines.map { it.angle }.sorted()[lines.size / 2].toDouble().let { if (kotlin.math.abs(it) > 30) 0.0 else it }
        val lineH = lines.map { it.h }.sorted()[lines.size / 2].coerceAtLeast(8f)
        data class P(val l: TextLine, val x: Double, val y: Double)
        fun group(deg: Double): List<List<P>> {
            val rad = Math.toRadians(-deg)
            val c = kotlin.math.cos(rad); val sn = kotlin.math.sin(rad)
            // Straightened positions: each centre rotated back by the slant.
            val ps = lines.map { l -> P(l, l.cx * c - l.cy * sn, l.cx * sn + l.cy * c) }
            val out = ArrayList<MutableList<P>>()
            for (p in ps.sortedBy { it.y }) {
                val row = out.lastOrNull()
                if (row != null && kotlin.math.abs(p.y - row.map { it.y }.average()) <= lineH * 0.6) row += p else out += mutableListOf(p)
            }
            return out
        }
        // The slant as reported, its opposite (in case the sign is the other way) and none:
        // whichever lines the most up into rows wins.
        val best = listOf(slant, -slant, 0.0).distinct().map { group(it) }.minBy { it.size }
        return best.map { r -> r.sortedBy { it.x }.joinToString(" ") { it.l.text } }
    }

    /** Every single-word nutrient name, for fixing small camera misreads ("Kolvetnl" → "kolvetni"). */
    private val oneWordNames by lazy {
        (carbWords + sugarWords + fibreWords + proteinWords + fatWords + saturatedWords).filter { ' ' !in it && it.length >= 5 }.distinct()
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    /** Words one letter off a nutrient name (two for long ones) become that name; digits read as letters too ("Ko1vetni"). */
    private fun fixWords(line: String): String = line.split(' ').joinToString(" ") { w ->
        val word = w.trim(':', '.', ',', '-', '(', ')')
        if (word.length < 5 || word.any { it.isDigit() } && word.count { it.isLetter() } < 4) return@joinToString w
        val letters = word.replace('1', 'l').replace('0', 'o')
        if (letters in oneWordNames) return@joinToString w.replace(word, letters)
        val best = oneWordNames.minByOrNull { distance(letters, it) } ?: return@joinToString w
        val allowed = if (best.length >= 9) 2 else 1
        if (distance(letters, best) <= allowed) w.replace(word, best) else w
    }

    /**
     * Tables read as two columns (all the names, then all the numbers): pair the nutrient names
     * that have no number with the number-only lines, in order.
     */
    private fun pairColumns(clean: List<String>): List<String> {
        val all = carbWords + sugarWords + fibreWords + proteinWords + fatWords + listOf("orka", "energy", "energi", "salt")
        val names = clean.filter { l -> all.any { l.contains(it) } && l.none { it.isDigit() } }
        val values = clean.filter { l -> l.isNotBlank() && Regex("^[<~]?\\s*\\d").containsMatchIn(l.trim()) && l.count { it.isLetter() } <= 6 }
        if (names.size < 3 || values.size < names.size) return emptyList()
        // Energy rows often take two values (kJ and kcal): line the rest up from the end when needed.
        val v = if (values.size > names.size && values.any { it.contains("kj") }) values.filter { !it.contains("kcal") && !it.contains("kkal") } else values
        return names.zip(v.take(names.size)) { n, x -> "$n $x" }
    }

    /** [lines] in reading order. The first amount on (or right after) a matching line is per 100 g. */
    fun read(lines: List<String>): Per100? {
        val fixed = lines.map { fixWords(normal(it)) }
        val clean = fixed + pairColumns(fixed)
        fun find(words: List<String>, avoid: List<String> = emptyList(), skip: List<String> = emptyList()): Double? {
            for ((k, l) in clean.withIndex()) {
                if (skip.any { l.contains(it) }) continue
                val at = words.filter { l.contains(it) }.minOfOrNull { l.indexOf(it) } ?: continue
                // "of which sugars" before the word means this row is about sugars, not carbs.
                if (avoid.any { w -> l.indexOf(w).let { it in 0 until at } }) continue
                val after = l.substring(words.filter { l.contains(it) }.maxOf { l.indexOf(it) + it.length })
                val here = number(after) ?: number(l) ?: clean.getOrNull(k + 1)?.let { next ->
                    // A value on the next row, when that row isn't another nutrient.
                    if ((carbWords + sugarWords + fibreWords + proteinWords + fatWords).any { next.contains(it) }) null else number(next)
                }
                if (here != null) return here
            }
            return null
        }
        val carbs = find(carbWords, avoid = sugarWords + fibreWords)
        val sugars = find(sugarWords)
        val fibre = find(fibreWords)
        val protein = find(proteinWords)
        // Fat: never a carbs row ("kolvetni" holds "vet") or a saturated-fat row ("þar af mettuð fita").
        val fatClean = find(fatWords, skip = carbWords + sugarWords + saturatedWords)
        if (carbs == null && sugars == null) return null
        val us = isUs(clean)
        return Per100(carbs, sugars, fibre, protein = protein, fat = fatClean, us = us, serving = if (us) servingGrams(clean) else null)
    }

    /** "Serving size 1 cup (40g)" → 40. */
    fun servingGrams(lines: List<String>): Double? {
        val l = lines.firstOrNull { it.contains("serving size") } ?: return null
        return Regex("(\\d{1,4}(?:\\.\\d)?)\\s*g\\b").find(l)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it in 1.0..2000.0 }
    }

    /** A US-style label, where carbs include fibre. */
    fun isUs(lines: List<String>): Boolean = lines.any { l -> usMarks.any { l.lowercase(Locale.ROOT).contains(it) } }

    /** Lower case, decimal commas as points, and "per 100 g" headings taken out so 100 isn't read as a value. */
    private fun normal(s: String) = s.lowercase(Locale.ROOT)
        .replace(Regex("(\\d),(\\d)"), "$1.$2")
        .replace(Regex("(per|pr\\.?|í|i|je|pour|por|w|på|pro)?\\s*100\\s*(g|ml)\\b"), " ")

    /** The first amount: "12.4 g", "12.4g", "<0.5 g", or a bare "12.4" when the unit was lost. */
    private fun number(l: String): Double? {
        Regex("<?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)\\s*g\\b").find(l)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it <= 100.0 }?.let { return it }
        return Regex("(?<![\\d.])(\\d{1,2}(?:\\.\\d{1,2})?)(?![\\d.]|\\s*(kj|kcal|kkal|%))").find(l)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it <= 100.0 }
    }

    /** Prompt for the on-device model: the label's text in, per-100 g numbers out. */
    fun prompt(text: String, us: Boolean = false) =
        if (us) "This is text read by a camera from a US \"Nutrition Facts\" label. Find the values per serving as printed, " +
            "the serving size in grams, and the product's name if it's there. " +
            "Reply with only JSON: {\"name\":\"granola\",\"serving\":40,\"carbs\":24,\"sugars\":9,\"fibre\":3,\"protein\":4,\"fat\":5}. " +
            "Use a dot for decimals. Use null for anything missing.\n\n" + text.take(1500)
        else "This is text read by a camera from a food packet's nutrition label (it may be Icelandic, English or another " +
            "European language, with decimal commas). Find the values per 100 g or 100 ml, and the product's name if it's there. " +
            "Reply with only JSON: {\"name\":\"granola\",\"carbs\":60.5,\"sugars\":22,\"fibre\":7,\"protein\":9,\"fat\":12}. " +
            "Use a dot for decimals (60.5, not 60,5). Use null for anything missing.\n\n" +
            text.take(1500)

    /** Reads the model's reply to [prompt]. */
    fun parseAi(reply: String?): Per100? {
        if (reply.isNullOrBlank()) return null
        val a = reply.indexOf('{'); val b = reply.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            // A decimal comma ("60,5") would break the JSON: make it a point first.
            val o = org.json.JSONObject(reply.substring(a, b + 1).replace(Regex("(\\d),(\\d)"), "$1.$2"))
            fun d(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() && it in 0.0..100.0 }
            val p = Per100(
                d("carbs"), d("sugars"), d("fibre"), o.optString("name").takeIf { it != "null" }.orEmpty().take(40),
                protein = d("protein"), fat = d("fat"),
                serving = if (o.isNull("serving")) null else o.optDouble("serving").takeIf { !it.isNaN() && it in 1.0..2000.0 },
            )
            if (p.carbs == null && p.sugars == null) null else p
        } catch (e: Exception) { null }
    }

    /** The item for [grams] eaten of a product with [p] per 100 g. */
    fun item(name: String, grams: Int, p: Per100): MealItem = MealItem(
        name = name.ifBlank { "packet food" },
        portion = "$grams g",
        carbs = Grams.round((p.netCarbs ?: 0.0) * grams / 100.0),
        protein = p.protein?.let { Grams.round(it * grams / 100.0) },
        fat = p.fat?.let { Grams.round(it * grams / 100.0) },
        sugars = p.sugars?.let { Grams.round(it * grams / 100.0) },
        fibre = p.fibre?.let { Grams.round(it * grams / 100.0) },
    )
}

/** How much a food or meal tends to lift the person's numbers, in words that don't need colour. */
enum class Rise(val en: String) {
    GENTLE("Gentle rise"), MODERATE("Moderate rise"), BIG("Big rise");

    /** "Gentle rise"… in the app's language. */
    val word: String get() = when (this) {
        GENTLE -> tr("Gentle rise")
        MODERATE -> tr("Moderate rise")
        BIG -> tr("Big rise")
    }

    /** Without the word "rise", for a small badge: "Gentle", "Moderate", "Big". */
    val short: String get() = when (this) {
        GENTLE -> tr("Gentle")
        MODERATE -> tr("Moderate")
        BIG -> tr("Big")
    }

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
    fun estimate(start: Double, foods: List<String>, readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>): Estimate? =
        estimate(start, foods, Foods.meals(readings, sensor, meals))

    /** The same, from meals already measured (leave out the meal being looked at). */
    fun estimate(start: Double, foods: List<String>, measured: List<Foods.Meal>): Estimate? {
        if (foods.isEmpty()) return null
        val wanted = foods.toSet()
        val past = measured.filter { m -> m.rise != null && m.foods.any { it in wanted } }
        if (past.size < Foods.MIN_MEALS) return null
        val shared = past.flatMap { m -> m.foods.filter { it in wanted } }.distinct()
        return Estimate(start, Foods.median(past.mapNotNull { it.rise }), past.size, shared)
    }

    /** How a logged meal went: its start, peak and when, from what came after (null until then). */
    data class Outcome(val start: Double, val peak: Double, val peakAt: Long, val startAt: Long) {
        val rise get() = peak - start
    }

    fun outcome(e: MealEntry, readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>): Outcome? =
        outcome(Foods.meals(readings, sensor, meals).firstOrNull { it.entryId == e.id })

    /** How a measured meal went (null when nothing came after it yet). */
    fun outcome(m: Foods.Meal?): Outcome? {
        if (m == null) return null
        val best = m.after.filter { it.first in 45..180 }.maxByOrNull { it.second } ?: return null
        return Outcome(m.start, m.start + best.second, m.at + best.first * 60_000L, m.startAt)
    }
}
