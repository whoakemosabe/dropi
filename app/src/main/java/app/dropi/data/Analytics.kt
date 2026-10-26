package app.dropi.data

import app.dropi.L
import app.dropi.tr
import app.dropi.trn
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
    /** The time zone the days were counted in. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

enum class InsightKind { GOOD, ATTENTION, INFO }

/**
 * One finding. [days] are the days it's about, the most telling first (the one to show when the
 * finding is tapped); empty when it's about the period as a whole.
 */
data class Insight(val kind: InsightKind, val text: String, val days: List<LocalDate> = emptyList())

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

    const val BIG_MEAL = 60

    /** (carbs, after-meal mmol) for each after-meal reading whose meal has carbs logged. */
    fun carbPairs(readings: List<Reading>, meals: List<MealEntry> = emptyList()): List<Pair<Int, Double>> {
        val sorted = readings.sortedBy { it.at }
        return sorted.filter { it.tag == MealTag.AFTER_MEAL }.mapNotNull { after ->
            val own = after.carbs ?: MealLink.mealBefore(after, meals)?.carbs?.let { Math.round(it).toInt() }
            val carbs = own ?: sorted.lastOrNull {
                it.tag == MealTag.BEFORE_MEAL && it.at < after.at && after.at - it.at <= 3 * 3_600_000L && it.carbs != null
            }?.carbs
            carbs?.let { it to after.mmol }
        }
    }

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
            zone = zone,
        )
    }

    /**
     * Plain-language findings, only where there's enough data behind them. [v] formats a
     * mmol/L value in the person's unit (e.g. "7.8" or "140").
     */
    fun insights(r: Report, targets: Targets, meals: List<MealEntry> = emptyList(), v: (Double) -> String): List<Insight> {
        val out = ArrayList<Insight>()
        if (r.count < 3) return out
        val week = r.days == 7
        val zone = r.zone
        /** The days of [rs], in the order given, each once. */
        fun daysOf(rs: List<Reading>): List<LocalDate> = rs.map { it.date(zone) }.distinct()

        // Lows first: they matter most.
        if (r.lows > 0) {
            val lowReadings = r.readings.filter { it.mmol < Glucose.LOW }
            val common = lowReadings.groupBy { it.tag }.maxByOrNull { it.value.size }
            val mostly = common != null && r.lows >= 2 && common.value.size * 2 > r.lows
            val text = when {
                week && mostly -> trn(r.lows, "{0} low reading this week, mostly {1}.", "{0} low readings this week, mostly {1}.", common!!.key.label.lowercase(L.locale))
                week -> trn(r.lows, "{0} low reading this week.", "{0} low readings this week.")
                mostly -> trn(r.lows, "{0} low reading in the last {1} days, mostly {2}.", "{0} low readings in the last {1} days, mostly {2}.", r.days, common!!.key.label.lowercase(L.locale))
                else -> trn(r.lows, "{0} low reading in the last {1} days.", "{0} low readings in the last {1} days.", r.days)
            }
            // The day with the most lows first; on a tie, the most recent.
            val lowDays = lowReadings.groupBy { it.date(zone) }.entries
                .sortedWith(compareByDescending<Map.Entry<LocalDate, List<Reading>>> { it.value.size }.thenByDescending { it.key })
                .map { it.key }
            out += Insight(InsightKind.ATTENTION, text, lowDays)
        } else if (r.count >= 10) {
            out += Insight(InsightKind.GOOD, if (week) tr("No lows this week.") else tr("No lows in the last {0} days.", r.days))
        }

        val fasting = r.byTag.firstOrNull { it.tag == MealTag.FASTING }
        if (fasting != null && fasting.count >= 3) {
            val (lo, hi) = targets.forTag(MealTag.FASTING)
            val fastingReadings = r.readings.filter { it.tag == MealTag.FASTING }
            out += when {
                fasting.average > hi -> Insight(
                    InsightKind.ATTENTION,
                    tr("Fasting readings average {0}, above the {1}–{2} target.", v(fasting.average), v(lo), v(hi)),
                    daysOf(fastingReadings.filter { it.mmol > hi }.sortedByDescending { it.mmol }),
                )
                fasting.average < lo -> Insight(
                    InsightKind.INFO,
                    tr("Fasting readings average {0}, a little under the {1}–{2} target.", v(fasting.average), v(lo), v(hi)),
                    daysOf(fastingReadings.filter { it.mmol < lo }.sortedBy { it.mmol }),
                )
                else -> Insight(InsightKind.GOOD, tr("Fasting readings are on target, averaging {0}.", v(fasting.average)))
            }
        }

        val after = r.readings.filter { it.tag == MealTag.AFTER_MEAL }
        if (after.size >= 3) {
            val over = after.count { it.mmol > targets.afterHigh }
            // The days of the over readings, the highest first.
            val overDays = daysOf(after.filter { it.mmol > targets.afterHigh }.sortedByDescending { it.mmol })
            out += when {
                over * 2 >= after.size -> Insight(InsightKind.ATTENTION, tr("After-eating readings were over {0} in {1} of {2}.", v(targets.afterHigh), over, after.size), overDays)
                over == 0 -> Insight(InsightKind.GOOD, tr("Every after-eating reading stayed under {0}.", v(targets.afterHigh)))
                else -> Insight(InsightKind.INFO, tr("After-eating readings were over {0} in {1} of {2}.", v(targets.afterHigh), over, after.size), overDays)
            }
        }

        val tir = r.bands?.inRange
        val prev = r.previousInRange
        if (tir != null && prev != null && r.count >= 10) {
            val d = tir.roundToInt() - prev.roundToInt()
            if (d >= 5) out += Insight(InsightKind.GOOD, tr("In range {0}% of the time, up from {1}% before.", tir.roundToInt(), prev.roundToInt()))
            else if (d <= -5) out += Insight(InsightKind.ATTENTION, tr("In range {0}% of the time, down from {1}% before.", tir.roundToInt(), prev.roundToInt()))
        }

        val cv = r.cv
        if (cv != null && r.count >= 10) {
            // The days with the widest swing (highest minus lowest), widest first.
            val swingDays = r.byDay.filter { it.count >= 2 && it.max > it.min }
                .sortedWith(compareByDescending<DayStat> { it.max - it.min }.thenByDescending { it.day })
                .map { it.day }
            out += if (cv > 36) Insight(InsightKind.ATTENTION, tr("Readings swing quite a bit (variability {0}%). Steadier is 36% or less.", cv.roundToInt()), swingDays)
            else Insight(InsightKind.GOOD, tr("Readings are steady (variability {0}%, goal 36% or less).", cv.roundToInt()))
        }

        // The moment of the day that runs highest, when it's over its target.
        val highest = r.byTag.filter { it.count >= 3 }.maxByOrNull { it.average }
        if (highest != null && highest.tag != MealTag.FASTING && highest.average > targets.forTag(highest.tag).second) {
            out += Insight(
                InsightKind.INFO,
                tr("Your highest readings tend to be {0} (average {1}).", highest.tag.label.lowercase(L.locale), v(highest.average)),
                daysOf(r.readings.filter { it.tag == highest.tag }.sortedByDescending { it.mmol }),
            )
        }

        // Carbs and after-meal readings: the carbs logged on a before-meal reading (or on the
        // after-meal one itself), compared for bigger and smaller meals.
        val pairs = carbPairs(r.readings, meals)
        val big = pairs.filter { it.first >= BIG_MEAL }.map { it.second }
        val small = pairs.filter { it.first < BIG_MEAL }.map { it.second }
        if (big.size >= 3 && small.size >= 3) {
            val d = big.average() - small.average()
            if (d >= 1.0) {
                out += Insight(
                    InsightKind.INFO,
                    tr("After meals of {0} g carbs or more you average {1}, against {2} after smaller ones.", BIG_MEAL, v(big.average()), v(small.average())),
                )
            }
        }

        if (r.days >= 7 && r.daysLogged * 10 >= r.days * 8) {
            out += Insight(InsightKind.GOOD, tr("Logged on {0} of {1} days. Nice and consistent.", r.daysLogged, r.days))
        }
        return out
    }
}
