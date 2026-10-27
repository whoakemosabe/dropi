package app.dropi.data

import app.dropi.L
import app.dropi.tr
import app.dropi.trn
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
    enum class Speed(val en: String) {
        FAST("Fast carbs"), MEDIUM("Medium carbs"), SLOW("Slow carbs");

        /** "Fast carbs"… in the app's language ([en] is the English, for prompts). */
        val word: String get() = when (this) {
            FAST -> tr("Fast carbs")
            MEDIUM -> tr("Medium carbs")
            SLOW -> tr("Slow carbs")
        }
    }

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
        "grjónagrautur" to 70, "laufabrauð" to 70, "danish pastry" to 59, "caramelised potatoes" to 80,
    ).sortedByDescending { it.first.length }

    fun lookup(name: String): Int? {
        val n = name.lowercase(Locale.ROOT)
        table.firstOrNull { (k, _) -> n.contains(k) }?.let { return it.second }
        // Typed in Icelandic: the food's name in this table ("hrísgrjón" → rice).
        val en = FoodNames.english(name) ?: return null
        return table.firstOrNull { (k, _) -> en.contains(k) }?.second
    }

    private val drinks = listOf(
        "juice", "safi", "soda", "cola", "coke", "pepsi", "fanta", "sprite", "lemonade", "smoothie", "milkshake", "shake",
        "latte", "frappe", "frappuccino", "iced tea", "sweet tea", "energy drink", "red bull", "monster", "kókómjólk", "gos",
        "appelsín", "malt", "hot chocolate", "cocoa", "kakó", "sports drink", "gatorade", "powerade", "chocolate milk", "drink",
    )
    private val sugarFree = listOf("diet", "zero", "sugar free", "sugar-free", "sykurlaus", "light", "max", "water", "vatn", "kristall")

    fun isDrink(name: String): Boolean = name.lowercase(Locale.ROOT).let { n -> drinks.any { n.contains(it) } } ||
        FoodNames.english(name)?.let { en -> drinks.any { en.contains(it) } } == true
    fun isSugarFree(name: String): Boolean = name.lowercase(Locale.ROOT).let { n -> sugarFree.any { n.contains(it) } }
}

/** Things that went with a meal and change how it lands, picked up from the person's words or tapped. */
object MealTags {
    /** [en] is the English name (prompts); [label] is shown, in the app's language. */
    data class Tag(val key: String, val en: String, val words: List<String>) {
        val label: String get() = when (key) {
            "veg_first" -> tr("Veg first")
            "walk_after" -> tr("Walked after")
            "alcohol" -> tr("Alcohol")
            "ill" -> tr("Feeling ill")
            "stress" -> tr("Stressed")
            else -> en
        }
    }

    val ALL = listOf(
        Tag("veg_first", "Veg first", listOf("veg first", "vegetables first", "salad first", "veggies first")),
        Tag("walk_after", "Walked after", listOf("walk", "walked", "walking", "went for a walk", "gym after", "exercise after", "göngutúr", "labbaði")),
        Tag("alcohol", "Alcohol", listOf("wine", "beer", "bjór", "vín", "cocktail", "vodka", "whisky", "whiskey", "gin", "rum", "prosecco", "champagne", "cider")),
        Tag("ill", "Feeling ill", listOf("sick", "ill", "unwell", "flu", "fever", "a cold", "veik", "veikur")),
        Tag("stress", "Stressed", listOf("stress", "stressed", "anxious", "stressaður", "stressuð")),
    )
    val KEYS = ALL.map { it.key }.toSet()

    /** The tag's name as shown, in the app's language. */
    fun label(key: String) = ALL.firstOrNull { it.key == key }?.label ?: key

    /** The tag's English name (for prompts). */
    fun en(key: String) = ALL.firstOrNull { it.key == key }?.en ?: key

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
    fun perGram(readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>): Double? =
        perGram(Foods.meals(readings, sensor, meals), meals)

    /** The same, from meals already measured. */
    fun perGram(measured: List<Foods.Meal>, meals: List<MealEntry>): Double? {
        val byId = meals.associateBy { it.id }
        val ratios = measured.mapNotNull { m ->
            val e = byId[m.entryId] ?: return@mapNotNull null
            val rise = m.rise ?: return@mapNotNull null
            if (e.carbs < 15) null else rise.coerceAtLeast(0.0) / e.carbs
        }
        return if (ratios.size < 4) null else Foods.median(ratios).coerceIn(0.015, 0.15)
    }

    /**
     * [learntPeak]: the person's own peak time (see [Learning.peak]); the usual 60 min is replaced
     * by it and the adjustments below (fast, slow, fat…) are kept relative to it.
     */
    fun predict(meal: Meal, perGram: Double?, history: MealHistory.Estimate?, learntPeak: Int? = null): Curve? {
        if (meal.items.isEmpty() || meal.carbs <= 0) return null
        val reasons = ArrayList<String>()
        var rise = meal.carbs * (perGram ?: DEFAULT_PER_GRAM)
        var peak = 60
        val drinkOnly = meal.items.all { it.sugaryDrink || it.carbs == 0.0 } && meal.hasSugaryDrink
        when (meal.speed) {
            Gi.Speed.FAST -> { rise *= 1.15; peak = 45; reasons += tr("fast carbs") }
            Gi.Speed.SLOW -> { rise *= 0.85; peak = 75; reasons += tr("slow carbs") }
            else -> {}
        }
        if (meal.hasSugaryDrink) {
            rise *= 1.1
            peak = if (drinkOnly) 35 else minOf(peak, 50)
            reasons += tr("a sugary drink hits fast")
        }
        if ((meal.fibre ?: 0.0) >= 10) { rise *= 0.9; reasons += tr("fibre softens it") }
        if ("veg_first" in meal.tags) { rise *= 0.9; reasons += tr("veg first") }
        if ("walk_after" in meal.tags) { rise *= 0.85; reasons += tr("a walk after") }
        val fat = meal.fat ?: 0.0
        val protein = meal.protein ?: 0.0
        var tail = 90
        when {
            meal.slowDigesting -> { peak += 45; tail += 60; rise *= 0.9; reasons += tr("fat and protein push the peak later") }
            meal.someFatProtein -> { peak += 20; tail += 30; reasons += tr("some fat and protein slow it a little") }
        }
        if (fat >= 25 && protein >= 30 && !meal.slowDigesting) peak += 10
        if (learntPeak != null) {
            peak = (peak - 60 + learntPeak).coerceIn(30, 240)
            reasons += tr("your usual peak time")
        }
        // Past meals with the same foods know this person best: they count for half.
        if (history != null) {
            rise = 0.5 * rise + 0.5 * history.rise.coerceAtLeast(0.0)
            val f = history.foods.take(2).map { Foods.display(it).lowercase(Locale.ROOT) }
            reasons += if (f.size >= 2) tr("your past meals with {0} and {1}", f[0], f[1]) else tr("your past meals with {0}", f.firstOrNull().orEmpty())
        }
        return Curve(rise.coerceIn(0.0, 12.0), peak, peak + tail, reasons, perGram != null || history != null || learntPeak != null)
    }

    /** "1 h 15 min", "45 min", "2 h". */
    fun duration(min: Int): String {
        val h = min / 60; val m = (min % 60 + 2) / 5 * 5
        return when {
            h == 0 -> tr("{0} min", min)
            m == 0 || m == 60 -> tr("{0} h", if (m == 60) h + 1 else h)
            else -> tr("{0} h {1} min", h, m)
        }
    }
}

/**
 * What the person's own meals show: groups of meals compared (with fibre or without, with a walk
 * or without…). Only said when each side has at least three measured meals and the gap is real.
 */
object MealPatterns {
    /** [text] is shown, in the app's language; [en] is the same in English (for prompts). */
    data class Pattern(val text: String, val good: Boolean, val en: String = text)

    /** The English form of a counted sentence (the count is {0}, then the other values). */
    private fun english(n: Int, one: String, other: String, vararg args: Any?): String =
        L.fill(if (n == 1) one else other, arrayOf<Any?>(n, *args))

    private data class Measured(val e: MealEntry, val rise: Double, val peakMin: Int)

    private fun measured(all: List<Foods.Meal>, meals: List<MealEntry>): List<Measured> {
        val byId = meals.associateBy { it.id }
        return all.mapNotNull { m ->
            val e = byId[m.entryId] ?: return@mapNotNull null
            val rise = m.rise ?: return@mapNotNull null
            val peak = m.after.filter { it.first in 30..240 }.maxByOrNull { it.second }?.first ?: return@mapNotNull null
            Measured(e, rise, peak)
        }
    }

    fun find(readings: List<Reading>, sensor: List<SensorPoint>, meals: List<MealEntry>, unit: GlucoseUnit, minEach: Int = 3): List<Pattern> =
        fromMeasured(Foods.meals(readings, sensor, meals), readings, meals, unit, minEach)

    /** The same, from meals already measured with [Foods.meals]. */
    fun fromMeasured(all: List<Foods.Meal>, readings: List<Reading>, meals: List<MealEntry>, unit: GlucoseUnit, minEach: Int = 3): List<Pattern> {
        val ms = measured(all, meals)
        val out = ArrayList<Pattern>()
        fun d(v: Double) = Foods.delta(abs(v), unit).removePrefix("+")
        fun dPlain(v: Double) = Foods.delta(abs(v), unit, plain = true).removePrefix("+")
        // say(less, shown diff, English diff, meals) gives the sentence shown and its English.
        fun compareRise(yes: List<Measured>, no: List<Measured>, say: (less: Boolean, diff: String, enDiff: String, n: Int) -> Pair<String, String>) {
            if (yes.size < minEach || no.size < minEach) return
            val diff = Foods.median(yes.map { it.rise }) - Foods.median(no.map { it.rise })
            if (abs(diff) < 0.8) return
            val (text, en) = say(diff < 0, d(diff), dPlain(diff), yes.size)
            out += Pattern(text, diff < 0, en)
        }
        compareRise(ms.filter { (it.e.meal.fibre ?: 0.0) >= 8 }, ms.filter { (it.e.meal.fibre ?: 0.0) < 8 }) { less, diff, enDiff, n ->
            if (less) trn(n, "Meals with 8 g or more fibre rise {1} less for you ({0} meal).", "Meals with 8 g or more fibre rise {1} less for you ({0} meals).", diff) to
                english(n, "Meals with 8 g or more fibre rise {1} less for you ({0} meal).", "Meals with 8 g or more fibre rise {1} less for you ({0} meals).", enDiff)
            else trn(n, "Meals with 8 g or more fibre rise {1} more for you ({0} meal).", "Meals with 8 g or more fibre rise {1} more for you ({0} meals).", diff) to
                english(n, "Meals with 8 g or more fibre rise {1} more for you ({0} meal).", "Meals with 8 g or more fibre rise {1} more for you ({0} meals).", enDiff)
        }
        compareRise(ms.filter { "walk_after" in it.e.meal.tags }, ms.filter { "walk_after" !in it.e.meal.tags }) { less, diff, enDiff, n ->
            if (less) trn(n, "With a walk after, your rise is {1} smaller ({0} meal).", "With a walk after, your rise is {1} smaller ({0} meals).", diff) to
                english(n, "With a walk after, your rise is {1} smaller ({0} meal).", "With a walk after, your rise is {1} smaller ({0} meals).", enDiff)
            else trn(n, "With a walk after, your rise is {1} bigger ({0} meal).", "With a walk after, your rise is {1} bigger ({0} meals).", diff) to
                english(n, "With a walk after, your rise is {1} bigger ({0} meal).", "With a walk after, your rise is {1} bigger ({0} meals).", enDiff)
        }
        compareRise(ms.filter { "veg_first" in it.e.meal.tags }, ms.filter { "veg_first" !in it.e.meal.tags }) { less, diff, enDiff, n ->
            if (less) trn(n, "Eating veg first, your rise is {1} smaller ({0} meal).", "Eating veg first, your rise is {1} smaller ({0} meals).", diff) to
                english(n, "Eating veg first, your rise is {1} smaller ({0} meal).", "Eating veg first, your rise is {1} smaller ({0} meals).", enDiff)
            else trn(n, "Eating veg first, your rise is {1} bigger ({0} meal).", "Eating veg first, your rise is {1} bigger ({0} meals).", diff) to
                english(n, "Eating veg first, your rise is {1} bigger ({0} meal).", "Eating veg first, your rise is {1} bigger ({0} meals).", enDiff)
        }
        compareRise(ms.filter { it.e.meal.speed == Gi.Speed.SLOW }, ms.filter { it.e.meal.speed == Gi.Speed.FAST }) { less, diff, enDiff, n ->
            if (less) trn(n, "Slow-carb meals rise {1} less than fast-carb ones for you ({0} slow-carb meal).", "Slow-carb meals rise {1} less than fast-carb ones for you ({0} slow-carb meals).", diff) to
                english(n, "Slow-carb meals rise {1} less than fast-carb ones for you ({0} slow-carb meal).", "Slow-carb meals rise {1} less than fast-carb ones for you ({0} slow-carb meals).", enDiff)
            else trn(n, "Slow-carb meals rise {1} more than fast-carb ones for you ({0} slow-carb meal).", "Slow-carb meals rise {1} more than fast-carb ones for you ({0} slow-carb meals).", diff) to
                english(n, "Slow-carb meals rise {1} more than fast-carb ones for you ({0} slow-carb meal).", "Slow-carb meals rise {1} more than fast-carb ones for you ({0} slow-carb meals).", enDiff)
        }
        compareRise(ms.filter { it.e.meal.hasSugaryDrink }, ms.filter { !it.e.meal.hasSugaryDrink }) { less, diff, enDiff, n ->
            if (less) trn(n, "Meals with a sugary drink rise {1} less for you ({0} meal).", "Meals with a sugary drink rise {1} less for you ({0} meals).", diff) to
                english(n, "Meals with a sugary drink rise {1} less for you ({0} meal).", "Meals with a sugary drink rise {1} less for you ({0} meals).", enDiff)
            else trn(n, "Meals with a sugary drink rise {1} more for you ({0} meal).", "Meals with a sugary drink rise {1} more for you ({0} meals).", diff) to
                english(n, "Meals with a sugary drink rise {1} more for you ({0} meal).", "Meals with a sugary drink rise {1} more for you ({0} meals).", enDiff)
        }
        // Fat and protein move the peak later rather than higher.
        val rich = ms.filter { it.e.meal.slowDigesting }
        val lean = ms.filter { !it.e.meal.slowDigesting && !it.e.meal.someFatProtein }
        if (rich.size >= minEach && lean.size >= minEach) {
            val later = Foods.median(rich.map { it.peakMin.toDouble() }) - Foods.median(lean.map { it.peakMin.toDouble() })
            if (later >= 25) {
                val mins = later.roundToInt() / 5 * 5
                out += Pattern(
                    trn(rich.size, "Fatty or protein-rich meals peak about {1} min later for you ({0} meal).", "Fatty or protein-rich meals peak about {1} min later for you ({0} meals).", mins),
                    true,
                    english(rich.size, "Fatty or protein-rich meals peak about {1} min later for you ({0} meal).", "Fatty or protein-rich meals peak about {1} min later for you ({0} meals).", mins),
                )
            }
        }
        // Alcohol: the next morning's fasting reading.
        val zone = ZoneId.systemDefault()
        fun nextFasting(e: MealEntry): Reading? {
            // A drink after midnight still belongs to that evening: the morning after is the same day.
            val day = Instant.ofEpochMilli(e.at - 6 * 3_600_000L).atZone(zone).toLocalDate().plusDays(1)
            return readings.firstOrNull { it.tag == MealTag.FASTING && it.date(zone) == day }
        }
        val drinksDays = meals.filter { "alcohol" in it.meal.tags }.mapNotNull { nextFasting(it) }.distinct()
        val dryDays = readings.filter { it.tag == MealTag.FASTING }.filter { f ->
            val prev = f.date(zone).minusDays(1)
            meals.none { "alcohol" in it.meal.tags && Instant.ofEpochMilli(it.at - 6 * 3_600_000L).atZone(zone).toLocalDate() == prev }
        }
        if (drinksDays.size >= minEach && dryDays.size >= minEach) {
            val diff = Foods.median(drinksDays.map { it.mmol }) - Foods.median(dryDays.map { it.mmol })
            val n = drinksDays.size
            if (abs(diff) >= 0.5) out += (if (diff < 0) Pattern(
                trn(n, "After an evening with alcohol, your fasting reading is {1} lower ({0} morning).", "After an evening with alcohol, your fasting reading is {1} lower ({0} mornings).", d(diff)),
                false,
                english(n, "After an evening with alcohol, your fasting reading is {1} lower ({0} morning).", "After an evening with alcohol, your fasting reading is {1} lower ({0} mornings).", dPlain(diff)),
            ) else Pattern(
                trn(n, "After an evening with alcohol, your fasting reading is {1} higher ({0} morning).", "After an evening with alcohol, your fasting reading is {1} higher ({0} mornings).", d(diff)),
                false,
                english(n, "After an evening with alcohol, your fasting reading is {1} higher ({0} morning).", "After an evening with alcohol, your fasting reading is {1} higher ({0} mornings).", dPlain(diff)),
            ))
        }
        return out
    }
}

/** What was eaten each day: carbs, sugars, fibre, protein and fat added up. */
object DayTotals {
    data class Totals(val day: LocalDate, val meals: Int, val carbs: Double, val sugars: Double?, val fibre: Double?, val protein: Double?, val fat: Double?)

    fun of(day: LocalDate, meals: List<MealEntry>): Totals {
        fun sum(f: (Meal) -> Double?): Double? = meals.mapNotNull { f(it.meal) }.takeIf { it.isNotEmpty() }?.let { Grams.round(it.sum()) }
        return Totals(day, meals.size, Grams.round(meals.sumOf { it.carbs }), sum { it.sugars }, sum { it.fibre }, sum { it.protein }, sum { it.fat })
    }

    fun byDay(meals: List<MealEntry>, zone: ZoneId): Map<LocalDate, Totals> =
        meals.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.mapValues { (d, ms) -> of(d, ms) }

    /** Average carbs a day over the 7 days up to [today] and the 7 before, counting only days with meals. */
    fun weekCarbs(meals: List<MealEntry>, today: LocalDate, zone: ZoneId): Pair<Int?, Int?> {
        val days = byDay(meals, zone)
        fun avg(from: Long, to: Long): Int? {
            val ds = (from..to).mapNotNull { days[today.minusDays(it)] }
            return if (ds.size < 3) null else (ds.sumOf { it.carbs } / ds.size).roundToInt()
        }
        return avg(0, 6) to avg(7, 13)
    }

    /** "142 g carbs · 38 g sugars · 22 g fibre · 85 g protein · 60 g fat" (missing ones left out). */
    fun text(t: Totals): String = listOfNotNull(
        tr("{0} g carbs", Grams.text(t.carbs)), t.sugars?.let { tr("{0} g sugars", Grams.text(it)) }, t.fibre?.let { tr("{0} g fibre", Grams.text(it)) },
        t.protein?.let { tr("{0} g protein", Grams.text(it)) }, t.fat?.let { tr("{0} g fat", Grams.text(it)) },
    ).joinToString(" · ")
}

/**
 * Everyday foods with their numbers for one usual portion, so a food added by hand gets its
 * nutrition straight away (the on-device model fills in anything not here). Typical values,
 * carbs counted the European way (without fibre).
 */
object Basics {
    private val table: List<MealItem> = listOf(
        MealItem("egg", "1 egg", carbs = 0.4, sugars = 0.4, fibre = 0.0, protein = 6.3, fat = 5.0),
        MealItem("bacon", "2 slices", carbs = 0.3, sugars = 0.0, fibre = 0.0, protein = 6.0, fat = 7.0),
        MealItem("sausage", "1 sausage", carbs = 2.0, sugars = 0.5, fibre = 0.2, protein = 7.0, fat = 13.0),
        MealItem("ham", "2 slices", carbs = 1.0, sugars = 0.8, fibre = 0.0, protein = 10.0, fat = 3.0),
        MealItem("chicken", "1 breast", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 31.0, fat = 4.0),
        MealItem("salmon", "1 fillet", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 25.0, fat = 13.0),
        MealItem("cod", "1 fillet", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 23.0, fat = 1.0),
        MealItem("tuna", "1 can", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 25.0, fat = 1.0),
        MealItem("lamb", "1 portion", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 25.0, fat = 18.0),
        MealItem("beef", "1 portion", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 26.0, fat = 15.0),
        MealItem("cheese", "1 slice", carbs = 0.1, sugars = 0.1, fibre = 0.0, protein = 5.0, fat = 6.0),
        MealItem("butter", "1 tsp", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 0.1, fat = 4.1),
        MealItem("avocado", "½ avocado", carbs = 1.9, sugars = 0.2, fibre = 4.6, protein = 1.4, fat = 10.5),
        MealItem("skyr", "1 cup", carbs = 6.8, sugars = 6.8, fibre = 0.0, protein = 19.0, fat = 0.3),
        MealItem("greek yogurt", "1 cup", carbs = 7.0, sugars = 7.0, fibre = 0.0, protein = 15.0, fat = 10.0),
        MealItem("milk", "1 glass", carbs = 11.8, sugars = 11.8, fibre = 0.0, protein = 8.0, fat = 8.0),
        MealItem("banana", "1 medium", carbs = 23.0, sugars = 12.0, fibre = 2.6, protein = 1.3, fat = 0.4),
        MealItem("apple", "1 medium", carbs = 19.0, sugars = 14.0, fibre = 3.3, protein = 0.4, fat = 0.3),
        MealItem("orange", "1 medium", carbs = 12.0, sugars = 9.0, fibre = 3.0, protein = 1.2, fat = 0.2),
        MealItem("rye bread", "1 slice", carbs = 22.0, sugars = 8.0, fibre = 3.0, protein = 3.0, fat = 1.0),
        MealItem("rúgbrauð", "1 slice", carbs = 22.0, sugars = 8.0, fibre = 3.0, protein = 3.0, fat = 1.0),
        MealItem("toast", "1 slice", carbs = 13.0, sugars = 1.5, fibre = 1.0, protein = 3.0, fat = 1.0),
        MealItem("bread", "1 slice", carbs = 13.0, sugars = 1.5, fibre = 1.0, protein = 3.0, fat = 1.0),
        MealItem("porridge", "1 bowl", carbs = 27.0, sugars = 1.0, fibre = 4.0, protein = 5.0, fat = 3.0),
        MealItem("oatmeal", "1 bowl", carbs = 27.0, sugars = 1.0, fibre = 4.0, protein = 5.0, fat = 3.0),
        MealItem("rice", "1 cup", carbs = 45.0, sugars = 0.1, fibre = 0.6, protein = 4.3, fat = 0.4),
        MealItem("pasta", "1 cup", carbs = 43.0, sugars = 0.8, fibre = 2.5, protein = 8.0, fat = 1.3),
        MealItem("potato", "1 medium", carbs = 30.0, sugars = 1.3, fibre = 2.2, protein = 3.0, fat = 0.2),
        MealItem("orange juice", "1 glass", carbs = 26.0, sugars = 21.0, fibre = 0.5, protein = 1.7, fat = 0.5),
        MealItem("cola", "1 can", carbs = 35.0, sugars = 35.0, fibre = 0.0, protein = 0.0, fat = 0.0, drink = true),
        MealItem("coffee", "1 cup", carbs = 0.0, sugars = 0.0, fibre = 0.0, protein = 0.3, fat = 0.0, drink = true),
        MealItem("tomato", "1 medium", carbs = 3.5, sugars = 2.6, fibre = 1.5, protein = 1.1, fat = 0.2),
        MealItem("cucumber", "½ cucumber", carbs = 3.6, sugars = 1.7, fibre = 0.5, protein = 0.7, fat = 0.1),
        MealItem("salad", "1 bowl", carbs = 3.0, sugars = 2.0, fibre = 2.0, protein = 1.5, fat = 0.3),
    ).plus(parse(MORE)).distinctBy { it.name }.sortedByDescending { it.name.length }

    private fun parse(rows: String): List<MealItem> = rows.lines().mapNotNull { l ->
        val f = l.trim().split('|')
        if (f.size < 7) return@mapNotNull null
        MealItem(f[0], f[1], carbs = f[2].toDouble(), sugars = f[3].toDouble(), fibre = f[4].toDouble(), protein = f[5].toDouble(), fat = f[6].toDouble(),
            drink = f.getOrNull(7) == "d")
    }

    /**
     * More everyday and Icelandic foods (name|portion|carbs|sugars|fibre|protein|fat|d for a drink),
     * typical values rounded, carbs without fibre.
     */
    private const val MORE = """
        flatkaka|1 piece|17|1|2.5|3|0.6
        kleina|1 piece|20|7|0.6|3|8
        croissant|1 croissant|26|6|1.5|5|12
        bagel|1 bagel|48|6|2.5|10|1.5
        baguette|1 slice|15|1|0.7|2.5|0.3
        pita|1 pita|33|1|1.3|5.5|0.8
        tortilla|1 wrap|30|1.5|1.5|5|4
        naan|1 piece|45|3|2|9|5
        hrökkbrauð|1 slice|7|0.3|1.7|1|0.2
        crispbread|1 slice|7|0.3|1.7|1|0.2
        rice cake|1 cake|7|0.1|0.4|0.7|0.3
        crackers|4 crackers|14|1|0.6|2|3
        bread roll|1 roll|28|2|1.5|5|1.5
        rúnstykki|1 roll|28|2|1.5|5|1.5
        muesli|1 bowl|33|8|4|5|3
        granola|1 bowl|30|10|3|5|8
        cornflakes|1 bowl|25|2.5|1|2|0.3
        cheerios|1 bowl|20|0.3|3|3|2
        overnight oats|1 jar|45|15|6|12|8
        couscous|1 cup|36|0.1|2|6|0.3
        quinoa|1 cup|34|1.6|5|8|3.5
        noodles|1 cup|40|0.6|1.8|7|2
        spaghetti|1 plate|65|1.5|3.5|12|2
        lasagne|1 portion|35|6|3|20|15
        pizza|1 slice|30|3|2|10|10
        burger|1 burger|35|6|2|25|20
        pylsa|1 hot dog|30|6|1|10|15
        hot dog|1 hot dog|30|6|1|10|15
        fries|1 portion|41|0.3|4|4|17
        crisps|1 bag|20|0.2|1.8|2.5|13
        popcorn|1 bowl|15|0.2|3|3|5
        sweet potato|1 medium|24|5|4|2|0.1
        corn|1 cob|17|5|2|3|1.5
        peas|½ cup|8|4|4|4|0.3
        carrot|1 medium|6|3|2|0.6|0.1
        broccoli|1 cup|4|1.5|2.4|2.5|0.3
        beans|½ cup|14|0.3|6|7|0.5
        baked beans|½ can|21|7|6|7|0.5
        lentils|1 cup|30|3.6|16|18|0.8
        chickpeas|½ cup|18|4|6|7|2
        hummus|2 spoons|4|0.3|2|2.5|5
        plokkfiskur|1 portion|20|2|1.5|22|14
        fiskibollur|4 balls|14|1|0.5|12|8
        harðfiskur|1 portion|0|0|0|25|1
        hangikjöt|2 slices|1|1|0|10|5
        kjötsúpa|1 bowl|20|6|3|20|10
        fiskisúpa|1 bowl|12|4|1|15|9
        fish soup|1 bowl|12|4|1|15|9
        soup|1 bowl|15|4|2|6|5
        shrimp|1 portion|0|0|0|20|1
        rækjur|1 portion|0|0|0|20|1
        pork chop|1 chop|0|0|0|30|12
        meatballs|5 balls|8|2|0.5|15|14
        kjötbollur|5 balls|8|2|0.5|15|14
        turkey|2 slices|1|1|0|9|1
        tofu|1 portion|2|0.5|1|12|7
        scrambled eggs|2 eggs|1.5|1|0|13|14
        omelette|2 eggs|1.5|1|0|13|15
        vanilla skyr|1 cup|17|16|0|17|0.3
        berry skyr|1 cup|18|17|0.5|17|0.3
        ab mjólk|1 cup|10|10|0|8|3
        súrmjólk|1 cup|10|10|0|8|3
        yogurt|1 cup|10|10|0|7|6
        kókómjólk|1 carton|25|24|0|8|5|d
        cream cheese|2 spoons|1|1|0|2|10
        ice cream|2 scoops|24|21|0.7|3.5|11
        pear|1 medium|22|17|5|0.6|0.2
        grapes|1 cup|27|23|1.4|1|0.2
        strawberries|1 cup|8|7|3|1|0.4
        blueberries|1 cup|21|15|3.6|1|0.5
        raspberries|1 cup|7|5|8|1.5|0.8
        mango|1 cup|25|23|2.6|1.4|0.6
        pineapple|1 cup|20|16|2.3|0.9|0.2
        watermelon|1 wedge|21|17|1|1.7|0.4
        kiwi|1 kiwi|9|6.5|2|0.8|0.4
        clementine|1 clementine|9|7|1.3|0.6|0.1
        mandarin|1 mandarin|9|7|1.3|0.6|0.1
        peach|1 peach|13|11|2|1.4|0.4
        raisins|1 small box|22|18|1.2|1|0.1
        dates|3 dates|30|28|3|1|0.1
        chocolate|1 bar|28|26|1.5|3.5|15
        milk chocolate|1 bar|28|26|1.5|3.5|15
        dark chocolate|4 squares|13|9|4|3|17
        lakkrís|1 bag|38|25|0.3|2|0.2
        liquorice|1 bag|38|25|0.3|2|0.2
        sweets|1 handful|36|28|0|0|0
        candy|1 handful|36|28|0|0|0
        cookie|2 cookies|20|10|0.8|1.5|7
        cake|1 slice|50|35|1|5|15
        muffin|1 muffin|50|28|1.5|6|20
        doughnut|1 doughnut|25|10|1|3.5|12
        donut|1 donut|25|10|1|3.5|12
        pancakes|2 pancakes|28|5|1|6|7
        waffle|1 waffle|25|5|1|6|10
        cinnamon bun|1 bun|45|18|2|6|14
        snúður|1 bun|45|18|2|6|14
        honey|1 tsp|6|6|0|0|0
        jam|1 spoon|13|12|0.2|0|0
        sugar|1 tsp|4|4|0|0|0
        nutella|1 spoon|11|10|0.6|1.2|6
        peanut butter|1 spoon|3|1|1|4|8
        nuts|1 handful|4|1.2|2.7|6|15
        almonds|1 handful|3|1.2|3.5|6|15
        latte|1 cup|13|13|0|8|7|d
        cappuccino|1 cup|8|8|0|5|5|d
        tea|1 cup|0|0|0|0|0|d
        juice|1 glass|26|22|0.4|1|0.3|d
        apple juice|1 glass|28|26|0.5|0.2|0.3|d
        smoothie|1 glass|38|30|3|3|1|d
        appelsín|1 can|36|36|0|0|0|d
        malt|1 can|30|23|0|1|0|d
        energy drink|1 can|27|27|0|0|0|d
        sports drink|1 bottle|30|30|0|0|0|d
        beer|1 can|12|0|0|1.5|0|d
        bjór|1 can|12|0|0|1.5|0|d
        wine|1 glass|4|1|0|0|0|d
        milkshake|1 cup|60|50|1|10|12|d
        hot chocolate|1 cup|25|24|1|8|6|d
        oat milk|1 glass|16|10|2|1|3.5|d
        caesar salad|1 bowl|10|3|2|15|20
        sushi|8 pieces|56|8|2|14|4
        chicken wrap|1 wrap|38|3|3|24|12
        kebab|1 portion|45|5|3|28|25
        taco|1 taco|13|1|2|8|8
        curry|1 portion|12|6|3|25|18
        chili con carne|1 bowl|20|6|7|20|12
        ketchup|1 spoon|4|3.7|0|0.2|0
        mayonnaise|1 spoon|0.1|0.1|0|0.1|10
        remúlaði|1 spoon|1.5|1|0|0.1|8
        gravy|1 ladle|3|0.5|0|1|2
        sósa|1 ladle|3|0.5|0|1|2
        grjónagrautur|1 bowl|40|14|0.5|9|6
        laufabrauð|1 piece|11|0.3|0.5|2|5
        liver sausage|1 slice|8.5|0.5|0.5|5.5|8
        blood pudding|1 slice|11|0.5|0.5|4|6.5
        danish pastry|1 piece|30|12|1|4|15
        caramelised potatoes|1 portion|38|14|2|2.5|4
    """

    /**
     * The basic food named in [name] ("hard boiled egg" → egg, "soðnar kartöflur" → potato), at
     * its usual portion, or null.
     */
    fun find(name: String): MealItem? {
        match(name)?.let { return it }
        return FoodNames.english(name)?.let { match(it) }
    }

    private fun match(name: String): MealItem? {
        val n = " " + name.lowercase(Locale.ROOT) + " "
        return table.firstOrNull { b -> Regex("(?<![\\p{L}])${Regex.escape(b.name)}(e?s)?(?![\\p{L}])").containsMatchIn(n) }
    }
}
