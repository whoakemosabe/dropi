package app.dropi.work

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.dropi.MainActivity
import app.dropi.Prefs
import app.dropi.R
import app.dropi.data.Db
import app.dropi.data.MealTag
import app.dropi.data.OneOff
import app.dropi.data.Schedule
import app.dropi.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Check-time reminders. One exact alarm is kept for the soonest reminder (a planned check or a
 * one-off); when it fires, everything due is posted and the next alarm is set. A check that
 * was already logged (a little early) is skipped.
 */
object Reminders {
    const val ACTION_FIRE = "app.dropi.REMINDER_FIRE"
    const val ACTION_SNOOZE = "app.dropi.REMINDER_SNOOZE"
    const val EXTRA_TAG = "tag"
    const val EXTRA_ID = "notification_id"
    private const val SNOOZE_MIN = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun firePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets (or clears) the alarm for the next reminder. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val p = Prefs(app)
        val now = System.currentTimeMillis()
        val at = Schedule.nextAlarm(p.slots, p.oneOffs, p.remindersOn, now, ZoneId.systemDefault())
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePending(app)
        if (at == null) {
            am.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun rescheduleAsync(context: Context, then: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try { reschedule(app) } catch (e: Exception) { }
            try { Widgets.updateAll(app) } catch (e: Exception) { }
            then()
        }
    }

    /** The alarm went off: post what's due, then set the next one. */
    suspend fun fire(context: Context) {
        val app = context.applicationContext
        val p = Prefs(app)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val readings = try { Db.get(app).since(now - 2 * 86_400_000L) } catch (e: Exception) { emptyList() }

        if (p.remindersOn) {
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
            val sent = p.notifiedSlots.toMutableSet()
            Schedule.dueSlots(p.slots, readings, now, zone).forEach { (slot, at) ->
                val key = "$day@${slot.id}"
                if (key in sent) return@forEach
                sent += key
                post(app, idFor(slot.id), "Time to check: ${slot.name}", "Your ${clock(app, at, zone)} check. Tap to log it.", slot.tag)
            }
            p.notifiedSlots = sent
        }

        // One-offs that are due (or overdue: posted late rather than lost).
        val (due, later) = p.oneOffs.partition { it.at <= now + 60_000 }
        due.forEach { o ->
            val text = when (o.kind) {
                OneOff.Kind.AFTER_MEAL -> "About ${p.afterMealMinutes / 60} h since your before-meal reading. Tap to log."
                OneOff.Kind.RECHECK -> "15 minutes are up. Check again to see you're back above 3.9."
                OneOff.Kind.SNOOZE -> "Your snoozed check. Tap to log it."
            }
            post(app, 2000 + (o.at / 60_000 % 1000).toInt(), o.kind.title, text, o.tag, urgent = o.kind == OneOff.Kind.RECHECK)
        }
        if (due.isNotEmpty()) p.oneOffs = later

        reschedule(app)
        try { Widgets.updateAll(app) } catch (e: Exception) { }
    }

    /** Snooze from the notification: the same check again in 10 minutes. */
    suspend fun snooze(context: Context, tag: MealTag, notificationId: Int) {
        val app = context.applicationContext
        NotificationManagerCompat.from(app).cancel(notificationId)
        val p = Prefs(app)
        val now = System.currentTimeMillis()
        p.oneOffs = p.oneOffs + OneOff(now + SNOOZE_MIN * 60_000L, tag, OneOff.Kind.SNOOZE, now)
        reschedule(app)
    }

    /** A low was just logged: remind to re-check in 15 minutes. Returns when. */
    fun remindRecheck(context: Context, tag: MealTag): Long {
        val app = context.applicationContext
        val p = Prefs(app)
        val now = System.currentTimeMillis()
        val at = now + 15 * 60_000L
        p.oneOffs = p.oneOffs + OneOff(at, tag, OneOff.Kind.RECHECK, now)
        rescheduleAsync(app)
        return at
    }

    /** A new reading answers any reminder showing now. */
    fun clearShown(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        try {
            nm.activeNotifications.filter { it.notification.channelId == Alerts.CH_REMINDERS }.forEach { nm.cancel(it.id) }
        } catch (e: Exception) { }
    }

    private fun idFor(slotId: String) = 1000 + (slotId.hashCode() and 0x3FF)

    private fun clock(context: Context, at: Long, zone: ZoneId): String {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(Instant.ofEpochMilli(at).atZone(zone))
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String, tag: MealTag, urgent: Boolean = false) {
        Alerts.createChannels(context)
        if (!Alerts.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Alerts.EXTRA_LOG_TAG, tag.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_TAG, tag.name)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Alerts.CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(if (urgent) 0xFFD0263A.toInt() else 0xFF4F5BEA.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(R.drawable.lc_plus, "Log reading", open)
            .addAction(R.drawable.lc_clock, "Snooze $SNOOZE_MIN min", snooze)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, Alerts.CH_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_drop)
                    .setContentTitle("Dropi reminder")
                    .build()
            )
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

/** The reminder alarm and the Snooze button. Not exported: only our own PendingIntents reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> Reminders.fire(app)
                    Reminders.ACTION_SNOOZE -> Reminders.snooze(
                        app,
                        MealTag.fromKey(intent.getStringExtra(Reminders.EXTRA_TAG)),
                        intent.getIntExtra(Reminders.EXTRA_ID, 0),
                    )
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** After a restart, an app update or a clock / time zone change, the alarm is set again. */
class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts; anything else (another app poking it) is ignored.
        if (intent.action !in actions) return
        Scheduler.ensure(context)
        val pending = goAsync()
        Reminders.rescheduleAsync(context) { pending.finish() }
    }
}
