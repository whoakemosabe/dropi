package app.dropi

import app.dropi.data.Gi
import app.dropi.data.Label
import app.dropi.data.Meal
import app.dropi.data.MealAi
import app.dropi.data.MealCurve
import app.dropi.data.MealEntry
import app.dropi.data.MealItem
import app.dropi.data.MealTags
import app.dropi.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {
    @Test
    fun giLooksUpTheLongestName() {
        assertEquals(68, Gi.lookup("Brown rice"))
        assertEquals(73, Gi.lookup("rice"))
        assertEquals(50, Gi.lookup("orange juice"))
        assertEquals(Gi.Speed.SLOW, Gi.speed(32))
        assertEquals(Gi.Speed.FAST, Gi.speed(78))
        assertTrue(MealItem("orange juice", "1 glass", carbs = 26, sugars = 21).sugaryDrink)
        assertFalse(MealItem("pepsi max", "1 can", carbs = 0, sugars = 0).sugaryDrink)
    }

    @Test
    fun tagsFromWords() {
        assertEquals(listOf("walk_after", "alcohol"), MealTags.fromWords("pasta, a glass of wine, then walked the dog"))
        assertTrue(MealTags.fromWords("ginger tea").isEmpty())
    }

    @Test
    fun readsTheModelsFullReply() {
        val m = MealAi.parse(
            """{"items":[{"food":"pizza","portion":"3 slices","carbs":90,"sugars":9,"fibre":6,"protein":30,"fat":33,"gi":60,"drink":false},
               {"food":"cola","portion":"1 can","carbs":35,"sugars":35,"drink":true}],"rich":false,"tags":["alcohol","nonsense"]}""",
            "photo",
        )!!
        assertEquals(30, m.protein)
        assertEquals(33, m.fat)
        assertTrue(m.rich) // 33 g fat
        assertTrue(m.hasSugaryDrink)
        assertEquals(listOf("alcohol"), m.tags)
        assertEquals(m, Meal.fromJson(m.toJson()))
    }

    @Test
    fun labelsGiveProteinFatAndUsCarbs() {
        val isl = Label.read(listOf("Næringargildi í 100 g", "Fita 12 g", "þar af mettuð fita 3,1 g", "Kolvetni 54 g", "Prótein 9,5 g"))!!
        assertEquals(12.0, isl.fat!!, 0.01)
        assertEquals(9.5, isl.protein!!, 0.01)
        assertEquals(54.0, isl.netCarbs!!, 0.01)
        assertFalse(isl.us)
        val us = Label.read(listOf("Nutrition Facts", "Total Fat 8g", "Total Carbohydrate 37g", "Dietary Fiber 4g", "Total Sugars 12g", "Protein 3g"))!!
        assertTrue(us.us)
        assertEquals(33.0, us.netCarbs!!, 0.01)
        assertEquals(30, Label.item("cereal", 90, us).carbs)
    }

    @Test
    fun curveShapes() {
        val toast = Meal(listOf(MealItem("white bread", "2 slices", carbs = 30), MealItem("jam", "1 spoon", carbs = 13)))
        val pizza = Meal(listOf(MealItem("pizza", "3 slices", carbs = 90, protein = 30, fat = 33)))
        val t = MealCurve.predict(toast, null, null)!!
        val p = MealCurve.predict(pizza, null, null)!!
        assertTrue(p.peakMin > t.peakMin + 30)
        assertTrue(p.backMin > t.backMin)
        assertEquals(t.rise, t.at(t.peakMin.toDouble()), 1e-9)
        assertEquals(0.0, t.at(t.backMin + 10.0), 1e-9)
        val walked = MealCurve.predict(toast.copy(tags = listOf("walk_after")), null, null)!!
        assertTrue(walked.rise < t.rise)
        assertNotNull(MealCurve.duration(75))
        assertEquals("1 h 15 min", MealCurve.duration(75))
    }

    @Test
    fun afterCheckGapFollowsFatAndProtein() {
        fun e(m: Meal) = MealEntry(at = 0, name = "Dinner", meal = m)
        assertEquals(120, Schedule.gapFor(e(Meal(listOf(MealItem("rice", "1 cup", carbs = 45)))), 120))
        assertEquals(150, Schedule.gapFor(e(Meal(listOf(MealItem("chicken wrap", "1", carbs = 38, protein = 26, fat = 12)))), 120))
        assertEquals(180, Schedule.gapFor(e(Meal(listOf(MealItem("pizza", "3 slices", carbs = 90, fat = 33)))), 120))
    }
}
