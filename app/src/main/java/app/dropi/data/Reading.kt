package app.dropi.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The moment a reading was taken. Saved by name in the database, so names never change. */
enum class MealTag(val label: String, val short: String) {
    FASTING("Fasting", "Fasting"),
    BEFORE_MEAL("Before meal", "Before"),
    AFTER_MEAL("After meal", "After"),
    BEDTIME("Bedtime", "Bedtime"),
    OVERNIGHT("Overnight", "Night"),
    OTHER("Other", "Other");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name == k } ?: OTHER
    }
}

/** What kind of insulin was taken; logged as given, never suggested. */
enum class InsulinKind(val label: String) {
    RAPID("Rapid"),
    LONG("Long"),
    MIXED("Mixed");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name == k }
    }
}

/** One blood glucose reading. [mmol] is exact (mg/dL entries are converted, not rounded). */
data class Reading(
    val id: Long = 0,
    val at: Long,
    val mmol: Double,
    val tag: MealTag,
    val note: String = "",
    /** "" when typed in Dropi; "hc:<record id>" when it came in from Health Connect. */
    val source: String = "",
    /** Optional details: carbs eaten (g), insulin taken (units), tablets taken, exercise (minutes). */
    val carbs: Int? = null,
    val insulin: Double? = null,
    val insulinKind: InsulinKind? = null,
    val meds: Boolean = false,
    val exercise: Int? = null,
    /** What was eaten with this reading ("porridge", "banana"), lower case; for food scores. */
    val foods: List<String> = emptyList(),
    /** The foods with their portions and numbers, when they came from the meal screen. */
    val meal: Meal? = null,
) {
    val imported get() = source.isNotEmpty()
    val hasDetails get() = carbs != null || insulin != null || meds || exercise != null || foods.isNotEmpty()

    /** "45 g carbs · 4 u rapid · tablets · 30 min exercise", or "" with none. */
    fun detailsText(): String = listOfNotNull(
        foods.takeIf { it.isNotEmpty() }?.joinToString(", ") { Foods.display(it) },
        carbs?.let { "$it g carbs" },
        insulin?.let { u -> "${trim(u)} u" + (insulinKind?.let { " ${it.label.lowercase()}" } ?: "") },
        if (meds) "tablets" else null,
        exercise?.let { "$it min exercise" },
    ).joinToString(" · ")

    private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else java.lang.String.format(java.util.Locale.ROOT, "%.1f", d)

    fun date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

object Tagging {
    private const val MIN = 60_000L

    /**
     * A sensible tag for a reading taken at [at], so most of the time it's already right:
     * - within 30 min–3 h of a "before meal" reading → after meal
     * - 00:00–04:59 → overnight
     * - 05:00–10:59 and nothing logged yet today → fasting
     * - 21:00 onwards → bedtime
     * - otherwise → before meal
     */
    fun suggest(at: Long, recent: List<Reading>, zone: ZoneId): MealTag {
        val previous = recent.filter { it.at < at }.maxByOrNull { it.at }
        if (previous != null && previous.tag == MealTag.BEFORE_MEAL) {
            val gap = at - previous.at
            if (gap in 30 * MIN..180 * MIN) return MealTag.AFTER_MEAL
        }
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = t.toLocalDate()
        val loggedToday = recent.any { it.at < at && it.date(zone) == today && it.tag != MealTag.OVERNIGHT }
        return when (t.hour) {
            in 0..4 -> MealTag.OVERNIGHT
            in 5..10 -> if (loggedToday) MealTag.BEFORE_MEAL else MealTag.FASTING
            in 21..23 -> MealTag.BEDTIME
            else -> MealTag.BEFORE_MEAL
        }
    }
}
