package app.dropi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.dropi.data.GlucoseUnit
import app.dropi.data.OneOff
import app.dropi.data.Schedule
import app.dropi.data.Slot
import app.dropi.data.A1c
import app.dropi.data.LabA1c
import app.dropi.data.Targets

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

    /** The first-run welcome (units and every permission) has been through. */
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit { putBoolean("onboarded", v) }

    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit { putBoolean("haptics", v) }

    // ---- Targets (set with the care team; ADA defaults) ----

    var targets: Targets
        get() {
            val d = Targets()
            return Targets(
                beforeLow = p.getFloat("t_before_low", d.beforeLow.toFloat()).toDouble(),
                beforeHigh = p.getFloat("t_before_high", d.beforeHigh.toFloat()).toDouble(),
                afterHigh = p.getFloat("t_after_high", d.afterHigh.toFloat()).toDouble(),
            ).safe()
        }
        set(v) = p.edit {
            val t = v.safe()
            putFloat("t_before_low", t.beforeLow.toFloat())
            putFloat("t_before_high", t.beforeHigh.toFloat())
            putFloat("t_after_high", t.afterHigh.toFloat())
        }

    /** Lab A1C results, oldest first. */
    var labA1c: List<LabA1c>
        get() = A1c.fromJson(p.getString("lab_a1c", null))
        set(v) = p.edit { putString("lab_a1c", A1c.toJson(v.sortedBy { it.date }.takeLast(60))) }

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

    /** A Sunday-evening summary of the week. */
    var recapOn: Boolean
        get() = p.getBoolean("recap_on", true)
        set(v) = p.edit { putBoolean("recap_on", v) }

    /** The week (its Monday, "2026-10-19") the last recap was posted for. */
    var recapSentFor: String
        get() = p.getString("recap_sent_for", "") ?: ""
        set(v) = p.edit { putString("recap_sent_for", v) }

    /** Slot reminders already posted ("slotId@2026-10-23"), so each fires once a day. */
    var notifiedSlots: Set<String>
        get() = p.getStringSet("notified_slots", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("notified_slots", v.toSortedSet().toList().takeLast(40).toSet()) }

    // ---- Health Connect ----

    /** Two-way blood glucose sync with Health Connect (asks for permission when switched on). */
    var healthSync: Boolean
        get() = p.getBoolean("health_sync", false)
        set(v) = p.edit { putBoolean("health_sync", v) }

    var lastHealthSync: Long
        get() = p.getLong("last_health_sync", 0L)
        set(v) = p.edit { putLong("last_health_sync", v) }

    /** Imported readings deleted in Dropi, so a sync doesn't bring them back. */
    var ignoredSources: Set<String>
        get() = p.getStringSet("ignored_sources", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("ignored_sources", v.toSortedSet().toList().takeLast(2000).toSet()) }

    // ---- Live sensor (CGM) ----

    /** Live mode: sensor values from xDrip+, Juggluco or Health Connect drive the home screen. */
    var liveOn: Boolean
        get() = p.getBoolean("live_on", false)
        set(v) = p.edit { putBoolean("live_on", v) }

    /** The ongoing notification with the latest value and arrow (a Live Update on Android 16). */
    var liveNotify: Boolean
        get() = p.getBoolean("live_notify", true)
        set(v) = p.edit { putBoolean("live_notify", v) }

    /** A heads-up when the sensor trend points under 3.9 within 20 minutes. */
    var headsUpOn: Boolean
        get() = p.getBoolean("heads_up_on", true)
        set(v) = p.edit { putBoolean("heads_up_on", v) }

    var lastHeadsAt: Long
        get() = p.getLong("last_heads_at", 0L)
        set(v) = p.edit { putLong("last_heads_at", v) }

    var lastHeadsKind: String
        get() = p.getString("last_heads_kind", "") ?: ""
        set(v) = p.edit { putString("last_heads_kind", v) }

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

    // ---- Meals ----

    /** The person's own amounts per food (food → portion and carbs), used before any guess. */
    var usualFoods: Map<String, app.dropi.data.Usual>
        get() = try {
            val o = org.json.JSONObject(p.getString("usual_foods", null) ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val u = o.getJSONObject(k)
                app.dropi.data.Usual(u.optString("portion", "1 portion"), u.optInt("carbs"), if (u.has("sugars")) u.optInt("sugars") else null)
            }
        } catch (e: Exception) { emptyMap() }
        set(v) = p.edit {
            val o = org.json.JSONObject()
            v.entries.take(300).forEach { (k, u) ->
                o.put(k, org.json.JSONObject().put("portion", u.portion).put("carbs", u.carbs).apply { u.sugars?.let { put("sugars", it) } })
            }
            putString("usual_foods", o.toString())
        }
}
