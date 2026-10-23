package app.dropi.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A Lucide icon from res/drawable, tinted. */
@Composable
fun Ic(@DrawableRes id: Int, color: Color, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(painterResource(id), contentDescription = contentDescription, tint = color, modifier = modifier.size(size))
}

/** Wall-clock time that updates every [periodMs], aligned to the period. */
@Composable
fun rememberNow(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            delay(periodMs - (t % periodMs))
        }
    }
    return now
}

val CardShape = RoundedCornerShape(20.dp)

fun Modifier.card(shape: Shape = CardShape, fill: Color = C.Card): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, C.GlassBorder, shape)

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val press = rememberPress()
    Row(
        modifier = modifier
            // A 48 dp touch area (Android's minimum) around the smaller chip.
            .minimumInteractiveComponentSize()
            .pressed(press)
            .clip(RoundedCornerShape(50))
            .background(if (selected) C.Accent else C.SurfaceLight)
            .border(1.dp, if (selected) C.Accent else C.GlassBorder, RoundedCornerShape(50))
            .tap(press, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ic(icon, if (selected) Color.White else C.TextMuted, 14.dp)
        Text(text, style = inter(13.sp, FontWeight.SemiBold, if (selected) Color.White else C.TextSecondary), maxLines = 1)
    }
}

/** A big filled button. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val press = rememberPress()
    Row(
        modifier
            .heightIn(min = 52.dp)
            .pressed(press)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(C.Accent, C.AccentGlow))
                else Brush.horizontalGradient(listOf(C.SurfaceLight, C.SurfaceLight))
            )
            .tap(press, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) Color.White else C.TextMuted
        if (icon != null) {
            Ic(icon, fg, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = inter(16.sp, FontWeight.Bold, fg))
    }
}

/** A quiet text button. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.TextSecondary, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).tap(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = inter(15.sp, FontWeight.SemiBold, color)) }
}

/** A thin bar that fills with download progress. */
@Composable
fun ProgressLine(progress: Float) {
    val shown by animateFloatAsState(progress, tween(260), label = "progress")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.SurfaceLight)) {
        Box(
            Modifier
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(C.Accent, C.AccentGlow)))
        )
    }
}

/**
 * Track with a brand pill that glides (and slightly overshoots) to the chosen option, like the
 * segment rows in Ljós and F1 Pro. Labels fade between muted and white as the pill passes.
 */
@Composable
fun SlidingSegments(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    onSelect: (Int) -> Unit,
) {
    val view = LocalView.current
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 380f), label = "segment")
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(C.SurfaceLight)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(14.dp))
            .padding(3.dp)
    ) {
        val segW = maxWidth / options.size
        Box(
            Modifier
                .offset { IntOffset((segW * pos).roundToPx(), 0) }
                .width(segW)
                .height(height - 6.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.horizontalGradient(listOf(C.Accent, C.AccentGlow)))
        )
        Row(Modifier.fillMaxWidth().height(height - 6.dp)) {
            options.forEachIndexed { i, label ->
                val closeness = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                val color = lerp(C.TextMuted, Color.White, closeness)
                Box(
                    Modifier
                        .weight(1f)
                        .height(height - 6.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) Haptics.segment(view)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = inter(13.sp, FontWeight.SemiBold, color), maxLines = 1)
                }
            }
        }
    }
}

/** A sheet's title row with a round × that slides it closed. */
@Composable
fun SheetTitle(title: String, close: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = inter(20.sp, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceLight).tap { close() },
            contentAlignment = Alignment.Center,
        ) { Ic(app.dropi.R.drawable.lc_x, C.TextSecondary, 18.dp, contentDescription = "Close") }
    }
}

/** One tile in a [ChoiceGrid]. */
data class Choice(val label: String, @DrawableRes val icon: Int? = null)

/**
 * Options laid out as a grid of tiles that all fit on screen: nothing scrolls or swipes sideways.
 * The chosen tile fills with the brand gradient and pops slightly.
 */
@Composable
fun ChoiceGrid(
    choices: List<Choice>,
    selected: Int?,
    columns: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPick: (Int) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(columns).forEachIndexed { row, items ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { col, c ->
                    val i = row * columns + col
                    ChoiceTile(c, i == selected, compact, Modifier.weight(1f)) { onPick(i) }
                }
                // Keep a short last row's tiles the same width as the rest.
                repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceTile(c: Choice, selected: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val fg = if (selected) Color.White else C.TextSecondary
    Column(
        modifier
            .heightIn(min = if (compact) 48.dp else 64.dp)
            .pressed(press)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(C.Accent, C.AccentGlow))
                else Brush.linearGradient(listOf(C.SurfaceLight, C.SurfaceLight))
            )
            .border(1.dp, if (selected) Color.Transparent else C.GlassBorder, shape)
            .semantics { this.selected = selected }
            .tap(press, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (compact) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (c.icon != null && !compact) {
            Ic(c.icon, if (selected) Color.White else C.TextMuted, 18.dp)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            c.label,
            style = inter(12.sp, FontWeight.SemiBold, fg),
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
