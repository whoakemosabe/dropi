package app.dropi.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dropi.MainActivity
import app.dropi.R

/** Notification channels and the update notification. Check reminders are in Reminders. */
object Alerts {
    const val CH_UPDATES = "updates"
    const val CH_REMINDERS = "reminders"
    const val CH_RECAP = "recap"
    const val CH_AI = "ai"

    /** Opens the meal screen ("your lunch is worked out") or a meal action from a shortcut. */
    const val EXTRA_OPEN_MEAL = "open_meal"

    /** Opens Ask Dropi (the "on-device AI is ready" notification). */
    const val EXTRA_OPEN_ASK = "open_ask"

    /** Opens a tab: "trends" (the weekly recap). */
    const val EXTRA_OPEN_TAB = "open_tab"
    const val EXTRA_OPEN_UPDATES = "open_updates"

    /** Opens the log sheet: a MealTag name, or "new" to pick the moment from the time. */
    const val EXTRA_LOG_TAG = "log_tag"
    private const val ID_UPDATE = 4
    private const val ID_AI_READY = 5
    private const val ID_MEAL = 6

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Check reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Your planned check times, after-meal checks and low re-checks"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RECAP, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sunday evening: your week's time in range, lows and logging"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When a new version of Dropi is ready"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AI, "On-device AI", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "On-device AI: the model download, and meals worked out while Dropi was closed"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun postUpdate(context: Context, version: String) {
        createChannels(context)
        if (!canNotify(context)) return
        // An explicit intent to our own screen; the flag is the only thing it carries.
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_UPDATES, true)
        val open = PendingIntent.getActivity(
            context, ID_UPDATE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Dropi $version is ready")
            .setContentText("Tap to see what's new and update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
    }

    /** The Gemma download has finished and checked out: meal photos and questions work now. */
    @SuppressLint("MissingPermission")
    fun postAiReady(context: Context) {
        createChannels(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_ASK, true)
        val open = PendingIntent.getActivity(
            context, ID_AI_READY, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_AI)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Dropi's on-device AI is ready")
            .setContentText("Meal photos and questions now work on your phone. Tap to try Ask Dropi.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(ID_AI_READY, n)
    }

    /** A meal photo was worked out while Dropi was closed: what it found, to check. */
    @SuppressLint("MissingPermission")
    fun postMealReady(context: Context, carbs: String, foods: List<String>, peak: String?) {
        createChannels(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_MEAL, "draft")
        val open = PendingIntent.getActivity(context, ID_MEAL, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val list = foods.take(4).joinToString(", ") { app.dropi.data.Foods.display(it) }
        val text = list + (peak?.let { ". Meals like this usually peak around $it" } ?: "") + ". Is it right?"
        val n = NotificationCompat.Builder(context, CH_AI)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Your meal: about $carbs g carbs")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Check it", open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MEAL, n)
    }

    /** Worked on while the screen was closed, and it couldn't be made out. */
    @android.annotation.SuppressLint("MissingPermission") // canNotify checks it
    fun postMealFailed(context: Context) {
        createChannels(context)
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_MEAL, "draft")
        val open = PendingIntent.getActivity(context, ID_MEAL, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CH_AI)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Couldn't make out your meal")
            .setContentText("Tap to say what you ate, or add the foods yourself.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MEAL, n)
    }

    fun cancelMeal(context: Context) = NotificationManagerCompat.from(context).cancel(ID_MEAL)
}
