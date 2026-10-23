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
    fun readingsFindTheirCheck() {
        // Bedtime logged early, tagged by the clock as before a meal: still the bedtime check.
        val c = Schedule.today(slots, listOf(r(at(21, 20), MealTag.BEFORE_MEAL)), at(22, 30), zone)
        assertEquals(CheckState.DONE, c[3].state)
        // An after-meal reading an hour after the before-lunch time isn't that check.
        val d = Schedule.today(slots, listOf(r(at(13, 0), MealTag.AFTER_MEAL)), at(13, 5), zone)
        assertEquals(CheckState.DUE, d[1].state)
        // Lunch logged late (13:50, before-meal) still answers lunch, not dinner.
        val e = Schedule.today(slots, listOf(r(at(13, 50), MealTag.BEFORE_MEAL)), at(14), zone)
        assertEquals(CheckState.DONE, e[1].state)
        assertEquals(CheckState.UPCOMING, e[2].state)
    }

    @Test
    fun newReadingTakesTheOpenChecksMoment() {
        assertEquals(MealTag.BEFORE_MEAL, Schedule.momentFor(at(12, 20), slots, emptyList(), zone))
        assertEquals(MealTag.BEDTIME, Schedule.momentFor(at(21, 15), slots, emptyList(), zone))
        // Lunch already done: 12:30 has no open check near it.
        assertNull(Schedule.momentFor(at(12, 30), slots, listOf(r(at(12, 0), MealTag.BEFORE_MEAL)), zone))
        assertNull(Schedule.momentFor(at(15, 0), slots, emptyList(), zone))
    }

    @Test
    fun afterMealCheckFollowsTheMeal() {
        val withAfter = slots + Slot("after", 14 * 60, MealTag.AFTER_MEAL)
        val lunch = r(at(12, 40), MealTag.BEFORE_MEAL)
        val c = Schedule.today(withAfter, listOf(lunch), at(14, 20), zone).first { it.slot.id == "after" }
        assertEquals(at(14, 40), c.at)
        assertEquals(CheckState.UPCOMING, c.state)
        assertEquals(lunch, c.anchor)
        // Its own after-meal reminder covers it, so the slot doesn't remind twice.
        assertTrue(Schedule.dueSlots(withAfter, listOf(lunch), at(14, 40), zone, 120, afterMealReminderOn = true).none { it.first.id == "after" })
        assertTrue(Schedule.dueSlots(withAfter, listOf(lunch), at(14, 40), zone, 120, afterMealReminderOn = false).any { it.first.id == "after" })
    }

    @Test
    fun bedtimeHasALongerWindow() {
        assertEquals(CheckState.DUE, Schedule.today(slots, emptyList(), at(23, 50), zone)[3].state)
        val tight = slots.map { if (it.tag == MealTag.BEDTIME) it.copy(window = 30) else it }
        assertEquals(CheckState.MISSED, Schedule.today(tight, emptyList(), at(23, 50), zone)[3].state)
    }

    @Test
    fun learnsUsualTimes() {
        val late = (17..22).map { d -> r(at(23, 10, d), MealTag.BEDTIME) }
        val m = Schedule.moves(slots, late, at(12), zone)
        assertEquals(1, m.size)
        assertEquals("bedtime", m[0].slot.id)
        assertEquals(23 * 60 + 10, m[0].minute)
        assertEquals(6, m[0].days)
        // Close to the plan already: nothing to suggest.
        assertTrue(Schedule.moves(slots, (17..22).map { d -> r(at(22, 10, d), MealTag.BEDTIME) }, at(12), zone).isEmpty())
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
