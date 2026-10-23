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
