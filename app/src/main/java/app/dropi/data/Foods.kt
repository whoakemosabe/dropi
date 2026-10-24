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
        s.replace('|', ' ').trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(40)

    fun display(s: String): String = s.replaceFirstChar { it.titlecase(Locale.ROOT) }

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
        val sorted = readings.sortedBy { it.at }
        val sensorSorted = sensor.sortedBy { it.at }
        val ordered = entries.filter { it.foods.isNotEmpty() }.sortedBy { it.at }
        return ordered.mapIndexedNotNull { i, e ->
            val start = MealLink.before(e, sorted) ?: return@mapIndexedNotNull null
            val nextMeal = ordered.drop(i + 1).firstOrNull { it.at > e.at + 30 * MIN }?.at ?: Long.MAX_VALUE
            val end = minOf(e.at + 240 * MIN, nextMeal)
            val from = maxOf(e.at, start.at)
            val after = ArrayList<Pair<Int, Double>>()
            for (x in sorted) {
                if (x.at <= from || x.at > end || x.id == start.id) continue
                after += ((x.at - e.at) / MIN).toInt() to (x.mmol - start.mmol)
            }
            for (p in sensorSorted) {
                if (p.at <= from || p.at > end) continue
                after += ((p.at - e.at) / MIN).toInt() to (p.mmol - start.mmol)
            }
            Meal(e.at, start.mmol, e.foods, after.sortedBy { it.first }, e.id, start.at)
        }
    }

    /** Every food with at least one measured meal, gentlest first. */
    fun scores(readings: List<Reading>, sensor: List<SensorPoint>, entries: List<MealEntry>): List<Score> {
        val byFood = HashMap<String, MutableList<Meal>>()
        for (m in meals(readings, sensor, entries)) {
            if (m.rise == null) continue
            for (f in m.foods.distinct()) byFood.getOrPut(f) { ArrayList() } += m
        }
        return byFood.map { (food, ms) ->
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

    /** A change in the person's unit with its sign: "+2.4", "−0.3", "+43". */
    fun delta(mmol: Double, unit: GlucoseUnit): String {
        val v = if (unit == GlucoseUnit.MGDL) (mmol * Glucose.MGDL_PER_MMOL).roundToInt().toDouble() else Math.round(mmol * 10) / 10.0
        val body = if (unit == GlucoseUnit.MGDL) "${kotlin.math.abs(v).toInt()}" else String.format(Locale.ROOT, "%.1f", kotlin.math.abs(v))
        return when {
            v > 0 -> "+$body"
            v < 0 -> "−$body"
            else -> body
        }
    }
}
