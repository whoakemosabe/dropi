package app.dropi.data

import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ask Dropi, looking things up: instead of answering from a fixed summary, the on-device model
 * first picks one look-up (readings in a stretch, meals with a food, a comparison…), Dropi runs
 * it on the log on the phone, and the model answers from exactly what came back.
 *
 * Works the same on Gemma and Gemini Nano: the pick is a small JSON reply, not the model's own
 * tool calling, so nothing depends on one model's features. If the pick can't be read, Ask falls
 * back to the summary as before. Everything stays on the phone.
 */
object AskTools {
    /** The pick, held to this shape on Gemma (plain types only; unions break it). */
    const val PLAN_SCHEMA = """{"type":"object","properties":{"tool":{"type":"string"},"days":{"type":"integer"},"days_ago":{"type":"integer"},"moment":{"type":"string"},"meal":{"type":"string"},"food":{"type":"string"},"above":{"type":"number"},"below":{"type":"number"}},"required":["tool"]}"""

    /** What the model picked, filled in with sensible defaults. */
    data class Plan(
        val tool: String, val days: Int, val daysAgo: Int, val moment: MealTag?, val meal: String?,
        val food: String?, val above: Double?, val below: Double?,
    ) {
        /** In plain English, for the "Looked at" line under the answer (shown translated). */
        val en: String get() = when (tool) {
            "readings" -> "readings, " + window() + (moment?.let { ", ${it.en.lowercase()}" } ?: "")
            "meals" -> "meals, " + window() + (meal?.let { ", ${it.lowercase()}" } ?: "") + (food?.let { ", with $it" } ?: "")
            "food" -> "meals with ${food ?: "that food"}"
            "compare" -> "the last $days days against the $days before"
            "hours" -> "time of day, " + window()
            else -> "a summary, " + window()
        }
        private fun window() = if (daysAgo > 0) "$days days ending $daysAgo days ago" else "last $days days"

        /** The same, in the app's language, for the line under the answer. */
        val shown: String get() {
            val w = if (daysAgo > 0) app.dropi.trn(days, "{0} day, ending {1} days ago", "{0} days, ending {1} days ago", daysAgo)
                else app.dropi.trn(days, "the last {0} day", "the last {0} days")
            return when (tool) {
                "readings" -> app.dropi.tr("readings, {0}", w) + (moment?.let { ", " + it.label.lowercase(app.dropi.L.locale) } ?: "")
                "meals" -> app.dropi.tr("meals, {0}", w) + (meal?.let { ", " + MealEntry.displayName(it).lowercase(app.dropi.L.locale) } ?: "") + (food?.let { ", $it" } ?: "")
                "food" -> app.dropi.tr("meals with {0}", food ?: "")
                "compare" -> app.dropi.trn(days, "the last {0} day against the one before", "the last {0} days against the {0} before")
                "hours" -> app.dropi.tr("time of day, {0}", w)
                else -> app.dropi.tr("a summary, {0}", w)
            }
        }
    }

    fun planPrompt(question: String, today: java.time.LocalDate, unit: GlucoseUnit): String =
        "You help a blood sugar diary app look up the right data before answering. Today is $today. Unit: ${unit.label}.\n" +
            "Pick ONE look-up for the question and reply with JSON only:\n" +
            "{\"tool\": one of \"summary\", \"readings\", \"meals\", \"food\", \"compare\", \"hours\", \"none\",\n" +
            " \"days\": how many days (1-90), \"days_ago\": 0 unless the question is about an earlier stretch,\n" +
            " \"moment\": \"fasting\", \"before\", \"after\", \"bedtime\" or \"night\" if the question is about one,\n" +
            " \"meal\": \"breakfast\", \"lunch\", \"dinner\" or \"snack\" if about one, \"food\": a food name if about one,\n" +
            " \"above\"/\"below\": a glucose value if the question asks about readings over or under it}\n" +
            "summary = averages and time in range; readings = the readings themselves; meals = meals with what happened after;\n" +
            "food = how one food affects them; compare = this stretch against the one before; hours = by time of day;\n" +
            "none = a general question that needs no data.\n" +
            "Question: ${question.take(300)}"

    /** The model's pick, or null when it can't be read. */
    fun parsePlan(raw: String?): Plan? {
        val text = raw ?: return null
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val o = JSONObject(text.substring(start, end + 1))
            val tool = o.optString("tool", "").lowercase(Locale.ROOT).trim()
            if (tool !in TOOLS) return null
            fun num(k: String) = if (o.has(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
            fun str(k: String) = o.optString(k, "").trim().takeIf { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in setOf("any", "none", "null") }
            Plan(
                tool = tool,
                days = (num("days")?.toInt() ?: 14).coerceIn(1, 90),
                daysAgo = (num("days_ago")?.toInt() ?: 0).coerceIn(0, 89),
                moment = str("moment")?.let { moment(it) },
                meal = str("meal")?.let { m -> MealEntry.NAMES.firstOrNull { it.equals(m, ignoreCase = true) } },
                food = str("food")?.take(40),
                above = num("above"), below = num("below"),
            )
        } catch (e: Exception) { null }
    }

    private val TOOLS = setOf("summary", "readings", "meals", "food", "compare", "hours", "none")

    private fun moment(s: String): MealTag? = when (s.lowercase(Locale.ROOT)) {
        "fasting", "waking", "morning" -> MealTag.FASTING
        "before", "before eating", "before meal", "before meals" -> MealTag.BEFORE_MEAL
        "after", "after eating", "after meal", "after meals" -> MealTag.AFTER_MEAL
        "bedtime", "bed" -> MealTag.BEDTIME
        "night", "overnight" -> MealTag.OVERNIGHT
        else -> null
    }

    /**
     * Runs the pick on the log. Returns plain English facts (decimal points, the person's unit),
     * kept short, or null for "none". Values the person typed in mg/dL are understood too.
     */
    fun run(p: Plan, c: Ask.Ctx): String? {
        if (p.tool == "none") return null
        val today = c.today.minusDays(p.daysAgo.toLong())
        val from = today.minusDays((p.days - 1).toLong())
        val fromAt = from.atStartOfDay(c.zone).toInstant().toEpochMilli()
        val toAt = today.plusDays(1).atStartOfDay(c.zone).toInstant().toEpochMilli()
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ROOT)
        fun at(t: Long) = fmt.format(Instant.ofEpochMilli(t).atZone(c.zone))
        // A threshold given in mg/dL (anything over 35) is turned back into mmol/L.
        fun mmol(v: Double?) = v?.let { if (it > 35) it / Glucose.MGDL_PER_MMOL else it }
        val sb = StringBuilder()
        sb.append("Unit: ${c.u}. Today is ${c.today}. Low is under ${c.p(Glucose.LOW)}, in range ${c.p(Glucose.LOW)}-${c.p(Glucose.TIR_HIGH)}. ")
        sb.append("Targets: before eating ${c.p(c.targets.beforeLow)}-${c.p(c.targets.beforeHigh)}, after eating under ${c.p(c.targets.afterHigh)}.\n")
        sb.append("Looked up: ${p.en} ($from to $today).\n")
        when (p.tool) {
            "summary", "compare" -> {
                fun line(label: String, end: java.time.LocalDate) {
                    val r = Analytics.report(c.readings, p.days, end, c.zone)
                    if (r.count == 0) { sb.append("$label: no readings.\n"); return }
                    sb.append("$label: ${r.count} readings on ${r.daysLogged} days, average ${c.p(r.mean!!)}")
                    r.bands?.let { sb.append(", ${it.inRange.roundToInt()}% in range") }
                    sb.append(", ${r.lows} lows")
                    r.gmiPercent?.let { sb.append(", estimated A1C ${String.format(Locale.ROOT, "%.1f", it)}%") }
                    sb.append(".\n")
                    r.byTag.forEach { sb.append("  ${it.tag.en}: ${it.count} readings, average ${c.p(it.average)}.\n") }
                }
                line("This stretch", today)
                if (p.tool == "compare") line("The ${p.days} days before", today.minusDays(p.days.toLong()))
            }
            "readings" -> {
                val lo = mmol(p.above); val hi = mmol(p.below)
                val rs = c.readings.filter { it.at in fromAt until toAt && (p.moment == null || it.tag == p.moment) && (lo == null || it.mmol > lo) && (hi == null || it.mmol < hi) }
                sb.append("${rs.size} readings match")
                if (rs.isNotEmpty()) sb.append(", average ${c.p(rs.map { it.mmol }.average())}, lowest ${c.p(rs.minOf { it.mmol })}, highest ${c.p(rs.maxOf { it.mmol })}")
                sb.append(".\n")
                rs.sortedByDescending { it.at }.take(25).forEach { x ->
                    sb.append("- ${at(x.at)} ${x.tag.en} ${c.p(x.mmol)}")
                    val d = x.detailsText(english = true)
                    if (d.isNotEmpty()) sb.append(" ($d)")
                    sb.append('\n')
                }
            }
            "meals", "food" -> {
                val key = p.food?.let { Foods.key(it) }
                val byId = c.allMeasured.associateBy { it.entryId }
                val ms = c.meals.filter { m ->
                    (p.tool == "food" || m.at in fromAt until toAt) &&
                        (p.meal == null || m.name == p.meal) &&
                        (key == null || m.foods.any { f -> Foods.key(f) == key || f.contains(p.food!!, ignoreCase = true) })
                }.sortedByDescending { it.at }
                sb.append("${ms.size} meals match.\n")
                val rises = ms.mapNotNull { byId[it.id]?.rise }
                if (rises.isNotEmpty()) sb.append("Typical rise after them: ${Foods.delta(Foods.median(rises), c.unit, plain = true)} (${rises.size} with readings before and after).\n")
                ms.take(15).forEach { m ->
                    val out = MealHistory.outcome(byId[m.id])
                    sb.append("- ${at(m.at)} ${m.name}: ${m.foods.joinToString(", ")}, ${Grams.plain(m.carbs)} g carbs")
                    if (out != null) sb.append(", from ${c.p(out.start)} to a peak of ${c.p(out.peak)}")
                    sb.append('\n')
                }
                if (p.tool == "food" && key != null) {
                    Foods.scores(c.allMeasured).firstOrNull { Foods.key(it.food) == key }?.let {
                        sb.append("Food score: ${it.food} rises ${Foods.delta(it.rise, c.unit, plain = true)} on average over ${it.meals} meals.\n")
                    }
                }
            }
            "hours" -> {
                val rs = c.readings.filter { it.at in fromAt until toAt }
                if (rs.isEmpty()) sb.append("No readings.\n")
                rs.groupBy { Instant.ofEpochMilli(it.at).atZone(c.zone).hour / 3 }.toSortedMap().forEach { (b, xs) ->
                    sb.append("%02d:00-%02d:00: %d readings, average %s, highest %s.\n".format(Locale.ROOT, b * 3, b * 3 + 3, xs.size, c.p(xs.map { it.mmol }.average()), c.p(xs.maxOf { it.mmol })))
                }
            }
        }
        return sb.toString().take(3500)
    }
}
