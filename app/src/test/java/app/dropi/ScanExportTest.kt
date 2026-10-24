package app.dropi

import app.dropi.data.GlucoseUnit
import app.dropi.data.MealTag
import app.dropi.data.MeterScan
import app.dropi.data.MeterScan.Piece
import app.dropi.data.Reading
import app.dropi.work.Export
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScanExportTest {
    @Test
    fun theBigNumberOnTheMeterWins() {
        val photo = listOf(Piece("12:45", 30), Piece("23.10", 28), Piece("5.8", 140), Piece("mmol/L", 25))
        assertEquals(5.8, MeterScan.pick(photo, GlucoseUnit.MMOL)!!, 1e-9)
    }

    @Test
    fun aMissedDecimalPointIsReadAsMmol() {
        assertEquals(5.8, MeterScan.pick(listOf(Piece("58", 120)), GlucoseUnit.MMOL)!!, 1e-9)
        // The same digits on a mg/dL meter.
        assertEquals(58 / 18.0182, MeterScan.pick(listOf(Piece("58", 120)), GlucoseUnit.MGDL)!!, 1e-9)
    }

    @Test
    fun lookAlikeLettersAndNonsense() {
        assertEquals(10.0, MeterScan.pick(listOf(Piece("1O.0", 100)), GlucoseUnit.MMOL)!!, 1e-9)
        assertNull(MeterScan.pick(listOf(Piece("HI", 100), Piece("2026", 30)), GlucoseUnit.MMOL))
        assertNull(MeterScan.pick(emptyList(), GlucoseUnit.MMOL))
    }

    @Test
    fun csvHasBothUnitsAndSafeNotes() {
        val zone = ZoneId.of("Atlantic/Reykjavik")
        val at = LocalDateTime.of(2026, 10, 23, 7, 30).atZone(zone).toInstant().toEpochMilli()
        val breakfast = app.dropi.data.MealEntry(
            at = at + 40 * 60_000L, name = "Breakfast",
            meal = app.dropi.data.Meal(listOf(app.dropi.data.MealItem("toast", "2 slices", carbs = 30))),
        )
        val csv = Export.csv(
            listOf(Reading(1, at, 5.5, MealTag.FASTING, "toast, jam", carbs = 45, insulin = 4.5, insulinKind = app.dropi.data.InsulinKind.RAPID)),
            zone, listOf(breakfast),
        )
        assertEquals(
            "date,time,mmol_per_l,mg_per_dl,moment,carbs_g,insulin_units,insulin_kind,tablets,exercise_min,foods,note\n" +
                "2026-10-23,07:30,5.5,99,Fasting (on waking),45,4.5,Rapid,,,,\"toast, jam\"\n" +
                "2026-10-23,08:10,,,Breakfast,30,,,,,Toast (2 slices),\n",
            csv,
        )
        assertEquals("'=SUM(A1)", Export.quote("=SUM(A1)"))
        assertEquals("\"say \"\"hi\"\"\"", Export.quote("say \"hi\""))
    }
}
