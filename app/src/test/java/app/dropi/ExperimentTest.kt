package app.dropi

import app.dropi.data.A1cCalibration
import app.dropi.data.Experiment
import app.dropi.data.Foods
import app.dropi.data.LabA1c
import app.dropi.data.Meal
import app.dropi.data.MealEntry
import app.dropi.data.MealItem
import app.dropi.data.MealTag
import app.dropi.data.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExperimentTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private val day = 86_400_000L
    private val start = LocalDate.of(2026, 10, 20).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()

    private fun meal(id: Long, at: Long, walked: Boolean) =
        MealEntry(id = id, at = at, name = "Lunch", meal = Meal(listOf(MealItem("rice", "1 cup", carbs = 45.0)), tags = if (walked) listOf("walk_after") else emptyList()))

    private fun measured(e: MealEntry, rise: Double) = Foods.Meal(e.at, 6.0, e.foods, listOf(90 to rise), e.id, e.at - 600_000L)

    @Test fun walkWeekShowsAClearSmallerRise() {
        val before = (1..6L).map { meal(it, start - it * day, walked = false) }
        val during = (7..10L).map { meal(it, start + (it - 6) * day / 2, walked = true) }
        val ms = before.map { measured(it, 3.5) } + during.map { measured(it, 2.0) }
        val r = Experiment.result(Experiment.Running(Experiment.Kind.WALK, start), ms, before + during, zone, now = start + 6 * day)
        assertEquals(4, r.triedMeals)
        assertEquals(6, r.usualMeals)
        assertEquals(-1.5, r.clearDiff!!, 1e-9)
    }

    @Test fun smallGapIsNoClearDifference() {
        val before = (1..4L).map { meal(it, start - it * day, false) }
        val during = (5..8L).map { meal(it, start + (it - 4) * day / 2, true) }
        val ms = before.map { measured(it, 3.0) } + during.map { measured(it, 2.6) }
        val r = Experiment.result(Experiment.Running(Experiment.Kind.WALK, start), ms, before + during, zone, now = start + 6 * day)
        assertNull(r.clearDiff)
    }

    @Test fun labGapCorrectsTheEstimate() {
        val lab = LabA1c(LocalDate.of(2026, 10, 1), 6.5)
        val rs = (0 until 30).map { i ->
            Reading(at = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), mmol = 7.0, tag = MealTag.FASTING)
        }
        val gap = A1cCalibration.gap(listOf(lab), rs, zone, LocalDate.of(2026, 10, 27))
        assertNotNull(gap)
        // GMI at 7.0 mmol/L is about 6.33 %: the lab ran about 0.17 above.
        assertEquals(6.5 - gap!!.gmiThen, gap.points, 1e-9)
        assertEquals(6.0 + gap.points, A1cCalibration.adjusted(6.0, gap)!!, 1e-9)
    }
}
