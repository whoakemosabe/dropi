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
        /** When the photo, words or label came in: the default time it was eaten. */
        val started: Long = System.currentTimeMillis(),
    )

    var draft by mutableStateOf<Draft?>(null)
        private set

    /** The meal screen is showing. */
    var open by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var counter = 0L

    private fun start(status: Status, meal: Meal? = null, photo: Uri? = null): Long {
        val id = ++counter
        draft = Draft(id, status, meal, photo)
        open = true
        return id
    }

    /** Only the newest piece of work may land. */
    private fun land(id: Long, d: Draft) {
        val now = draft
        // The time it started and the meal being changed carry over.
        if (now?.id == id) draft = d.copy(started = now.started, forMeal = d.forMeal ?: now.forMeal)
    }

    fun usual(context: Context): Map<String, Usual> = Prefs(context).usualFoods

    /** Remembers [item] at its portion as the person's own amount for that food. */
    fun remember(context: Context, item: MealItem) {
        val p = Prefs(context)
        val per = if (item.qty > 0) 1.0 / item.qty else 1.0
        fun s(v: Int?) = v?.let { Math.round(it * per).toInt() }
        p.usualFoods = p.usualFoods + (item.key to Usual(item.portion, Math.round(item.carbs * per).toInt(), s(item.sugars), s(item.protein), s(item.fat)))
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
        scope.launch {
            val meal = try { Ai.mealFromPhoto(ctx, uri) } catch (e: Throwable) { null }
            try { File(File(ctx.cacheDir, "scans"), "plate.jpg").delete() } catch (e: Exception) { }
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
        scope.launch {
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
        scope.launch {
            val meal = try { Ai.mealFromWords(ctx, MealAi.change(current, text), current.source) } catch (e: Throwable) { null }
                ?.let { m -> m.copy(tags = (current.tags + m.tags + MealTags.fromWords(text)).distinct()) }
            if (meal == null) land(id, Draft(id, Status.Ready, current, forMeal = draft?.forMeal, started = draft?.started ?: System.currentTimeMillis()).also { failedChange = true })
            else finish(ctx, id, meal.copy(rich = meal.rich || current.rich), "")
        }
    }

    /** True for a moment after a change in words didn't work (the meal stays as it was). */
    var failedChange by mutableStateOf(false)

    /** A photo of a nutrition label: exact numbers, then how much was eaten. */
    fun fromLabel(context: Context, uri: Uri) {
        val ctx = context.applicationContext
        val id = start(Status.Working("Reading the label…"))
        scope.launch {
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
        draft = d.copy(status = Status.Ready, meal = Meal(listOf(item), (item.fat ?: 0) >= 25 || (item.protein ?: 0) >= 40, "label"))
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
        draft = Draft(id, Status.Ready, entry.meal, forMeal = entry, started = entry.at)
        open = true
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
            return
        }
        val withUsual = MealAi.applyUsual(meal, usual(ctx))
        land(id, Draft(id, Status.Ready, withUsual, forMeal = draft?.forMeal, started = draft?.started ?: System.currentTimeMillis()))
        // Worked out while the screen was closed: a notification to come back and check it.
        if (!open) Alerts.postMealReady(ctx, withUsual.carbs, withUsual.foods, null)
    }
}
