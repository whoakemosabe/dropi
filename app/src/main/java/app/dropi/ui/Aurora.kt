package app.dropi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.tr

/**
 * The aurora behind the top of every page: three soft lights (green, cyan, violet) that fade
 * into the night. Fixed to the screen, so the content scrolls over it and the header blurs it.
 */
@Composable
fun AuroraGlow(modifier: Modifier, tint: Color? = null) {
    // Tinted: one colour (the latest reading's range), softer, so it reads as light, not decoration.
    val (a, b, c) = if (tint != null) listOf(tint, tint, tint) else C.Aurora
    val strength = if (tint != null) (if (C.dark) 0.3f else 0.26f) else if (C.dracula) 1f else if (C.dark) 0.55f else 0.45f
    Canvas(modifier) {
        val w = size.width
        fun light(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha * strength), Color.Transparent), Offset(cx, cy), r),
                radius = r, center = Offset(cx, cy),
            )
        }
        light(a, w * 0.22f, w * 0.62f, w * 0.85f, 0.5f)
        light(c, w * 0.86f, w * 0.38f, w * 0.75f, 0.42f)
        light(b, w * 0.55f, w * 0.12f, w * 0.6f, 0.32f)
    }
}

