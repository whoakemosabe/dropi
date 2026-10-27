package app.dropi.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A lab HbA1c result, as the clinic reported it, to compare with the estimate (GMI). */
data class LabA1c(val date: LocalDate, val percent: Double) {
    /** IFCC units, what Icelandic and European labs report: (% − 2.15) × 10.929. */
    val mmolMol: Double get() = A1c.toMmolMol(percent)
}

object A1c {
    fun toMmolMol(percent: Double) = (percent - 2.15) * 10.929
    fun fromMmolMol(mmolMol: Double) = mmolMol / 10.929 + 2.15

    /** Lab results go from about 4% to 15%; anything outside is a typo. */
    fun plausible(percent: Double) = percent in 3.5..18.0

    fun toJson(list: List<LabA1c>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("date", it.date.toString()).put("pct", it.percent)) }
    }.toString()

    fun fromJson(json: String?): List<LabA1c> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val pct = o.getDouble("pct")
                if (plausible(pct)) LabA1c(LocalDate.parse(o.getString("date")), pct) else null
            }.sortedBy { it.date }
        } catch (e: Exception) { emptyList() }
    }
}


/**
 * A lab result corrects the estimate: some people's A1C runs a little above or below what their
 * average glucose predicts (GMI). The gap at the time of the latest lab result (GMI from the 90
 * days before it, at least 14 readings over 7 days) is added to today's estimate. Only for a lab
 * result in the last year, and only gaps up to 2 points (more is likely a typo or a mismatch).
 */
object A1cCalibration {
    private const val DAY = 86_400_000L

    data class Gap(val lab: LabA1c, val gmiThen: Double) {
        val points: Double get() = lab.percent - gmiThen
    }

    fun gap(labs: List<LabA1c>, readings: List<Reading>, zone: java.time.ZoneId, today: java.time.LocalDate): Gap? {
        val lab = labs.lastOrNull() ?: return null
        if (lab.date.isBefore(today.minusDays(365))) return null
        val end = lab.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val start = end - 90 * DAY
        val rs = readings.filter { it.at in start until end }
        if (rs.size < 14) return null
        val days = rs.map { it.date(zone) }.distinct().size
        if (days < 7) return null
        val g = Analytics.gmiPercent(rs.map { it.mmol }.average())
        return Gap(lab, g).takeIf { kotlin.math.abs(it.points) <= 2.0 }
    }

    /** Today's estimate corrected by the gap, or null without one. */
    fun adjusted(gmiNow: Double?, gap: Gap?): Double? = if (gmiNow == null || gap == null) null else gmiNow + gap.points
}
