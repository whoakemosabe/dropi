package app.dropi.data

import java.time.LocalDate
import java.time.ZoneId

/** Numbers for a set of readings (a day, a week). Averages are of mmol/L values. */
data class Summary(
    val count: Int,
    val average: Double?,
    val min: Double?,
    val max: Double?,
    /** Share of readings in 3.9–10.0 mmol/L, 0..100, or null with no readings. */
    val inRangePct: Int?,
    val lows: Int,
)

object Stats {
    fun summary(readings: List<Reading>): Summary {
        if (readings.isEmpty()) return Summary(0, null, null, null, null, 0)
        val values = readings.map { it.mmol }
        val inRange = values.count { Glucose.inTir(it) }
        return Summary(
            count = values.size,
            average = values.average(),
            min = values.min(),
            max = values.max(),
            inRangePct = Math.round(inRange * 100.0 / values.size).toInt(),
            lows = values.count { it < Glucose.LOW },
        )
    }

    fun onDay(readings: List<Reading>, day: LocalDate, zone: ZoneId) = readings.filter { it.date(zone) == day }

    /**
     * Days in a row with at least one reading, ending today, or yesterday when today has none yet
     * (so the streak doesn't look lost first thing in the morning).
     */
    fun streak(readings: List<Reading>, today: LocalDate, zone: ZoneId): Int {
        val days = readings.map { it.date(zone) }.toHashSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}
