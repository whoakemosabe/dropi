package app.dropi.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dropi.CrashLog
import app.dropi.Prefs
import app.dropi.tr
import app.dropi.trn
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.MealEntry
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Schedule
import app.dropi.data.Slot
import app.dropi.data.Status
import app.dropi.data.Tagging
import app.dropi.data.Targets
import app.dropi.data.Ask
import app.dropi.data.MealLink
import app.dropi.data.Foods
import app.dropi.data.SensorPoint
import app.dropi.work.HealthSync
import app.dropi.work.LiveCgm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import app.dropi.work.Reminders
import app.dropi.work.UpdateWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** What the screens show and the actions they take. One per activity. */
class AppState(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = Prefs(context)
    /** The log in use: the demo one while demo data is on, else the real one. */
    private val db get() = Db.get(context)

    val zone: ZoneId get() = ZoneId.systemDefault()
    /** The person's target bands (ADA defaults until changed in Settings). */
    var targets by mutableStateOf(prefs.targets)
        private set

    var labA1c by mutableStateOf(prefs.labA1c)
        private set

    var recapOn by mutableStateOf(prefs.recapOn)
        private set

    /** Everything ever logged, loaded when History opens. */
    var history by mutableStateOf<List<Reading>>(emptyList())
        private set

    var ready by mutableStateOf(false)
        private set

    /** The last 90 days, oldest first (enough for today, streaks and trends). */
    var readings by mutableStateOf<List<Reading>>(emptyList())
        private set

    /** Meals from the last 90 days, oldest first. */
    var meals by mutableStateOf<List<MealEntry>>(emptyList())
        private set

    var unit by mutableStateOf(prefs.unit)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var haptics by mutableStateOf(prefs.haptics)
        private set

    /** False until the first-run welcome is finished. */
    var onboarded by mutableStateOf(prefs.onboarded)
        private set

    fun finishOnboarding() {
        onboarded = true
        prefs.onboarded = true
        Reminders.rescheduleAsync(context)
    }

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

    /** The try-it week running (or finished, until put away), see [app.dropi.data.Experiment]. */
    var experiment by mutableStateOf(
        app.dropi.data.Experiment.Kind.of(prefs.experiment)?.let { app.dropi.data.Experiment.Running(it, prefs.experimentStart) }
    )
        private set

    fun startExperiment(kind: app.dropi.data.Experiment.Kind) {
        val now = System.currentTimeMillis()
        prefs.experiment = kind.key; prefs.experimentStart = now
        experiment = app.dropi.data.Experiment.Running(kind, now)
    }

    fun endExperiment() {
        prefs.experiment = ""; prefs.experimentStart = 0L
        experiment = null
    }

    /** Steps and sleep from Health Connect (read only), for learnt patterns; empty while off. */
    var activityOn by mutableStateOf(prefs.activityOn)
        private set
    var steps by mutableStateOf<List<app.dropi.data.Span>>(emptyList())
        private set
    var sleep by mutableStateOf<List<app.dropi.data.Span>>(emptyList())
        private set

    fun changeActivity(on: Boolean) {
        activityOn = on
        prefs.activityOn = on
        if (on) scope.launch { loadActivity() } else { steps = emptyList(); sleep = emptyList() }
    }

    private suspend fun loadActivity() {
        if (!activityOn || demo) return
        val (st, sl) = try { HealthSync.activity(context) } catch (e: Exception) { emptyList<app.dropi.data.Span>() to emptyList() }
        steps = st; sleep = sl
    }
    /** What the last Health Connect sync did, for Settings. */
    var healthNote by mutableStateOf<String?>(null)

    /** The log sheet: null when closed; a reading with id 0 is a new one. */
    var editing by mutableStateOf<Reading?>(null)

    /** A low (or very high) reading just saved, for the guidance card. */
    var alertFor by mutableStateOf<Reading?>(null)

    var tab by mutableStateOf(Tab.TODAY)
    /** Trends › More detail opened (by moment, daily table, PDF). */
    var trendsMore by mutableStateOf(false)

    /** The Trends period (7, 14, 30 or 90 days); the header shows its dates. */
    var trendsDays by mutableIntStateOf(14)
    var settingsOpen by mutableStateOf(false)
    var scheduleOpen by mutableStateOf(false)
    var exportOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var backupOpen by mutableStateOf(false)
    var liveOpen by mutableStateOf(false)
    var askOpen by mutableStateOf(false)
    var changelogOpen by mutableStateOf(false)
    var reportOpen by mutableStateOf(false)
    /** The food scores sheet, and the food opened in it (null: the list). */
    var foodsOpen by mutableStateOf(false)
    /** Meals › What your meals show: patterns, Your plate, what's been learnt, try-it weeks. */
    var insightsOpen by mutableStateOf(false)
    /** More › My foods: the foods saved as "my …". */
    var myFoodsOpen by mutableStateOf(false)
    /** Meals page: all 14 days shown, not just the last three. */
    var mealsAllDays by mutableStateOf(false)
    var foodOpen by mutableStateOf<String?>(null)
    /** The + panel: reading, meal photo, words, label. */
    var addOpen by mutableStateOf(false)
    /** A meter photo being read, or what it found, for the log sheet. */
    data class MeterScan(val busy: Boolean = false, val value: Double? = null, val problem: String? = null)
    var meterScan by mutableStateOf<MeterScan?>(null)

    /** "Breakfast logged" after a meal is saved, for a moment. */
    var mealNote by mutableStateOf<String?>(null)

    /** Closes every sheet (a notification asking for a page). */
    fun closeSheets() {
        settingsOpen = false; scheduleOpen = false; exportOpen = false; historyOpen = false; backupOpen = false
        liveOpen = false; askOpen = false; changelogOpen = false; foodsOpen = false; addOpen = false; reportOpen = false
        insightsOpen = false; myFoodsOpen = false
        editing = null
        app.dropi.work.MealWork.open = false
    }

    /** Any sheet over the page (the tab bar hides). */
    val sheetOpen get() = !onboarded || editing != null || settingsOpen || scheduleOpen || exportOpen || historyOpen || backupOpen ||
        liveOpen || askOpen || changelogOpen || foodsOpen || addOpen || reportOpen || insightsOpen || myFoodsOpen || app.dropi.work.MealWork.open

    // ---- Live sensor ----

    /** Sensor points from the last month, oldest first. */
    var sensor by mutableStateOf<List<SensorPoint>>(emptyList())
        private set
    var liveOn by mutableStateOf(prefs.liveOn)
        private set
    var liveNotify by mutableStateOf(prefs.liveNotify)
        private set
    var headsUpOn by mutableStateOf(prefs.headsUpOn)
        private set
    private var livePoll: Job? = null

    var lastCrash by mutableStateOf<String?>(null)
        private set

    // ---- Developer: demo data ----

    var devUnlocked by mutableStateOf(prefs.devUnlocked)
        private set
    var demo by mutableStateOf(prefs.demo)
        private set

    fun unlockDev(on: Boolean) {
        devUnlocked = on
        prefs.devUnlocked = on
    }

    /** Switches to a fresh month of demo readings, in their own database. The real log is untouched. */
    fun loadDemo() {
        scope.launch {
            Db.wipeDemo(context)
            prefs.demo = true
            demo = true
            // Reminders belong to the log in use; the demo month starts with none.
            prefs.oneOffs = emptyList()
            val demoData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                app.dropi.data.Demo.make(System.currentTimeMillis(), zone)
            }
            try {
                db.merge(demoData.readings)
                db.addSensor(demoData.sensor)
                // Each meal is tied to the before reading made with it.
                val saved = db.since(Long.MIN_VALUE)
                demoData.meals.forEach { m ->
                    val before = m.before?.let { at -> saved.firstOrNull { it.at == at }?.id }
                    db.saveMeal(m.copy(before = before))
                }
            } catch (e: Exception) { }
            reload()
            Reminders.rescheduleAsync(context)
        }
    }

    /** Back to the real log, exactly as it was; the demo readings are thrown away. */
    fun leaveDemo() {
        scope.launch {
            prefs.demo = false
            demo = false
            prefs.oneOffs = emptyList()
            Db.wipeDemo(context)
            reload()
            Reminders.rescheduleAsync(context)
        }
    }

    init {
        Haptics.enabled = haptics
        lastCrash = CrashLog.read(context)
        scope.launch {
            reload()
            ready = true
        }
        // New sensor values (from a broadcast or Health Connect) refresh the screen.
        scope.launch { LiveCgm.ticks.collect { if (it > 0) reloadSensor() } }
    }

    // ---- Measured meals: worked out once per change, off the main thread ----

    /** Every meal with a start reading and what followed it (see [Foods.meals]). */
    var measured by mutableStateOf<List<Foods.Meal>>(emptyList())
        private set
    var measuredById by mutableStateOf<Map<Long, Foods.Meal>>(emptyMap())
        private set
    /** Food scores (Your plate), from [measured]. */
    var foodScores by mutableStateOf<List<Foods.Score>>(emptyList())
        private set

    /** What's been learnt from the meals, and how well past guesses did (see [app.dropi.data.Learning]). */
    var learning by mutableStateOf(app.dropi.data.Learning.Model.EMPTY)
        private set

    /** After-eating checks follow the learnt peak time (Meals › What Dropi has learnt). */
    var checkAtPeak by mutableStateOf(prefs.checkAtPeak)
        private set

    fun changeCheckAtPeak(on: Boolean) {
        checkAtPeak = on
        prefs.checkAtPeak = on
        app.dropi.data.Learning.peakForChecks = learning.peakMin.takeIf { on }
    }

    private fun applyLearning(m: app.dropi.data.Learning.Model) {
        learning = m
        // Demo data never sets when the real reminders are due.
        if (demo) return
        prefs.learntPeak = m.peakMin ?: -1
        app.dropi.data.Learning.peakForChecks = m.peakMin.takeIf { checkAtPeak }
    }

    /** How a logged meal went, from the shared measurements. */
    fun outcome(e: MealEntry) = app.dropi.data.MealHistory.outcome(measuredById[e.id])

    private suspend fun measure(rs: List<Reading>, sp: List<SensorPoint>, ms: List<MealEntry>) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val all = try { Foods.meals(rs, sp, ms) } catch (e: Exception) { emptyList() }
            val learnt = try { app.dropi.data.Learning.build(all, ms) } catch (e: Exception) { app.dropi.data.Learning.Model.EMPTY }
            Measured(all, all.associateBy { it.entryId }, try { Foods.scores(all) } catch (e: Exception) { emptyList() }, learnt)
        }

    private data class Measured(
        val all: List<Foods.Meal>, val byId: Map<Long, Foods.Meal>, val scores: List<Foods.Score>, val learnt: app.dropi.data.Learning.Model,
    )

    /** One load at a time, so a sensor value arriving mid-save can't leave a mix of old and new. */
    private val loading = kotlinx.coroutines.sync.Mutex()

    private suspend fun reloadSensor() = loading.withLock {
        val sp = try { db.sensorSince(System.currentTimeMillis() - 31L * 86_400_000) } catch (e: Exception) { emptyList() }
        val (all, byId, scores, learnt) = measure(readings, sp, meals)
        sensor = sp
        measured = all; measuredById = byId; foodScores = scores
        applyLearning(learnt)
    }

    /**
     * Loads the log. Everything is read and worked out first, then shown in one go, so the screen
     * redraws once. The sensor is only re-read when asked (a saved reading or meal doesn't change it).
     */
    private suspend fun reload(withSensor: Boolean = true) = loading.withLock {
        val since = LocalDate.now(zone).minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        val rs = try { db.since(since) } catch (e: Exception) { emptyList() }
        val ms = try { db.mealsSince(since) } catch (e: Exception) { emptyList() }
        val sp = if (withSensor) try { db.sensorSince(System.currentTimeMillis() - 31L * 86_400_000) } catch (e: Exception) { emptyList() } else sensor
        val (all, byId, scores, learnt) = measure(rs, sp, ms)
        readings = rs; meals = ms; sensor = sp
        measured = all; measuredById = byId; foodScores = scores
        applyLearning(learnt)
        if (historyOpen) loadHistory()
    }

    suspend fun loadHistory() {
        history = try { db.all() } catch (e: Exception) { emptyList() }
    }

    fun openHistory() {
        historyOpen = true
        scope.launch { loadHistory() }
    }

    /** Coming back to the app: the day may have changed and an update may be out. */
    fun onOpen() {
        app.dropi.work.MealWork.visible = true
        scope.launch {
            reload()
            if (healthSync) syncHealth()
            loadActivity()
            try { UpdateWatch.check(context, background = false) } catch (e: Exception) { }
        }
        Reminders.rescheduleAsync(context)
        startLivePoll()
    }

    /** Leaving the app: stop the once-a-minute Health Connect look. */
    fun onLeave() {
        app.dropi.work.MealWork.visible = false
        livePoll?.cancel()
        livePoll = null
    }

    /** While the app is open in live mode, Health Connect is checked every minute for new sensor values. */
    private fun startLivePoll() {
        livePoll?.cancel()
        if (!liveOn || !healthSync) return
        livePoll = scope.launch {
            while (true) {
                delay(60_000)
                try { HealthSync.pull(context, days = 1) } catch (e: Exception) { }
            }
        }
    }

    fun changeLive(on: Boolean) {
        liveOn = on
        prefs.liveOn = on
        if (!on) LiveCgm.cancelLive(context)
        else scope.launch { try { LiveCgm.changed(context) } catch (e: Exception) { } }
        startLivePoll()
    }

    fun changeLiveNotify(on: Boolean) {
        liveNotify = on
        prefs.liveNotify = on
        if (!on) LiveCgm.cancelLive(context) else scope.launch { try { LiveCgm.changed(context) } catch (e: Exception) { } }
    }

    fun changeHeadsUp(on: Boolean) {
        headsUpOn = on
        prefs.headsUpOn = on
    }

    /**
     * Saves a meal eaten at [at]. [before] is the reading it starts from (null: the latest in
     * the 90 minutes before). The after-meal reminder is counted from when it was eaten, 3 h for
     * a rich meal.
     */
    fun saveMeal(meal: app.dropi.data.Meal, at: Long, name: String, before: Long?, id: Long = 0) {
        scope.launch {
            val saved = try { db.saveMeal(MealEntry(id, at, name, meal, before)) } catch (e: Exception) { null } ?: return@launch
            reload(withSensor = false)
            val now = System.currentTimeMillis()
            prefs.oneOffs = Schedule.afterMeal(prefs.oneOffs, saved.at, Schedule.gapFor(saved, prefs.afterMealMinutes), prefs.afterMealOn, now, saved.id)
            Reminders.rescheduleAsync(context)
            mealNote = if (id == 0L) tr("{0} logged", mealName(saved.name)) else tr("{0} changed", mealName(saved.name))
        }
    }

    /** The meal just deleted, for the Undo bar. */
    var lastDeletedMeal by mutableStateOf<MealEntry?>(null)

    fun deleteMeal(e: MealEntry) {
        lastDeletedMeal = e
        lastDeleted = null
        scope.launch {
            try { db.deleteMeal(e.id) } catch (x: Exception) { }
            prefs.oneOffs = Schedule.withoutMeal(prefs.oneOffs, e.id)
            reload(withSensor = false)
            Reminders.rescheduleAsync(context)
        }
    }

    fun undoDeleteMeal() {
        val e = lastDeletedMeal ?: return
        lastDeletedMeal = null
        scope.launch {
            val back = try { db.saveMeal(e.copy(id = 0)) } catch (x: Exception) { null }
            reload(withSensor = false)
            if (back != null) {
                prefs.oneOffs = Schedule.afterMeal(prefs.oneOffs, back.at, Schedule.gapFor(back, prefs.afterMealMinutes), prefs.afterMealOn, System.currentTimeMillis(), back.id)
                Reminders.rescheduleAsync(context)
            }
        }
    }

    /** The reading a meal eaten at [at] would start from: the latest in the 90 minutes before (or 15 after). */
    fun startFor(at: Long): Reading? = MealLink.before(MealEntry(at = at, name = "", meal = app.dropi.data.Meal(emptyList())), readings)

    /** The latest reading today before [at], for "your last reading was at 07:15". */
    fun lastReadingBefore(at: Long): Reading? = readings.filter { it.at <= at + 15 * 60_000L }.maxByOrNull { it.at }

    /** Meals logged lately, newest first, one per set of foods (the + panel's "Again"). */
    val recentMeals: List<app.dropi.data.Meal>
        get() = meals.asReversed().map { it.meal }.filter { it.items.isNotEmpty() }
            .distinctBy { m -> m.foods.sorted().joinToString("|") }.take(3)

    /**
     * What a reading was taken around: "before breakfast", "2 h after lunch", in the app's
     * language (the same links as [MealLink.text], which stays English).
     */
    fun linkText(r: Reading): String? {
        MealLink.mealAfter(r, meals, readings)?.let { return beforeMealText(it.name) }
        MealLink.mealBefore(r, meals)?.let { m -> return afterMealText(gapText(((r.at - m.at) / 60_000L).toInt()), m.name) }
        return null
    }

    fun askCtx() = Ask.Ctx(readings, sensor, unit, targets, LocalDate.now(zone), zone, meals, measured)

    fun status(r: Reading): Status = Glucose.status(r.mmol, r.tag, targets)

    /**
     * The moment for a reading at [at]: an after-meal one when a before-meal reading was 30 min
     * to 3 h earlier, else the open check it's nearest to, else one from the time of day.
     */
    fun suggestTag(at: Long): MealTag {
        // A meal eaten 45 min to 4 h ago, with no after-eating reading since: this is that one.
        val ate = meals.filter { at - it.at in 45 * 60_000L..240 * 60_000L }.maxByOrNull { it.at }
        if (ate != null && readings.none { it.tag == MealTag.AFTER_MEAL && it.at in ate.at..at - 60_000L }) return MealTag.AFTER_MEAL
        val byTime = Tagging.suggest(at, readings, zone)
        if (byTime == MealTag.AFTER_MEAL) return byTime
        return Schedule.momentFor(at, slots, readings, zone, afterMealMinutes, meals) ?: byTime
    }

    /** Opens the log sheet for a new reading, with [tag] or one picked from the time of day. */
    fun startNew(tag: MealTag? = null) {
        val now = System.currentTimeMillis()
        // A finished scan from an earlier sheet doesn't fill in this one.
        if (meterScan?.busy != true) meterScan = null
        editing = Reading(at = now, mmol = 0.0, tag = tag ?: suggestTag(now))
    }

    fun save(r: Reading) {
        scope.launch {
            val saved = try { db.save(r) } catch (e: Exception) { null } ?: return@launch
            reload(withSensor = false)
            if (r.id == 0L) {
                // A new reading answers the reminders set before it, and a before-meal one sets
                // the after-meal reminder.
                prefs.oneOffs = Schedule.afterSave(prefs.oneOffs, saved, prefs.afterMealOn, prefs.afterMealMinutes, System.currentTimeMillis(), meals)
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
        lastDeletedMeal = null
        scope.launch {
            try { db.delete(r.id) } catch (e: Exception) { }
            reload(withSensor = false)
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
            // Meals that started from this reading point at it again.
            meals.filter { it.before == r.id }.forEach { m -> try { db.saveMeal(m.copy(before = back.id)) } catch (e: Exception) { } }
            reload(withSensor = false)
            Reminders.rescheduleAsync(context)
            if (healthSync && !back.imported) try { HealthSync.push(context, listOf(back)) } catch (e: Exception) { }
        }
    }

    /** "Remind me to re-check" on the low card; returns the time it will fire. */
    fun remindRecheck(r: Reading): Long = Reminders.remindRecheck(context, r.tag)

    fun changeUnit(u: GlucoseUnit) { unit = u; prefs.unit = u; Reminders.rescheduleAsync(context) }
    fun changeTheme(t: String) { theme = t; prefs.theme = t; Reminders.rescheduleAsync(context) }
    fun changeHaptics(on: Boolean) { haptics = on; prefs.haptics = on; Haptics.enabled = on }

    /** Check times the person usually does at another time, not yet turned down. */
    var dismissedMoves by mutableStateOf(prefs.dismissedMoves)
        private set

    fun moveSuggestion(now: Long): app.dropi.data.MoveSuggestion? =
        Schedule.moves(slots, readings, now, zone).firstOrNull { "${it.slot.id}@${it.minute}" !in dismissedMoves }

    fun acceptMove(m: app.dropi.data.MoveSuggestion) {
        changeSlots(slots.map { if (it.id == m.slot.id) it.copy(minute = m.minute) else it })
    }

    fun dismissMove(m: app.dropi.data.MoveSuggestion) {
        dismissedMoves = dismissedMoves + "${m.slot.id}@${m.minute}"
        prefs.dismissedMoves = dismissedMoves
    }

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
            healthNote = if (added > 0) trn(added, "Brought in {0} reading", "Brought in {0} readings") else tr("Up to date")
        } catch (e: Exception) {
            healthNote = tr("Couldn't sync with Health Connect")
        }
    }

    /** Health Connect switched on (after permission) or off. */
    fun changeHealthSync(on: Boolean) {
        healthSync = on
        prefs.healthSync = on
        healthNote = null
        if (on) scope.launch { syncHealth() }
    }

    fun changeTargets(t: Targets) {
        val safe = t.safe()
        targets = safe
        prefs.targets = safe
        Reminders.rescheduleAsync(context)
    }

    fun changeRecap(on: Boolean) {
        recapOn = on
        prefs.recapOn = on
        Reminders.rescheduleAsync(context)
    }

    fun addLabA1c(r: app.dropi.data.LabA1c) {
        val list = (labA1c.filter { it.date != r.date } + r).sortedBy { it.date }
        labA1c = list
        prefs.labA1c = list
    }

    fun removeLabA1c(r: app.dropi.data.LabA1c) {
        val list = labA1c.filter { it != r }
        labA1c = list
        prefs.labA1c = list
    }

    /** An encrypted backup of everything, as bytes for the file the person picked. */
    suspend fun backupBytes(password: CharArray): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        // Always the real log, even while demo data is on.
        val real = Db.real(context)
        val c = app.dropi.data.Backup.Contents(
            real.all(), prefs.slots, prefs.targets, prefs.labA1c, System.currentTimeMillis(), real.mealsSince(Long.MIN_VALUE),
        )
        app.dropi.data.Backup.seal(app.dropi.data.Backup.toJson(c), password)
    }

    /**
     * Restores a backup: readings are merged (nothing already here is doubled or lost), and the
     * backup's check times, targets and lab results replace the current ones. Returns how many
     * readings came back.
     */
    suspend fun restore(bytes: ByteArray, password: CharArray): Int {
        val c = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            app.dropi.data.Backup.fromJson(app.dropi.data.Backup.open(bytes, password))
        }
        val added = Db.real(context).merge(c.readings)
        try {
            // Each meal's chosen start comes as a reading time: find that reading here.
            val byMinute = Db.real(context).all().associateBy { it.at / 60_000 }
            Db.real(context).mergeMeals(c.meals.map { m -> m.copy(before = m.before?.let { t -> byMinute[t / 60_000]?.id }) })
            // Older backups kept foods on readings: those become meals too.
            Db.real(context).tidyMeals()
        } catch (e: Exception) { }
        c.slots?.let { changeSlots(it) }
        c.targets?.let { changeTargets(it) }
        if (c.labA1c.isNotEmpty()) {
            val merged = (labA1c + c.labA1c).distinctBy { it.date }.sortedBy { it.date }
            labA1c = merged
            prefs.labA1c = merged
        }
        reload()
        Reminders.rescheduleAsync(context)
        return added
    }

    fun dismissCrash() {
        CrashLog.clear(context)
        lastCrash = null
    }
}
