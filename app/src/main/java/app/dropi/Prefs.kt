package app.dropi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.dropi.data.GlucoseUnit
import app.dropi.data.OneOff
import app.dropi.data.Schedule
import app.dropi.data.Slot

/** Settings and small bits of state. Readings live in the database, not here. */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences("dropi", Context.MODE_PRIVATE)

    /** Readings are always stored in mmol/L; this is only how they're shown and typed. */
    var unit: GlucoseUnit
        get() = GlucoseUnit.fromKey(p.getString("unit", GlucoseUnit.MMOL.key))
        set(v) = p.edit { putString("unit", v.key) }

    /** "system", "dark" or "light". */
    var theme: String
        get() = p.getString("theme", "system") ?: "system"
        set(v) = p.edit { putString("theme", v) }

    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit { putBoolean("haptics", v) }

    // ---- Planned checks and reminders ----

    /** The daily check times; the four defaults until they're edited. */
    var slots: List<Slot>
        get() = Schedule.slotsFromJson(p.getString("slots", null)) ?: Schedule.defaults()
        set(v) = p.edit { putString("slots", Schedule.slotsToJson(v)) }

    /** Notifications at the check times (off until switched on, which asks for permission). */
    var remindersOn: Boolean
        get() = p.getBoolean("reminders_on", false)
        set(v) = p.edit { putBoolean("reminders_on", v) }

    /** After a before-meal reading, a reminder for the after-meal check. */
    var afterMealOn: Boolean
        get() = p.getBoolean("after_meal_on", true)
        set(v) = p.edit { putBoolean("after_meal_on", v) }

    /** Minutes from the before-meal reading to the after-meal check (ADA: 1–2 h after starting). */
    var afterMealMinutes: Int
        get() = p.getInt("after_meal_minutes", 120)
        set(v) = p.edit { putInt("after_meal_minutes", v) }

    var oneOffs: List<OneOff>
        get() = Schedule.oneOffsFromJson(p.getString("one_offs", null))
        set(v) = p.edit { putString("one_offs", Schedule.oneOffsToJson(v.sortedBy { it.at }.takeLast(20))) }

    /** Slot reminders already posted ("slotId@2026-10-23"), so each fires once a day. */
    var notifiedSlots: Set<String>
        get() = p.getStringSet("notified_slots", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("notified_slots", v.toSortedSet().toList().takeLast(40).toSet()) }

    // ---- Updates (same keys and meaning as F1 Pro) ----

    /** The newest version GitHub has, when it's newer than this one; "" when up to date. */
    var availableVersion: String
        get() = p.getString("available_version", "") ?: ""
        set(v) = p.edit { putString("available_version", v) }

    /** That version's highlights, one per line. */
    var availableNotes: String
        get() = p.getString("available_notes", "") ?: ""
        set(v) = p.edit { putString("available_notes", v) }

    /** How many releases it is ahead of this one. */
    var availableBehind: Int
        get() = p.getInt("available_behind", 1)
        set(v) = p.edit { putInt("available_behind", v) }

    var lastUpdateCheck: Long
        get() = p.getLong("last_update_check", 0L)
        set(v) = p.edit { putLong("last_update_check", v) }

    /** The version a notification was already posted for (one per version). */
    var notifiedVersion: String
        get() = p.getString("notified_version", "") ?: ""
        set(v) = p.edit { putString("notified_version", v) }
}
