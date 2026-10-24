package app.dropi.ui

import android.content.Context
import android.text.format.DateFormat
import app.dropi.L
import app.dropi.R
import app.dropi.tr
import app.dropi.data.MealTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Times the way the phone shows them (12 or 24 hour), with day and month names in the app's language. */
class Fmt(context: Context, private val zone: ZoneId) {
    /** The phone uses a 24-hour clock. */
    val is24: Boolean = DateFormat.is24HourFormat(context)
    private val loc = L.locale
    private val isl = L.icelandic
    private val clock = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", if (isl) loc else Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE", loc)
    private val dayMonth = DateTimeFormatter.ofPattern(if (isl) "EEE d. MMM" else "EEE d MMM", loc)
    private val longDay = DateTimeFormatter.ofPattern(if (isl) "EEEE d. MMMM" else "EEEE, d MMMM", loc)

    fun time(at: Long): String = clock.format(Instant.ofEpochMilli(at).atZone(zone))

    fun longDate(day: LocalDate): String = longDay.format(day).cap()

    /** "14:05", "Yesterday 22:10", "Mon 08:00" or "Thu 9 Oct 08:00". */
    fun dayTime(at: Long, now: Long = System.currentTimeMillis()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val d = t.toLocalDate()
        val c = clock.format(t)
        return when {
            d == today -> c
            d == today.minusDays(1) -> tr("Yesterday {0}", c)
            d.isAfter(today.minusDays(7)) -> "${weekday.format(t).cap()} $c"
            else -> "${dayMonth.format(t).cap()} $c"
        }
    }

    /** "just now", "12 min ago", "3 h ago", or the day and time beyond 12 hours. */
    fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
        val min = (now - at) / 60_000
        return when {
            min < 1 -> tr("just now")
            min < 60 -> tr("{0} min ago", min)
            min < 12 * 60 -> tr("{0} h ago", min / 60)
            else -> dayTime(at, now)
        }
    }
}

/**
 * Day and month names in the app's language: [pattern] is the English pattern ("EEE d MMM");
 * in Icelandic a point follows the day number ("lau. 24. okt.").
 */
fun dayPattern(pattern: String): DateTimeFormatter =
    if (L.icelandic) DateTimeFormatter.ofPattern(pattern.replace(Regex("\\bd\\b"), "d.").replace(",", ""), L.locale)
    else DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)

/** Icelandic day names are written in lower case; at the start of a label they're capitalised. */
fun String.cap(): String = replaceFirstChar { it.titlecase(L.locale) }

/**
 * A meal's name as shown. The default names ("Breakfast", "Lunch", "Dinner", "Snack") are saved
 * in English on each meal, so only those are translated here; a person's own name is shown as typed.
 */
fun mealName(name: String): String = when (name) {
    "Breakfast" -> tr("Breakfast")
    "Lunch" -> tr("Lunch")
    "Dinner" -> tr("Dinner")
    "Snack" -> tr("Snack")
    else -> name
}

/** "before breakfast": a reading taken just before a meal (whole phrases, for Icelandic case). */
fun beforeMealText(name: String): String = when (name) {
    "Breakfast" -> tr("before breakfast")
    "Lunch" -> tr("before lunch")
    "Dinner" -> tr("before dinner")
    "Snack" -> tr("before snack")
    else -> tr("before {0}", name.lowercase(L.locale))
}

/** "2 h after breakfast": [gap] is the time since the meal ("2 h", "45 min"). */
fun afterMealText(gap: String, name: String): String = when (name) {
    "Breakfast" -> tr("{0} after breakfast", gap)
    "Lunch" -> tr("{0} after lunch", gap)
    "Dinner" -> tr("{0} after dinner", gap)
    "Snack" -> tr("{0} after snack", gap)
    else -> tr("{0} after {1}", gap, name.lowercase(L.locale))
}

/** "after your breakfast at 08:10", for the next check that follows a meal. */
fun afterYourMealText(name: String, time: String): String = when (name) {
    "Breakfast" -> tr("after your breakfast at {0}", time)
    "Lunch" -> tr("after your lunch at {0}", time)
    "Dinner" -> tr("after your dinner at {0}", time)
    "Snack" -> tr("after your snack at {0}", time)
    else -> tr("after your {0} at {1}", name.lowercase(L.locale), time)
}

/** A time gap in minutes as "45 min", "2 h" or "1 h 20 min" (rounded to the hour within 10 minutes). */
fun gapText(mins: Int): String {
    val h = mins / 60
    val rest = mins % 60
    return when {
        h == 0 -> tr("{0} min", mins)
        rest < 10 -> tr("{0} h", h)
        rest > 50 -> tr("{0} h", h + 1)
        else -> tr("{0} h {1} min", h, rest)
    }
}

/** An icon for each moment. */
fun MealTag.icon(): Int = when (this) {
    MealTag.FASTING -> R.drawable.lc_sun
    MealTag.BEFORE_MEAL, MealTag.AFTER_MEAL -> R.drawable.lc_utensils
    MealTag.BEDTIME, MealTag.OVERNIGHT -> R.drawable.lc_moon
    MealTag.OTHER -> R.drawable.lc_droplet
}

/** English-only "1 reading", "3 readings". Shown text uses the translated plural function instead. */
@Deprecated("Use the translated plural function, trn")
fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
