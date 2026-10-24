package app.dropi.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How fast foods' carbs hit (glycaemic index, glucose = 100): a small built-in table of common
 * foods, from published international GI tables, rounded. Values are typical, not exact; the
 * on-device model's own figure is used first when it gives one.
 */
object Gi {
    enum class Speed(val word: String) { FAST("Fast carbs"), MEDIUM("Medium carbs"), SLOW("Slow carbs") }

    fun speed(gi: Int) = when {
        gi >= 70 -> Speed.FAST
        gi >= 56 -> Speed.MEDIUM
        else -> Speed.SLOW
    }

    // Longest names first when matching, so "brown rice" wins over "rice".
    private val table: List<Pair<String, Int>> = listOf(
        "jasmine rice" to 89, "sticky rice" to 87, "white rice" to 73, "brown rice" to 68, "basmati" to 58, "wild rice" to 57,
        "rice noodles" to 53, "sushi" to 55, "risotto" to 69, "rice cake" to 82, "rice" to 73,
        "white bread" to 75, "wholemeal bread" to 74, "whole wheat bread" to 74, "rye bread" to 58, "rúgbrauð" to 58,
        "sourdough" to 54, "flatkaka" to 58, "bagel" to 72, "croissant" to 67, "naan" to 71, "pita" to 68, "baguette" to 95,
        "tortilla" to 50, "wrap" to 50, "crispbread" to 64, "crackers" to 70, "toast" to 75, "bread" to 72, "bread roll" to 70,
        "spaghetti" to 49, "pasta" to 49, "macaroni" to 47, "lasagne" to 47, "noodles" to 47, "couscous" to 65, "bulgur" to 46,
        "quinoa" to 53, "instant oats" to 79, "porridge" to 55, "oatmeal" to 55, "hafragrautur" to 55, "oats" to 55, "muesli" to 57,
        "granola" to 55, "cornflakes" to 81, "cereal" to 70,
        "mashed potato" to 87, "baked potato" to 85, "boiled potatoes" to 78, "potatoes" to 78, "potato" to 78, "fries" to 63,
        "chips" to 63, "crisps" to 56, "sweet potato" to 63, "corn" to 52, "popcorn" to 65,
        "lentils" to 32, "chickpeas" to 28, "kidney beans" to 24, "baked beans" to 40, "beans" to 30, "peas" to 51, "hummus" to 6,
        "pizza" to 60, "burger" to 66, "pancakes" to 66, "waffles" to 76, "cake" to 60, "muffin" to 60, "doughnut" to 76,
        "cookies" to 62, "biscuits" to 62, "kleina" to 70, "chocolate" to 40, "ice cream" to 51, "honey" to 61, "jam" to 51,
        "sugar" to 65, "candy" to 70, "sweets" to 70,
        "watermelon" to 76, "pineapple" to 59, "grapes" to 59, "raisins" to 64, "dates" to 42, "mango" to 51, "banana" to 51,
        "orange" to 43, "apple" to 36, "pear" to 38, "peach" to 42, "kiwi" to 50, "strawberries" to 40, "blueberries" to 53,
        "berries" to 40, "cherries" to 22,
        "orange juice" to 50, "apple juice" to 41, "juice" to 50, "cola" to 63, "coke" to 63, "soda" to 63, "lemonade" to 60,
        "smoothie" to 45, "chocolate milk" to 40, "kókómjólk" to 40, "milkshake" to 41, "beer" to 66, "sports drink" to 78,
        "skyr" to 36, "yoghurt" to 41, "yogurt" to 41, "milk" to 39,
    ).sortedByDescending { it.first.length }

    fun lookup(name: String): Int? {
        val n = name.lowercase(Locale.ROOT)
        return table.firstOrNull { (k, _) -> n.contains(k) }?.second
    }

    private val drinks = listOf(
        "juice", "safi", "soda", "cola", "coke", "pepsi", "fanta", "sprite", "lemonade", "smoothie", "milkshake", "shake",
        "latte", "frappe", "frappuccino", "iced tea", "sweet tea", "energy drink", "red bull", "monster", "kókómjólk", "gos",
        "appelsín", "malt", "hot chocolate", "cocoa", "kakó", "sports drink", "gatorade", "powerade", "chocolate milk", "drink",
    )
    private val sugarFree = listOf("diet", "zero", "sugar free", "sugar-free", "sykurlaus", "light", "max", "water", "vatn")

    fun isDrink(name: String): Boolean = name.lowercase(Locale.ROOT).let { n -> drinks.any { n.contains(it) } }
    fun isSugarFree(name: String): Boolean = name.lowercase(Locale.ROOT).let { n -> sugarFree.any { n.contains(it) } }
}

/** Things that went with a meal and change how it lands, picked up from the person's words or tapped. */
object MealTags {
    data class Tag(val key: String, val label: String, val words: List<String>)

    val ALL = listOf(
        Tag("veg_first", "Veg first", listOf("veg first", "vegetables first", "salad first", "veggies first")),
        Tag("walk_after", "Walked after", listOf("walk", "walked", "walking", "went for a walk", "gym after", "exercise after", "göngutúr", "labbaði")),
        Tag("alcohol", "Alcohol", listOf("wine", "beer", "bjór", "vín", "cocktail", "vodka", "whisky", "whiskey", "gin", "rum", "prosecco", "champagne", "cider")),
        Tag("ill", "Feeling ill", listOf("sick", "ill", "unwell", "flu", "fever", "a cold", "veik", "veikur")),
        Tag("stress", "Stressed", listOf("stress", "stressed", "anxious", "stressaður", "stressuð")),
    )
    val KEYS = ALL.map { it.key }.toSet()

    fun label(key: String) = ALL.firstOrNull { it.key == key }?.label ?: key

    /** Tags mentioned in [text], by plain word match (the model's tags are added to these). */
    fun fromWords(text: String): List<String> {
        val t = " " + text.lowercase(Locale.ROOT) + " "
        return ALL.filter { tag -> tag.words.any { w -> Regex("(?<![\\p{L}])${Regex.escape(w)}(?![\\p{L}])").containsMatchIn(t) } }.map { it.key }
    }
}

/**
 * The expected shape of the rise after a meal: how high, when it peaks and when it's back near
 * the start. From the meal's numbers (carbs, how fast they hit, fibre, fat and protein, what went
 * with it), scaled by how the person's own meals have gone. An estimate, labelled as one.
 */
object MealCurve {
    data class Curve(val rise: Double, val peakMin: Int, val backMin: Int, val reasons: List<String>, val personal: Boolean) {
        /** The expected change from the start [min] minutes after eating. */
        fun at(min: Double): Double = when {
            min <= 0 -> 0.0
            min <= peakMin -> rise * sin(PI / 2 * min / peakMin)
            min <= backMin -> rise * (0.5 + 0.5 * cos(PI * (min - peakMin) / (backMin - peakMin)))
            else -> 0.0
        }
    }

    /** mmol/L per gram of carbs when there's nothing personal yet: about +3 for 50 g. */
    const val DEFAULT_PER_GRAM = 0.06

    /**
     * How much the person rises per gram of carbs: the median over past meals with at least 15 g
     * and an after reading. Null with fewer than four such meals.
     */
    fun perGram(readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>): Double? {
        val byId = meals.associateBy { it.id }
        val ratios = Foods.meals(readings, sensor, meals).mapNotNull { m ->
            val e = byId[m.entryId] ?: return@mapNotNull null
            val rise = m.rise ?: return@mapNotNull null
            if (e.carbs < 15) null else rise.coerceAtLeast(0.0) / e.carbs
        }
        return if (ratios.size < 4) null else Foods.median(ratios).coerceIn(0.015, 0.15)
    }

    fun predict(meal: Meal, perGram: Double?, history: MealHistory.Estimate?): Curve? {
        if (meal.items.isEmpty() || meal.carbs <= 0) return null
        val reasons = ArrayList<String>()
        var rise = meal.carbs * (perGram ?: DEFAULT_PER_GRAM)
        var peak = 60
        val drinkOnly = meal.items.all { it.sugaryDrink || it.carbs == 0 } && meal.hasSugaryDrink
        when (meal.speed) {
            Gi.Speed.FAST -> { rise *= 1.15; peak = 45; reasons += "fast carbs" }
            Gi.Speed.SLOW -> { rise *= 0.85; peak = 75; reasons += "slow carbs" }
            else -> {}
        }
        if (meal.hasSugaryDrink) {
            rise *= 1.1
            peak = if (drinkOnly) 35 else minOf(peak, 50)
            reasons += "a sugary drink hits fast"
        }
        if ((meal.fibre ?: 0) >= 10) { rise *= 0.9; reasons += "fibre softens it" }
        if ("veg_first" in meal.tags) { rise *= 0.9; reasons += "veg first" }
        if ("walk_after" in meal.tags) { rise *= 0.85; reasons += "a walk after" }
        val fat = meal.fat ?: 0
        val protein = meal.protein ?: 0
        var tail = 90
        when {
            meal.slowDigesting -> { peak += 45; tail += 60; rise *= 0.9; reasons += "fat and protein push the peak later" }
            meal.someFatProtein -> { peak += 20; tail += 30; reasons += "some fat and protein slow it a little" }
        }
        if (fat >= 25 && protein >= 30 && !meal.slowDigesting) peak += 10
        // Past meals with the same foods know this person best: they count for half.
        if (history != null) {
            rise = 0.5 * rise + 0.5 * history.rise.coerceAtLeast(0.0)
            reasons += "your past meals with ${history.foods.take(2).joinToString(" and ") { Foods.display(it).lowercase(Locale.ROOT) }}"
        }
        return Curve(rise.coerceIn(0.0, 12.0), peak, peak + tail, reasons, perGram != null || history != null)
    }

    /** "1 h 15 min", "45 min", "2 h". */
    fun duration(min: Int): String {
        val h = min / 60; val m = (min % 60 + 2) / 5 * 5
        return when {
            h == 0 -> "$min min"
            m == 0 || m == 60 -> "${if (m == 60) h + 1 else h} h"
            else -> "$h h $m min"
        }
    }
}

/**
 * What the person's own meals show: groups of meals compared (with fibre or without, with a walk
 * or without…). Only said when each side has at least three measured meals and the gap is real.
 */
object MealPatterns {
    data class Pattern(val text: String, val good: Boolean)

    private data class Measured(val e: MealEntry, val rise: Double, val peakMin: Int)

    private fun measured(readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>): List<Measured> {
        val byId = meals.associateBy { it.id }
        return Foods.meals(readings, sensor, meals).mapNotNull { m ->
            val e = byId[m.entryId] ?: return@mapNotNull null
            val rise = m.rise ?: return@mapNotNull null
            val peak = m.after.filter { it.first in 30..240 }.maxByOrNull { it.second }?.first ?: return@mapNotNull null
            Measured(e, rise, peak)
        }
    }

    fun find(readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>, unit: GlucoseUnit, minEach: Int = 3): List<Pattern> {
        val ms = measured(readings, sensor, meals)
        val out = ArrayList<Pattern>()
        fun d(v: Double) = Foods.delta(abs(v), unit).removePrefix("+")
        fun compareRise(yes: List<Measured>, no: List<Measured>, say: (less: Boolean, diff: String, n: Int) -> String) {
            if (yes.size < minEach || no.size < minEach) return
            val diff = Foods.median(yes.map { it.rise }) - Foods.median(no.map { it.rise })
            if (abs(diff) < 0.8) return
            out += Pattern(say(diff < 0, d(diff), yes.size), diff < 0)
        }
        compareRise(ms.filter { (it.e.meal.fibre ?: 0) >= 8 }, ms.filter { (it.e.meal.fibre ?: 0) < 8 }) { less, diff, n ->
            "Meals with 8 g or more fibre rise $diff ${if (less) "less" else "more"} for you ($n meals)."
        }
        compareRise(ms.filter { "walk_after" in it.e.meal.tags }, ms.filter { "walk_after" !in it.e.meal.tags }) { less, diff, n ->
            "With a walk after, your rise is $diff ${if (less) "smaller" else "bigger"} ($n meals)."
        }
        compareRise(ms.filter { "veg_first" in it.e.meal.tags }, ms.filter { "veg_first" !in it.e.meal.tags }) { less, diff, n ->
            "Eating veg first, your rise is $diff ${if (less) "smaller" else "bigger"} ($n meals)."
        }
        compareRise(ms.filter { it.e.meal.speed == Gi.Speed.SLOW }, ms.filter { it.e.meal.speed == Gi.Speed.FAST }) { less, diff, n ->
            "Slow-carb meals rise $diff ${if (less) "less" else "more"} than fast-carb ones for you ($n slow-carb meals)."
        }
        compareRise(ms.filter { it.e.meal.hasSugaryDrink }, ms.filter { !it.e.meal.hasSugaryDrink }) { less, diff, n ->
            "Meals with a sugary drink rise $diff ${if (less) "less" else "more"} for you ($n meals)."
        }
        // Fat and protein move the peak later rather than higher.
        val rich = ms.filter { it.e.meal.slowDigesting }
        val lean = ms.filter { !it.e.meal.slowDigesting && !it.e.meal.someFatProtein }
        if (rich.size >= minEach && lean.size >= minEach) {
            val later = Foods.median(rich.map { it.peakMin.toDouble() }) - Foods.median(lean.map { it.peakMin.toDouble() })
            if (later >= 25) out += Pattern("Fatty or protein-rich meals peak about ${later.roundToInt() / 5 * 5} min later for you (${rich.size} meals).", true)
        }
        // Alcohol: the next morning's fasting reading.
        val zone = ZoneId.systemDefault()
        fun nextFasting(e: MealEntry): Reading? {
            val day = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate().plusDays(1)
            return readings.firstOrNull { it.tag == MealTag.FASTING && it.date(zone) == day }
        }
        val drinksDays = meals.filter { "alcohol" in it.meal.tags }.mapNotNull { nextFasting(it) }.distinct()
        val dryDays = readings.filter { it.tag == MealTag.FASTING }.filter { f ->
            val prev = f.date(zone).minusDays(1)
            meals.none { "alcohol" in it.meal.tags && Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == prev }
        }
        if (drinksDays.size >= minEach && dryDays.size >= minEach) {
            val diff = Foods.median(drinksDays.map { it.mmol }) - Foods.median(dryDays.map { it.mmol })
            if (abs(diff) >= 0.5) out += Pattern(
                "After an evening with alcohol, your fasting reading is ${d(diff)} ${if (diff < 0) "lower" else "higher"} (${drinksDays.size} mornings).",
                false,
            )
        }
        return out
    }
}

/** What was eaten each day: carbs, sugars, fibre, protein and fat added up. */
object DayTotals {
    data class Totals(val day: LocalDate, val meals: Int, val carbs: Int, val sugars: Int?, val fibre: Int?, val protein: Int?, val fat: Int?)

    fun of(day: LocalDate, meals: List<MealEntry>): Totals {
        fun sum(f: (Meal) -> Int?): Int? = meals.mapNotNull { f(it.meal) }.takeIf { it.isNotEmpty() }?.sum()
        return Totals(day, meals.size, meals.sumOf { it.carbs }, sum { it.sugars }, sum { it.fibre }, sum { it.protein }, sum { it.fat })
    }

    fun byDay(meals: List<MealEntry>, zone: ZoneId): Map<LocalDate, Totals> =
        meals.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.mapValues { (d, ms) -> of(d, ms) }

    /** Average carbs a day over the 7 days up to [today] and the 7 before, counting only days with meals. */
    fun weekCarbs(meals: List<MealEntry>, today: LocalDate, zone: ZoneId): Pair<Int?, Int?> {
        val days = byDay(meals, zone)
        fun avg(from: Long, to: Long): Int? {
            val ds = (from..to).mapNotNull { days[today.minusDays(it)] }
            return if (ds.size < 3) null else (ds.sumOf { it.carbs }.toDouble() / ds.size).roundToInt()
        }
        return avg(0, 6) to avg(7, 13)
    }

    /** "142 g carbs · 38 g sugars · 22 g fibre · 85 g protein · 60 g fat" (missing ones left out). */
    fun text(t: Totals): String = listOfNotNull(
        "${t.carbs} g carbs", t.sugars?.let { "$it g sugars" }, t.fibre?.let { "$it g fibre" },
        t.protein?.let { "$it g protein" }, t.fat?.let { "$it g fat" },
    ).joinToString(" · ")
}
