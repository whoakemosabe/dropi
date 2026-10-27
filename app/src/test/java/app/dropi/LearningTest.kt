package app.dropi

import app.dropi.data.Foods
import app.dropi.data.Learning
import app.dropi.data.Meal
import app.dropi.data.MealEntry
import app.dropi.data.MealItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    private val day = 86_400_000L
    private fun entry(id: Long, at: Long, name: String, carbs: Double) =
        MealEntry(id = id, at = at, name = name, meal = Meal(listOf(MealItem("oats", "1 bowl", carbs = carbs))))

    /** A measured meal: start 6.0, an after reading 90 min later [rise] higher. */
    private fun measured(id: Long, at: Long, rise: Double, sensor: Boolean = false) = Foods.Meal(
        at = at, start = 6.0, foods = listOf("oats"),
        after = if (sensor) (0..18).map { k -> (20 + k * 10) to (if (20 + k * 10 == 70) rise else rise * 0.5) } else listOf(90 to rise),
        entryId = id, startAt = at - 600_000L,
    )

    @Test fun ownFactorByMealOnceThereAreFour() {
        val es = (1..6L).map { entry(it, it * day, if (it <= 4) "Breakfast" else "Dinner", 50.0) }
        val ms = es.map { measured(it.id, it.at, if (it.name == "Breakfast") 4.0 else 2.0) }
        // Breakfast has 4 of its own: 4.0 / 50 g.
        assertEquals(0.08, Learning.perGramFor(ms, es, "Breakfast")!!, 1e-9)
        // Dinner has only 2: falls back to the overall median of (0.08 ×4, 0.04 ×2).
        assertEquals(0.08, Learning.perGramFor(ms, es, "Dinner")!!, 1e-9)
        assertNull(Learning.perGramFor(ms.take(3), es, null))
    }

    @Test fun peakNeedsDenseValues() {
        val es = (1..3L).map { entry(it, it * day, "Lunch", 40.0) }
        assertNull(Learning.peak(es.map { measured(it.id, it.at, 3.0) }))
        val p = Learning.peak(es.map { measured(it.id, it.at, 3.0, sensor = true) })
        assertEquals(70, p!!.first)
        assertEquals(3, p.second)
    }

    @Test fun hindcastsUseOnlyEarlierMeals() {
        val es = (1..8L).map { entry(it, it * day, "Lunch", 50.0) }
        val ms = es.map { measured(it.id, it.at, 3.0) }
        val m = Learning.build(ms, es)
        assertEquals(8, m.hindcasts.size)
        // Later guesses know the person rises 3.0 for these 50 g (and share the food): close.
        val last = m.hindcasts.last()
        assertEquals(9.0, last.actual, 1e-9)
        assertEquals(9.0, last.expected, 0.6)
        assertTrue(last.close)
        assertNotNull(m.spread)
    }
}
