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

/** One blood glucose reading. [mmol] is exact (mg/dL entries are converted, not rounded). */
data class Reading(
    val id: Long = 0,
    val at: Long,
    val mmol: Double,
    val tag: MealTag,
    val note: String = "",
    /** "" when typed in Dropi; "hc:<record id>" when it came in from Health Connect. */
    val source: String = "",
) {
    val imported get() = source.isNotEmpty()

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
