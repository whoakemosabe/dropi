package app.dropi.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dropi.CrashLog
import app.dropi.Prefs
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Schedule
import app.dropi.data.Slot
import app.dropi.data.Status
import app.dropi.data.Tagging
import app.dropi.data.Targets
import app.dropi.work.HealthSync
import app.dropi.work.Reminders
import app.dropi.work.UpdateWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** What the screens show and the actions they take. One per activity. */
class AppState(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = Prefs(context)
    private val db = Db.get(context)

    val zone: ZoneId get() = ZoneId.systemDefault()
    val targets = Targets()

    var ready by mutableStateOf(false)
        private set

    /** The last 90 days, oldest first (enough for today, streaks and trends). */
    var readings by mutableStateOf<List<Reading>>(emptyList())
        private set

    var unit by mutableStateOf(prefs.unit)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var haptics by mutableStateOf(prefs.haptics)
        private set

    var slots by mutableStateOf(prefs.slots)
        private set
    var remindersOn by mutableStateOf(prefs.remindersOn)
        private set
    var afterMealOn by mutableStateOf(prefs.afterMealOn)
        private set
    var afterMealMinutes by mutableIntStateOf(prefs.afterMealMinutes)
        private set

    var healthSync by mutableStateOf(prefs.healthSync)
        private set
    /** What the last Health Connect sync did, for Settings. */
    var healthNote by mutableStateOf<String?>(null)

    /** The log sheet: null when closed; a reading with id 0 is a new one. */
    var editing by mutableStateOf<Reading?>(null)

    /** A low (or very high) reading just saved, for the guidance card. */
    var alertFor by mutableStateOf<Reading?>(null)

    var tab by mutableStateOf(Tab.TODAY)

    /** The Trends period (7, 14, 30 or 90 days); the header shows its dates. */
    var trendsDays by mutableIntStateOf(14)
    var settingsOpen by mutableStateOf(false)
    var scheduleOpen by mutableStateOf(false)
    var exportOpen by mutableStateOf(false)

    var lastCrash by mutableStateOf<String?>(null)
        private set

    init {
        Haptics.enabled = haptics
        lastCrash = CrashLog.read(context)
        scope.launch {
            reload()
            ready = true
        }
    }

    private suspend fun reload() {
        val since = LocalDate.now(zone).minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        readings = try { db.since(since) } catch (e: Exception) { emptyList() }
    }

    /** Coming back to the app: the day may have changed and an update may be out. */
    fun onOpen() {
        scope.launch {
            reload()
            if (healthSync) syncHealth()
            try { UpdateWatch.check(context, background = false) } catch (e: Exception) { }
        }
        Reminders.rescheduleAsync(context)
    }

    fun status(r: Reading): Status = Glucose.status(r.mmol, r.tag, targets)

    /** Opens the log sheet for a new reading, with [tag] or one picked from the time of day. */
    fun startNew(tag: MealTag? = null) {
        val now = System.currentTimeMillis()
        editing = Reading(at = now, mmol = 0.0, tag = tag ?: Tagging.suggest(now, readings, zone))
    }

    fun save(r: Reading) {
        scope.launch {
            val saved = try { db.save(r) } catch (e: Exception) { null } ?: return@launch
            reload()
            if (r.id == 0L) {
                // A new reading answers the reminders set before it, and a before-meal one sets
                // the after-meal reminder.
                prefs.oneOffs = Schedule.afterSave(prefs.oneOffs, saved, prefs.afterMealOn, prefs.afterMealMinutes, System.currentTimeMillis())
                Reminders.clearShown(context)
                val s = status(saved)
                // Fresh lows and very highs get the guidance card (not ones logged hours late).
                val recent = System.currentTimeMillis() - saved.at < 60 * 60_000L
                if (recent && (s.isLow || s == Status.VERY_HIGH)) alertFor = saved
            }
            Reminders.rescheduleAsync(context)
            if (healthSync) try { HealthSync.push(context, listOf(saved)) } catch (e: Exception) { }
        }
    }

    /** The reading just deleted, for the Undo bar (cleared after a few seconds). */
    var lastDeleted by mutableStateOf<Reading?>(null)

    fun delete(r: Reading) {
        lastDeleted = r
        scope.launch {
            try { db.delete(r.id) } catch (e: Exception) { }
            reload()
            Reminders.rescheduleAsync(context)
            try { HealthSync.remove(context, r) } catch (e: Exception) { }
        }
    }

    /** Undo on the bar after a delete: the reading comes back as it was. */
    fun undoDelete() {
        val r = lastDeleted ?: return
        lastDeleted = null
        if (r.imported) prefs.ignoredSources = prefs.ignoredSources - r.source
        scope.launch {
            val back = try { db.save(r.copy(id = 0)) } catch (e: Exception) { null } ?: return@launch
            reload()
            Reminders.rescheduleAsync(context)
            if (healthSync && !back.imported) try { HealthSync.push(context, listOf(back)) } catch (e: Exception) { }
        }
    }

    /** "Remind me to re-check" on the low card; returns the time it will fire. */
    fun remindRecheck(r: Reading): Long = Reminders.remindRecheck(context, r.tag)

    fun changeUnit(u: GlucoseUnit) { unit = u; prefs.unit = u; Reminders.rescheduleAsync(context) }
    fun changeTheme(t: String) { theme = t; prefs.theme = t; Reminders.rescheduleAsync(context) }
    fun changeHaptics(on: Boolean) { haptics = on; prefs.haptics = on; Haptics.enabled = on }

    fun changeSlots(list: List<Slot>) {
        val sorted = list.sortedBy { it.minute }
        slots = sorted
        prefs.slots = sorted
        Reminders.rescheduleAsync(context)
    }

    fun changeReminders(on: Boolean) {
        remindersOn = on
        prefs.remindersOn = on
        Reminders.rescheduleAsync(context)
    }

    fun changeAfterMeal(on: Boolean, minutes: Int = afterMealMinutes) {
        afterMealOn = on
        afterMealMinutes = minutes
        prefs.afterMealOn = on
        prefs.afterMealMinutes = minutes
    }

    /** Pulls in other apps' readings and sends ours; quietly does nothing without permission. */
    suspend fun syncHealth() {
        try {
            val added = HealthSync.pull(context)
            HealthSync.push(context, readings)
            if (added > 0) reload()
            healthNote = if (added > 0) "Brought in $added ${if (added == 1) "reading" else "readings"}" else "Up to date"
        } catch (e: Exception) {
            healthNote = "Couldn't sync with Health Connect"
        }
    }

    /** Health Connect switched on (after permission) or off. */
    fun changeHealthSync(on: Boolean) {
        healthSync = on
        prefs.healthSync = on
        healthNote = null
        if (on) scope.launch { syncHealth() }
    }

    fun dismissCrash() {
        CrashLog.clear(context)
        lastCrash = null
    }
}
