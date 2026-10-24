package app.dropi

import app.dropi.data.Label
import app.dropi.data.Meal
import app.dropi.data.MealAi
import app.dropi.data.MealItem
import app.dropi.data.Rise
import app.dropi.data.Usual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealTest {
    @Test
    fun readsJsonInsideWords() {
        val reply = """Sure! Here it is:
            ```json
            {"items":[{"food":"Basmati Rice","portion":"1 cup","carbs":45,"sugars":0,"fibre":1},
                      {"food":"naan bread","portion":"1 piece","carbs":"about 40 g","sugars":3}],"rich":true}
            ```"""
        val m = MealAi.parse(reply, "photo")!!
        assertEquals(listOf("basmati rice", "naan bread"), m.foods)
        assertEquals(85.0, m.carbs, 0.01)
        assertEquals(3.0, m.sugars!!, 0.01)
        assertTrue(m.rich)
        assertEquals("photo", m.source)
    }

    @Test
    fun fallsBackToOldLines() {
        val m = MealAi.parse("FOODS: toast; jam\nCARBS: 44", "photo")!!
        assertEquals(listOf("toast", "jam"), m.foods)
        assertEquals(44.0, m.carbs, 0.01)
        assertNull(MealAi.parse("I can't tell what this is.", "photo"))
    }

    @Test
    fun portionsScale() {
        val i = MealItem("rice", "1 cup", carbs = 45.0, sugars = 1.0, fibre = 2.0)
        val half = i.withQty(0.5)
        assertEquals(22.5, half.carbs, 0.01)
        assertEquals("½ × 1 cup", half.portionText())
        assertEquals(90.0, i.withQty(2.0).carbs, 0.01)
        assertEquals("1½", Meal.qtyText(1.5))
    }

    @Test
    fun usualReplacesGuess() {
        val m = Meal(listOf(MealItem("naan bread", "1 piece", qty = 2.0, carbs = 80.0)))
        val u = MealAi.applyUsual(m, mapOf("naan bread" to Usual("1 piece", 55.0)))
        assertEquals(110.0, u.carbs, 0.01)
        assertTrue(u.items[0].usual)
    }

    @Test
    fun jsonRoundTrip() {
        val m = Meal(listOf(MealItem("rice", "1 cup", 1.5, 68.0, 1.0, null, true)), rich = true, source = "words")
        assertEquals(m, Meal.fromJson(m.toJson()))
        assertNull(Meal.fromJson("not json"))
    }

    @Test
    fun readsLabels() {
        val en = Label.read(listOf("Nutrition per 100 g per serving", "Energy 1650 kJ", "Carbohydrate 60.5 g 24.2 g", "of which sugars 22 g 8.8 g", "Fibre 7 g"))!!
        assertEquals(60.5, en.carbs!!, 0.01)
        assertEquals(22.0, en.sugars!!, 0.01)
        assertEquals(7.0, en.fibre!!, 0.01)
        val isl = Label.read(listOf("Næringargildi í 100 g", "Kolvetni 12,4 g", "þar af sykurtegundir 4,1 g"))!!
        assertEquals(12.4, isl.carbs!!, 0.01)
        assertEquals(4.1, isl.sugars!!, 0.01)
        assertNull(Label.read(listOf("Best before 12.11.26")))
        val item = Label.item("granola", 50, en)
        assertEquals(30.3, item.carbs, 0.01)
        assertEquals("50 g", item.portion)
    }

    @Test
    fun readsEuropeanLabels() {
        val de = Label.read(listOf("Nährwerte pro 100 g", "Kohlenhydrate 12,4 g", "davon Zucker 3,1 g", "Ballaststoffe 2 g"))!!
        assertEquals(12.4, de.carbs!!, 0.01)
        assertEquals(3.1, de.sugars!!, 0.01)
        assertEquals(2.0, de.fibre!!, 0.01)
        // Name and values on one row, per-portion column after.
        val fr = Label.read(listOf("Glucides 60 g dont sucres 22 g"))!!
        assertEquals(60.0, fr.carbs!!, 0.01)
        assertEquals(22.0, fr.sugars!!, 0.01)
        // The unit lost by the camera, and a "per 100 g" heading on the same row.
        val is2 = Label.read(listOf("Í 100 g: Kolvetni 54,3", "þar af sykurtegundir <0,5 g"))!!
        assertEquals(54.3, is2.carbs!!, 0.01)
        assertEquals(0.5, is2.sugars!!, 0.01)
        // Value on the row below its name.
        val nl = Label.read(listOf("Koolhydraten", "45 g", "waarvan suikers", "5 g"))!!
        assertEquals(45.0, nl.carbs!!, 0.01)
        assertEquals(5.0, nl.sugars!!, 0.01)
    }

    @Test
    fun readsTheModelsLabelReply() {
        val p = Label.parseAi("Here: {\"name\":\"Skyr\",\"carbs\":3.9,\"sugars\":3.9,\"fibre\":null}")!!
        assertEquals(3.9, p.carbs!!, 0.01)
        assertEquals("Skyr", p.name)
        assertNull(p.fibre)
        assertNull(Label.parseAi("no idea"))
    }

    @Test
    fun riseWords() {
        assertEquals(Rise.GENTLE, Rise.of(1.4))
        assertEquals(Rise.MODERATE, Rise.of(3.0))
        assertEquals(Rise.BIG, Rise.of(4.1))
        assertNotNull(Rise.BIG.word)
    }
}
