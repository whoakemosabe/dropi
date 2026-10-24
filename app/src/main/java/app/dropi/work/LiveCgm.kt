package app.dropi.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dropi.MainActivity
import app.dropi.Prefs
import app.dropi.R
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.Heads
import app.dropi.data.SensorPoint
import app.dropi.data.Trend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Live sensor mode. Values arrive from xDrip+ or Juggluco (their "xDrip+ broadcast", sent to
 * this app) or from Health Connect, are kept in the sensor table, and drive:
 * - the live number, arrow and trace on the home screen
 * - an ongoing notification (a Live Update chip in the status bar on Android 16)
 * - a heads-up when the trend points under 3.9 within 20 minutes
 * Everything is worked out on the phone. It's an estimate to glance at; the CGM's own app
 * remains the alarm to rely on.
 */
object LiveCgm {
    const val CH_LIVE = "live"
    const val CH_HEADS = "heads_up"
    private const val ID_LIVE = 20
    private const val ID_HEADS = 21

    /** xDrip+'s broadcast (Juggluco sends the same one). */
    const val ACTION_XDRIP = "com.eveningoutpost.dexdrip.BgEstimate"
    private const val EXTRA_MGDL = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
    private const val EXTRA_TIME = "com.eveningoutpost.dexdrip.Extras.Time"

    /** Bumped whenever new sensor data lands, so an open screen reloads. */
    val ticks = MutableStateFlow(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Live sensor value", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Your latest sensor value and trend, kept up to date"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_HEADS, "Heading low", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When your sensor trend points under 3.9 mmol/L soon"
            }
        )
    }

    /**
     * A value from another app's broadcast. Anything implausible is dropped: a value outside
     * what sensors read, a time more than an hour old or in the future.
     */
    fun fromBroadcast(intent: Intent, now: Long = System.currentTimeMillis()): SensorPoint? {
        if (intent.action != ACTION_XDRIP) return null
        val mgdl = intent.getDoubleExtra(EXTRA_MGDL, Double.NaN)
        val at = intent.getLongExtra(EXTRA_TIME, 0L)
        if (mgdl.isNaN() || mgdl < 20 || mgdl > 600) return null
        if (at <= 0 || at > now + 5 * 60_000 || now - at > 60 * 60_000) return null
        val mmol = Glucose.toMmol(mgdl).coerceIn(Glucose.MIN_MMOL, Glucose.MAX_MMOL)
        return SensorPoint(at, mmol)
    }

    /** The apps that send this broadcast. */
    val BRIDGES = listOf("com.eveningoutpost.dexdrip", "tk.glucodata")

    /**
     * A broadcast's sender can't be verified (neither app shares its identity), so values are
     * only taken while one of the bridge apps is actually installed, and only as plain numbers.
     */
    fun bridgeInstalled(context: Context): Boolean = BRIDGES.any {
        try {
            context.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Stores a broadcast value (when live mode is on) and refreshes everything that shows it. */
    fun receive(context: Context, intent: Intent, done: () -> Unit = {}) {
        val p = Prefs(context)
        val point = if (p.liveOn && bridgeInstalled(context)) fromBroadcast(intent) else null
        if (point == null) {
            done()
            return
        }
        scope.launch {
            try {
                if (Db.real(context).addSensor(listOf(point)) > 0) changed(context)
            } catch (e: Exception) {
            } finally {
                done()
            }
        }
    }

    /** New sensor data: update the screen, the notification and maybe send a heads-up. */
    suspend fun changed(context: Context) {
        ticks.value = System.currentTimeMillis()
        val p = Prefs(context)
        if (!p.liveOn) return
        val points = Db.real(context).sensorSince(System.currentTimeMillis() - 60 * 60_000L)
        val now = System.currentTimeMillis()
        if (p.liveNotify) postLive(context, points, now) else cancelLive(context)
        if (p.headsUpOn) {
            val last = Heads.entries.firstOrNull { it.name == p.lastHeadsKind }
            Trend.headsUp(points, now, p.lastHeadsAt, last)?.let { h ->
                p.lastHeadsAt = now
                p.lastHeadsKind = h.kind.name
                postHeads(context, h.kind, h.mmol, h.minutes)
            }
        }
    }

    private fun openApp(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun postLive(context: Context, points: List<SensorPoint>, now: Long) {
        createChannels(context)
        if (!Alerts.canNotify(context)) return
        val last = Trend.latest(points) ?: return
        if (!Trend.fresh(points, now)) {
            cancelLive(context)
            return
        }
        val unit = Prefs(context).unit
        val value = Glucose.format(last.mmol, unit)
        val arrow = Trend.arrow(points)
        val words = when {
            last.mmol < Glucose.LOW -> "Low"
            last.mmol > Glucose.TIR_HIGH -> "Above range"
            else -> "In range"
        }
        val n = NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("$value ${unit.label}${arrow?.let { " ${it.glyph}" } ?: ""}")
            .setContentText(listOfNotNull(words, arrow?.words?.replaceFirstChar { it.uppercase() }).joinToString(", ") + " · from your sensor")
            .setShortCriticalText("$value${arrow?.glyph ?: ""}")
            .setContentIntent(openApp(context, ID_LIVE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(last.at)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Gone by itself once the value is too old to be useful.
            .setTimeoutAfter((last.at + Trend.FRESH_MS - now).coerceAtLeast(60_000L))
            .setRequestPromotedOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_LIVE, n)
    }

    fun cancelLive(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_LIVE)
    }

    @SuppressLint("MissingPermission")
    private fun postHeads(context: Context, kind: Heads, mmol: Double, minutes: Int) {
        createChannels(context)
        if (!Alerts.canNotify(context)) return
        val unit = Prefs(context).unit
        val v = Glucose.format(mmol, unit)
        val low = Glucose.format(Glucose.LOW, unit)
        val (title, text) = when (kind) {
            Heads.LOW_NOW -> "Your sensor reads $v" to
                "That's under $low ${unit.label}. Check with your meter and follow your plan for lows."
            Heads.HEADING_LOW -> "Heading low: $v and falling" to
                "At this rate you could be under $low in about $minutes min. Check with your meter and follow your plan."
        }
        val n = NotificationCompat.Builder(context, CH_HEADS)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nDropi's estimate from your sensor trend, not a medical alarm."))
            .setContentIntent(openApp(context, ID_HEADS))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(30 * 60_000L)
            .build()
        NotificationManagerCompat.from(context).notify(ID_HEADS, n)
    }

    private var registered: BroadcastReceiver? = null

    /**
     * While Dropi runs, also listen without a named receiver: xDrip+ set to broadcast to "any
     * app" reaches this one, which the manifest receiver alone can't get on Android 8+.
     */
    fun listen(context: Context) {
        if (registered != null) return
        val r = CgmReceiver()
        try {
            ContextCompat.registerReceiver(
                context.applicationContext, r, IntentFilter(ACTION_XDRIP), ContextCompat.RECEIVER_EXPORTED,
            )
            registered = r
        } catch (e: Exception) { }
    }
}

/**
 * xDrip+ and Juggluco broadcasts. Exported because another app sends them; it accepts only the
 * one action, reads two plain numbers, sanity-checks them and does nothing unless live mode is
 * switched on. It never starts anything or forwards the intent.
 */
class CgmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LiveCgm.ACTION_XDRIP) return
        val pending = goAsync()
        LiveCgm.receive(context, intent) { pending.finish() }
    }
}
