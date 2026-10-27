package app.dropi.work

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import app.dropi.data.PlateGuess
import app.dropi.tr

/**
 * The on-device model behind meal photos and typed questions. Gemini Nano when the phone has it
 * (nothing to download from us); otherwise Gemma, once it has been downloaded in Settings.
 * Either way the work happens on the phone and nothing is uploaded.
 */
object Ai {
    /** Which model the person picked in Settings › On-device AI (only matters when both are there). */
    enum class Pick { GEMMA, NANO }

    /** Gemma unless the person picked Gemini Nano. Read from prefs on the first refresh. */
    var pick by mutableStateOf(Pick.GEMMA)
        private set

    private var pickLoaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dropi_ai", Context.MODE_PRIVATE)

    private fun loadPick(context: Context) {
        if (pickLoaded) return
        pick = if (prefs(context).getString("model_pick", "gemma") == "nano") Pick.NANO else Pick.GEMMA
        pickLoaded = true
    }

    fun choose(context: Context, p: Pick) {
        pick = p
        pickLoaded = true
        prefs(context).edit { putString("model_pick", if (p == Pick.NANO) "nano" else "gemma") }
        // Gemma takes several GB of memory while loaded: let it go once it's no longer in use.
        if (p == Pick.NANO) {
            // Only once Nano is really there: until then Gemma keeps answering.
            if (Nano.state == Nano.State.READY) Gemma.unload()
            if (Nano.state == Nano.State.NEEDS_DOWNLOAD) Nano.startDownload()
        }
    }

    /** Gemini Nano is on the phone, or can be (some phones, like OPPO's, offer it as "downloadable"). */
    val nanoThere: Boolean
        get() = Nano.state == Nano.State.READY || Nano.state == Nano.State.NEEDS_DOWNLOAD || Nano.state == Nano.State.DOWNLOADING

    /** Both models can be used, so the person gets to pick. */
    val canPick: Boolean get() = nanoThere && Gemma.state == Gemma.State.Ready

    /**
     * Use Gemini Nano: when it's the only option (Gemma not downloaded), or when the person
     * picked it and it's actually ready. A downloaded Gemma is used by default (it's held to a
     * strict answer shape for meals and labels), and is never passed over for a Nano that would
     * first need a download of several minutes.
     */
    val nano: Boolean
        get() = nanoThere && (Gemma.state != Gemma.State.Ready || (pick == Pick.NANO && Nano.state == Nano.State.READY))

    /** Nano was picked but is still downloading, so Gemma fills in until it's ready. */
    val nanoPending: Boolean
        get() = pick == Pick.NANO && Gemma.state == Gemma.State.Ready && nanoThere && Nano.state != Nano.State.READY

    /** Nano's answer couldn't be used, and Gemma is downloaded to try instead. */
    private val gemmaBackup: Boolean get() = nano && Gemma.state == Gemma.State.Ready

    /** Meal photos and typed questions can be used right now. */
    val usable: Boolean get() = nano || Gemma.state == Gemma.State.Ready

    /** Still finding out what this phone has. */
    val checking: Boolean
        get() = Nano.state == Nano.State.CHECKING || Gemma.state == Gemma.State.Checking

    /** The model's name as shown in the app. */
    val name: String get() = if (nano) "Gemini Nano" else "Gemma"

    /** What to say while the first answer is on its way, when it takes longer than usual. */
    val warmUp: String?
        get() = when {
            nano && (Nano.state == Nano.State.DOWNLOADING || Nano.state == Nano.State.NEEDS_DOWNLOAD) -> tr("Getting Gemini Nano ready on your phone. The first time takes a few minutes…")
            !nano && !Gemma.loaded -> tr("Loading Gemma on your phone. The first answer takes a few seconds…")
            else -> null
        }

    suspend fun refresh(context: Context) {
        loadPick(context)
        Nano.refresh()
        // Always check Gemma too: a downloaded Gemma is used even when the phone also has Nano.
        val g = Gemma.refresh(context)
        if (g is Gemma.State.Downloading || g == Gemma.State.WaitingForWifi || g == Gemma.State.Verifying) Gemma.watch(context)
    }

    suspend fun plate(context: Context, uri: Uri): PlateGuess? = when {
        nano -> Nano.plate(context, uri)
        Gemma.state == Gemma.State.Ready -> Gemma.plate(context, uri)
        else -> null
    }

    /** A meal breakdown from a photo, or null when the model couldn't make it out. */
    suspend fun mealFromPhoto(context: Context, uri: Uri, foundSoFar: ((List<String>) -> Unit)? = null): app.dropi.data.Meal? {
        // Foods are passed on as the model names them, so the screen can show them before it's done.
        var shown = 0
        val partial: (String) -> Unit = { text ->
            val foods = app.dropi.data.MealAi.partial(text, "photo")?.items?.map { it.name }.orEmpty()
            if (foods.size > shown) { shown = foods.size; foundSoFar?.invoke(foods) }
        }
        val raw = when {
            nano -> Nano.imageText(context, uri, app.dropi.data.MealAi.PHOTO)
            Gemma.state == Gemma.State.Ready -> Gemma.imageJson(context, uri, app.dropi.data.MealAi.PHOTO, app.dropi.data.MealAi.SCHEMA, partial)
            else -> null
        }
        app.dropi.data.MealAi.parse(raw, "photo")?.let { return it }
        Diag.log(context, "Meal photo: reply couldn't be read: ${raw?.take(200) ?: "(no reply)"}")
        // A reply that went wrong part way still has the foods it finished: use those rather than start over.
        app.dropi.data.MealAi.partial(raw, "photo")?.let { return it }
        // Nano's reply couldn't be read: Gemma, when it's downloaded, has a go held to shape.
        if (gemmaBackup) {
            Diag.log(context, "Meal photo: Nano's reply unusable, asked Gemma instead")
            val g = Gemma.imageJson(context, uri, app.dropi.data.MealAi.PHOTO, app.dropi.data.MealAi.SCHEMA, partial)
            return app.dropi.data.MealAi.parse(g, "photo") ?: app.dropi.data.MealAi.partial(g, "photo")
        }
        // The held-to-shape reply didn't come out usable: ask again without holding it.
        if (!nano && Gemma.state == Gemma.State.Ready) {
            return app.dropi.data.MealAi.parse(Gemma.imageJson(context, uri, app.dropi.data.MealAi.PHOTO, null), "photo")
        }
        return null
    }

    /** A meal breakdown from a prompt in words (a description, or a change to a meal). */
    suspend fun mealFromWords(context: Context, prompt: String, source: String): app.dropi.data.Meal? {
        val raw = when {
            nano -> Nano.text(prompt)
            Gemma.state == Gemma.State.Ready -> Gemma.textJson(context, prompt, app.dropi.data.MealAi.SCHEMA)
            else -> null
        }
        app.dropi.data.MealAi.parse(raw, source)?.let { return it }
        app.dropi.data.MealAi.partial(raw, source)?.let { return it }
        if (gemmaBackup) {
            Diag.log(context, "Meal words: Nano's reply unusable, asked Gemma instead")
            val g = Gemma.textJson(context, prompt, app.dropi.data.MealAi.SCHEMA)
            return app.dropi.data.MealAi.parse(g, source) ?: app.dropi.data.MealAi.partial(g, source)
        }
        if (!nano && Gemma.state == Gemma.State.Ready) {
            return app.dropi.data.MealAi.parse(Gemma.textJson(context, prompt, null), source)
        }
        return null
    }

    /** One food's numbers from its name (and amount): a short question, a short answer. */
    suspend fun food(context: Context, name: String, portion: String?): app.dropi.data.MealItem? {
        val prompt = app.dropi.data.MealAi.food(name, portion)
        val raw = when {
            nano -> Nano.text(prompt)
            Gemma.state == Gemma.State.Ready -> Gemma.textJson(context, prompt, app.dropi.data.MealAi.FOOD_SCHEMA, maxOut = 120)
            else -> null
        }
        return app.dropi.data.MealAi.parseFood(raw, name, portion)
            // Nano's short answer couldn't be read: Gemma's, held to shape.
            ?: (if (gemmaBackup) app.dropi.data.MealAi.parseFood(
                Gemma.textJson(context, prompt, app.dropi.data.MealAi.FOOD_SCHEMA, maxOut = 120), name, portion,
            ) else null)
            // Rarely the short form doesn't come back right: the meal form, as before.
            ?: mealFromWords(context, app.dropi.data.MealAi.words("${portion ?: "1 normal portion"} of $name"), "typed")?.items?.firstOrNull()
    }

    /** Loads the on-device model ahead of time (the + panel, the camera or "Add a food" opening). */
    fun warm(context: Context) {
        if (!nano) Gemma.warm(context)
    }

    /** The model's plain reply to a prompt (reading a label's text). */
    suspend fun text(context: Context, prompt: String): String? = when {
        nano -> Nano.text(prompt)
        // A label's numbers come back as JSON: stop as soon as it's complete.
        Gemma.state == Gemma.State.Ready -> Gemma.textJson(context, prompt, null, maxOut = 250)
        else -> null
    }

    /** A label's text read by the model: Nano first when it's in use, then Gemma if Nano's can't be read. */
    suspend fun <T> readLabel(context: Context, prompt: String, read: (String?) -> T?): T? {
        read(text(context, prompt))?.let { return it }
        if (!gemmaBackup) return null
        Diag.log(context, "Label: Nano's reply unusable, asked Gemma instead")
        return read(Gemma.textJson(context, prompt, null, maxOut = 250))
    }

    /** Ask Dropi's look-up pick: a small JSON reply (see [app.dropi.data.AskTools]). */
    suspend fun plan(context: Context, prompt: String): String? = when {
        nano -> Nano.text(prompt)
        Gemma.state == Gemma.State.Ready -> Gemma.textJson(context, prompt, app.dropi.data.AskTools.PLAN_SCHEMA, maxOut = 120)
        else -> null
    }

    suspend fun ask(context: Context, prompt: String): String? = when {
        nano -> Nano.ask(prompt)
        Gemma.state == Gemma.State.Ready -> Gemma.ask(context, prompt)
        else -> null
    }
}
