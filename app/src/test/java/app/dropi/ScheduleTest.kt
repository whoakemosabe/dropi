package app.dropi

import app.dropi.data.CheckState
import app.dropi.data.MealTag
import app.dropi.data.OneOff
import app.dropi.data.Reading
import app.dropi.data.Schedule
import app.dropi.data.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private fun at(h: Int, m: Int = 0, d: Int = 23) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun r(t: Long, tag: MealTag, id: Long = t) = Reading(id = id, at = t, mmol = 6.0, tag = tag)
    private val slots = Schedule.defaults() // 07:30 fasting, 12:00 + 18:00 before meal, 22:00 bedtime

    @Test
    fun todaysChecksAreMarked() {
        val readings = listOf(r(at(7, 20), MealTag.FASTING), r(at(12, 40), MealTag.BEFORE_MEAL))
        val c = Schedule.today(slots, readings, at(18, 30), zone)
        assertEquals(listOf(CheckState.DONE, CheckState.DONE, CheckState.DUE, CheckState.UPCOMING), c.map { it.state })
        // Later the same evening the 18:00 one is missed.
        assertEquals(CheckState.MISSED, Schedule.today(slots, readings, at(19, 45), zone)[2].state)
    }

    @Test
    fun oneReadingAnswersOneCheck() {
        // Two checks 30 min apart, one reading between them: only the closer one is done.
        val two = listOf(Slot("a", 12 * 60, MealTag.BEFORE_MEAL), Slot("b", 12 * 60 + 30, MealTag.BEFORE_MEAL))
        val c = Schedule.today(two, listOf(r(at(12, 5), MealTag.BEFORE_MEAL)), at(13), zone)
        assertEquals(CheckState.DONE, c[0].state)
        assertTrue(c[1].state != CheckState.DONE)
    }

    @Test
    fun nextCheckRollsToTomorrow() {
        // Past 23:30 the 22:00 check is missed, so the next one is tomorrow morning.
        val n = Schedule.next(slots, emptyList(), at(23, 45), zone)!!
        assertEquals("fasting", n.slot.id)
        assertEquals(at(7, 30, 24), n.at)
        assertNull(Schedule.next(emptyList(), emptyList(), at(9), zone))
    }

    @Test
    fun alarmIsTheSoonestReminder() {
        assertEquals(at(12), Schedule.nextAlarm(slots, emptyList(), true, at(8), zone))
        val snooze = OneOff(at(8, 10), MealTag.FASTING, OneOff.Kind.SNOOZE, at(8))
        assertEquals(at(8, 10), Schedule.nextAlarm(slots, listOf(snooze), true, at(8), zone))
        // Reminders off: only one-offs count.
        assertNull(Schedule.nextAlarm(slots, emptyList(), false, at(8), zone))
        assertEquals(at(8, 10), Schedule.nextAlarm(slots, listOf(snooze), false, at(8), zone))
    }

    @Test
    fun earlyReadingSkipsTheReminder() {
        assertEquals(1, Schedule.dueSlots(slots, emptyList(), at(12), zone).size)
        assertEquals(0, Schedule.dueSlots(slots, listOf(r(at(11, 40), MealTag.BEFORE_MEAL)), at(12), zone).size)
        assertEquals(0, Schedule.dueSlots(slots, emptyList(), at(12, 45), zone).size) // too late to nag
    }

    @Test
    fun beforeMealSetsAfterMealAndAnswersOlderReminders() {
        val old = OneOff(at(12, 10), MealTag.BEFORE_MEAL, OneOff.Kind.SNOOZE, at(12))
        val out = Schedule.afterSave(listOf(old), r(at(12, 5), MealTag.BEFORE_MEAL), true, 120, at(12, 5))
        assertEquals(1, out.size)
        assertEquals(OneOff.Kind.AFTER_MEAL, out[0].kind)
        assertEquals(at(14, 5), out[0].at)
        // A reading logged hours late doesn't set one in the past.
        assertTrue(Schedule.afterSave(emptyList(), r(at(9), MealTag.BEFORE_MEAL), true, 120, at(15)).isEmpty())
    }

    @Test
    fun slotsRoundTripAsJson() {
        val back = Schedule.slotsFromJson(Schedule.slotsToJson(slots))!!
        assertEquals(slots, back)
        assertEquals("Before lunch", back[1].name)
        assertEquals("Before dinner", back[2].name)
    }
}
