package app.dropi

import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Stats
import app.dropi.data.Tagging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun r(t: Long, v: Double, tag: MealTag = MealTag.OTHER) = Reading(id = t, at = t, mmol = v, tag = tag)

    @Test
    fun summaryOfADay() {
        val rs = listOf(r(at(2026, 10, 23, 7), 5.0), r(at(2026, 10, 23, 13), 9.0), r(at(2026, 10, 23, 19), 3.5), r(at(2026, 10, 23, 22), 12.5))
        val s = Stats.summary(rs)
        assertEquals(4, s.count)
        assertEquals(7.5, s.average!!, 1e-9)
        assertEquals(3.5, s.min!!, 1e-9)
        assertEquals(12.5, s.max!!, 1e-9)
        assertEquals(50, s.inRangePct) // 5.0 and 9.0 of four
        assertEquals(1, s.lows)
        assertNull(Stats.summary(emptyList()).average)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val today = LocalDate.of(2026, 10, 23)
        val rs = listOf(21, 22, 23).map { r(at(2026, 10, it, 8), 6.0) } + r(at(2026, 10, 19, 8), 6.0)
        assertEquals(3, Stats.streak(rs, today, zone))
        // Nothing yet today: yesterday's run still counts.
        assertEquals(2, Stats.streak(rs.filter { it.date(zone) != today }, today, zone))
        assertEquals(0, Stats.streak(listOf(r(at(2026, 10, 19, 8), 6.0)), today, zone))
    }

    @Test
    fun tagsArePickedFromTheClockAndTheLastReading() {
        val none = emptyList<Reading>()
        assertEquals(MealTag.FASTING, Tagging.suggest(at(2026, 10, 23, 7, 30), none, zone))
        assertEquals(MealTag.OVERNIGHT, Tagging.suggest(at(2026, 10, 23, 3), none, zone))
        assertEquals(MealTag.BEDTIME, Tagging.suggest(at(2026, 10, 23, 22), none, zone))
        assertEquals(MealTag.BEFORE_MEAL, Tagging.suggest(at(2026, 10, 23, 12), none, zone))
        // A second morning check isn't fasting any more.
        val morning = listOf(r(at(2026, 10, 23, 7), 5.4, MealTag.FASTING))
        assertEquals(MealTag.BEFORE_MEAL, Tagging.suggest(at(2026, 10, 23, 9), morning, zone))
        // Two hours after a before-meal reading → after meal; five hours later it isn't.
        val lunch = listOf(r(at(2026, 10, 23, 12), 6.1, MealTag.BEFORE_MEAL))
        assertEquals(MealTag.AFTER_MEAL, Tagging.suggest(at(2026, 10, 23, 14), lunch, zone))
        assertEquals(MealTag.BEFORE_MEAL, Tagging.suggest(at(2026, 10, 23, 17), lunch, zone))
    }
}
