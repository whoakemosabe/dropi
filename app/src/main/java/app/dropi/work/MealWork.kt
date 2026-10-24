package app.dropi.work

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dropi.Prefs
import app.dropi.data.Foods
import app.dropi.data.Label
import app.dropi.data.Meal
import app.dropi.data.MealAi
import app.dropi.data.MealItem
import app.dropi.data.MealTags
import app.dropi.data.Usual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The meal being worked out, app-wide: a photo, words or a label go in, a breakdown comes out.
 * It lives outside any screen, so closing the meal screen doesn't stop the work; if it finishes
 * while the screen is closed, a notification brings the person back to check it.
 */
object MealWork {
    sealed interface Status {
        /** [found]: the foods named so far, shown while the rest is worked out. */
        data class Working(val what: String, val found: List<String> = emptyList()) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
        /** A label was read (or couldn't be, [read] false: the numbers are typed); how much was eaten is still needed. */
        data class NeedsGrams(val per100: Label.Per100, val read: Boolean = true, val why: String? = null, val text: List<String> = emptyList()) : Status
        /** Waiting for the person to say what they ate. */
        data object Words : Status
    }

    data class Draft(
        val id: Long,
        val status: Status,
        val meal: Meal? = null,
        /** The photo it came from, while it's being looked at. */
        val photo: Uri? = null,
        /** The logged meal being changed, or null for a new one. */
        val forMeal: app.dropi.data.MealEntry? = null,
        /** When it was eaten: when the photo, words or label came in, unless changed. */
        val started: Long = System.currentTimeMillis(),
        /** Breakfast, lunch…; null: picked from the time. */
        val name: String? = null,
        /** The reading it starts from, when the person chose one. */
        val before: Long? = null,
        /** Which meal this is (kept through changes), so late results never land on another one. */
        val session: Long = 0,
    )

    var draft by mutableStateOf<Draft?>(null)
        private set

    /** Dropi is on screen (set as it comes and goes); when it isn't, a finished meal is a notification. */
    @Volatile var visible = true

    /** The meal screen is showing. */
    var open by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var counter = 0L

    /** Background work that keeps going when the person leaves the app (see [MealService]). */
    private fun job(ctx: Context, block: suspend () -> Unit) {
        MealService.hold(ctx)
        scope.launch {
            try { block() } finally { MealService.release(ctx) }
        }
    }

    private fun start(status: Status, meal: Meal? = null, photo: Uri? = null): Long {
        val id = ++counter
        draft = Draft(id, status, meal, photo, session = id)
        open = true
        return id
    }

    /** Only the newest piece of work may land. */
    private fun land(id: Long, d: Draft) {
        val now = draft
        // The time it started and the meal being changed carry over.
        if (now?.id == id) draft = d.copy(started = now.started, forMeal = d.forMeal ?: now.forMeal, name = d.name ?: now.name, before = d.before ?: now.before, session = now.session)
    }

    fun usual(context: Context): Map<String, Usual> = Prefs(context).usualFoods

    /** Remembers [item] at its portion as the person's own amount for that food. */
    fun remember(context: Context, item: MealItem) {
        val p = Prefs(context)
        val per = if (item.qty > 0) 1.0 / item.qty else 1.0
        fun s(v: Double?) = v?.let { app.dropi.data.Grams.round(it * per) }
        p.usualFoods = p.usualFoods + (item.key to Usual(item.portion, app.dropi.data.Grams.round(item.carbs * per), s(item.sugars), s(item.protein), s(item.fat)))
    }

    fun forget(context: Context, food: String) {
        val p = Prefs(context)
        p.usualFoods = p.usualFoods - food
    }

    /** A photo of a plate. */
    fun fromPhoto(context: Context, uri: Uri) {
        val ctx = context.applicationContext
        Alerts.cancelMeal(ctx)
        val id = start(Status.Working("Looking at your plate…"), photo = uri)
        job(ctx) {
            val meal = try {
                Ai.mealFromPhoto(ctx, uri) { foods ->
                    // Each food as it's named, on the main thread with the screen.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val d = draft
                        if (d?.id == id && d.status is Status.Working) draft = d.copy(status = (d.status as Status.Working).copy(found = foods))
                    }
                }
            } catch (e: Throwable) { null }
            // Only our own copy of the photo is deleted, and only this job's.
            if (uri.authority == "${ctx.packageName}.files") try { File(File(ctx.cacheDir, "scans"), uri.lastPathSegment ?: "").takeIf { it.isFile }?.delete() } catch (e: Exception) { }
            finish(ctx, id, meal, "Couldn't make out the meal. Say what you ate instead, or add the foods yourself.")
        }
    }

    /** "Say what you ate", before anything has been said. */
    fun words() {
        start(Status.Words)
    }

    /** A meal said or typed in words. */
    fun fromWords(context: Context, text: String) {
        if (text.isBlank()) return
        val ctx = context.applicationContext
        val id = start(Status.Working("Working out your meal…"))
        job(ctx) {
            val meal = try { Ai.mealFromWords(ctx, MealAi.words(text), "words") } catch (e: Throwable) { null }
                ?.let { m -> m.copy(tags = (m.tags + MealTags.fromWords(text)).distinct()) }
            finish(ctx, id, meal, "Couldn't work that out. Try naming each food, like \"2 slices of toast and jam\".")
        }
    }

    /** "Make the juice a small glass": the change applied to the meal on screen. */
    fun change(context: Context, text: String) {
        val current = draft?.meal ?: return fromWords(context, text)
        if (text.isBlank()) return
        val ctx = context.applicationContext
        val id = ++counter
        draft = draft!!.copy(id = id, status = Status.Working("Changing it…"), meal = current)
        job(ctx) {
            val meal = try { Ai.mealFromWords(ctx, MealAi.change(current, text), current.source) } catch (e: Throwable) { null }
                ?.let { m -> m.copy(tags = (current.tags + m.tags + MealTags.fromWords(text)).distinct()) }
            if (meal == null) land(id, Draft(id, Status.Ready, current, forMeal = draft?.forMeal, started = draft?.started ?: System.currentTimeMillis()).also { failedChange = true })
            else finish(ctx, id, meal.copy(rich = meal.rich || current.rich), "")
        }
    }

    /** Foods added by hand whose numbers are being looked up. */
    var filling by mutableStateOf(setOf<String>())
        private set

    /**
     * A food added by hand with no numbers: they come from the built-in basics (eggs, bacon,
     * bread…) straight away, else the on-device model works them out from its name and portion.
     */
    fun fillIn(context: Context, item: MealItem) {
        if (item.carbs > 0 || item.protein != null || item.fat != null) return
        val session = draft?.session
        fun put(found: MealItem) {
            val d = draft ?: return
            if (d.session != session) return // a different meal by now
            val m = d.meal ?: return
            val portion = if (item.portion == "1 portion") found.portion else item.portion
            val filled = item.copy(
                portion = portion, carbs = found.carbs, sugars = found.sugars, fibre = found.fibre,
                protein = found.protein, fat = found.fat, gi = found.gi, drink = found.drink || item.drink,
            ).let { if (item.qty != 1.0) it.copy(qty = 1.0).withQty(item.qty) else it }
            draft = d.copy(meal = m.copy(items = m.items.map { if (it.key == item.key && it.carbs == 0.0 && it.protein == null) filled else it }))
        }
        val basic = app.dropi.data.Basics.find(item.name)
        // A portion that isn't the basic's own ("3 slices" of bacon) goes to the model when it can.
        if (basic != null && (item.portion == "1 portion" || item.portion == basic.portion || !Ai.usable)) { put(basic); return }
        val portion = item.portion.takeIf { it != "1 portion" }
        cached(context, item.name, portion)?.let { put(it); return }
        if (!Ai.usable) return
        val ctx = context.applicationContext
        filling = filling + item.key
        // Foods added within a moment of each other go to the model as one question.
        pending += PendingFill(item, portion, basic, ::put)
        if (pending.size == 1) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ flushFills(ctx) }, 600)
    }

    private class PendingFill(val item: MealItem, val portion: String?, val basic: MealItem?, val put: (MealItem) -> Unit)
    private val pending = ArrayList<PendingFill>()

    private fun flushFills(ctx: Context) {
        val batch = pending.toList()
        pending.clear()
        if (batch.isEmpty()) return
        job(ctx) {
            val found: List<MealItem?> = if (batch.size == 1) {
                val b = batch[0]
                listOf(try { lookup(ctx, b.item.name, b.portion) } catch (e: Throwable) { null })
            } else {
                // One meal question for all of them, matched back by name (or order).
                val words = batch.joinToString("; ") { "${it.portion ?: "1 normal portion"} of ${it.item.name}" }
                val items = try { Ai.mealFromWords(ctx, MealAi.words(words), "typed")?.items.orEmpty() } catch (e: Throwable) { emptyList() }
                batch.mapIndexed { i, b ->
                    (items.firstOrNull { it.key == b.item.key } ?: items.getOrNull(i)?.takeIf { items.size == batch.size })
                        ?.also { rememberAnswer(ctx, b.item.name, b.portion, it) }
                }
            }
            // Back on the main thread, where the screen changes the draft too.
            withContext(Dispatchers.Main) {
                batch.forEachIndexed { i, b ->
                    (found[i] ?: b.basic)?.let { b.put(it) }
                    filling = filling - b.item.key
                }
            }
        }
    }

    /** True for a moment after a change in words didn't work (the meal stays as it was). */
    var failedChange by mutableStateOf(false)

    /** A photo of a nutrition label: exact numbers, then how much was eaten. */
    fun fromLabel(context: Context, uri: Uri) {
        val ctx = context.applicationContext
        val id = start(Status.Working("Reading the label…"))
        job(ctx) {
            val lines = Scan.lines(ctx, uri)
            val found = Label.read(lines)
            // The on-device model reads the same text when the table didn't parse cleanly, and
            // fills in what's missing (sugars, fibre, the product's name).
            val ai = if (lines.isNotEmpty() && Ai.usable && (found?.carbs == null || found.sugars == null || found.fibre == null || found.protein == null || found.fat == null)) {
                // A US label is read per serving (with its serving size), the same as the parser reads it.
                try { Label.parseAi(Ai.text(ctx, Label.prompt(lines.joinToString("\n"), us = Label.isUs(lines)))) } catch (e: Throwable) { null }
            } else null
            val p = when {
                found?.carbs != null -> found.copy(
                    sugars = found.sugars ?: ai?.sugars, fibre = found.fibre ?: ai?.fibre, name = ai?.name.orEmpty(),
                    protein = found.protein ?: ai?.protein, fat = found.fat ?: ai?.fat,
                    serving = found.serving ?: ai?.serving,
                )
                ai?.carbs != null -> ai.copy(us = Label.isUs(lines))
                else -> null
            }
            val why = if (lines.isEmpty()) Scan.problem else "Found text, but no carbs or sugars in it."
            val seen = Scan.lastText
            land(id, Draft(id, if (p != null) Status.NeedsGrams(p, text = seen) else Status.NeedsGrams(Label.Per100(null, null, null, us = Label.isUs(lines)), read = false, why = why, text = seen)))
        }
    }

    /** How much of the labelled food was eaten, with the per-100 g numbers as checked or typed. */
    fun labelGrams(name: String, grams: Int, p: Label.Per100) {
        val d = draft ?: return
        val item = Label.item(name, grams, p)
        draft = d.copy(status = Status.Ready, meal = Meal(listOf(item), (item.fat ?: 0.0) >= 25 || (item.protein ?: 0.0) >= 40, "label"))
    }

    /** A meal logged before, again. */
    fun again(meal: Meal) {
        start(Status.Ready, meal.copy(source = "typed"))
    }

    /** An empty meal to fill in by hand. */
    fun manual() {
        start(Status.Ready, Meal(emptyList(), false, "typed"))
    }

    /** A logged meal, opened for changes. */
    fun edit(entry: app.dropi.data.MealEntry) {
        val id = ++counter
        draft = Draft(id, Status.Ready, entry.meal, forMeal = entry, started = entry.at, name = entry.name, before = entry.before, session = id)
        open = true
    }

    /** When it was eaten; a new time drops a chosen start reading. */
    fun setWhen(at: Long) { draft = draft?.copy(started = at, before = null) }
    fun setName(name: String) { draft = draft?.copy(name = name) }
    fun setBefore(readingId: Long?) { draft = draft?.copy(before = readingId) }

    /** Works out one food's numbers from its name and portion (the add-a-food preview). */
    suspend fun lookup(context: Context, name: String, portion: String?): MealItem? {
        cached(context, name, portion)?.let { return it }
        if (!Ai.usable) return null
        val found = try { Ai.food(context.applicationContext, name, portion) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { null }
        if (found != null) rememberAnswer(context, name, portion, found)
        return found
    }

    // ---- Answers kept, so a food looked up once is instant the next time ----

    private fun cacheKey(name: String, portion: String?) = Foods.normalize(name) + "|" + (portion?.trim()?.lowercase().orEmpty())

    /** A food the AI worked out before, at the same amount. */
    fun cached(context: Context, name: String, portion: String?): MealItem? =
        Prefs(context).foodCache[cacheKey(name, portion)]?.copy(name = Foods.normalize(name))

    private fun rememberAnswer(context: Context, name: String, portion: String?, item: MealItem) {
        val p = Prefs(context)
        p.foodCache = p.foodCache + (cacheKey(name, portion) to item)
    }

    /** The person changed the meal on screen. */
    fun update(meal: Meal) {
        val d = draft ?: return
        draft = d.copy(meal = meal, status = Status.Ready)
        failedChange = false
    }

    fun discard() {
        draft = null
        open = false
    }

    private fun finish(ctx: Context, id: Long, meal: Meal?, failed: String) {
        if (draft?.id != id) return
        if (meal == null) {
            land(id, Draft(id, Status.Failed(failed)))
            if (!open || !visible) Alerts.postMealFailed(ctx)
            return
        }
        val withUsual = MealAi.applyUsual(meal, usual(ctx))
        land(id, Draft(id, Status.Ready, withUsual, forMeal = draft?.forMeal, started = draft?.started ?: System.currentTimeMillis()))
        // Worked out while the screen was closed: a notification to come back and check it.
        if (!open || !visible) Alerts.postMealReady(ctx, app.dropi.data.Grams.text(withUsual.carbs), withUsual.foods, null)
    }
}
