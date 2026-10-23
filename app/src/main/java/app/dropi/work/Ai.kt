package app.dropi.work

import android.content.Context
import android.net.Uri
import app.dropi.data.PlateGuess

/**
 * The on-device model behind meal photos and typed questions. Gemini Nano when the phone has it
 * (nothing to download from us); otherwise Gemma, once it has been downloaded in Settings.
 * Either way the work happens on the phone and nothing is uploaded.
 */
object Ai {
    /** Gemini Nano is on this phone (ready, or AICore will fetch it on first use). */
    val nano: Boolean
        get() = Nano.state == Nano.State.READY || Nano.state == Nano.State.NEEDS_DOWNLOAD || Nano.state == Nano.State.DOWNLOADING

    /** Meal photos and typed questions can be used right now. */
    val usable: Boolean get() = nano || Gemma.state == Gemma.State.Ready

    /** Still finding out what this phone has. */
    val checking: Boolean
        get() = Nano.state == Nano.State.CHECKING || (Nano.state == Nano.State.UNAVAILABLE && Gemma.state == Gemma.State.Checking)

    /** The model's name as shown in the app. */
    val name: String get() = if (nano) "Gemini Nano" else "Gemma"

    /** What to say while the first answer is on its way, when it takes longer than usual. */
    val warmUp: String?
        get() = when {
            Nano.state == Nano.State.DOWNLOADING || Nano.state == Nano.State.NEEDS_DOWNLOAD -> "Getting Gemini Nano ready on your phone. The first time takes a few minutes…"
            !nano && !Gemma.loaded -> "Loading Gemma on your phone. The first answer takes a few seconds…"
            else -> null
        }

    suspend fun refresh(context: Context) {
        Nano.refresh()
        if (Nano.state == Nano.State.UNAVAILABLE) {
            val g = Gemma.refresh(context)
            if (g is Gemma.State.Downloading || g == Gemma.State.WaitingForWifi || g == Gemma.State.Verifying) Gemma.watch(context)
        }
    }

    suspend fun plate(context: Context, uri: Uri): PlateGuess? = when {
        nano -> Nano.plate(context, uri)
        Gemma.state == Gemma.State.Ready -> Gemma.plate(context, uri)
        else -> null
    }

    /** A meal breakdown from a photo, or null when the model couldn't make it out. */
    suspend fun mealFromPhoto(context: Context, uri: Uri): app.dropi.data.Meal? {
        val raw = when {
            nano -> Nano.imageText(context, uri, app.dropi.data.MealAi.PHOTO)
            Gemma.state == Gemma.State.Ready -> Gemma.imageJson(context, uri, app.dropi.data.MealAi.PHOTO, app.dropi.data.MealAi.SCHEMA)
            else -> null
        }
        return app.dropi.data.MealAi.parse(raw, "photo")
    }

    /** A meal breakdown from a prompt in words (a description, or a change to a meal). */
    suspend fun mealFromWords(context: Context, prompt: String, source: String): app.dropi.data.Meal? {
        val raw = when {
            nano -> Nano.text(prompt)
            Gemma.state == Gemma.State.Ready -> Gemma.textJson(context, prompt, app.dropi.data.MealAi.SCHEMA)
            else -> null
        }
        return app.dropi.data.MealAi.parse(raw, source)
    }

    /** The model's plain reply to a prompt (reading a label's text). */
    suspend fun text(context: Context, prompt: String): String? = when {
        nano -> Nano.text(prompt)
        Gemma.state == Gemma.State.Ready -> Gemma.ask(context, prompt)
        else -> null
    }

    suspend fun ask(context: Context, prompt: String): String? = when {
        nano -> Nano.ask(prompt)
        Gemma.state == Gemma.State.Ready -> Gemma.ask(context, prompt)
        else -> null
    }
}
