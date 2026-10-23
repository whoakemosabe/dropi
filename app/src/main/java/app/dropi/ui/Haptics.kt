package app.dropi.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Small, deliberate haptics, like Ljós and Afli: Android 14's richer constants where available,
 * subtle ticks elsewhere. Follows the in-app switch and the phone's own touch-feedback setting.
 */
object Haptics {
    var enabled by mutableStateOf(true)

    private fun fire(view: View, constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    /** Button or card press. */
    fun tap(view: View) = fire(view, HapticFeedbackConstants.VIRTUAL_KEY)

    fun toggle(view: View, on: Boolean) = fire(
        view,
        when {
            Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            Build.VERSION.SDK_INT >= 30 -> if (on) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.CLOCK_TICK
        },
    )

    /** A segment or tab snapped into place; a page swiped to the next race. */
    fun segment(view: View) = fire(
        view,
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Pull-to-refresh crossed the line; a sheet or banner settled. */
    fun settle(view: View) = fire(
        view,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Something finished well: refreshed, downloaded, a session just went live. */
    fun confirm(view: View) = fire(
        view,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )

    /** Something failed. */
    fun reject(view: View) = fire(
        view,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    fun longPress(view: View) = fire(view, HapticFeedbackConstants.LONG_PRESS)
}

/** clickable() with a tap haptic. */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val view = LocalView.current
    // Announced as a button by screen readers.
    clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) {
        Haptics.tap(view)
        onClick()
    }
}
