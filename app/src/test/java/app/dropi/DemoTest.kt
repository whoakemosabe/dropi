package app.dropi

import app.dropi.data.Demo
import app.dropi.data.Foods
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DemoTest {
    @Test
    fun aBelievableMonth() {
        val now = 1_792_800_000_000L
        val d = Demo.make(now, ZoneId.of("Atlantic/Reykjavik"))
        val rs = d.readings
        val sensor = d.sensor
        assertTrue(rs.size in 90..200)
        assertTrue(rs.all { it.at <= now && it.mmol in 2.6..22.0 })
        assertTrue(rs.any { it.mmol < 3.9 } && rs.any { it.mmol > 10.0 })
        // Meals with after-meal readings, so food scores and estimates have something to show.
        assertTrue(Foods.scores(rs.mapIndexed { i, r -> r.copy(id = i + 1L) }, emptyList(), d.meals.map { m -> m.copy(before = null) }).count { it.ranked } >= 5)
        // Breakfast is eaten a while after the fasting reading, as its own entry.
        assertTrue(d.meals.count { it.name == "Breakfast" } >= 20)
        assertTrue(sensor.size in 280..300)
    }
}
