package app.dropi.work

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dropi.Prefs
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
        data class Working(val what: String) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
        /** A label was read (or couldn't be, [read] false: the numbers are typed); how much was eaten is still needed. */
        data class NeedsGrams(val per100: Label.Per100, val read: Boolean = true, val why: String? = null) : Status
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
            val meal = try { Ai.mealFromPhoto(ctx, uri) } catch (e: Throwable) { null }
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
        if (!Ai.usable) return
        val ctx = context.applicationContext
        filling = filling + item.key
        job(ctx) {
            val m = try { Ai.mealFromWords(ctx, MealAi.words("${item.portion} of ${item.name}"), "typed") } catch (e: Throwable) { null }
            val found = m?.items?.firstOrNull() ?: basic
            // Back on the main thread, where the screen changes the draft too.
            withContext(Dispatchers.Main) {
                if (found != null) put(found)
                filling = filling - item.key
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
                try { Label.parseAi(Ai.text(ctx, Label.prompt(lines.joinToString("\n")))) } catch (e: Throwable) { null }
            } else null
            val p = when {
                found?.carbs != null -> found.copy(
                    sugars = found.sugars ?: ai?.sugars, fibre = found.fibre ?: ai?.fibre, name = ai?.name.orEmpty(),
                    protein = found.protein ?: ai?.protein, fat = found.fat ?: ai?.fat,
                )
                ai?.carbs != null -> ai.copy(us = Label.isUs(lines))
                else -> null
            }
            val why = if (lines.isEmpty()) Scan.problem else "Found text, but no carbs or sugars in it."
            land(id, Draft(id, if (p != null) Status.NeedsGrams(p) else Status.NeedsGrams(Label.Per100(null, null, null, us = Label.isUs(lines)), read = false, why = why)))
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
        if (!Ai.usable) return null
        val words = if (portion.isNullOrBlank()) "1 normal portion of $name" else "$portion of $name"
        val m = try { Ai.mealFromWords(context.applicationContext, MealAi.words(words), "typed") } catch (e: Throwable) { null }
        return m?.items?.firstOrNull()
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
