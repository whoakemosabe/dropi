package app.dropi

import app.dropi.data.Ask
import app.dropi.data.AskTools
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AskToolsTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun readsThePickLeniently() {
        val p = AskTools.parsePlan("Sure! {\"tool\":\"readings\",\"days\":30,\"moment\":\"after eating\",\"above\":10}")!!
        assertEquals("readings", p.tool)
        assertEquals(30, p.days)
        assertEquals(MealTag.AFTER_MEAL, p.moment)
        assertEquals(10.0, p.above!!, 1e-9)
        assertNull(AskTools.parsePlan("no json here"))
        assertNull(AskTools.parsePlan("{\"tool\":\"delete_everything\"}"))
        // Out-of-range days are kept sensible.
        assertEquals(90, AskTools.parsePlan("{\"tool\":\"summary\",\"days\":400}")!!.days)
    }

    @Test fun runsReadingsAboveAValue() {
        val rs = (0 until 10).map { i ->
            Reading(at = today.minusDays(i.toLong()).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), mmol = if (i % 2 == 0) 11.0 else 7.0, tag = MealTag.AFTER_MEAL)
        }
        val c = Ask.Ctx(rs, emptyList(), GlucoseUnit.MMOL, Targets(), today, zone)
        val out = AskTools.run(AskTools.parsePlan("{\"tool\":\"readings\",\"days\":14,\"above\":10}")!!, c)!!
        assertTrue(out, out.contains("5 readings match"))
        // A threshold in mg/dL means the same.
        val mg = AskTools.run(AskTools.parsePlan("{\"tool\":\"readings\",\"days\":14,\"above\":180}")!!, c)!!
        assertTrue(mg, mg.contains("5 readings match"))
        assertNull(AskTools.run(AskTools.parsePlan("{\"tool\":\"none\"}")!!, c))
    }
}
