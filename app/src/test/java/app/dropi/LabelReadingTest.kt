package app.dropi

import app.dropi.data.Label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fibre, protein and fat from labels as the camera really returns them. */
class LabelReadingTest {
    private fun check(p: Label.Per100, carbs: Double?, sugars: Double?, fibre: Double?, protein: Double?, fat: Double?) {
        fun eq(what: String, want: Double?, got: Double?) {
            if (want == null) assertNull(what, got) else assertEquals(what, want, got ?: -1.0, 0.01)
        }
        eq("carbs", carbs, p.carbs); eq("sugars", sugars, p.sugars); eq("fibre", fibre, p.fibre)
        eq("protein", protein, p.protein); eq("fat", fat, p.fat)
    }

    @Test fun ukLabelRowByRow() {
        val p = Label.read(listOf(
            "Typical values Per 100g Per 45g serving", "Energy 1650kJ / 390kcal 743kJ / 176kcal", "Fat 12g 5.4g",
            "of which saturates 3.1g 1.4g", "Carbohydrate 60.5g 27.2g", "of which sugars 22g 9.9g", "Fibre 7g 3.2g",
            "Protein 9g 4.1g", "Salt 0.5g 0.2g",
        ))!!
        check(p, 60.5, 22.0, 7.0, 9.0, 12.0)
    }

    @Test fun icelandicSkyrWithTrefjaefni() {
        val p = Label.read(listOf(
            "Næringargildi í 100 g", "Orka 260 kJ / 62 kkal", "Fita 0,2 g", "þar af mettuð fita 0,1 g", "Kolvetni 4,0 g",
            "þar af sykurtegundir 4,0 g", "Trefjaefni 0 g", "Prótein 11 g", "Salt 0,1 g",
        ))!!
        check(p, 4.0, 4.0, 0.0, 11.0, 0.2)
    }

    @Test fun rowsTheCameraRanTogether() {
        // Two table rows read as one line: the saturates row used to throw the fat away.
        val p = Label.read(listOf(
            "Fita 12 g þar af mettuð fita 3,1 g", "Kolvetni 60 g þar af sykurtegundir 22 g", "Trefjar 7 g Prótein 9,5 g", "Salt 0,8 g",
        ))!!
        check(p, 60.0, 22.0, 7.0, 9.5, 12.0)
    }

    @Test fun columnsStayInStepWithSaturates() {
        // Names as one block, numbers as another; "of which saturates" has no fat word in it.
        val p = Label.read(listOf(
            "Energy", "Fat", "of which saturates", "Carbohydrate", "of which sugars", "Fibre", "Protein", "Salt",
            "1650 kJ / 390 kcal", "12 g", "3.1 g", "60.5 g", "22 g", "7 g", "9 g", "0.5 g",
        ))!!
        check(p, 60.5, 22.0, 7.0, 9.0, 12.0)
    }

    @Test fun usLabelWithTransAndAddedSugars() {
        val p = Label.read(listOf(
            "Nutrition Facts", "Serving size 2/3 cup (55g)", "Calories 230", "Total Fat 8g 10%", "Saturated Fat 1g 5%", "Trans Fat 0g",
            "Total Carbohydrate 37g 13%", "Dietary Fiber 4g 14%", "Total Sugars 12g", "Includes 10g Added Sugars 20%", "Protein 3g",
        ))!!
        check(p, 37.0, 12.0, 4.0, 3.0, 8.0)
        assertEquals(55.0, p.serving!!, 0.01)
    }

    @Test fun germanFettsaeurenAreNotFat() {
        val p = Label.read(listOf(
            "Nährwerte pro 100 g", "Brennwert 1500 kJ / 358 kcal", "Fett 4,2 g", "davon gesättigte Fettsäuren 0,8 g",
            "Kohlenhydrate 66 g", "davon Zucker 1,2 g", "Ballaststoffe 10 g", "Eiweiß 12 g",
        ))!!
        check(p, 66.0, 1.2, 10.0, 12.0, 4.2)
    }

    @Test fun kolvetniIsNeverFatAndBilingualRows() {
        val p = Label.read(listOf("Kolvetni / Carbohydrate 54 g", "Fita / Fat 3 g", "Prótein / Protein 8 g", "Trefjar / Fibre 5 g"))!!
        check(p, 54.0, null, 5.0, 8.0, 3.0)
    }

    @Test fun oldIcelandicProteinWord() {
        val p = Label.read(listOf("Kolvetni 20 g", "Eggjahvítuefni 6 g", "Fita 2 g"))!!
        check(p, 20.0, null, null, 6.0, 2.0)
    }

    @Test fun modelRepliesWithUnitsStillCount() {
        val p = Label.parseAi("""{"name":"Skyr","carbs":"4,0 g","sugars":4,"fibre":"0 g","protein":"11 g","fat":"0.2"}""")!!
        check(p, 4.0, 4.0, 0.0, 11.0, 0.2)
    }
}
