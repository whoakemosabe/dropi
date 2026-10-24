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
    /** How long after its time the check still counts before it's missed, in minutes. */
    val window: Int = defaultWindow(tag),
) {
    companion object {
        /** Bedtime gets longer (people go to bed late more than they eat late). */
        fun defaultWindow(tag: MealTag) = if (tag == MealTag.BEDTIME) 120 else 90
        val WINDOWS = listOf(30, 60, 90, 120, 180)
    }

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
        AFTER_MEAL("Time for your after-eating check"),
        RECHECK("Re-check your low"),
        SNOOZE("Time to check");

        companion object {
            fun fromKey(k: String?) = entries.firstOrNull { it.name == k } ?: SNOOZE
        }
    }
}

/** How a planned check stands today. */
enum class CheckState { DONE, DUE, UPCOMING, MISSED }

/**
 * One of today's checks. [at] is when it's planned: the slot's time, or for an after-meal check
 * the before-meal reading it follows plus the after-meal gap ([anchor] is that reading).
 */
data class Check(
    val slot: Slot, val at: Long, val state: CheckState, val reading: Reading?, val anchor: Reading? = null,
    /** The meal an after-meal check follows, when one was logged. */
    val meal: MealEntry? = null,
) {
    /** When it stops counting and is missed. */
    val until: Long get() = at + slot.window * 60_000L
}

/** "You usually check bedtime at about 23:10: move it there?" */
data class MoveSuggestion(val slot: Slot, val minute: Int, val days: Int)

object Schedule {
    private const val MIN = 60_000L

    /**
     * A reading answers a check when it's near it: up to 2 h before or 2½ h after. Its moment
     * only rules it out when it says the opposite (an after-meal reading for a before-meal check,
     * or the other way round) and it's more than 30 min away; otherwise a different moment just
     * makes it a weaker match, so a bedtime check logged at 21:20 still counts.
     */
    private const val WINDOW_BEFORE = 120 * MIN
    private const val WINDOW_AFTER = 150 * MIN
    private const val ANY_TAG = 30 * MIN
    private const val OTHER_TAG = 45 * MIN


    fun defaults(): List<Slot> = listOf(
        Slot("fasting", 7 * 60 + 30, MealTag.FASTING),
        Slot("lunch", 12 * 60, MealTag.BEFORE_MEAL),
        Slot("dinner", 18 * 60, MealTag.BEFORE_MEAL),
        Slot("bedtime", 22 * 60, MealTag.BEDTIME),
    )

    private fun opposite(a: MealTag, b: MealTag): Boolean {
        val before = setOf(MealTag.BEFORE_MEAL, MealTag.FASTING)
        return (a == MealTag.AFTER_MEAL && b in before) || (b == MealTag.AFTER_MEAL && a in before)
    }

    /** How well [r] answers a check at [slotAt]: lower is better, null when it doesn't. */
    fun score(slotAt: Long, slotTag: MealTag, r: Reading): Long? {
        val d = r.at - slotAt
        if (d < -WINDOW_BEFORE || d > WINDOW_AFTER) return null
        val far = kotlin.math.abs(d)
        return when {
            r.tag == slotTag -> far
            opposite(r.tag, slotTag) -> if (far <= ANY_TAG) far + 2 * OTHER_TAG else null
            else -> far + OTHER_TAG
        }
    }

    fun matches(slotAt: Long, slotTag: MealTag, r: Reading): Boolean = score(slotAt, slotTag, r) != null

    /**
     * Which reading answers which of the day's checks: the best pairs first (closest in time, same
     * moment), each reading and each check used once. So a reading goes to the check it was
     * meant for even when another check is nearby.
     */
    private fun pair(timed: List<Pair<Slot, Long>>, readings: List<Reading>): Map<Int, Reading> {
        val options = ArrayList<Triple<Long, Int, Reading>>()
        timed.forEachIndexed { i, (s, at) ->
            for (r in readings) score(at, s.tag, r)?.let { options += Triple(it, i, r) }
        }
        val out = HashMap<Int, Reading>()
        val used = HashSet<Reading>()
        for ((_, i, r) in options.sortedBy { it.first }) {
            if (i in out || r in used) continue
            out[i] = r
            used += r
        }
        return out
    }

    /**
     * An after-meal check follows the meal, not the clock: when a before-meal (or fasting)
     * reading was logged in the 4 hours up to its time (or the hour after), it's due [afterMin]
     * minutes after that reading (3 h for a rich meal).
     */
    private data class Anchor(val at: Long, val reading: Reading?, val meal: MealEntry?)

    /**
     * The gap after eating before the after-meal check: 3 h when it's rich (or high in fat or
     * protein by the numbers), 2½ h with some fat or protein, else the usual gap.
     */
    fun gapFor(meal: MealEntry?, afterMin: Int): Int {
        val m = meal?.meal ?: return afterMin
        return when {
            m.slowDigesting -> maxOf(afterMin, 180)
            m.someFatProtein -> maxOf(afterMin, 150)
            else -> afterMin
        }
    }

    private fun anchorFor(s: Slot, slotAt: Long, readings: List<Reading>, meals: List<MealEntry>, afterMin: Int): Anchor? {
        if (s.tag != MealTag.AFTER_MEAL) return null
        // A logged meal is the best anchor: the check counts from when it was eaten.
        meals.filter { it.at in slotAt - 240 * MIN..slotAt + 60 * MIN }.maxByOrNull { it.at }?.let { m ->
            return Anchor(m.at + gapFor(m, afterMin) * MIN, MealLink.before(m, readings), m)
        }
        val before = readings.filter {
            (it.tag == MealTag.BEFORE_MEAL || it.tag == MealTag.FASTING) && it.at in slotAt - 240 * MIN..slotAt + 60 * MIN
        }.maxByOrNull { it.at } ?: return null
        return Anchor(before.at + afterMin * MIN, before, null)
    }

    /** Today's enabled checks in time order, each marked done, due, upcoming or missed. */
    fun today(
        slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId, afterMin: Int = 120,
        meals: List<MealEntry> = emptyList(),
    ): List<Check> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val planned = slots.filter { it.enabled }.sortedBy { it.minute }.map { s ->
            val fixed = s.at(day, zone)
            val anchor = anchorFor(s, fixed, readings, meals.filter { it.at <= now }, afterMin)
            Triple(s, anchor?.at ?: fixed, anchor)
        }
        val answered = pair(planned.map { it.first to it.second }, readings)
        return planned.mapIndexed { i, (s, at, anchor) ->
            val r = answered[i]
            val state = when {
                r != null -> CheckState.DONE
                now < at -> CheckState.UPCOMING
                now <= at + s.window * MIN -> CheckState.DUE
                else -> CheckState.MISSED
            }
            Check(s, at, state, r, anchor?.reading, anchor?.meal)
        }.sortedBy { it.at }
    }

    /** The next enabled check not done yet: one due now, later today, or the first one tomorrow. */
    fun next(
        slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId, afterMin: Int = 120,
        meals: List<MealEntry> = emptyList(),
    ): Check? {
        val todays = today(slots, readings, now, zone, afterMin, meals)
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
    fun dueSlots(
        slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId,
        afterMin: Int = 120, afterMealReminderOn: Boolean = false, meals: List<MealEntry> = emptyList(),
    ): List<Pair<Slot, Long>> {
        // Already checked a little early: no need to nag. Same pairing as Today shows.
        val checks = today(slots, readings.filter { it.at <= now }, now, zone, afterMin, meals)
        return checks.filter { c ->
            val late = now - c.at
            // An after-meal check following a before-meal reading already has its own reminder.
            val ownReminder = afterMealReminderOn && (c.meal != null || c.anchor?.tag == MealTag.BEFORE_MEAL)
            c.reading == null && !ownReminder && late >= -MIN && late <= 30 * MIN
        }.map { it.slot to it.at }
    }

    /**
     * Checks usually done at a different time from the plan: over the last two weeks, on at
     * least 5 days, the median time of the reading that answered it is 20+ minutes off. Rounded
     * to 5 minutes. After-meal checks follow meals already, so they're left out.
     */
    fun moves(slots: List<Slot>, readings: List<Reading>, now: Long, zone: ZoneId): List<MoveSuggestion> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val actual = HashMap<String, MutableList<Int>>()
        for (k in 1..14) {
            val day = today.minusDays(k.toLong())
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val dayReadings = readings.filter { it.date(zone) == day }
            if (dayReadings.isEmpty()) continue
            today(slots, dayReadings, end, zone).forEach { c ->
                val r = c.reading ?: return@forEach
                if (c.slot.tag == MealTag.AFTER_MEAL) return@forEach
                val t = Instant.ofEpochMilli(r.at).atZone(zone)
                actual.getOrPut(c.slot.id) { ArrayList() } += t.hour * 60 + t.minute
            }
        }
        return slots.filter { it.enabled && it.tag != MealTag.AFTER_MEAL }.mapNotNull { s ->
            val mins = actual[s.id] ?: return@mapNotNull null
            if (mins.size < 5) return@mapNotNull null
            val median = mins.sorted()[mins.size / 2]
            val rounded = ((median + 2) / 5 * 5).coerceIn(0, 1435)
            if (kotlin.math.abs(rounded - s.minute) < 20) null else MoveSuggestion(s, rounded, mins.size)
        }
    }

    /**
     * The moment for a new reading at [at]: the nearest open check around then (so logging at
     * 12:20 for the 12:00 check is "Before lunch" without picking it), or null when none is near.
     */
    fun momentFor(
        at: Long, slots: List<Slot>, readings: List<Reading>, zone: ZoneId, afterMin: Int = 120,
        meals: List<MealEntry> = emptyList(),
    ): MealTag? =
        today(slots, readings.filter { kotlin.math.abs(it.at - at) > MIN }, at, zone, afterMin, meals)
            .filter { it.reading == null && at - it.at in -WINDOW_BEFORE..WINDOW_AFTER }
            .minByOrNull { kotlin.math.abs(at - it.at) }
            ?.slot?.tag

    /**
     * After saving a new reading [r]: reminders set before it are answered, and a before-meal
     * reading sets the after-meal one ([afterMin] minutes later, when that's still ahead).
     */
    fun afterSave(
        oneOffs: List<OneOff>, r: Reading, afterMealOn: Boolean, afterMin: Int, now: Long,
        meals: List<MealEntry> = emptyList(),
    ): List<OneOff> {
        // A logged meal's own reminder (counted from when it was eaten) isn't answered by the
        // reading taken before it.
        val kept = oneOffs.filter { it.created > r.at || (it.kind == OneOff.Kind.AFTER_MEAL && r.tag != MealTag.AFTER_MEAL && it.at - r.at > 30 * MIN) }.toMutableList()
        val mealNear = meals.any { it.at in r.at - 15 * MIN..r.at + 90 * MIN }
        if (afterMealOn && r.tag == MealTag.BEFORE_MEAL && !mealNear) {
            val at = r.at + afterMin * MIN
            if (at > now) kept += OneOff(at, MealTag.AFTER_MEAL, OneOff.Kind.AFTER_MEAL, now)
        }
        return kept
    }

    /**
     * After logging a meal eaten at [mealAt]: the after-meal reminder is counted from then
     * ([gap] minutes), replacing any after-meal reminder set by a reading before it.
     */
    fun afterMeal(oneOffs: List<OneOff>, mealAt: Long, gap: Int, afterMealOn: Boolean, now: Long): List<OneOff> {
        val kept = oneOffs.filter { !(it.kind == OneOff.Kind.AFTER_MEAL && it.at in mealAt - 60 * MIN..mealAt + 5 * 60 * MIN) }.toMutableList()
        val at = mealAt + gap * MIN
        if (afterMealOn && at > now) kept += OneOff(at, MealTag.AFTER_MEAL, OneOff.Kind.AFTER_MEAL, now)
        return kept
    }

    // ---- Storage (JSON in prefs) ----

    fun slotsToJson(slots: List<Slot>): String = JSONArray().apply {
        slots.forEach { s ->
            put(JSONObject().put("id", s.id).put("minute", s.minute).put("tag", s.tag.name).put("on", s.enabled).put("window", s.window))
        }
    }.toString()

    fun slotsFromJson(json: String?): List<Slot>? {
        if (json.isNullOrBlank()) return null
        return try {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val tag = MealTag.fromKey(o.optString("tag"))
                Slot(o.getString("id"), o.getInt("minute").coerceIn(0, 1439), tag, o.optBoolean("on", true), o.optInt("window", Slot.defaultWindow(tag)).coerceIn(15, 360))
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
