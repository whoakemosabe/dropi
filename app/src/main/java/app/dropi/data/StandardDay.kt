package app.dropi.data

import app.dropi.tr
import java.time.ZoneId

/**
 * Standard day: a period's finger-prick readings grouped by check (on waking, after breakfast,
 * before lunch…) rather than by clock hour, so it works with a few checks a day. Each group gives
 * its usual (middle) value, the middle half of its checks, and its lowest and highest.
 *
 * Before/after readings take the meal they're linked to (see [MealLink]); without one, the meal
 * whose time window they fall in. "Other" readings and snacks are left out.
 */
object StandardDay {
    enum class Block(val tag: MealTag) {
        WAKING(MealTag.FASTING),
        BEFORE_BREAKFAST(MealTag.BEFORE_MEAL), AFTER_BREAKFAST(MealTag.AFTER_MEAL),
        BEFORE_LUNCH(MealTag.BEFORE_MEAL), AFTER_LUNCH(MealTag.AFTER_MEAL),
        BEFORE_DINNER(MealTag.BEFORE_MEAL), AFTER_DINNER(MealTag.AFTER_MEAL),
        BEDTIME(MealTag.BEDTIME),
        NIGHT(MealTag.OVERNIGHT);

        val label: String get() = when (this) {
            WAKING -> tr("Waking")
            BEFORE_BREAKFAST -> tr("Before breakfast")
            AFTER_BREAKFAST -> tr("After breakfast")
            BEFORE_LUNCH -> tr("Before lunch")
            AFTER_LUNCH -> tr("After lunch")
            BEFORE_DINNER -> tr("Before dinner")
            AFTER_DINNER -> tr("After dinner")
            BEDTIME -> tr("Bedtime")
            NIGHT -> tr("Overnight")
        }
    }

    /** One check's spread: middle value, middle half (25–75 %), lowest and highest. */
    data class Group(val block: Block, val count: Int, val median: Double, val q1: Double, val q3: Double, val low: Double, val high: Double)

    /** A group needs this many checks before it's shown. */
    const val MIN_CHECKS = 3

    private const val HOUR = 3_600_000L

    fun blockOf(r: Reading, meals: List<MealEntry>, readings: List<Reading>, zone: ZoneId): Block? = when (r.tag) {
        MealTag.FASTING -> Block.WAKING
        MealTag.BEDTIME -> Block.BEDTIME
        MealTag.OVERNIGHT -> Block.NIGHT
        MealTag.BEFORE_MEAL -> {
            val name = MealLink.mealAfter(r, meals, readings)?.name ?: MealEntry.nameFor(r.at + HOUR / 3, zone)
            when (name) {
                "Breakfast" -> Block.BEFORE_BREAKFAST
                "Lunch" -> Block.BEFORE_LUNCH
                "Dinner" -> Block.BEFORE_DINNER
                else -> null
            }
        }
        MealTag.AFTER_MEAL -> {
            val name = MealLink.mealBefore(r, meals)?.name ?: MealEntry.nameFor(r.at - 2 * HOUR, zone)
            when (name) {
                "Breakfast" -> Block.AFTER_BREAKFAST
                "Lunch" -> Block.AFTER_LUNCH
                "Dinner" -> Block.AFTER_DINNER
                else -> null
            }
        }
        MealTag.OTHER -> null
    }

    fun groups(readings: List<Reading>, meals: List<MealEntry>, all: List<Reading>, zone: ZoneId): List<Group> =
        readings.groupBy { blockOf(it, meals, all, zone) }
            .filterKeys { it != null }
            .mapNotNull { (b, rs) ->
                if (rs.size < MIN_CHECKS) return@mapNotNull null
                val v = rs.map { it.mmol }.sorted()
                Group(b!!, v.size, quantile(v, 0.5), quantile(v, 0.25), quantile(v, 0.75), v.first(), v.last())
            }
            .sortedBy { it.block.ordinal }

    /** Linear-interpolated quantile of sorted values. */
    fun quantile(sorted: List<Double>, q: Double): Double {
        if (sorted.size == 1) return sorted[0]
        val pos = q * (sorted.size - 1)
        val i = pos.toInt().coerceAtMost(sorted.size - 2)
        return sorted[i] + (sorted[i + 1] - sorted[i]) * (pos - i)
    }
}
