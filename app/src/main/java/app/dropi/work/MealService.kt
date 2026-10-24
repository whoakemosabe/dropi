package app.dropi.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.dropi.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps Dropi running while a meal is being worked out after the person leaves the app. Without
 * it Android freezes the app in the background and the work (and the "your meal is ready"
 * notification) only finishes when Dropi is opened again. A short foreground service with a quiet
 * "Working out your meal…" notification, and a wake lock so the screen going off doesn't pause it.
 */
class MealService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always become a foreground service first: Android requires it after
        // startForegroundService, even if the work has already finished by now.
        Alerts.createChannels(this)
        val n = NotificationCompat.Builder(this, Alerts.CH_AI)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF6E5BFF.toInt())
            .setContentTitle("Working out your meal…")
            .setContentText("Dropi lets you know when it's done.")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        try {
            ServiceCompat.startForeground(this, ID, n, type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = this
        if (jobs.get() <= 0) {
            stop()
            return START_NOT_STICKY
        }
        if (wake == null) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dropi:meal")
                .apply { acquire(3 * 60_000L) }
        }
        return START_NOT_STICKY
    }

    /** A short service gets about three minutes; stop cleanly when that's up (Android 14 and 15+). */
    @Deprecated("Android 14 form")
    override fun onTimeout(startId: Int) = stop()
    override fun onTimeout(startId: Int, fgsType: Int) = stop()

    internal fun stop() {
        try { wake?.takeIf { it.isHeld }?.release() } catch (e: Exception) { }
        wake = null
        if (running === this) running = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { wake?.takeIf { it.isHeld }?.release() } catch (e: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val ID = 4100
        private val jobs = AtomicInteger(0)

        /** A meal job started (call while Dropi is on screen, where starting a service is allowed). */
        fun hold(context: Context) {
            jobs.incrementAndGet()
            try {
                ContextCompat.startForegroundService(context, Intent(context, MealService::class.java))
            } catch (e: Exception) { }
        }

        /** A meal job finished: the last one stops the service (on the main thread, where it lives). */
        fun release(context: Context) {
            if (jobs.decrementAndGet() > 0) return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // A new job may have started meanwhile; it keeps the service.
                if (jobs.get() <= 0) running?.stop()
            }
        }

        @Volatile private var running: MealService? = null
    }
}
