package app.dropi.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "Ask Dropi". The ready-made questions are answered here, straight from the numbers, so
 * they're exact and work on every phone. Typed questions go to the on-device model with
 * [facts] as its only source.
 */
object Ask {
    enum class Q(val text: String) {
        WEEK("How was my week?"),
        HIGHEST("When do I run highest?"),
        FOODS("Which foods lift me most?"),
        LOWS("Any lows lately?"),
    }

    class Ctx(
        val readings: List<Reading>,
        val sensor: List<SensorPoint>,
        val unit: GlucoseUnit,
        val targets: Targets,
        val today: LocalDate,
        val zone: ZoneId,
        val meals: List<MealEntry> = emptyList(),
    ) {
        fun v(mmol: Double) = Glucose.format(mmol, unit)
        val u get() = unit.label
    }

    fun answer(q: Q, c: Ctx): String = when (q) {
        Q.WEEK -> week(c)
        Q.HIGHEST -> highest(c)
        Q.FOODS -> foods(c)
        Q.LOWS -> lows(c)
    }

    private fun week(c: Ctx): String {
        val r = Analytics.report(c.readings, 7, c.today, c.zone)
        if (r.count == 0) return "Nothing logged in the last 7 days yet. Log a few readings and ask again."
        val tir = r.bands?.inRange?.roundToInt()
        val parts = ArrayList<String>()
        parts += "${r.count} ${if (r.count == 1) "reading" else "readings"} on ${r.daysLogged} of 7 days, averaging ${c.v(r.mean!!)} ${c.u}."
        if (tir != null) {
            val prev = r.previousInRange?.roundToInt()
            parts += when {
                prev == null -> "$tir% were in range."
                tir > prev -> "$tir% were in range, up from $prev% the week before."
                tir < prev -> "$tir% were in range, down from $prev% the week before."
                else -> "$tir% were in range, the same as the week before."
            }
        }
        parts += if (r.lows == 0) "No lows." else "${r.lows} ${if (r.lows == 1) "low" else "lows"}."
        return parts.joinToString(" ")
    }

    private fun highest(c: Ctx): String {
        val r = Analytics.report(c.readings, 30, c.today, c.zone)
        if (r.count < 5) return "There aren't enough readings yet. After a few more days of logging, this gets clearer."
        val byTag = r.byTag.filter { it.count >= 3 }.sortedByDescending { it.average }
        val hours = r.readings.groupBy { block(Instant.ofEpochMilli(it.at).atZone(c.zone).hour) }
            .filter { it.value.size >= 3 }
            .mapValues { e -> e.value.map { it.mmol }.average() }
        val topBlock = hours.maxByOrNull { it.value }
        val parts = ArrayList<String>()
        byTag.firstOrNull()?.let { parts += "${it.tag.label} readings run highest, averaging ${c.v(it.average)} ${c.u} over the last 30 days." }
        topBlock?.let { parts += "By time of day, ${it.key} is your highest stretch (${c.v(it.value)} on average)." }
        byTag.lastOrNull()?.takeIf { byTag.size > 1 }?.let { parts += "${it.tag.label} is lowest at ${c.v(it.average)}." }
        return parts.ifEmpty { listOf("Your readings are spread too thinly to tell yet.") }.joinToString(" ")
    }

    private fun block(hour: Int) = when (hour) {
        in 5..10 -> "the morning"
        in 11..13 -> "around lunch"
        in 14..17 -> "the afternoon"
        in 18..21 -> "the evening"
        else -> "overnight"
    }

    private fun foods(c: Ctx): String {
        val s = Foods.scores(c.readings, c.sensor, c.meals).filter { it.ranked }
        if (s.isEmpty()) {
            return "No food scores yet. Log a meal with the + button, with a reading before it, then log an after-eating " +
                "reading. Once a food has two meals behind it, it gets a score."
        }
        val top = s.takeLast(3).reversed()
        val gentle = s.take(3).filter { it !in top }
        val parts = ArrayList<String>()
        parts += "Biggest rises: " + top.joinToString(", ") { "${Foods.display(it.food)} (${Foods.delta(it.rise, c.unit)})" } + "."
        if (gentle.isNotEmpty()) parts += "Gentlest: " + gentle.joinToString(", ") { "${Foods.display(it.food)} (${Foods.delta(it.rise, c.unit)})" } + "."
        parts += "Rises are the typical peak 45 min–3 h after eating, in ${c.u}."
        return parts.joinToString(" ")
    }

    private fun lows(c: Ctx): String {
        val r = Analytics.report(c.readings, 14, c.today, c.zone)
        val lows = r.readings.filter { it.mmol < Glucose.LOW }
        val since = System.currentTimeMillis() - 14 * 86_400_000L
        val sensorLows = c.sensor.filter { it.at >= since && it.mmol < Glucose.LOW }
        if (lows.isEmpty() && sensorLows.isEmpty()) return "No lows in the last 14 days."
        val parts = ArrayList<String>()
        if (lows.isNotEmpty()) {
            val last = lows.maxBy { it.at }
            val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(last.at).atZone(c.zone))
            parts += "${lows.size} low ${if (lows.size == 1) "reading" else "readings"} in the last 14 days, the latest ${c.v(last.mmol)} on $day."
            val common = lows.groupBy { block(Instant.ofEpochMilli(it.at).atZone(c.zone).hour) }.maxBy { it.value.size }
            if (lows.size >= 2 && common.value.size * 2 > lows.size) parts += "Most were ${common.key}."
        }
        if (sensorLows.isNotEmpty()) {
            val minutes = sensorLows.size * 5
            parts += "Your sensor spent about ${if (minutes >= 90) "${(minutes / 60.0).roundToInt()} h" else "$minutes min"} under ${c.v(Glucose.LOW)}."
        }
        parts += "Worth mentioning to your care team if they keep happening."
        return parts.joinToString(" ")
    }

    /** What the model may use: plain facts, a few hundred words, no names or ids. */
    fun facts(c: Ctx): String {
        val sb = StringBuilder()
        val r14 = Analytics.report(c.readings, 14, c.today, c.zone)
        sb.append("Unit: ${c.u}. Today is ${c.today}.\n")
        sb.append("Targets: fasting/before meals ${c.v(c.targets.beforeLow)}-${c.v(c.targets.beforeHigh)}, after meals under ${c.v(c.targets.afterHigh)}. Low is under ${c.v(Glucose.LOW)}.\n")
        if (r14.count > 0) {
            sb.append("Last 14 days: ${r14.count} readings on ${r14.daysLogged} days, average ${c.v(r14.mean!!)}, ")
            r14.bands?.let { sb.append("${it.inRange.roundToInt()}% in range ${c.v(Glucose.LOW)}-${c.v(Glucose.TIR_HIGH)}, ") }
            sb.append("${r14.lows} lows.\n")
            r14.byTag.forEach { sb.append("${it.tag.label}: ${it.count} readings, average ${c.v(it.average)}.\n") }
            r14.gmiPercent?.let { sb.append("Estimated A1C (GMI): ${String.format(Locale.ROOT, "%.1f", it)}%.\n") }
        }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ROOT)
        sb.append("Latest readings:\n")
        c.readings.sortedByDescending { it.at }.take(12).forEach { x ->
            sb.append("- ${fmt.format(Instant.ofEpochMilli(x.at).atZone(c.zone))} ${x.tag.label} ${c.v(x.mmol)}")
            val d = x.detailsText()
            if (d.isNotEmpty()) sb.append(" ($d)")
            sb.append('\n')
        }
        if (c.meals.isNotEmpty()) {
            sb.append("Latest meals:\n")
            c.meals.sortedByDescending { it.at }.take(6).forEach { m ->
                val extra = listOfNotNull(m.meal.protein?.let { "${Grams.text(it)} g protein" }, m.meal.fat?.let { "${Grams.text(it)} g fat" }, m.meal.speed?.word?.lowercase())
                sb.append("- ${fmt.format(Instant.ofEpochMilli(m.at).atZone(c.zone))} ${m.name}: ${m.foods.joinToString(", ")}, ${Grams.text(m.carbs)} g carbs")
                if (extra.isNotEmpty()) sb.append(", ").append(extra.joinToString(", "))
                if (m.meal.tags.isNotEmpty()) sb.append(" (").append(m.meal.tags.joinToString(", ") { MealTags.label(it).lowercase() }).append(")")
                sb.append('\n')
            }
            MealPatterns.find(c.readings, c.sensor, c.meals, c.unit).forEach { sb.append("Pattern: ${it.text}\n") }
        }
        val scores = Foods.scores(c.readings, c.sensor, c.meals).filter { it.ranked }
        if (scores.isNotEmpty()) {
            sb.append("Food rises (typical peak after eating): ")
            sb.append(scores.joinToString(", ") { "${it.food} ${Foods.delta(it.rise, c.unit)} over ${it.meals} meals" })
            sb.append(".\n")
        }
        Trend.latest(c.sensor)?.let { p ->
            if (System.currentTimeMillis() - p.at < Trend.FRESH_MS) {
                sb.append("Sensor now: ${c.v(p.mmol)}${Trend.arrow(c.sensor)?.let { ", ${it.words}" } ?: ""}.\n")
            }
        }
        return sb.toString().take(3500)
    }

    fun prompt(question: String, facts: String): String =
        "You are Dropi, a friendly blood sugar diary app. Answer the question using only the facts below. " +
            "Reply in at most three short sentences of plain text. If the facts don't cover it, say so. " +
            "Never suggest insulin or medicine doses, never diagnose, and for treatment questions say to ask their care team.\n\n" +
            "Facts:\n$facts\nQuestion: ${question.take(300)}"
}
