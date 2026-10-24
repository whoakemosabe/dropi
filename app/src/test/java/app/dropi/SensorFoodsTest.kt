package app.dropi

import app.dropi.data.Arrow
import app.dropi.data.Foods
import app.dropi.data.GlucoseUnit
import app.dropi.data.Heads
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.SensorPoint
import app.dropi.data.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFoodsTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L

    /** Points every 5 minutes ending at t0, changing by [perMin] mmol/L a minute. */
    private fun line(end: Double, perMin: Double, n: Int = 4) =
        (0 until n).map { i -> SensorPoint(t0 - (n - 1 - i) * 5 * min, end - perMin * (n - 1 - i) * 5) }

    @Test
    fun arrowsFollowTheSlope() {
        assertEquals(Arrow.FLAT, Trend.arrow(line(6.0, 0.0)))
        assertEquals(Arrow.DOWN_SLOW, Trend.arrow(line(6.0, -0.08))) // about −1.4 mg/dL a minute
        assertEquals(Arrow.DOWN_FAST, Trend.arrow(line(6.0, -0.2))) // about −3.6
        assertEquals(Arrow.UP, Trend.arrow(line(9.0, 0.13))) // about +2.3
        assertNull(Trend.arrow(listOf(SensorPoint(t0, 5.0))))
    }

    @Test
    fun headingLowNeedsFreshFallingData() {
        val falling = line(4.6, -0.06) // 4.6, −0.06/min: 3.9 in about 12 min
        val h = Trend.headsUp(falling, t0 + min, lastAt = 0, lastKind = null)
        assertNotNull(h)
        assertEquals(Heads.HEADING_LOW, h!!.kind)
        assertEquals(10, h.minutes)
        // Stale data says nothing; a recent heads-up keeps quiet.
        assertNull(Trend.headsUp(falling, t0 + 20 * min, 0, null))
        assertNull(Trend.headsUp(falling, t0 + min, t0 - 5 * min, Heads.HEADING_LOW))
        // Steady at 6: nothing.
        assertNull(Trend.headsUp(line(6.0, 0.0), t0, 0, null))
    }

    @Test
    fun lowNowBreaksThroughAWarning() {
        val low = line(3.6, -0.03)
        val h = Trend.headsUp(low, t0, lastAt = t0 - 10 * min, lastKind = Heads.HEADING_LOW)
        assertEquals(Heads.LOW_NOW, h!!.kind)
        assertNull(Trend.headsUp(low, t0, lastAt = t0 - 10 * min, lastKind = Heads.LOW_NOW))
    }

    @Test
    fun foodsPackAndNormalise() {
        assertEquals("porridge|banana", Foods.pack(listOf(" Porridge ", "banana", "PORRIDGE", "")))
        assertEquals(listOf("porridge", "banana"), Foods.unpack("porridge|banana"))
        assertEquals(emptyList<String>(), Foods.unpack(""))
        assertEquals("+2.4", Foods.delta(2.4, GlucoseUnit.MMOL))
        assertEquals("−0.3", Foods.delta(-0.31, GlucoseUnit.MMOL))
        assertEquals("+43", Foods.delta(2.4, GlucoseUnit.MGDL))
    }

    private fun meal(at: Long, vararg foods: String) = app.dropi.data.MealEntry(
        at = at, name = "Lunch",
        meal = app.dropi.data.Meal(foods.map { app.dropi.data.MealItem(it, "1 portion", carbs = 20.0) }),
    )

    @Test
    fun foodRisesComeFromWhatFollows() {
        var id = 1L
        fun r(at: Long, mmol: Double, tag: MealTag) = Reading(id++, at, mmol, tag)
        val day = 86_400_000L
        val readings = listOf(
            // Porridge twice: rises of 2.0 and 3.0.
            r(t0, 5.0, MealTag.BEFORE_MEAL),
            r(t0 + 90 * min, 7.0, MealTag.AFTER_MEAL),
            r(t0 + day, 5.5, MealTag.BEFORE_MEAL),
            r(t0 + day + 120 * min, 8.5, MealTag.AFTER_MEAL),
            // Eggs eaten 40 min after the fasting reading: that reading is still the start.
            r(t0 + 2 * day, 6.0, MealTag.FASTING),
            r(t0 + 2 * day + 140 * min, 6.5, MealTag.AFTER_MEAL),
            // Toast with nothing after: no score.
            r(t0 + 3 * day, 6.0, MealTag.BEFORE_MEAL),
        )
        val meals = listOf(
            meal(t0 + 5 * min, "porridge"),
            meal(t0 + day + 10 * min, "porridge", "banana"),
            meal(t0 + 2 * day + 40 * min, "eggs"),
            meal(t0 + 3 * day + 5 * min, "toast"),
        )
        val scores = Foods.scores(readings, emptyList(), meals)
        assertEquals(listOf("eggs", "porridge", "banana"), scores.map { it.food })
        val porridge = scores.first { it.food == "porridge" }
        assertEquals(2, porridge.meals)
        assertEquals(2.5, porridge.rise, 1e-9)
        assertTrue(porridge.ranked)
        assertEquals(0.5, scores.first().rise, 1e-9)
        assertTrue(!scores.first { it.food == "banana" }.ranked)
    }

    @Test
    fun mealsLinkToTheirReadings() {
        val zone = java.time.ZoneId.of("UTC")
        val wake = Reading(1, t0, 5.8, MealTag.FASTING)
        val after = Reading(2, t0 + 160 * min, 8.1, MealTag.AFTER_MEAL)
        val breakfast = meal(t0 + 40 * min, "toast").copy(name = "Breakfast")
        val readings = listOf(wake, after)
        assertEquals(wake, app.dropi.data.MealLink.before(breakfast, readings))
        assertEquals("before breakfast", app.dropi.data.MealLink.text(wake, listOf(breakfast), readings))
        assertEquals("2 h after breakfast", app.dropi.data.MealLink.text(after, listOf(breakfast), readings))
        // Nothing in the 90 minutes before: no start.
        assertEquals(null, app.dropi.data.MealLink.before(meal(t0 + 200 * min, "x"), listOf(wake)))
        assertEquals("Snack", app.dropi.data.MealEntry.nameFor(t0 - t0 % 86_400_000L + 16 * 60 * min, zone))
    }

    @Test
    fun sensorPointsFeedTheCurve() {
        val readings = listOf(Reading(1, t0, 5.0, MealTag.BEFORE_MEAL))
        val sensor = (1..48).map { i -> SensorPoint(t0 + i * 5 * min, 5.0 + if (i * 5 <= 60) i * 5 * 0.06 else (240 - i * 5) * 0.02) }
        val s = Foods.scores(readings, sensor, listOf(meal(t0, "rice"))).single()
        assertEquals("rice", s.food)
        assertEquals(3.6, s.rise, 0.05)
        assertEquals(0 to 0.0, s.curve.first())
        assertTrue(s.curve.size >= 6)
    }
}
