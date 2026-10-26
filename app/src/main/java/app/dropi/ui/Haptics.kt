package app.dropi.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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

    /** The lightest tick, fine to fire often: a scrub passing from one item to the next. */
    fun tick(view: View) = fire(
        view,
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
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

/**
 * Press feedback for buttons and tiles: they dip to 97% the moment a finger lands and spring
 * back on release, so the app feels like it heard the touch (before the tap even completes).
 */
class Press(val source: MutableInteractionSource, val scale: Float)

@Composable
fun rememberPress(): Press {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        if (pressed) tween(100) else spring(dampingRatio = 1f, stiffness = 900f),
        label = "press",
    )
    return Press(source, scale)
}

/** Put this first in the chain so the whole button (background too) dips on press. */
fun Modifier.pressed(p: Press): Modifier = graphicsLayer { scaleX = p.scale; scaleY = p.scale }

/** clickable() with a tap haptic, sharing [p]'s press state for the dip. */
fun Modifier.tap(p: Press, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val view = LocalView.current
    clickable(
        interactionSource = p.source,
        indication = LocalIndication.current,
        enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Button,
    ) {
        Haptics.tap(view)
        onClick()
    }
}
