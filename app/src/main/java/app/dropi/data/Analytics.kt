package app.dropi.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Shares of readings in each consensus band, 0..100 (they add up to 100, give or take rounding). */
data class Bands(val veryLow: Double, val low: Double, val inRange: Double, val high: Double, val veryHigh: Double)

data class DayStat(val day: LocalDate, val count: Int, val average: Double, val min: Double, val max: Double)

data class TagStat(val tag: MealTag, val count: Int, val average: Double)

data class Report(
    val days: Int,
    val from: LocalDate,
    val to: LocalDate,
    val readings: List<Reading>,
    val count: Int,
    val daysLogged: Int,
    val mean: Double?,
    val sd: Double?,
    /** Coefficient of variation, %. Consensus: 36% or less is stable. */
    val cv: Double?,
    /** Glucose Management Indicator, the A1C estimated from the average (only with enough data). */
    val gmiPercent: Double?,
    val gmiMmolMol: Double?,
    val bands: Bands?,
    val byDay: List<DayStat>,
    val byTag: List<TagStat>,
    val lows: Int,
    /** Time in range in the period before, for the comparison. */
    val previousInRange: Double?,
)

enum class InsightKind { GOOD, ATTENTION, INFO }

data class Insight(val kind: InsightKind, val text: String)

object Analytics {
    /** GMI needs a fair spread of data; fingersticks are sparser than a sensor, so ask for a week. */
    const val GMI_MIN_DAYS = 7
    const val GMI_MIN_READINGS = 14

    fun bands(values: List<Double>): Bands? {
        if (values.isEmpty()) return null
        val n = values.size.toDouble()
        fun pct(f: (Double) -> Boolean) = values.count(f) * 100.0 / n
        return Bands(
            veryLow = pct { it < Glucose.URGENT_LOW },
            low = pct { it >= Glucose.URGENT_LOW && it < Glucose.LOW },
            inRange = pct { Glucose.inTir(it) },
            high = pct { it > Glucose.TIR_HIGH && it <= Glucose.VERY_HIGH },
            veryHigh = pct { it > Glucose.VERY_HIGH },
        )
    }

    /** GMI (Bergenstal et al. 2018): % = 3.31 + 0.02392 × mean mg/dL; mmol/mol = 12.71 + 4.70587 × mean mmol/L. */
    fun gmiPercent(meanMmol: Double) = 3.31 + 0.02392 * Glucose.toMgdl(meanMmol)
    fun gmiMmolMol(meanMmol: Double) = 12.71 + 4.70587 * meanMmol

    fun sd(values: List<Double>): Double? {
        if (values.size < 2) return null
        val m = values.average()
        // Sample standard deviation.
        return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
    }

    /** The [days] up to and including [today]. */
    fun report(all: List<Reading>, days: Int, today: LocalDate, zone: ZoneId): Report {
        val from = today.minusDays((days - 1).toLong())
        val inPeriod = all.filter { val d = it.date(zone); !d.isBefore(from) && !d.isAfter(today) }.sortedBy { it.at }
        val values = inPeriod.map { it.mmol }
        val mean = values.takeIf { it.isNotEmpty() }?.average()
        val sd = sd(values)
        val byDay = inPeriod.groupBy { it.date(zone) }.map { (d, rs) ->
            val v = rs.map { it.mmol }
            DayStat(d, v.size, v.average(), v.min(), v.max())
        }.sortedBy { it.day }
        val enough = byDay.size >= GMI_MIN_DAYS && values.size >= GMI_MIN_READINGS
        val prevFrom = from.minusDays(days.toLong())
        val prev = all.filter { val d = it.date(zone); !d.isBefore(prevFrom) && d.isBefore(from) }.map { it.mmol }
        return Report(
            days = days,
            from = from,
            to = today,
            readings = inPeriod,
            count = values.size,
            daysLogged = byDay.size,
            mean = mean,
            sd = sd,
            cv = if (mean != null && sd != null && mean > 0) sd / mean * 100 else null,
            gmiPercent = if (enough && mean != null) gmiPercent(mean) else null,
            gmiMmolMol = if (enough && mean != null) gmiMmolMol(mean) else null,
            bands = bands(values),
            byDay = byDay,
            byTag = inPeriod.groupBy { it.tag }.map { (t, rs) -> TagStat(t, rs.size, rs.map { it.mmol }.average()) }
                .sortedBy { it.tag.ordinal },
            lows = values.count { it < Glucose.LOW },
            previousInRange = if (prev.size >= 10) bands(prev)?.inRange else null,
        )
    }

    /**
     * Plain-language findings, only where there's enough data behind them. [v] formats a
     * mmol/L value in the person's unit (e.g. "7.8" or "140").
     */
    fun insights(r: Report, targets: Targets, v: (Double) -> String): List<Insight> {
        val out = ArrayList<Insight>()
        if (r.count < 3) return out
        val period = if (r.days == 7) "this week" else "in the last ${r.days} days"

        // Lows first: they matter most.
        if (r.lows > 0) {
            val lowReadings = r.readings.filter { it.mmol < Glucose.LOW }
            val common = lowReadings.groupBy { it.tag }.maxByOrNull { it.value.size }
            val where = if (common != null && r.lows >= 2 && common.value.size * 2 > r.lows) ", mostly ${common.key.label.lowercase()}" else ""
            out += Insight(InsightKind.ATTENTION, "${r.lows} low ${if (r.lows == 1) "reading" else "readings"} $period$where.")
        } else if (r.count >= 10) {
            out += Insight(InsightKind.GOOD, "No lows $period.")
        }

        val fasting = r.byTag.firstOrNull { it.tag == MealTag.FASTING }
        if (fasting != null && fasting.count >= 3) {
            val (lo, hi) = targets.forTag(MealTag.FASTING)
            out += when {
                fasting.average > hi -> Insight(InsightKind.ATTENTION, "Fasting readings average ${v(fasting.average)}, above the ${v(lo)}–${v(hi)} target.")
                fasting.average < lo -> Insight(InsightKind.INFO, "Fasting readings average ${v(fasting.average)}, a little under the ${v(lo)}–${v(hi)} target.")
                else -> Insight(InsightKind.GOOD, "Fasting readings are on target, averaging ${v(fasting.average)}.")
            }
        }

        val after = r.readings.filter { it.tag == MealTag.AFTER_MEAL }
        if (after.size >= 3) {
            val over = after.count { it.mmol > targets.afterHigh }
            out += when {
                over * 2 >= after.size -> Insight(InsightKind.ATTENTION, "After-meal readings were over ${v(targets.afterHigh)} in $over of ${after.size}.")
                over == 0 -> Insight(InsightKind.GOOD, "Every after-meal reading stayed under ${v(targets.afterHigh)}.")
                else -> Insight(InsightKind.INFO, "After-meal readings were over ${v(targets.afterHigh)} in $over of ${after.size}.")
            }
        }

        val tir = r.bands?.inRange
        val prev = r.previousInRange
        if (tir != null && prev != null && r.count >= 10) {
            val d = tir.roundToInt() - prev.roundToInt()
            if (d >= 5) out += Insight(InsightKind.GOOD, "In range ${tir.roundToInt()}% of the time, up from ${prev.roundToInt()}% before.")
            else if (d <= -5) out += Insight(InsightKind.ATTENTION, "In range ${tir.roundToInt()}% of the time, down from ${prev.roundToInt()}% before.")
        }

        val cv = r.cv
        if (cv != null && r.count >= 10) {
            out += if (cv > 36) Insight(InsightKind.ATTENTION, "Readings swing quite a bit (variability ${cv.roundToInt()}%). Steadier is 36% or less.")
            else Insight(InsightKind.GOOD, "Readings are steady (variability ${cv.roundToInt()}%, goal 36% or less).")
        }

        // The moment of the day that runs highest, when it's over its target.
        val highest = r.byTag.filter { it.count >= 3 }.maxByOrNull { it.average }
        if (highest != null && highest.tag != MealTag.FASTING && highest.average > targets.forTag(highest.tag).second) {
            out += Insight(InsightKind.INFO, "Your highest readings tend to be ${highest.tag.label.lowercase()} (average ${v(highest.average)}).")
        }

        if (r.days >= 7 && r.daysLogged * 10 >= r.days * 8) {
            out += Insight(InsightKind.GOOD, "Logged on ${r.daysLogged} of ${r.days} days. Nice and consistent.")
        }
        return out
    }
}
