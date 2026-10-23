package app.dropi.data

import java.time.Instant
import java.time.ZoneId

/**
 * The "typical day" (ambulatory glucose profile): every day in a period laid over one 24-hour
 * clock. For each hour, the middle value and the spread (the middle half, and 8 in 10). Finger
 * pricks are few per hour, so each hour also draws on its neighbours; sensor values count too.
 */
object Agp {
    data class Hour(val hour: Int, val n: Int, val p10: Double, val p25: Double, val p50: Double, val p75: Double, val p90: Double)

    /** Hours with too few values to say anything are left out (null), so the line has gaps, not guesses. */
    fun profile(readings: List<Reading>, sensor: List<SensorPoint>, zone: ZoneId, minValues: Int = 4): List<Hour?> {
        val byHour = Array(24) { ArrayList<Double>() }
        fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour
        readings.forEach { byHour[hourOf(it.at)] += it.mmol }
        // Sensor points are one every few minutes; one per 15 minutes is enough and keeps them
        // from drowning out the finger pricks.
        sensor.groupBy { it.at / (15 * 60_000L) }.values.forEach { g -> byHour[hourOf(g.first().at)] += g.map { it.mmol }.average() }
        return (0 until 24).map { h ->
            val vals = (listOf(h - 1, h, h + 1)).flatMap { byHour[(it + 24) % 24] }.sorted()
            if (vals.size < minValues) null
            else Hour(h, vals.size, q(vals, 0.10), q(vals, 0.25), q(vals, 0.50), q(vals, 0.75), q(vals, 0.90))
        }
    }

    /** Quantile of sorted values, linear between neighbours. */
    fun q(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = p * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }
}
