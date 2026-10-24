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
