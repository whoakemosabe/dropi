package app.dropi

import app.dropi.data.A1c
import app.dropi.data.Analytics
import app.dropi.data.Backup
import app.dropi.data.InsulinKind
import app.dropi.data.LabA1c
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Schedule
import app.dropi.data.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MoreTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun backupRoundTripsWithThePassword() {
        val rs = listOf(
            Reading(at = at(23, 7), mmol = 5.6, tag = MealTag.FASTING, note = "ok", carbs = 40, insulin = 4.5, insulinKind = InsulinKind.RAPID, meds = true, exercise = 30),
            Reading(at = at(23, 13), mmol = 9.1, tag = MealTag.AFTER_MEAL),
        )
        val c = Backup.Contents(rs, Schedule.defaults(), Targets(beforeHigh = 7.8), listOf(LabA1c(LocalDate.of(2026, 9, 1), 7.1)), 1L)
        val file = Backup.seal(Backup.toJson(c), "correct horse".toCharArray(), iterations = 20_000)
        val back = Backup.fromJson(Backup.open(file, "correct horse".toCharArray()))
        assertEquals(rs.map { it.copy(id = 0) }, back.readings)
        assertEquals(Schedule.defaults(), back.slots)
        assertEquals(7.8, back.targets!!.beforeHigh, 1e-9)
        assertEquals(7.1, back.labA1c.single().percent, 1e-9)
        try {
            Backup.open(file, "wrong".toCharArray())
            fail("a wrong password must not open the file")
        } catch (e: Backup.WrongPassword) { }
        try {
            Backup.open("not a backup at all".toByteArray(), "x".toCharArray())
            fail()
        } catch (e: Backup.NotABackup) { }
    }

    @Test
    fun targetsStaySensible() {
        val t = Targets(beforeLow = 2.0, beforeHigh = 3.0, afterHigh = 20.0).safe()
        assertEquals(3.9, t.beforeLow, 1e-9) // never below the low threshold
        assertEquals(4.4, t.beforeHigh, 1e-9) // always above the low edge
        assertEquals(13.9, t.afterHigh, 1e-9)
        assertTrue(Targets().safe().isDefault)
    }

    @Test
    fun a1cUnits() {
        // 7.0% is 53 mmol/mol; 48 mmol/mol is 6.5%.
        assertEquals(53.0, A1c.toMmolMol(7.0), 0.5)
        assertEquals(6.5, A1c.fromMmolMol(48.0), 0.05)
    }

    @Test
    fun recapIsSundayEvening() {
        // 25 Oct 2026 is a Sunday.
        assertEquals(at(25, 19), Schedule.nextRecap(at(23, 10), zone))
        assertEquals(LocalDate.of(2026, 10, 19), Schedule.recapWeek(at(25, 19, 5), zone))
        assertNull(Schedule.recapWeek(at(25, 18), zone))
        assertNull(Schedule.recapWeek(at(24, 20), zone))
    }

    @Test
    fun carbsPairWithTheirAfterMealReading() {
        val rs = listOf(
            Reading(1, at(23, 12), 6.0, MealTag.BEFORE_MEAL, carbs = 80),
            Reading(2, at(23, 14), 11.0, MealTag.AFTER_MEAL),
            Reading(3, at(23, 18), 6.0, MealTag.BEFORE_MEAL, carbs = 30),
            Reading(4, at(23, 20), 8.0, MealTag.AFTER_MEAL),
            Reading(5, at(23, 22), 7.0, MealTag.AFTER_MEAL), // too long after, no carbs logged on it
        )
        assertEquals(listOf(80 to 11.0, 30 to 8.0), Analytics.carbPairs(rs))
    }
}
