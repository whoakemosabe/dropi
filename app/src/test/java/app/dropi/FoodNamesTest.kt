package app.dropi

import app.dropi.data.Basics
import app.dropi.data.FoodNames
import app.dropi.data.Gi
import app.dropi.data.Portions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Icelandic food words find Dropi's own foods; portions read in Icelandic. */
class FoodNamesTest {

    @Test
    fun everyTargetIsAFoodDropiKnows() {
        val unknown = FoodNames.targets.filter { Basics.find(it) == null && Gi.lookup(it) == null }
        assertTrue("not in Basics or Gi: $unknown", unknown.isEmpty())
    }

    @Test
    fun icelandicNamesFindTheBasics() {
        assertEquals("rice", Basics.find("hrísgrjón")?.name)
        assertEquals("rice", Basics.find("soðin hrísgrjón")?.name)
        assertEquals("potato", Basics.find("kartöflur")?.name)
        assertEquals("sweet potato", Basics.find("sætar kartöflur")?.name)
        assertEquals("toast", Basics.find("ristað brauð með smjöri")?.name)
        assertEquals("banana", Basics.find("banani með hnetusmjöri")?.name)
        assertEquals("porridge", Basics.find("hafragrautur með rúsínum")?.name)
        assertEquals("skyr", Basics.find("skyr með rjóma")?.name)
        assertEquals("grjónagrautur", Basics.find("grjónagraut")?.name)
        assertEquals("cola", Basics.find("kók")?.name)
        assertEquals("egg", Basics.find("2 soðin egg")?.name)
        assertEquals("cake", Basics.find("súkkulaðikaka")?.name)
    }

    @Test
    fun withoutAccentsToo() {
        assertEquals("rice", FoodNames.english("hrisgrjon"))
        assertEquals("potato", FoodNames.english("kartoflur"))
        // Short words need their accents: "is" isn't ice cream.
        assertNull(FoodNames.english("is"))
        assertEquals("ice cream", FoodNames.english("ís"))
    }

    @Test
    fun noFalseMatchesInsideOtherWords() {
        assertNull(FoodNames.english("kókosbolla"))
        assertFalse(Gi.isDrink("kókosbolla"))
        assertEquals("orange", FoodNames.english("appelsína"))
        assertEquals("appelsín", FoodNames.english("appelsín"))
    }

    @Test
    fun speedAndDrinks() {
        assertEquals(78, Gi.lookup("soðnar kartöflur"))
        assertEquals(87, Gi.lookup("kartöflumús"))
        assertEquals(41, Gi.lookup("eplasafi"))
        assertTrue(Gi.isDrink("appelsínusafi"))
        assertTrue(Gi.isDrink("kók"))
        assertTrue(Gi.isSugarFree("pepsi max"))
        // English names work as before.
        assertEquals(68, Gi.lookup("brown rice"))
        assertEquals("egg", Basics.find("hard boiled egg")?.name)
    }

    @Test
    fun portionsInIcelandic() {
        assertEquals("1 bolli", Portions.icelandic("1 cup"))
        assertEquals("3 sneiðar", Portions.icelandic("1 slice", 3.0))
        assertEquals("21 sneið", Portions.icelandic("1 slice", 21.0))
        assertEquals("½ dós", Portions.icelandic("½ can"))
        assertEquals("2 egg", Portions.icelandic("2 eggs"))
        assertEquals("1 meðalstórt", Portions.icelandic("1 medium"))
        assertEquals("1 lítill pakki", Portions.icelandic("1 small box"))
        assertEquals("1 stór banani", Portions.icelandic("1 large banana"))
        assertEquals("1 venjulegur skammtur", Portions.icelandic("1 normal portion"))
        assertEquals("½ × 1 bolli", Portions.icelandic("1 cup", 0.5))
        assertEquals("2 × 2 egg", Portions.icelandic("2 eggs", 2.0))
        assertNull(Portions.icelandic("1 cup cooked"))
        assertNotNull(Portions.icelandic("4 squares"))
        // In English nothing changes.
        assertEquals("1 cup", Portions.shown("1 cup", 1.0) { "1 cup" })
    }
}
