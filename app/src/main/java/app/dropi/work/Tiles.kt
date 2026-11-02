package app.dropi.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.dropi.MainActivity

/**
 * Quick Settings tiles (pull down the shade, edit, drag in): "Log a reading" and "Snap a meal".
 * On a locked phone Android asks to unlock first, so no health data shows over the lock screen.
 */
abstract class OpenTile(private val extra: String, private val value: String) : TileService() {
    override fun onStartListening() {
        qsTile?.let { it.state = Tile.STATE_INACTIVE; it.updateTile() }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { open() } else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(extra, value)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, value.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

class ReadingTile : OpenTile(Alerts.EXTRA_LOG_TAG, "new")
class MealTile : OpenTile(Alerts.EXTRA_OPEN_MEAL, "camera")

object Tiles {
    /** Android 13+: asks the system to add the "Log a reading" tile; [done] gets added / already / no. */
    fun request(context: android.content.Context, done: (Int) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            val sbm = context.getSystemService(android.app.StatusBarManager::class.java) ?: return
            sbm.requestAddTileService(
                android.content.ComponentName(context, ReadingTile::class.java),
                context.getString(app.dropi.R.string.sc_reading),
                android.graphics.drawable.Icon.createWithResource(context, app.dropi.R.drawable.ic_stat_drop),
                context.mainExecutor,
            ) { done(it) }
        } catch (e: Exception) { Diag.log(context, "Tile: request failed (${e.javaClass.simpleName})") }
    }
}
