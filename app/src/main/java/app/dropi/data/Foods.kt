package app.dropi.data

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Food scores: how much each food tends to lift the person's own numbers.
 *
 * A meal is a logged meal entry. Its starting point is the reading just before it (see
 * [MealLink.before]). What follows, from readings and sensor points up to 4 hours after eating
 * or until the next meal, gives the rise: the highest value 45 min–3 h after, minus the start. A food's score is the median rise of the
 * meals it was part of.
 */
object Foods {
    private const val MIN = 60_000L

    /** Meals needed before a food gets a ranking (one meal is a story, two a pattern). */
    const val MIN_MEALS = 2

    fun normalize(s: String): String =
        s.replace('|', ' ').trim().lowercase(Locale.ROOT).replace(SPACES, " ").take(40)

    private val SPACES = Regex("\\s+")

    fun display(s: String): String = s.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /**
     * The food a name stands for, so similar names share what's learnt: "white rice", "rice" and
     * "hrísgrjón" all count as rice. Only short names are pooled (up to two words, the basic food
     * one of them), so "banana bread" stays its own food. Cached: it's asked for every food of
     * every meal on each reload.
     */
    fun key(name: String): String {
        val n = normalize(name)
        keyCache[n]?.let { return it }
        val basic = try { Basics.find(n)?.name } catch (e: Exception) { null }
        val words = n.split(' ').filter { it.isNotEmpty() }
        val k = when {
            basic == null -> n
            words.size <= 2 && (words.any { it == basic || it == basic + "s" || it == basic + "es" } || FoodNames.english(n) != null) -> basic
            else -> n
        }
        if (keyCache.size > 2000) keyCache.clear()
        keyCache[n] = k
        return k
    }

    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun pack(foods: List<String>): String = foods.map(::normalize).filter { it.isNotEmpty() }.distinct().joinToString("|")

    fun unpack(s: String?): List<String> =
        s.orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * One measured meal: when it was eaten, its starting value (and that reading's time), the
     * foods, and how things moved after it (minutes after eating, change from the start).
     */
    data class Meal(
        val at: Long, val start: Double, val foods: List<String>, val after: List<Pair<Int, Double>>,
        val entryId: Long = 0, val startAt: Long = at,
    ) {
        /** The biggest rise 45 min–3 h after, or null with nothing in that window. */
        val rise: Double? get() = after.filter { it.first in 45..180 }.maxOfOrNull { it.second }
    }

    data class Score(
        val food: String,
        val meals: Int,
        /** Median rise (mmol/L) across its meals. */
        val rise: Double,
        /** Median change in each half hour after eating, starting at (0, 0). */
        val curve: List<Pair<Int, Double>>,
        /** Every point that went into it, for the dots behind the curve. */
        val points: List<Pair<Int, Double>>,
    ) {
        val ranked get() = meals >= MIN_MEALS
    }

    /**
     * The meals with a starting reading, and what followed each: readings and sensor points from
     * eating until 4 hours later or the next meal, whichever comes first.
     */
    fun meals(readings: List<Reading>, sensor: List<SensorPoint>, entries: List<MealEntry>): List<Meal> {
        // The log comes from the database oldest first; only sort what isn't already in order.
        val sorted = if (readings.zipWithNext().all { (a, b) -> a.at <= b.at }) readings else readings.sortedBy { it.at }
        val sensorSorted = if (sensor.zipWithNext().all { (a, b) -> a.at <= b.at }) sensor else sensor.sortedBy { it.at }
        val ordered = entries.filter { it.foods.isNotEmpty() }.sortedBy { it.at }
        val readingTimes = LongArray(sorted.size) { sorted[it].at }
        val sensorTimes = LongArray(sensorSorted.size) { sensorSorted[it].at }
        // First index with a time after [t].
        fun after(times: LongArray, t: Long): Int {
            var lo = 0; var hi = times.size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (times[mid] <= t) lo = mid + 1 else hi = mid }
            return lo
        }
        return ordered.mapIndexedNotNull { i, e ->
            val start = MealLink.before(e, sorted) ?: return@mapIndexedNotNull null
            var nextMeal = Long.MAX_VALUE
            for (k in i + 1 until ordered.size) if (ordered[k].at > e.at + 30 * MIN) { nextMeal = ordered[k].at; break }
            val end = minOf(e.at + 240 * MIN, nextMeal)
            val from = maxOf(e.at, start.at)
            val out = ArrayList<Pair<Int, Double>>()
            var k = after(readingTimes, from)
            while (k < sorted.size && sorted[k].at <= end) {
                val x = sorted[k++]
                if (x.id != start.id) out += ((x.at - e.at) / MIN).toInt() to (x.mmol - start.mmol)
            }
            k = after(sensorTimes, from)
            while (k < sensorSorted.size && sensorSorted[k].at <= end) {
                val p = sensorSorted[k++]
                out += ((p.at - e.at) / MIN).toInt() to (p.mmol - start.mmol)
            }
            Meal(e.at, start.mmol, e.foods, out.sortedBy { it.first }, e.id, start.at)
        }
    }

    /** Every food with at least one measured meal, gentlest first. */
    fun scores(readings: List<Reading>, sensor: List<SensorPoint>, entries: List<MealEntry>): List<Score> =
        scores(meals(readings, sensor, entries))

    /** The same, from meals already measured with [meals]. */
    fun scores(measured: List<Meal>): List<Score> {
        // Grouped by the food each name stands for ([key]), shown under the name used most.
        val byFood = HashMap<String, MutableList<Meal>>()
        val names = HashMap<String, MutableMap<String, Int>>()
        for (m in measured) {
            if (m.rise == null) continue
            for (f in m.foods.map { it to key(it) }.distinctBy { it.second }) {
                byFood.getOrPut(f.second) { ArrayList() } += m
                names.getOrPut(f.second) { HashMap() }.merge(f.first, 1, Int::plus)
            }
        }
        return byFood.map { (k, ms) ->
            val food = names[k]?.maxByOrNull { it.value }?.key ?: k
            val points = ms.flatMap { it.after }.filter { it.first <= 240 }
            val curve = ArrayList<Pair<Int, Double>>()
            curve += 0 to 0.0
            for (bin in 0 until 8) {
                val inBin = points.filter { it.first > bin * 30 && it.first <= (bin + 1) * 30 }.map { it.second }
                if (inBin.isNotEmpty()) curve += (bin * 30 + 15) to median(inBin)
            }
            Score(food, ms.size, median(ms.mapNotNull { it.rise }), curve, points)
        }.sortedWith(compareBy<Score> { it.rise }.thenBy { it.food })
    }

    /** The foods logged most often lately, for one-tap picking. */
    fun recent(entries: List<MealEntry>, limit: Int = 10): List<String> =
        entries.sortedByDescending { it.at }.take(300).flatMap { it.foods }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }

    fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** A change in the person's unit with its sign: "+2.4" ("+2,4" in Icelandic), "−0.3", "+43". [plain]: always a point (prompts, CSV). */
    fun delta(mmol: Double, unit: GlucoseUnit, plain: Boolean = false): String {
        val v = if (unit == GlucoseUnit.MGDL) (mmol * Glucose.MGDL_PER_MMOL).roundToInt().toDouble() else Math.round(mmol * 10) / 10.0
        val body = if (unit == GlucoseUnit.MGDL) "${kotlin.math.abs(v).toInt()}"
            else String.format(Locale.ROOT, "%.1f", kotlin.math.abs(v)).let { if (plain) it else it.replace('.', app.dropi.L.decimal) }
        return when {
            v > 0 -> "+$body"
            v < 0 -> "−$body"
            else -> body
        }
    }
}
