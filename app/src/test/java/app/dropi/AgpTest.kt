package app.dropi

import app.dropi.data.Agp
import app.dropi.data.MealTag
import app.dropi.data.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AgpTest {
    private val zone = ZoneId.of("Atlantic/Reykjavik")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun quantiles() {
        val v = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(3.0, Agp.q(v, 0.5), 1e-9)
        assertEquals(2.0, Agp.q(v, 0.25), 1e-9)
        assertEquals(1.4, Agp.q(v, 0.1), 1e-9)
    }

    @Test
    fun hoursNeedEnoughValues() {
        // Ten mornings at 07:30, nothing in the afternoon.
        val rs = (1..10).map { d -> Reading(at = at(d, 7, 30), mmol = 5.0 + d * 0.1, tag = MealTag.FASTING) }
        val p = Agp.profile(rs, emptyList(), zone)
        val seven = p[7]
        assertNotNull(seven)
        assertEquals(5.55, seven!!.p50, 1e-6)
        // 06:00 and 08:00 borrow from their neighbour, 15:00 has nothing.
        assertNotNull(p[6]); assertNotNull(p[8])
        assertNull(p[15])
    }
}
