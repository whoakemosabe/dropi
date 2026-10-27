package app.dropi.data

import app.dropi.tr
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** One value from a continuous glucose sensor (CGM), in mmol/L. */
data class SensorPoint(val at: Long, val mmol: Double)

/**
 * Which way the sensor says glucose is heading, with the usual CGM steps (in mg/dL a minute:
 * over 3 fast, 2–3 steady, 1–2 slowly, under 1 flat).
 */
enum class Arrow(val glyph: String, val words: String) {
    UP_FAST("⇈", "rising fast"),
    UP("↑", "rising"),
    UP_SLOW("↗", "rising slowly"),
    FLAT("→", "steady"),
    DOWN_SLOW("↘", "falling slowly"),
    DOWN("↓", "falling"),
    DOWN_FAST("⇊", "falling fast");

    /** [words] in the app's language, for showing. [words] itself stays English (it goes into AI prompts). */
    val label: String get() = when (this) {
        UP_FAST -> tr("rising fast")
        UP -> tr("rising")
        UP_SLOW -> tr("rising slowly")
        FLAT -> tr("steady")
        DOWN_SLOW -> tr("falling slowly")
        DOWN -> tr("falling")
        DOWN_FAST -> tr("falling fast")
    }

    companion object {
        fun of(mmolPerMin: Double): Arrow {
            val mg = mmolPerMin * Glucose.MGDL_PER_MMOL
            return when {
                mg > 3 -> UP_FAST
                mg > 2 -> UP
                mg > 1 -> UP_SLOW
                mg >= -1 -> FLAT
                mg >= -2 -> DOWN_SLOW
                mg >= -3 -> DOWN
                else -> DOWN_FAST
            }
        }
    }
}

/** What a heads-up is about. */
enum class Heads { LOW_NOW, HEADING_LOW }

data class HeadsUp(val kind: Heads, val mmol: Double, val minutes: Int)

/**
 * The live picture from a sensor: the latest value, the trend over the last quarter hour and a
 * straight-line look ahead. Plain arithmetic on the sensor's own numbers: an estimate to glance
 * at, never a diagnosis.
 */
object Trend {
    private const val MIN = 60_000L

    /** Sensor data older than this isn't "live" any more. */
    const val FRESH_MS = 12 * MIN

    /** How long after one heads-up another can come (unless it's now actually low). */
    const val QUIET_MS = 30 * MIN

    fun latest(points: List<SensorPoint>): SensorPoint? = points.maxByOrNull { it.at }

    fun fresh(points: List<SensorPoint>, now: Long): Boolean = latest(points)?.let { now - it.at <= FRESH_MS } ?: false

    /**
     * Slope in mmol/L a minute: a least-squares line through the last 15 minutes (at least two
     * points 4+ minutes apart), so one noisy value doesn't swing the arrow. Null without enough data.
     */
    fun slope(points: List<SensorPoint>): Double? = slopeOver(points, 16)

    /** The same over the last [minutes] minutes. */
    private fun slopeOver(points: List<SensorPoint>, minutes: Int): Double? {
        val last = latest(points) ?: return null
        val window = points.filter { it.at in (last.at - minutes * MIN)..last.at }
        if (window.size < 2) return null
        val span = window.maxOf { it.at } - window.minOf { it.at }
        if (span < 4 * MIN) return null
        val xs = window.map { (it.at - last.at) / MIN.toDouble() }
        val ys = window.map { it.mmol }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) null else num / den
    }

    fun arrow(points: List<SensorPoint>): Arrow? = slope(points)?.let { Arrow.of(it) }

    /** How quickly an easing change dies away (minutes). */
    private const val EASE = 15.0

    /**
     * Whether the change is easing off: the last 8 minutes run at under 70 % of the quarter
     * hour's rate (or the other way). Then the look-ahead curves off instead of running straight,
     * which is what usually happens and what stops false warnings; a change that holds or speeds
     * up stays a straight line, so real lows are never warned later than before.
     */
    /** The last 8 minutes' rate when the change is easing off, else null (see above). */
    private fun easing(points: List<SensorPoint>, s: Double): Double? {
        val recent = slopeOver(points, 8) ?: return null
        return recent.takeIf { it * s < 0 || abs(it) < abs(s) * 0.7 }
    }

    /** The change expected [t] minutes ahead (mmol/L), or null without a slope. */
    fun change(points: List<SensorPoint>, t: Double): Double? {
        val s = slope(points) ?: return null
        // Easing: from today's slower rate, dying away. Otherwise the straight line.
        val r = easing(points, s) ?: return s * t
        return r * EASE * (1 - exp(-t / EASE))
    }

    /** Where it's heading [minutes] ahead, kept within what a sensor can read. */
    fun ahead(points: List<SensorPoint>, minutes: Int): Double? {
        val last = latest(points) ?: return null
        val d = change(points, minutes.toDouble()) ?: return null
        return (last.mmol + d).coerceIn(Glucose.MIN_MMOL, Glucose.MAX_MMOL)
    }

    /**
     * A heads-up worth sending now, or null:
     * - under 3.9 now → low now (again after 30 min, or sooner if the last one was only a warning)
     * - falling, and the line crosses 3.9 within 20 minutes → heading low (once per 30 min)
     * Only for fresh data.
     */
    fun headsUp(points: List<SensorPoint>, now: Long, lastAt: Long, lastKind: Heads?): HeadsUp? {
        if (!fresh(points, now)) return null
        val last = latest(points) ?: return null
        val quiet = now - lastAt < QUIET_MS
        if (last.mmol < Glucose.LOW) {
            if (quiet && lastKind == Heads.LOW_NOW) return null
            return HeadsUp(Heads.LOW_NOW, last.mmol, 0)
        }
        val s = slope(points) ?: return null
        if (s >= 0) return null
        val drop = last.mmol - Glucose.LOW
        val r = easing(points, s)
        val minutes = if (r != null) {
            // An easing fall only gets so far: its current rate × 15 at most. Short of 3.9 (or
            // already turning up), no warning.
            if (r >= 0) return null
            val k = drop / (-r * EASE)
            if (k >= 1) return null
            -EASE * ln(1 - k)
        } else drop / -s
        if (minutes > 20) return null
        if (quiet) return null
        // Round to 5 minutes; "about 5 min" at the soonest.
        val rounded = ((minutes / 5).roundToInt() * 5).coerceAtLeast(5)
        return HeadsUp(Heads.HEADING_LOW, last.mmol, rounded)
    }

    /** Arrow angle for drawing: 0° flat, +90° straight up (3 mg/dL a minute or more). */
    fun degrees(mmolPerMin: Double): Float {
        val mg = mmolPerMin * Glucose.MGDL_PER_MMOL
        return (mg * 30).coerceIn(-90.0, 90.0).toFloat().let { if (abs(it) < 8f) 0f else it }
    }
}


/** A stretch of time from Health Connect: steps ([count]) or a sleep session (count 0). */
data class Span(val start: Long, val end: Long, val count: Long)
