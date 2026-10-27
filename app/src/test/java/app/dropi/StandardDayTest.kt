package app.dropi

import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.StandardDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StandardDayTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDate.of(2026, 10, day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun quantiles() {
        val v = listOf(4.0, 5.0, 6.0, 7.0, 8.0)
        assertEquals(6.0, StandardDay.quantile(v, 0.5), 1e-9)
        assertEquals(5.0, StandardDay.quantile(v, 0.25), 1e-9)
        assertEquals(7.0, StandardDay.quantile(v, 0.75), 1e-9)
        assertEquals(4.0, StandardDay.quantile(listOf(4.0), 0.5), 1e-9)
    }

    @Test fun groupsByCheckWithoutMeals() {
        val rs = (20..24).flatMap { d ->
            listOf(
                Reading(at = at(d, 7), mmol = 6.0 + d % 2, tag = MealTag.FASTING),
                // After eating at 10:00, with no logged meal: counts as after breakfast (eaten ~08:00).
                Reading(at = at(d, 10), mmol = 9.0, tag = MealTag.AFTER_MEAL),
                // Before eating at 18:30: before dinner.
                Reading(at = at(d, 18, 30), mmol = 7.0, tag = MealTag.BEFORE_MEAL),
            )
        } + Reading(at = at(24, 22), mmol = 6.5, tag = MealTag.BEDTIME) // only one: not shown
        val g = StandardDay.groups(rs, emptyList(), rs, zone)
        assertEquals(listOf(StandardDay.Block.WAKING, StandardDay.Block.AFTER_BREAKFAST, StandardDay.Block.BEFORE_DINNER), g.map { it.block })
        assertEquals(5, g[0].count)
        assertEquals(9.0, g[1].median, 1e-9)
    }
}
