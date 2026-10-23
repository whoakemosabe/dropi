package app.dropi.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/*
 * Frosted glass on Kyant's Backdrop 2.x, ported from F1 Pro. A page (or a sheet's list) is
 * recorded with layerBackdrop and redrawn blurred behind its sticky header, so content scrolls
 * underneath it. The fade-out needs Android 13; older phones get a solid tinted band instead.
 */

val glassFull: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** How tall the sticky header is, so pages start their content just below it. */
val LocalHeaderHeight = staticCompositionLocalOf { 0.dp }

/**
 * Makes a floating bar solid to touch: taps and drags on it stop here instead of reaching the
 * page underneath. Buttons inside it still work, since they sit inside this node.
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { if (!it.isConsumed) it.consume() }
    }
}

/** Fades the frosted copy out over the pane's last stretch, (1 - x)², so there's no edge. */
private const val Dissolve = """
uniform shader content;
uniform float2 offset;
uniform float fadeTop;
uniform float fadeBottom;

half4 main(float2 coord) {
    float y = coord.y + offset.y;
    float x = clamp((y - fadeTop) / max(fadeBottom - fadeTop, 1.0), 0.0, 1.0);
    float a = (1.0 - x) * (1.0 - x);
    return content.eval(coord) * half(a);
}
"""

fun BackdropEffectScope.dissolve(fadeTop: Float, fadeBottom: Float) {
    val pad = padding
    runtimeShaderEffect("DropiDissolve", Dissolve, "content") {
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("fadeTop", fadeTop)
        setFloatUniform("fadeBottom", fadeBottom)
    }
}

/**
 * The frosted band behind the status bar and header: no lens, dissolves at the bottom. The pane
 * reaches [margin] past the top and sides and the page is mirrored into that margin, so the blur
 * never pulls in empty space at the edges (Afli's fix for a clear strip along the very top).
 */
@Composable
fun GlassHeader(
    backdrop: LayerBackdrop,
    height: Dp,
    modifier: Modifier = Modifier,
    fade: Dp = 24.dp,
    margin: Dp = 40.dp,
    base: Color = C.Bg,
) {
    // Before Android 13 the blur can't fade out and costs more per frame: a plain tinted band
    // instead (same look at rest, no per-frame redraw of the page behind).
    if (!glassFull) {
        val tint = base.copy(alpha = 0.96f)
        Box(
            modifier.fillMaxWidth().height(height).background(
                Brush.verticalGradient(0f to tint, 0.75f to tint, 1f to Color.Transparent)
            )
        )
        return
    }
    val density = LocalDensity.current
    val fadePx = with(density) { fade.toPx() }
    val m = with(density) { margin.roundToPx() }
    val screenW = remember { floatArrayOf(0f) }
    Box(modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    screenW[0] = constraints.maxWidth.toFloat()
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth + m * 2, constraints.maxHeight + m))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(-m, -m) }
                }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        colorControls(saturation = 1.2f)
                        blur(18.dp.toPx())
                        dissolve(size.height - fadePx, size.height)
                    },
                    onDrawBackdrop = { drawPage ->
                        drawPage()
                        val mf = m.toFloat()
                        val right = mf + screenW[0]
                        withTransform({ scale(-1f, 1f, pivot = Offset(mf, 0f)) }) { drawPage() }
                        withTransform({ scale(-1f, 1f, pivot = Offset(right, 0f)) }) { drawPage() }
                        withTransform({ scale(1f, -1f, pivot = Offset(0f, mf)) }) { drawPage() }
                    },
                    onDrawSurface = {
                        val h = size.height
                        val k = ((h - fadePx) / h).coerceIn(0f, 1f)
                        // Enough tint that the header text stays readable over busy content.
                        val tint = base.copy(alpha = if (C.dark) 0.5f else 0.62f)
                        drawRect(Brush.verticalGradient(0f to tint, k to tint.copy(alpha = tint.alpha * 0.6f), 1f to Color.Transparent))
                    },
                ),
        )
    }
}


/**
 * The header's frosted glass on any shape (the tab bar's wings): the same blur, saturation and
 * tint, so the two read as one material. Older phones get the solid tinted fallback.
 */
fun Modifier.frosted(backdrop: LayerBackdrop, shape: androidx.compose.ui.graphics.Shape, base: Color = C.Bg): Modifier =
    if (!glassFull) this.background(base.copy(alpha = 0.94f), shape)
    else this.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.2f)
            blur(18.dp.toPx())
        },
        onDrawBackdrop = { drawPage -> drawPage() },
        onDrawSurface = { drawRect(base.copy(alpha = if (C.dark) 0.5f else 0.62f)) },
    )
