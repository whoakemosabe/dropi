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
import app.dropi.data.Analytics
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.Reading
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
    const val ACTION_QUICK_LOG = "app.dropi.REMINDER_QUICK_LOG"
    const val KEY_VALUE = "value"
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
        val at = Schedule.nextAlarm(p.slots, p.oneOffs, p.remindersOn, now, ZoneId.systemDefault(), recapOn = p.recapOn)
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

        // Sunday evening: the week in one notification (once per week).
        Schedule.recapWeek(now, zone)?.let { monday ->
            if (p.recapOn && p.recapSentFor != monday.toString()) {
                p.recapSentFor = monday.toString()
                val week = try { Db.get(app).since(now - 8 * 86_400_000L) } catch (e: Exception) { emptyList() }
                postRecap(app, week, zone, p)
            }
        }

        reschedule(app)
        try { Widgets.updateAll(app) } catch (e: Exception) { }
    }

    @SuppressLint("MissingPermission")
    private fun postRecap(context: Context, readings: List<Reading>, zone: ZoneId, p: Prefs) {
        val today = Instant.now().atZone(zone).toLocalDate()
        val r = Analytics.report(readings, 7, today, zone)
        if (r.count == 0) return
        Alerts.createChannels(context)
        if (!Alerts.canNotify(context)) return
        val unit = p.unit
        val parts = listOfNotNull(
            r.bands?.let { "In range ${Math.round(it.inRange)}%" },
            r.mean?.let { "average ${Glucose.format(it, unit)}" },
            if (r.lows == 0) "no lows" else "${r.lows} low${if (r.lows == 1) "" else "s"}",
            "logged on ${r.daysLogged} of 7 days",
        )
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Alerts.EXTRA_OPEN_TAB, "trends"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = parts.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
        val n = NotificationCompat.Builder(context, Alerts.CH_RECAP)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Your week in Dropi")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(3, n)
    }

    /**
     * "Type reading" on a reminder: the number typed in the notification is saved without opening
     * the app. Anything that isn't a meter number gets a gentle retry instead.
     */
    @SuppressLint("MissingPermission")
    suspend fun quickLog(context: Context, intent: Intent) {
        val app = context.applicationContext
        val p = Prefs(app)
        val tag = MealTag.fromKey(intent.getStringExtra(EXTRA_TAG))
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val typed = androidx.core.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_VALUE)?.toString().orEmpty()
        val unit = p.unit
        val mmol = Glucose.parse(typed.trim().replace(',', '.'), unit)
        if (mmol == null) {
            post(
                app, id, "Couldn't save that",
                "Type just the number on your meter, like ${if (unit == GlucoseUnit.MMOL) "6.4" else "115"}.", tag,
            )
            return
        }
        val now = System.currentTimeMillis()
        val saved = Db.get(app).save(Reading(at = now, mmol = mmol, tag = tag))
        p.oneOffs = Schedule.afterSave(p.oneOffs, saved, p.afterMealOn, p.afterMealMinutes, now)
        clearShown(app)
        val status = Glucose.status(mmol, tag, p.targets)
        val value = "${Glucose.format(mmol, unit)} ${unit.label}"
        if (status.isLow) {
            // A low saved from the notification still gets the standard steps and a re-check.
            p.oneOffs = p.oneOffs + OneOff(now + 15 * 60_000L, tag, OneOff.Kind.RECHECK, now)
            post(
                app, 2999, "Low: $value",
                "Take 15 g of fast-acting sugar now (4 glucose tablets or about 150 ml of juice). I'll remind you to re-check in 15 minutes. Follow your care plan.",
                tag, urgent = true,
            )
        } else if (Alerts.canNotify(app)) {
            val n = NotificationCompat.Builder(app, Alerts.CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_drop)
                .setColor(0xFF6E5BFF.toInt())
                .setContentTitle("Saved $value")
                .setContentText("${tag.label} · ${status.label}")
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setTimeoutAfter(6_000)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(app).notify(2998, n)
        }
        if (p.healthSync) try { HealthSync.push(app, listOf(saved)) } catch (e: Exception) { }
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

    /** "Type reading": a reply box right in the notification (the PendingIntent has to be mutable for it). */
    private fun typeAction(context: Context, id: Int, tag: MealTag): NotificationCompat.Action {
        val unit = Prefs(context).unit
        val input = androidx.core.app.RemoteInput.Builder(KEY_VALUE).setLabel("Reading in ${unit.label}").build()
        val pi = PendingIntent.getBroadcast(
            context, id + 5000,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_QUICK_LOG)
                .putExtra(EXTRA_TAG, tag.name)
                .putExtra(EXTRA_ID, id),
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.lc_pencil, "Type reading", pi)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
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
        // Before a meal: one tap to the camera for the plate (the body still opens the log).
        val snap = if (tag == MealTag.BEFORE_MEAL) PendingIntent.getActivity(
            context, id + 50_000,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Alerts.EXTRA_OPEN_MEAL, "camera"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ) else null
        val n = NotificationCompat.Builder(context, Alerts.CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(if (urgent) 0xFFD0263A.toInt() else 0xFF6E5BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .apply {
                if (snap != null) addAction(R.drawable.lc_camera, "Snap your plate", snap)
                else addAction(R.drawable.lc_plus, "Log reading", open)
            }
            .addAction(typeAction(context, id, tag))
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
                    Reminders.ACTION_QUICK_LOG -> Reminders.quickLog(app, intent)
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
