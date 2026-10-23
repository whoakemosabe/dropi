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

/** Notification channels and posts. Reading reminders join this in the next phase. */
object Alerts {
    const val CH_UPDATES = "updates"
    const val EXTRA_OPEN_UPDATES = "open_updates"
    private const val ID_UPDATE = 4

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When a new version of Dropi is ready"
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
            .setColor(0xFF4F5BEA.toInt())
            .setContentTitle("Dropi $version is ready")
            .setContentText("Tap to see what's new and update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATE, n)
    }
}
