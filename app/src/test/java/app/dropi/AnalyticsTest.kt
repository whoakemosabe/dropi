package app.dropi

import app.dropi.data.Analytics
import app.dropi.data.InsightKind
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class AnalyticsTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(day: LocalDate, h: Int) = LocalDateTime.of(day, java.time.LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()
    private var id = 0L
    private fun r(day: LocalDate, h: Int, v: Double, tag: MealTag = MealTag.OTHER) = Reading(++id, at(day, h), v, tag)
    private fun v(m: Double) = String.format(Locale.ROOT, "%.1f", m)

    @Test
    fun gmiMatchesThePublishedFormula() {
        // Bergenstal 2018: a mean of 154 mg/dL (8.5 mmol/L) is a GMI of about 7.0%.
        assertEquals(7.0, Analytics.gmiPercent(154 / 18.0182), 0.01)
        assertEquals(52.7, Analytics.gmiMmolMol(8.5), 0.1)
    }

    @Test
    fun bandsSplitAtTheConsensusThresholds() {
        val b = Analytics.bands(listOf(2.8, 3.5, 3.9, 10.0, 10.1, 13.9, 14.0, 6.0, 7.0, 8.0))!!
        assertEquals(10.0, b.veryLow, 1e-9)
        assertEquals(10.0, b.low, 1e-9)
        assertEquals(50.0, b.inRange, 1e-9) // 3.9, 10.0, 6, 7, 8
        assertEquals(20.0, b.high, 1e-9) // 10.1, 13.9
        assertEquals(10.0, b.veryHigh, 1e-9)
    }

    @Test
    fun reportCoversThePeriodOnly() {
        val rs = (0 until 10).flatMap { d ->
            val day = today.minusDays(d.toLong())
            listOf(r(day, 7, 6.0, MealTag.FASTING), r(day, 14, 9.0, MealTag.AFTER_MEAL))
        }
        val rep = Analytics.report(rs, 7, today, zone)
        assertEquals(14, rep.count)
        assertEquals(7, rep.daysLogged)
        assertEquals(7.5, rep.mean!!, 1e-9)
        assertNotNull(rep.gmiPercent) // 7 days, 14 readings
        assertEquals(today.minusDays(6), rep.from)
        // Sample SD of seven 6s and seven 9s.
        assertEquals(1.5566, rep.sd!!, 1e-3)
    }

    @Test
    fun noGmiWithTooLittleData() {
        val rs = listOf(r(today, 8, 6.0), r(today, 12, 7.0), r(today, 18, 8.0))
        assertNull(Analytics.report(rs, 7, today, zone).gmiPercent)
    }

    @Test
    fun insightsNameLowsAndHighAfterMeals() {
        val rs = listOf(
            r(today, 3, 3.4, MealTag.OVERNIGHT),
            r(today.minusDays(1), 2, 3.6, MealTag.OVERNIGHT),
            r(today.minusDays(2), 13, 11.5, MealTag.AFTER_MEAL),
            r(today.minusDays(3), 13, 12.0, MealTag.AFTER_MEAL),
            r(today.minusDays(4), 13, 8.0, MealTag.AFTER_MEAL),
            r(today, 7, 8.1, MealTag.FASTING),
            r(today.minusDays(1), 7, 7.9, MealTag.FASTING),
            r(today.minusDays(2), 7, 8.0, MealTag.FASTING),
        )
        val rep = Analytics.report(rs, 7, today, zone)
        val ins = Analytics.insights(rep, Targets()) { v(it) }
        val texts = ins.map { it.text }
        assertTrue(texts.any { it.startsWith("2 low readings this week, mostly overnight") })
        assertTrue(texts.any { it.contains("Fasting readings average 8.0, above the 4.4–7.2 target") })
        assertTrue(texts.any { it.contains("over 10.0 in 2 of 3") })
        assertEquals(InsightKind.ATTENTION, ins.first().kind)
    }

    @Test
    fun insightsPointAtTheDaysTheyAreAbout() {
        val d1 = today.minusDays(1)
        val d3 = today.minusDays(3)
        val rs = listOf(
            r(d3, 3, 3.5, MealTag.OVERNIGHT),
            r(d1, 2, 3.6, MealTag.OVERNIGHT),
            r(d1, 4, 3.7, MealTag.OVERNIGHT),
            r(today.minusDays(2), 13, 11.5, MealTag.AFTER_MEAL),
            r(d3, 13, 12.0, MealTag.AFTER_MEAL),
            r(today.minusDays(4), 13, 8.0, MealTag.AFTER_MEAL),
        )
        val rep = Analytics.report(rs, 7, today, zone)
        val ins = Analytics.insights(rep, Targets()) { v(it) }
        val lows = ins.first { it.text.contains("low readings") }
        // The day with the most lows first.
        assertEquals(listOf(d1, d3), lows.days)
        val after = ins.first { it.text.contains("After-eating") }
        // The highest over reading's day first.
        assertEquals(listOf(d3, today.minusDays(2)), after.days)
        // Whole-period findings point at no day.
        ins.filter { it.text.startsWith("Logged on") }.forEach { assertTrue(it.days.isEmpty()) }
    }
}
