package app.dropi.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dropi.CrashLog
import app.dropi.Prefs
import app.dropi.data.Db
import app.dropi.data.GlucoseUnit
import app.dropi.data.Reading
import app.dropi.data.Status
import app.dropi.data.Glucose
import app.dropi.data.Targets
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

    /** The last 90 days, oldest first (enough for today, streaks and the coming trends). */
    var readings by mutableStateOf<List<Reading>>(emptyList())
        private set

    var unit by mutableStateOf(prefs.unit)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var haptics by mutableStateOf(prefs.haptics)
        private set

    /** The log sheet: null when closed; a reading with id 0 is a new one. */
    var editing by mutableStateOf<Reading?>(null)

    /** A low (or very high) reading just saved, for the guidance card. */
    var alertFor by mutableStateOf<Reading?>(null)

    var settingsOpen by mutableStateOf(false)

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
            try { UpdateWatch.check(context, background = false) } catch (e: Exception) { }
        }
    }

    fun status(r: Reading): Status = Glucose.status(r.mmol, r.tag, targets)

    fun startNew() {
        editing = Reading(at = System.currentTimeMillis(), mmol = 0.0, tag = app.dropi.data.Tagging.suggest(System.currentTimeMillis(), readings, zone))
    }

    fun save(r: Reading) {
        scope.launch {
            val saved = try { db.save(r) } catch (e: Exception) { null } ?: return@launch
            editing = null
            reload()
            val s = status(saved)
            // Fresh lows and very highs get the guidance card; edits of old ones don't.
            if (r.id == 0L && (s.isLow || s == Status.VERY_HIGH)) alertFor = saved
        }
    }

    fun delete(r: Reading) {
        scope.launch {
            try { db.delete(r.id) } catch (e: Exception) { }
            editing = null
            reload()
        }
    }

    fun setUnit(u: GlucoseUnit) { unit = u; prefs.unit = u }
    fun setTheme(t: String) { theme = t; prefs.theme = t }
    fun setHaptics(on: Boolean) { haptics = on; prefs.haptics = on; Haptics.enabled = on }

    fun dismissCrash() {
        CrashLog.clear(context)
        lastCrash = null
    }
}
