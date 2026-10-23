package app.dropi

import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealTag
import app.dropi.data.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseTest {
    private val mmol = GlucoseUnit.MMOL
    private val mgdl = GlucoseUnit.MGDL

    @Test
    fun conversionMatchesTheStandardThresholds() {
        // The consensus cut-offs line up in both units.
        assertEquals("70", Glucose.format(Glucose.LOW, mgdl))
        assertEquals("54", Glucose.format(Glucose.URGENT_LOW, mgdl))
        assertEquals("180", Glucose.format(Glucose.TIR_HIGH, mgdl))
        assertEquals("250", Glucose.format(Glucose.VERY_HIGH, mgdl))
        assertEquals("5.5", Glucose.format(Glucose.toMmol(100.0), mmol))
        assertEquals("7.0", Glucose.format(Glucose.toMmol(126.0), mmol))
    }

    @Test
    fun formatsOneDecimalWithAPoint() {
        assertEquals("6.4", Glucose.format(6.44, mmol))
        assertEquals("6.5", Glucose.format(6.45, mmol))
        assertEquals("10.0", Glucose.format(9.96, mmol))
    }

    @Test
    fun parsesOnlyWhatAMeterCanShow() {
        assertEquals(6.4, Glucose.parse("6.4", mmol)!!, 1e-9)
        assertEquals(5.0, Glucose.parse("5.", mmol)!!, 1e-9)
        assertEquals(100 / 18.0182, Glucose.parse("100", mgdl)!!, 1e-9)
        assertNotNullAt("1.1", mmol)
        assertNotNullAt("33.3", mmol)
        assertNotNullAt("600", mgdl)
        assertNull(Glucose.parse("0.9", mmol))
        assertNull(Glucose.parse("40", mmol))
        assertNull(Glucose.parse("700", mgdl))
        assertNull(Glucose.parse("", mmol))
    }

    private fun assertNotNullAt(s: String, u: GlucoseUnit) = assertTrue("$s should parse", Glucose.parse(s, u) != null)

    @Test
    fun keypadRules() {
        fun typeAll(keys: String, u: GlucoseUnit) = keys.fold("") { t, k -> Glucose.type(t, k, u) }
        assertEquals("12.4", typeAll("12.4", mmol))
        assertEquals("12.4", typeAll("12.45", mmol)) // one decimal only
        assertEquals("12", typeAll("123", mmol)) // two digits before the point
        assertEquals("0.5", typeAll(".5", mmol))
        assertEquals("5", typeAll("05", mmol)) // no leading zero
        assertEquals("5.", typeAll("5..", mmol))
        assertEquals("123", typeAll("1234", mgdl))
        assertEquals("12", typeAll("1.2", mgdl)) // no point in mg/dL
        assertEquals("1", Glucose.type("12", '⌫', mmol))
    }

    @Test
    fun statusFollowsTheMoment() {
        assertEquals(Status.URGENT_LOW, Glucose.status(2.9, MealTag.FASTING))
        assertEquals(Status.LOW, Glucose.status(3.5, MealTag.AFTER_MEAL))
        assertEquals(Status.BELOW_TARGET, Glucose.status(4.1, MealTag.FASTING))
        assertEquals(Status.IN_RANGE, Glucose.status(4.1, MealTag.AFTER_MEAL))
        assertEquals(Status.IN_RANGE, Glucose.status(6.8, MealTag.BEFORE_MEAL))
        assertEquals(Status.HIGH, Glucose.status(8.0, MealTag.FASTING))
        assertEquals(Status.IN_RANGE, Glucose.status(8.0, MealTag.AFTER_MEAL))
        assertEquals(Status.HIGH, Glucose.status(11.0, MealTag.AFTER_MEAL))
        assertEquals(Status.VERY_HIGH, Glucose.status(14.2, MealTag.OTHER))
        // Edges belong to the safer side: exactly 3.9 isn't low, exactly 10.0 is in range.
        assertEquals(Status.IN_RANGE, Glucose.status(3.9, MealTag.OTHER))
        assertEquals(Status.IN_RANGE, Glucose.status(10.0, MealTag.AFTER_MEAL))
        assertTrue(Glucose.inTir(3.9))
        assertTrue(Glucose.inTir(10.0))
        assertFalse(Glucose.inTir(10.1))
    }
}
