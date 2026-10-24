package app.dropi.data

import app.dropi.L
import app.dropi.tr
import app.dropi.trn
import app.dropi.ui.dayPattern
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
    /** [en] is the English question; [text] is shown, in the app's language. */
    enum class Q(val en: String) {
        WEEK("How was my week?"),
        HIGHEST("When do I run highest?"),
        FOODS("Which foods lift me most?"),
        LOWS("Any lows lately?");

        val text: String get() = when (this) {
            WEEK -> tr("How was my week?")
            HIGHEST -> tr("When do I run highest?")
            FOODS -> tr("Which foods lift me most?")
            LOWS -> tr("Any lows lately?")
        }
    }

    class Ctx(
        val readings: List<Reading>,
        val sensor: List<SensorPoint>,
        val unit: GlucoseUnit,
        val targets: Targets,
        val today: LocalDate,
        val zone: ZoneId,
        val meals: List<MealEntry> = emptyList(),
        /** Meals already measured (from the app's state); worked out here when not given. */
        val measured: List<Foods.Meal>? = null,
    ) {
        val allMeasured: List<Foods.Meal> by lazy { measured ?: Foods.meals(readings, sensor, meals) }
        /** For showing (decimal comma in Icelandic). */
        fun v(mmol: Double) = Glucose.format(mmol, unit)
        /** For the model's facts: always with a point. */
        fun p(mmol: Double) = Glucose.plain(mmol, unit)
        /** A rise for showing: "+2.4" ("+2,4" in Icelandic). */
        fun d(mmol: Double) = Foods.delta(mmol, unit)
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
        if (r.count == 0) return tr("Nothing logged in the last 7 days yet. Log a few readings and ask again.")
        val tir = r.bands?.inRange?.roundToInt()
        val parts = ArrayList<String>()
        parts += trn(
            r.count,
            "{0} reading on {1} of 7 days, averaging {2} {3}.",
            "{0} readings on {1} of 7 days, averaging {2} {3}.",
            r.daysLogged, c.v(r.mean!!), c.u,
        )
        if (tir != null) {
            val prev = r.previousInRange?.roundToInt()
            parts += when {
                prev == null -> tr("{0}% were in range.", tir)
                tir > prev -> tr("{0}% were in range, up from {1}% the week before.", tir, prev)
                tir < prev -> tr("{0}% were in range, down from {1}% the week before.", tir, prev)
                else -> tr("{0}% were in range, the same as the week before.", tir)
            }
        }
        parts += if (r.lows == 0) tr("No lows.") else trn(r.lows, "{0} low.", "{0} lows.")
        return parts.joinToString(" ")
    }

    private fun highest(c: Ctx): String {
        val r = Analytics.report(c.readings, 30, c.today, c.zone)
        if (r.count < 5) return tr("There aren't enough readings yet. After a few more days of logging, this gets clearer.")
        val byTag = r.byTag.filter { it.count >= 3 }.sortedByDescending { it.average }
        val hours = r.readings.groupBy { block(Instant.ofEpochMilli(it.at).atZone(c.zone).hour) }
            .filter { it.value.size >= 3 }
            .mapValues { e -> e.value.map { it.mmol }.average() }
        val topBlock = hours.maxByOrNull { it.value }
        val parts = ArrayList<String>()
        byTag.firstOrNull()?.let { parts += tr("{0} readings run highest, averaging {1} {2} over the last 30 days.", it.tag.label, c.v(it.average), c.u) }
        topBlock?.let { parts += highestStretch(it.key, c.v(it.value)) }
        byTag.lastOrNull()?.takeIf { byTag.size > 1 }?.let { parts += tr("{0} is lowest at {1}.", it.tag.label, c.v(it.average)) }
        return parts.ifEmpty { listOf(tr("Your readings are spread too thinly to tell yet.")) }.joinToString(" ")
    }

    /** A stretch of the day, as a key for grouping (never shown as is). */
    private enum class Block { MORNING, LUNCH, AFTERNOON, EVENING, OVERNIGHT }

    private fun block(hour: Int) = when (hour) {
        in 5..10 -> Block.MORNING
        in 11..13 -> Block.LUNCH
        in 14..17 -> Block.AFTERNOON
        in 18..21 -> Block.EVENING
        else -> Block.OVERNIGHT
    }

    private fun highestStretch(b: Block, value: String) = when (b) {
        Block.MORNING -> tr("By time of day, the morning is your highest stretch ({0} on average).", value)
        Block.LUNCH -> tr("By time of day, around lunch is your highest stretch ({0} on average).", value)
        Block.AFTERNOON -> tr("By time of day, the afternoon is your highest stretch ({0} on average).", value)
        Block.EVENING -> tr("By time of day, the evening is your highest stretch ({0} on average).", value)
        Block.OVERNIGHT -> tr("By time of day, overnight is your highest stretch ({0} on average).", value)
    }

    private fun mostLows(b: Block) = when (b) {
        Block.MORNING -> tr("Most were in the morning.")
        Block.LUNCH -> tr("Most were around lunch.")
        Block.AFTERNOON -> tr("Most were in the afternoon.")
        Block.EVENING -> tr("Most were in the evening.")
        Block.OVERNIGHT -> tr("Most were overnight.")
    }

    private fun foods(c: Ctx): String {
        val s = Foods.scores(c.allMeasured).filter { it.ranked }
        if (s.isEmpty()) {
            return tr("No food scores yet. Log a meal with the + button, with a reading before it, then log an after-eating reading. Once a food has two meals behind it, it gets a score.")
        }
        val top = s.takeLast(3).reversed()
        val gentle = s.take(3).filter { it !in top }
        val parts = ArrayList<String>()
        parts += tr("Biggest rises: {0}.", top.joinToString(", ") { "${Foods.display(it.food)} (${c.d(it.rise)})" })
        if (gentle.isNotEmpty()) parts += tr("Gentlest: {0}.", gentle.joinToString(", ") { "${Foods.display(it.food)} (${c.d(it.rise)})" })
        parts += tr("Rises are the typical peak 45 min–3 h after eating, in {0}.", c.u)
        return parts.joinToString(" ")
    }

    private fun lows(c: Ctx): String {
        val r = Analytics.report(c.readings, 14, c.today, c.zone)
        val lows = r.readings.filter { it.mmol < Glucose.LOW }
        val since = System.currentTimeMillis() - 14 * 86_400_000L
        val sensorLows = c.sensor.filter { it.at >= since && it.mmol < Glucose.LOW }
        if (lows.isEmpty() && sensorLows.isEmpty()) return tr("No lows in the last 14 days.")
        val parts = ArrayList<String>()
        if (lows.isNotEmpty()) {
            val last = lows.maxBy { it.at }
            val day = dayPattern("EEE d MMM").format(Instant.ofEpochMilli(last.at).atZone(c.zone))
            parts += trn(
                lows.size,
                "{0} low reading in the last 14 days, the latest {1} on {2}.",
                "{0} low readings in the last 14 days, the latest {1} on {2}.",
                c.v(last.mmol), day,
            )
            val common = lows.groupBy { block(Instant.ofEpochMilli(it.at).atZone(c.zone).hour) }.maxBy { it.value.size }
            if (lows.size >= 2 && common.value.size * 2 > lows.size) parts += mostLows(common.key)
        }
        if (sensorLows.isNotEmpty()) {
            val minutes = sensorLows.size * 5
            parts += if (minutes >= 90) tr("Your sensor spent about {0} h under {1}.", (minutes / 60.0).roundToInt(), c.v(Glucose.LOW))
            else tr("Your sensor spent about {0} min under {1}.", minutes, c.v(Glucose.LOW))
        }
        parts += tr("Worth mentioning to your care team if they keep happening.")
        return parts.joinToString(" ")
    }

    /**
     * What the model may use: plain facts, a few hundred words, no names or ids. Always English
     * with decimal points (the model reads it), whatever the app's language.
     */
    fun facts(c: Ctx): String {
        val sb = StringBuilder()
        val r14 = Analytics.report(c.readings, 14, c.today, c.zone)
        sb.append("Unit: ${c.u}. Today is ${c.today}.\n")
        sb.append("Targets: fasting/before meals ${c.p(c.targets.beforeLow)}-${c.p(c.targets.beforeHigh)}, after meals under ${c.p(c.targets.afterHigh)}. Low is under ${c.p(Glucose.LOW)}.\n")
        if (r14.count > 0) {
            sb.append("Last 14 days: ${r14.count} readings on ${r14.daysLogged} days, average ${c.p(r14.mean!!)}, ")
            r14.bands?.let { sb.append("${it.inRange.roundToInt()}% in range ${c.p(Glucose.LOW)}-${c.p(Glucose.TIR_HIGH)}, ") }
            sb.append("${r14.lows} lows.\n")
            r14.byTag.forEach { sb.append("${it.tag.en}: ${it.count} readings, average ${c.p(it.average)}.\n") }
            r14.gmiPercent?.let { sb.append("Estimated A1C (GMI): ${String.format(Locale.ROOT, "%.1f", it)}%.\n") }
        }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ROOT)
        sb.append("Latest readings:\n")
        c.readings.sortedByDescending { it.at }.take(12).forEach { x ->
            sb.append("- ${fmt.format(Instant.ofEpochMilli(x.at).atZone(c.zone))} ${x.tag.en} ${c.p(x.mmol)}")
            val d = x.detailsText(english = true)
            if (d.isNotEmpty()) sb.append(" ($d)")
            sb.append('\n')
        }
        if (c.meals.isNotEmpty()) {
            sb.append("Latest meals:\n")
            c.meals.sortedByDescending { it.at }.take(6).forEach { m ->
                val extra = listOfNotNull(m.meal.protein?.let { "${Grams.plain(it)} g protein" }, m.meal.fat?.let { "${Grams.plain(it)} g fat" }, m.meal.speed?.en?.lowercase())
                sb.append("- ${fmt.format(Instant.ofEpochMilli(m.at).atZone(c.zone))} ${m.name}: ${m.foods.joinToString(", ")}, ${Grams.plain(m.carbs)} g carbs")
                if (extra.isNotEmpty()) sb.append(", ").append(extra.joinToString(", "))
                if (m.meal.tags.isNotEmpty()) sb.append(" (").append(m.meal.tags.joinToString(", ") { MealTags.en(it).lowercase() }).append(")")
                sb.append('\n')
            }
            MealPatterns.fromMeasured(c.allMeasured, c.readings, c.meals, c.unit).forEach { sb.append("Pattern: ${it.en}\n") }
        }
        val scores = Foods.scores(c.allMeasured).filter { it.ranked }
        if (scores.isNotEmpty()) {
            sb.append("Food rises (typical peak after eating): ")
            sb.append(scores.joinToString(", ") { "${it.food} ${Foods.delta(it.rise, c.unit, plain = true)} over ${it.meals} meals" })
            sb.append(".\n")
        }
        Trend.latest(c.sensor)?.let { p ->
            if (System.currentTimeMillis() - p.at < Trend.FRESH_MS) {
                sb.append("Sensor now: ${c.p(p.mmol)}${Trend.arrow(c.sensor)?.let { ", ${it.words}" } ?: ""}.\n")
            }
        }
        return sb.toString().take(3500)
    }

    fun prompt(question: String, facts: String): String =
        "You are Dropi, a friendly blood sugar diary app. Answer the question using only the facts below. " +
            "Reply in at most three short sentences of plain text. If the facts don't cover it, say so. " +
            "Never suggest insulin or medicine doses, never diagnose, and for treatment questions say to ask their care team." +
            (if (L.icelandic) " Answer in Icelandic." else "") + "\n\n" +
            "Facts:\n$facts\nQuestion: ${question.take(300)}"
}
