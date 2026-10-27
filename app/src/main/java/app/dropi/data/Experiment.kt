package app.dropi.data

import app.dropi.tr
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Try-it weeks: the person tries one change for a few days, and Dropi compares the meals in that
 * stretch with their usual meals from the four weeks before. Honest by design: it says "no clear
 * difference" unless the gap is 0.8 mmol/L or more with at least three meals on each side.
 */
object Experiment {
    private const val DAY = 86_400_000L
    const val DAYS = 5
    const val MIN_EACH = 3
    const val CLEAR = 0.8

    enum class Kind(val key: String) {
        WALK("walk"), VEG_FIRST("veg_first"), NO_SUGARY_DRINKS("no_drinks"), EARLY_DINNER("early_dinner");

        val title: String get() = when (this) {
            WALK -> tr("A 10-minute walk after eating")
            VEG_FIRST -> tr("Veg first at lunch and dinner")
            NO_SUGARY_DRINKS -> tr("No sugary drinks with meals")
            EARLY_DINNER -> tr("Dinner before 19:00")
        }

        /** What to do while it runs, in one line. */
        val how: String get() = when (this) {
            WALK -> tr("Walk for about 10 minutes soon after a meal, and tick \"Walked after\" on the meal (or turn on steps in Settings).")
            VEG_FIRST -> tr("Start lunch and dinner with the vegetables, and tick \"Veg first\" on the meal.")
            NO_SUGARY_DRINKS -> tr("Water, or a zero or diet drink, with your meals instead of juice or soda.")
            EARLY_DINNER -> tr("Eat dinner before 19:00, and log it as usual.")
        }

        companion object {
            fun of(key: String?): Kind? = entries.firstOrNull { it.key == key }
        }
    }

    data class Running(val kind: Kind, val start: Long) {
        val end: Long get() = start + DAYS * DAY
        fun day(now: Long): Int = ((now - start) / DAY).toInt().coerceIn(0, DAYS - 1) + 1
        fun done(now: Long): Boolean = now >= end
    }

    /** The outcome: middle rise with the change and as usual, and how many meals each. */
    data class Result(val tried: Double?, val usual: Double?, val triedMeals: Int, val usualMeals: Int) {
        /** Smaller (negative) or bigger rise with the change, when the gap is clear; null when it isn't (yet). */
        val clearDiff: Double?
            get() = if (tried == null || usual == null || triedMeals < MIN_EACH || usualMeals < MIN_EACH) null
            else (tried - usual).takeIf { abs(it) >= CLEAR }
        val enough: Boolean get() = triedMeals >= MIN_EACH && usualMeals >= MIN_EACH
    }

    /**
     * Which meals count as having tried the change, during the stretch: tagged (walk, veg first),
     * or simply eaten then (no sugary drinks, early dinner). Before it, the baseline is the meals
     * of the same kind without the change.
     */
    fun result(
        r: Running, measured: List<Foods.Meal>, meals: List<MealEntry>, zone: ZoneId,
        steps: List<Span> = emptyList(), now: Long = System.currentTimeMillis(),
    ): Result {
        val byId = meals.associateBy { it.id }
        val baselineFrom = r.start - 28 * DAY
        val endAt = minOf(r.end, now)
        fun hour(at: Long) = Instant.ofEpochMilli(at).atZone(zone).let { it.hour + it.minute / 60.0 }
        fun walked(e: MealEntry) = "walk_after" in e.meal.tags ||
            (steps.isNotEmpty() && Learning.stepsBetween(steps, e.at, e.at + 60 * 60_000L) >= Learning.WALK_STEPS)
        fun inScope(e: MealEntry) = when (r.kind) {
            Kind.VEG_FIRST -> e.name == "Lunch" || e.name == "Dinner"
            Kind.EARLY_DINNER -> e.name == "Dinner"
            else -> true
        }
        fun tried(e: MealEntry) = when (r.kind) {
            Kind.WALK -> walked(e)
            Kind.VEG_FIRST -> "veg_first" in e.meal.tags
            Kind.NO_SUGARY_DRINKS -> !e.meal.hasSugaryDrink
            Kind.EARLY_DINNER -> hour(e.at) < 19.0
        }
        val during = ArrayList<Double>(); val before = ArrayList<Double>()
        for (m in measured) {
            val e = byId[m.entryId] ?: continue
            val rise = m.rise ?: continue
            if (!inScope(e)) continue
            when (m.at) {
                in r.start until endAt -> if (tried(e)) during += rise
                // The usual way: meals without the change. For drinks, every usual meal (most have none anyway).
                in baselineFrom until r.start -> if (r.kind == Kind.NO_SUGARY_DRINKS || !tried(e)) before += rise
            }
        }
        return Result(
            during.takeIf { it.isNotEmpty() }?.let { Foods.median(it) },
            before.takeIf { it.isNotEmpty() }?.let { Foods.median(it) },
            during.size, before.size,
        )
    }
}
