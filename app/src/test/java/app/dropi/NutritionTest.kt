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
        assertTrue(MealItem("orange juice", "1 glass", carbs = 26.0, sugars = 21.0).sugaryDrink)
        assertFalse(MealItem("pepsi max", "1 can", carbs = 0.0, sugars = 0.0).sugaryDrink)
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
        assertEquals(30.0, m.protein!!, 0.01)
        assertEquals(33.0, m.fat!!, 0.01)
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
        assertEquals(29.7, Label.item("cereal", 90, us).carbs, 0.01)
    }

    @Test
    fun curveShapes() {
        val toast = Meal(listOf(MealItem("white bread", "2 slices", carbs = 30.0), MealItem("jam", "1 spoon", carbs = 13.0)))
        val pizza = Meal(listOf(MealItem("pizza", "3 slices", carbs = 90.0, protein = 30.0, fat = 33.0)))
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
        assertEquals(120, Schedule.gapFor(e(Meal(listOf(MealItem("rice", "1 cup", carbs = 45.0)))), 120))
        assertEquals(150, Schedule.gapFor(e(Meal(listOf(MealItem("chicken wrap", "1", carbs = 38.0, protein = 26.0, fat = 12.0)))), 120))
        assertEquals(180, Schedule.gapFor(e(Meal(listOf(MealItem("pizza", "3 slices", carbs = 90.0, fat = 33.0)))), 120))
    }
}

class BasicsAndGramsTest {
    @Test
    fun basicsFindEverydayFoods() {
        val egg = app.dropi.data.Basics.find("hard boiled eggs")!!
        assertEquals("egg", egg.name)
        assertTrue(egg.protein!! > 5)
        assertEquals(null, app.dropi.data.Basics.find("eggplant"))
        assertEquals("bacon", app.dropi.data.Basics.find("Bacon")!!.name)
    }

    @Test
    fun gramsKeepOneDecimal() {
        assertEquals("12.5", app.dropi.data.Grams.text(12.46))
        assertEquals("12", app.dropi.data.Grams.text(12.0))
        assertEquals(12.5, app.dropi.data.Grams.parse("12,5")!!, 1e-9)
        val m = MealAi.parse("""{"items":[{"food":"egg","portion":"1 egg","carbs":0.6,"protein":6.3,"fat":5.3,"gi":0}]}""", "words")!!
        assertEquals(6.3, m.protein!!, 1e-9)
        assertEquals(null, m.items[0].gi)
    }
}

class IcelandicLabelTest {
    private fun line(text: String, cx: Float, cy: Float, angle: Float = 0f) =
        Label.TextLine(text, cx, cy, cx - 40f, 30f, angle)

    @Test
    fun tiltedTableStillPairsNamesAndNumbers() {
        // A label photographed 6° tilted: each row's number sits 30–40 px lower than its name.
        val t = Math.tan(Math.toRadians(6.0)).toFloat()
        fun at(x: Float, y: Float) = y + x * t
        val lines = listOf(
            line("Næringargildi í 100 g", 200f, at(200f, 100f), 6f),
            line("Orka", 100f, at(100f, 160f), 6f), line("1650 kJ / 394 kkal", 420f, at(420f, 160f), 6f),
            line("Fita", 100f, at(100f, 210f), 6f), line("12 g", 420f, at(420f, 210f), 6f),
            line("þar af mettuð fita", 130f, at(130f, 260f), 6f), line("3,1 g", 420f, at(420f, 260f), 6f),
            line("Kolvetni", 100f, at(100f, 310f), 6f), line("54 g", 420f, at(420f, 310f), 6f),
            line("þar af sykurtegundir", 140f, at(140f, 360f), 6f), line("4,5 g", 420f, at(420f, 360f), 6f),
            line("Trefjar", 100f, at(100f, 410f), 6f), line("6 g", 420f, at(420f, 410f), 6f),
            line("Prótein", 100f, at(100f, 460f), 6f), line("9,5 g", 420f, at(420f, 460f), 6f),
        )
        val rows = Label.rows(lines)
        assertTrue(rows.any { it == "Kolvetni 54 g" })
        val p = Label.read(rows)!!
        assertEquals(54.0, p.carbs!!, 0.01)
        assertEquals(4.5, p.sugars!!, 0.01)
        assertEquals(6.0, p.fibre!!, 0.01)
        assertEquals(9.5, p.protein!!, 0.01)
        assertEquals(12.0, p.fat!!, 0.01)
    }

    @Test
    fun namesAndNumbersReadAsSeparateColumns() {
        // The camera returned the names as one block and the numbers as another.
        val p = Label.read(listOf(
            "Næringargildi í 100 g", "Orka", "Fita", "þar af mettuð fita", "Kolvetni", "þar af sykurtegundir", "Trefjar", "Prótein", "Salt",
            "1650 kJ / 394 kkal", "12 g", "3,1 g", "54 g", "4,5 g", "6 g", "9,5 g", "0,8 g",
        ))!!
        assertEquals(54.0, p.carbs!!, 0.01)
        assertEquals(4.5, p.sugars!!, 0.01)
        assertEquals(9.5, p.protein!!, 0.01)
    }

    @Test
    fun smallMisreadsAreForgiven() {
        val p = Label.read(listOf("Kolvetnl 54 g", "þar af sykurtegundlr 4,5 g", "Prótcin 9 g"))!!
        assertEquals(54.0, p.carbs!!, 0.01)
        assertEquals(4.5, p.sugars!!, 0.01)
        assertEquals(9.0, p.protein!!, 0.01)
    }
}
