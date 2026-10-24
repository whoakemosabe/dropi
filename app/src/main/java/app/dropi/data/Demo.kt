package app.dropi.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A month of believable readings for trying the app: fasting, before and after meals with
 * their foods, bedtime, the odd low and high, a few skipped checks, and a day of sensor values.
 * Only ever written to the demo database, never the real log.
 */
object Demo {
    private const val MIN = 60_000L

    private data class Dish(val items: List<MealItem>, val rich: Boolean, val rise: Double)

    private val breakfasts = listOf(
        Dish(listOf(MealItem("porridge", "1 bowl", carbs = 30, sugars = 1, fibre = 4), MealItem("banana", "1 medium", carbs = 23, sugars = 12, fibre = 3)), false, 2.4),
        Dish(listOf(MealItem("skyr", "1 cup", carbs = 12, sugars = 11), MealItem("granola", "40 g", carbs = 26, sugars = 9, fibre = 3)), false, 2.0),
        Dish(listOf(MealItem("toast", "2 slices", carbs = 30, sugars = 3, fibre = 2), MealItem("jam", "1 spoon", carbs = 13, sugars = 12)), false, 2.9),
        Dish(listOf(MealItem("eggs", "2 eggs", carbs = 1), MealItem("rye bread", "1 slice", carbs = 15, sugars = 1, fibre = 3)), true, 1.2),
    )
    private val lunches = listOf(
        Dish(listOf(MealItem("basmati rice", "1 cup", carbs = 45, sugars = 0, fibre = 1), MealItem("chicken curry", "1 bowl", carbs = 8, sugars = 4), MealItem("naan bread", "1 piece", carbs = 40, sugars = 3, fibre = 2)), true, 4.1),
        Dish(listOf(MealItem("fish soup", "1 bowl", carbs = 14, sugars = 4, fibre = 1), MealItem("rye bread", "1 slice", carbs = 15, sugars = 1, fibre = 3)), false, 1.6),
        Dish(listOf(MealItem("chicken wrap", "1 wrap", carbs = 38, sugars = 3, fibre = 3), MealItem("apple", "1 medium", carbs = 21, sugars = 16, fibre = 4)), false, 2.6),
        Dish(listOf(MealItem("salad", "1 bowl", carbs = 9, sugars = 4, fibre = 4), MealItem("hummus", "2 spoons", carbs = 6, fibre = 2)), false, 0.9),
    )
    private val dinners = listOf(
        Dish(listOf(MealItem("pasta", "1 plate", carbs = 70, sugars = 3, fibre = 4), MealItem("tomato sauce", "1 ladle", carbs = 9, sugars = 6, fibre = 2)), false, 3.6),
        Dish(listOf(MealItem("lamb", "1 portion", carbs = 0), MealItem("potatoes", "4 small", carbs = 34, sugars = 1, fibre = 3)), true, 2.3),
        Dish(listOf(MealItem("salmon", "1 fillet", carbs = 0), MealItem("brown rice", "1 cup", carbs = 44, sugars = 0, fibre = 3)), false, 2.2),
        Dish(listOf(MealItem("pizza", "3 slices", carbs = 90, sugars = 9, fibre = 6)), true, 4.6),
        Dish(listOf(MealItem("chicken", "1 breast", carbs = 0), MealItem("vegetables", "1 plate", carbs = 12, sugars = 6, fibre = 5)), false, 0.8),
    )

    /** [MealEntry.before] here holds the before reading's time (ids come when saved). */
    data class Data(val readings: List<Reading>, val sensor: List<SensorPoint>, val meals: List<MealEntry>)

    /** Readings and meals for the [days] days up to now (oldest first), and sensor points for the last day. */
    fun make(now: Long, zone: ZoneId, days: Int = 30): Data {
        val rnd = Random(42)
        val out = ArrayList<Reading>()
        val meals = ArrayList<MealEntry>()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun at(day: LocalDate, h: Int, m: Int) = day.atStartOfDay(zone).plusMinutes((h * 60 + m).toLong()).toInstant().toEpochMilli()
        fun jitter(base: Int) = base + rnd.nextInt(-25, 26)
        fun round1(v: Double) = (v * 10).roundToInt() / 10.0

        for (k in days - 1 downTo 0) {
            val day = today.minusDays(k.toLong())
            // A slow improvement over the month, like someone getting into a routine.
            val drift = 0.9 * k / days
            fun add(t: Long, v: Double, tag: MealTag) {
                if (t > now) return
                out += Reading(at = t, mmol = round1(v.coerceIn(2.6, 22.0)), tag = tag)
            }
            fun eat(t: Long, name: String, dish: Dish, beforeAt: Long?) {
                if (t > now) return
                meals += MealEntry(at = t, name = name, meal = Meal(dish.items, dish.rich, "photo"), before = beforeAt)
            }
            // Fasting on waking, then breakfast a while later (not always straight after).
            val fasting = 5.6 + drift + rnd.nextDouble(-0.6, 0.9)
            val wake = at(day, 7, jitter(25))
            if (rnd.nextInt(10) != 0) {
                add(wake, fasting, MealTag.FASTING)
                eat(wake + rnd.nextInt(10, 75) * MIN, "Breakfast", breakfasts[rnd.nextInt(breakfasts.size)], wake)
            }

            for ((hour, list) in listOf(12 to lunches, 18 to dinners)) {
                if (rnd.nextInt(8) == 0) continue // a skipped meal check
                val dish = list[rnd.nextInt(list.size)]
                val beforeAt = at(day, hour, jitter(15))
                val before = 5.4 + drift + rnd.nextDouble(-0.5, 1.2)
                add(beforeAt, before, MealTag.BEFORE_MEAL)
                val ateAt = beforeAt + rnd.nextInt(5, 25) * MIN
                eat(ateAt, if (hour == 12) "Lunch" else "Dinner", dish, beforeAt)
                if (rnd.nextInt(4) != 0) {
                    val gap = if (dish.rich) 180 else 120
                    add(ateAt + (gap + rnd.nextInt(-10, 20)) * MIN, before + dish.rise + drift * 0.5 + rnd.nextDouble(-0.6, 0.8), MealTag.AFTER_MEAL)
                }
            }
            if (rnd.nextInt(6) != 0) add(at(day, 22, jitter(30)), 6.4 + drift + rnd.nextDouble(-0.8, 1.4), MealTag.BEDTIME)
            // Now and then a low before dinner, or a high late evening.
            if (rnd.nextInt(9) == 0) add(at(day, 16, rnd.nextInt(0, 50)), rnd.nextDouble(3.1, 3.8), MealTag.OTHER)
            if (rnd.nextInt(11) == 0) add(at(day, 21, rnd.nextInt(0, 40)), rnd.nextDouble(12.5, 15.5), MealTag.OTHER)
        }

        // The last 24 hours as a sensor trace: a calm base with meal bumps, every 5 minutes.
        val sensor = ArrayList<SensorPoint>()
        var t = now - 24 * 60 * MIN
        while (t <= now) {
            val local = java.time.Instant.ofEpochMilli(t).atZone(zone)
            val h = local.hour + local.minute / 60.0
            fun bump(peak: Double, size: Double) = size * exp(-((h - peak) * (h - peak)) / 0.9)
            val v = 5.8 + 0.4 * sin(2 * PI * h / 24) + bump(9.0, 2.2) + bump(13.6, 3.0) + bump(19.6, 3.4) + rnd.nextDouble(-0.15, 0.15)
            sensor += SensorPoint(t, round1(v))
            t += 5 * MIN
        }
        return Data(out, sensor, meals)
    }
}
