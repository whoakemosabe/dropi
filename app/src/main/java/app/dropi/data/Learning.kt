package app.dropi.data

import kotlin.math.abs

/**
 * What Dropi has learnt from the person's own meals, and how well its guesses have done.
 *
 * - Rise per gram of carbs, overall and for breakfast, lunch and dinner on their own (many people
 *   rise more after the same food at breakfast).
 * - Their own peak time after eating, from meals with enough values after them (a sensor). Used
 *   for the expected curve and, unless turned off, for when the after-eating check is due.
 * - Hindcasts: for each past meal, what Dropi would have expected using only the meals before it,
 *   next to what actually happened. That gives the track record and the likely range.
 *
 * Everything here is an estimate from the person's own readings, never advice.
 */
object Learning {
    private const val MIN = 60_000L

    /** Meals needed before a meal-time factor of its own is used (otherwise the overall one). */
    const val MIN_BY_NAME = 4

    /** Meals with a clear peak needed before the person's own peak time is used. */
    const val MIN_PEAK_MEALS = 3

    /** Guesses checked before a track record and a range are shown. */
    const val MIN_TRACK = 5

    /** What a guess is compared over: "within 1 mmol/L" counts as close. */
    const val CLOSE = 1.0

    /**
     * The peak time learnt from the person's meals, for code with no screen (reminders). Set
     * whenever the log is worked out, and kept in prefs so reminders have it after a restart.
     * Null until there's enough to go on, or when the person chose their own set time.
     */
    @Volatile var peakForChecks: Int? = null

    /** One past meal: what was expected (from the meals before it) and what happened. */
    data class Hindcast(val entryId: Long, val at: Long, val name: String, val start: Double, val expected: Double, val actual: Double) {
        val error: Double get() = actual - expected
        val close: Boolean get() = abs(error) <= CLOSE
    }

    data class Model(
        val perGram: Double?,
        /** Breakfast / Lunch / Dinner (English names) → mmol per gram, with how many meals it's from. */
        val byName: Map<String, Pair<Double, Int>>,
        /** Meals behind [perGram]. */
        val perGramMeals: Int,
        /** Own peak time in minutes after eating, and from how many meals. */
        val peakMin: Int?,
        val peakMeals: Int,
        val hindcasts: List<Hindcast>,
    ) {
        /** The recent guesses the track record is about (newest last). */
        val recent: List<Hindcast> get() = hindcasts.takeLast(20)

        /** How far off guesses usually are: 4 in 5 were within this (mmol/L); null until there are enough. */
        val spread: Double?
            get() = recent.takeIf { it.size >= MIN_TRACK }?.map { abs(it.error) }?.sorted()?.let { e ->
                e[((e.size - 1) * 0.8).toInt()].coerceIn(0.5, 4.0)
            }

        /** How many of the recent guesses were within [CLOSE]. */
        val closeCount: Int get() = recent.count { it.close }

        /** The usual miss (median of the absolute errors). */
        val typicalMiss: Double? get() = recent.takeIf { it.size >= MIN_TRACK }?.let { Foods.median(it.map { h -> abs(h.error) }) }

        /** Whether the guesses tend to come out low or high (median signed error), when clear. */
        val bias: Double? get() = recent.takeIf { it.size >= MIN_TRACK }?.let { Foods.median(it.map { h -> h.error }) }?.takeIf { abs(it) >= 0.5 }

        fun hindcast(entryId: Long): Hindcast? = hindcasts.lastOrNull { it.entryId == entryId }

        companion object {
            val EMPTY = Model(null, emptyMap(), 0, null, 0, emptyList())
        }
    }

    private fun ratios(measured: List<Foods.Meal>, byId: Map<Long, MealEntry>, name: String? = null): List<Double> =
        measured.mapNotNull { m ->
            val e = byId[m.entryId] ?: return@mapNotNull null
            if (name != null && e.name != name) return@mapNotNull null
            val rise = m.rise ?: return@mapNotNull null
            if (e.carbs < 15) null else rise.coerceAtLeast(0.0) / e.carbs
        }

    /**
     * mmol per gram for a meal called [name]: its own factor with enough meals of that name,
     * else the overall one ([MealCurve.perGram]'s rule: four meals of 15 g or more).
     */
    fun perGramFor(measured: List<Foods.Meal>, meals: List<MealEntry>, name: String?): Double? {
        val byId = meals.associateBy { it.id }
        if (name != null && name in MealEntry.NAMES && name != "Snack") {
            val own = ratios(measured, byId, name)
            if (own.size >= MIN_BY_NAME) return Foods.median(own).coerceIn(0.015, 0.15)
        }
        val all = ratios(measured, byId)
        return if (all.size < 4) null else Foods.median(all).coerceIn(0.015, 0.15)
    }

    /**
     * The person's usual peak time: from meals with at least six values 20 min–4 h after eating
     * (in practice a sensor), the minute of the highest one, median over [MIN_PEAK_MEALS]+ meals,
     * rounded to 5 minutes. Finger-pricks alone are too far apart to find a peak.
     */
    fun peak(measured: List<Foods.Meal>): Pair<Int, Int>? {
        val peaks = measured.mapNotNull { m ->
            val pts = m.after.filter { it.first in 20..240 }
            if (pts.size < 6) return@mapNotNull null
            val best = pts.maxBy { it.second }
            // A meal that barely moved has no peak worth timing.
            if (best.second < 0.8) null else best.first.toDouble()
        }
        if (peaks.size < MIN_PEAK_MEALS) return null
        val p = (Foods.median(peaks) / 5).let { Math.round(it) * 5 }.toInt().coerceIn(30, 180)
        return p to peaks.size
    }

    /**
     * How the sensor compares with the meter: finger-pricks with a sensor value within 5 minutes,
     * over the last 14 days. Returns the middle difference (sensor minus meter, mmol/L) and the
     * number of pairs, or null with fewer than five.
     */
    fun sensorOffset(readings: List<Reading>, sensor: List<SensorPoint>, now: Long): Pair<Double, Int>? {
        if (sensor.isEmpty()) return null
        val times = LongArray(sensor.size) { sensor[it].at }
        val diffs = readings.filter { it.at >= now - 14 * 1440 * MIN }.mapNotNull { r ->
            var lo = 0; var hi = times.size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (times[mid] < r.at) lo = mid + 1 else hi = mid }
            val near = listOfNotNull(sensor.getOrNull(lo), sensor.getOrNull(lo - 1)).minByOrNull { abs(it.at - r.at) }
            near?.takeIf { abs(it.at - r.at) <= 5 * MIN }?.let { it.mmol - r.mmol }
        }
        return if (diffs.size < 5) null else Foods.median(diffs) to diffs.size
    }

    /**
     * Everything at once, from the meals measured in AppState. Hindcasts cover the last 60 meals
     * with an outcome, each predicted only from the meals eaten before it.
     */
    fun build(measured: List<Foods.Meal>, meals: List<MealEntry>): Model {
        val byId = meals.associateBy { it.id }
        val all = ratios(measured, byId)
        val byName = listOf("Breakfast", "Lunch", "Dinner").mapNotNull { n ->
            val r = ratios(measured, byId, n)
            if (r.size >= MIN_BY_NAME) n to (Foods.median(r).coerceIn(0.015, 0.15) to r.size) else null
        }.toMap()
        val peak = peak(measured)
        val ordered = measured.filter { it.rise != null }.sortedBy { it.at }
        val hind = ArrayList<Hindcast>()
        for (m in ordered.takeLast(60)) {
            val e = byId[m.entryId] ?: continue
            if (e.meal.items.isEmpty() || e.carbs <= 0) continue
            // Only what was known before this meal: meals eaten at least an hour earlier.
            val before = measured.filter { it.at < m.at - 60 * MIN }
            val pg = perGramFor(before, meals, e.name)
            val est = MealHistory.estimate(m.start, e.foods, before)
            val curve = MealCurve.predict(e.meal, pg, est, peak(before)?.first) ?: continue
            hind += Hindcast(e.id, m.at, e.name, m.start, m.start + curve.rise, m.start + (m.rise ?: continue))
        }
        return Model(
            perGram = if (all.size < 4) null else Foods.median(all).coerceIn(0.015, 0.15),
            byName = byName,
            perGramMeals = all.size,
            peakMin = peak?.first,
            peakMeals = peak?.second ?: 0,
            hindcasts = hind,
        )
    }
}
