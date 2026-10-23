package app.dropi.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A planned check, every day at [minute] after midnight (local time). */
data class Slot(
    val id: String,
    val minute: Int,
    val tag: MealTag,
    val enabled: Boolean = true,
) {
    val hour get() = minute / 60
    val min get() = minute % 60

    /** "Fasting", "Before lunch", "After dinner", "Bedtime"… */
    val name: String
        get() {
            fun meal() = when (hour) {
                in 4..10 -> "breakfast"
                in 11..15 -> "lunch"
                else -> "dinner"
            }
            return when (tag) {
                MealTag.BEFORE_MEAL -> "Before ${meal()}"
                MealTag.AFTER_MEAL -> "After ${meal()}"
                else -> tag.label
            }
        }

    /** This slot's time on [day], as epoch ms. */
    fun at(day: LocalDate, zone: ZoneId): Long =
        day.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
}

/** A one-time reminder: two hours after a before-meal reading, a low re-check, a snooze. */
data class OneOff(
    val at: Long,
    val tag: MealTag,
    val kind: Kind,
    /** When it was set; a reading logged after this answers it. */
    val created: Long,
) {
    enum class Kind(val title: String) {
        AFTER_MEAL("Time for your after-meal check"),
        RECHECK("Re-check your low"),
        SNOOZE("Time to check");

        companion object {
            fun fromKey(k: String?) = entries.firstOrNull { it.name == k } ?: SNOOZE
        }
    }
}

/** How a planned check stands today. */
enum class CheckState { DONE, DUE, UPCOMING, MISSED }

data class Check(val slot: Slot, val at: Long, val state: CheckState, val reading: Reading?)

object Schedule {
    private const val MIN = 60_000L

    /** A reading answers a check when it's close in time: same moment within 1 h before to 1.5 h after, or anything within 30 min. */
    private const val SAME_TAG_BEFORE = 60 * MIN
    private const val SAME_TAG_AFTER = 90 * MIN
    private const val ANY_TAG = 30 * MIN

    /** A check stays "due" for 90 minutes, then counts as missed. */
    const val DUE_FOR = 90 * MIN

    fun defaults(): List<Slot> = listOf(
        Slot("fasting", 7 * 60 + 30, MealTag.FASTING),
        Slot("lunch", 12 * 60, MealTag.BEFORE_MEAL),
        Slot("dinner", 18 * 60, MealTag.BEFORE_MEAL),
        Slot("bedtime", 22 * 60, MealTag.BEDTIME),
    )

    fun matches(slotAt: Long, slotTag: MealTag, r: Reading): Boolean {
        val d = r.at - slotAt
        if (kotlin.math.abs(d) <= ANY_TAG) return true
        return r.tag == slotTag && d >= -SAME_TAG_BEFORE && d <= SAME_TAG_AFTER
    }

    /** Today's enabled checks in time order, each marked done, due, upcoming or missed. */
    fun today(slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId): List<Check> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val used = HashSet<Long>()
        return slots.filter { it.enabled }.sortedBy { it.minute }.map { s ->
            val at = s.at(day, zone)
            // Each reading answers one check at most, the closest one first in time.
            val r = readings
                .filter { it.id !in used && matches(at, s.tag, it) }
                .minByOrNull { kotlin.math.abs(it.at - at) }
            if (r != null) used += r.id
            val state = when {
                r != null -> CheckState.DONE
                now < at -> CheckState.UPCOMING
                now <= at + DUE_FOR -> CheckState.DUE
                else -> CheckState.MISSED
            }
            Check(s, at, state, r)
        }
    }

    /** The next enabled check not done yet: one due now, later today, or the first one tomorrow. */
    fun next(slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId): Check? {
        val todays = today(slots, readings, now, zone)
        todays.firstOrNull { it.state == CheckState.DUE || it.state == CheckState.UPCOMING }?.let { return it }
        val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        val first = slots.filter { it.enabled }.minByOrNull { it.minute } ?: return null
        return Check(first, first.at(tomorrow, zone), CheckState.UPCOMING, null)
    }

    /** The weekly recap goes out on Sunday at 19:00. */
    const val RECAP_HOUR = 19

    /** The next Sunday 19:00 after [now]. */
    fun nextRecap(now: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        var d = t.toLocalDate()
        while (true) {
            if (d.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                val at = d.atStartOfDay(zone).plusHours(RECAP_HOUR.toLong()).toInstant().toEpochMilli()
                if (at > now) return at
            }
            d = d.plusDays(1)
        }
    }

    /** The Monday of the week to recap, when [now] is Sunday evening (19:00 to midnight); else null. */
    fun recapWeek(now: Long, zone: ZoneId): LocalDate? {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        if (t.dayOfWeek != java.time.DayOfWeek.SUNDAY || t.hour < RECAP_HOUR) return null
        return t.toLocalDate().minusDays(6)
    }

    /** When the next reminder should fire after [now]: the next slot time (if reminders are on) or a one-off. */
    fun nextAlarm(
        slots: List<Slot>, oneOffs: List<OneOff>, remindersOn: Boolean, now: Long, zone: ZoneId, recapOn: Boolean = false,
    ): Long? {
        val times = ArrayList<Long>()
        if (recapOn) times += nextRecap(now, zone)
        if (remindersOn) {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            slots.filter { it.enabled }.forEach { s ->
                val t = s.at(today, zone)
                times += if (t > now) t else s.at(today.plusDays(1), zone)
            }
        }
        oneOffs.forEach { times += maxOf(it.at, now + 1_000) }
        return times.minOrNull()
    }

    /** Slots whose time is now (allowing a late alarm), not yet answered by a reading. */
    fun dueSlots(slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId): List<Pair<Slot, Long>> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return slots.filter { it.enabled }.mapNotNull { s ->
            val at = s.at(day, zone)
            val late = now - at
            if (late < -MIN || late > 30 * MIN) return@mapNotNull null
            // Already checked a little early: no need to nag.
            if (readings.any { matches(at, s.tag, it) && it.at <= now }) null else s to at
        }
    }

    /**
     * After saving a new reading [r]: reminders set before it are answered, and a before-meal
     * reading sets the after-meal one ([afterMin] minutes later, when that's still ahead).
     */
    fun afterSave(oneOffs: List<OneOff>, r: Reading, afterMealOn: Boolean, afterMin: Int, now: Long): List<OneOff> {
        val kept = oneOffs.filter { it.created > r.at }.toMutableList()
        if (afterMealOn && r.tag == MealTag.BEFORE_MEAL) {
            // A rich meal (high fat or protein) peaks later, so its check waits for 3 hours.
            val at = r.at + (if (r.meal?.rich == true) maxOf(afterMin, 180) else afterMin) * MIN
            if (at > now) kept += OneOff(at, MealTag.AFTER_MEAL, OneOff.Kind.AFTER_MEAL, now)
        }
        return kept
    }

    // ---- Storage (JSON in prefs) ----

    fun slotsToJson(slots: List<Slot>): String = JSONArray().apply {
        slots.forEach { s ->
            put(JSONObject().put("id", s.id).put("minute", s.minute).put("tag", s.tag.name).put("on", s.enabled))
        }
    }.toString()

    fun slotsFromJson(json: String?): List<Slot>? {
        if (json.isNullOrBlank()) return null
        return try {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Slot(o.getString("id"), o.getInt("minute").coerceIn(0, 1439), MealTag.fromKey(o.optString("tag")), o.optBoolean("on", true))
            }
        } catch (e: Exception) { null }
    }

    fun oneOffsToJson(list: List<OneOff>): String = JSONArray().apply {
        list.forEach { o ->
            put(JSONObject().put("at", o.at).put("tag", o.tag.name).put("kind", o.kind.name).put("created", o.created))
        }
    }.toString()

    fun oneOffsFromJson(json: String?): List<OneOff> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                OneOff(o.getLong("at"), MealTag.fromKey(o.optString("tag")), OneOff.Kind.fromKey(o.optString("kind")), o.optLong("created"))
            }
        } catch (e: Exception) { emptyList() }
    }
}
