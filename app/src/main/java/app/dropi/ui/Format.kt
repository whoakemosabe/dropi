package app.dropi.ui

import android.content.Context
import android.text.format.DateFormat
import app.dropi.R
import app.dropi.data.MealTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Times the way the phone shows them (12 or 24 hour), in English like the rest of the app. */
class Fmt(context: Context, private val zone: ZoneId) {
    /** The phone uses a 24-hour clock. */
    val is24: Boolean = DateFormat.is24HourFormat(context)
    private val clock = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val longDay = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    fun time(at: Long): String = clock.format(Instant.ofEpochMilli(at).atZone(zone))

    fun longDate(day: LocalDate): String = longDay.format(day)

    /** "14:05", "Yesterday 22:10", "Mon 08:00" or "Thu 9 Oct 08:00". */
    fun dayTime(at: Long, now: Long = System.currentTimeMillis()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val d = t.toLocalDate()
        val c = clock.format(t)
        return when {
            d == today -> c
            d == today.minusDays(1) -> "Yesterday $c"
            d.isAfter(today.minusDays(7)) -> "${weekday.format(t)} $c"
            else -> "${dayMonth.format(t)} $c"
        }
    }

    /** "just now", "12 min ago", "3 h ago", or the day and time beyond 12 hours. */
    fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
        val min = (now - at) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 12 * 60 -> "${min / 60} h ago"
            else -> dayTime(at, now)
        }
    }
}

/** An icon for each moment. */
fun MealTag.icon(): Int = when (this) {
    MealTag.FASTING -> R.drawable.lc_sun
    MealTag.BEFORE_MEAL, MealTag.AFTER_MEAL -> R.drawable.lc_utensils
    MealTag.BEDTIME, MealTag.OVERNIGHT -> R.drawable.lc_moon
    MealTag.OTHER -> R.drawable.lc_droplet
}

/** "1 reading", "3 readings". */
fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
