package app.dropi

import app.dropi.data.MealItem
import app.dropi.data.gramsUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Changing how much was eaten moves every number with it. */
class AmountTest {
    private val skyr = MealItem("skyr", "150 g", carbs = 6.0, sugars = 6.0, fibre = 0.0, protein = 16.5, fat = 0.3)

    @Test fun readsWeights() {
        assertEquals(150.0, gramsUnit("150 g")!!.first, 0.01)
        assertEquals(1500.0, gramsUnit("1,5 kg")!!.first, 0.01)
        assertEquals("ml", gramsUnit("330 ml")!!.second)
        assertEquals(250.0, gramsUnit("2,5 dl")!!.first, 0.01)
        assertNull(gramsUnit("1 cup"))
        assertNull(gramsUnit("2 slices"))
    }

    @Test fun everyNumberFollowsTheWeight() {
        val big = skyr.withGrams(300.0)
        assertEquals("300 g", big.portion)
        assertEquals(12.0, big.carbs, 0.01)
        assertEquals(12.0, big.sugars!!, 0.01)
        assertEquals(33.0, big.protein!!, 0.01)
        assertEquals(0.6, big.fat!!, 0.01)
        assertEquals(0.0, big.fibre!!, 0.01)
        assertEquals(300.0, big.grams!!, 0.01)
    }

    @Test fun twoTimesAWeighedPortionIsItsGrams() {
        assertEquals(300.0, skyr.withQty(2.0).grams!!, 0.01)
        // From the same starting numbers, a small and back again lands where it began.
        assertEquals(16.5, skyr.withGrams(5.0).let { skyr.withGrams(150.0) }.protein!!, 0.01)
    }

    @Test fun notAWeightStaysAsItIs() {
        val bread = MealItem("bread", "1 slice", carbs = 15.0)
        assertNull(bread.grams)
        assertEquals(bread, bread.withGrams(200.0))
    }
}
