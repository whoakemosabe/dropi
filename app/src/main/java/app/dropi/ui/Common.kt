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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.dropi.tr
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

/**
 * The four corner sizes: small (chips, icon tiles, links), medium (buttons, fields, rows),
 * large (cards), extra large (sheets and dialogs). Hairline bars (2–3 dp) are the only others.
 */
object Corner {
    // 1.1: tighter, instrument-like corners. Rounder only for sheets.
    val S = 4.dp
    val M = 8.dp
    val L = 8.dp
    val XL = 20.dp
}

val CardShape = RoundedCornerShape(Corner.L)

/**
 * A panel for things that really are objects (a banner, a draft, a receipt): a quiet fill, a
 * hairline edge, small corners. Plain sections use [RuleHeader] and [ruleTop] instead.
 */
fun Modifier.card(shape: Shape = CardShape, fill: Color = C.Card): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, C.Border.copy(alpha = 0.6f), shape)

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
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) C.Accent else Color.Transparent)
            .border(1.dp, if (selected) C.AccentText.copy(alpha = 0.45f) else C.Border, RoundedCornerShape(6.dp))
            .tap(press, onClick = onClick)
            // Screen readers hear whether it's on, not just the colour.
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ic(icon, if (selected) C.OnAccent else C.TextSecondary, 14.dp)
        Text(text, style = inter(13.sp, FontWeight.SemiBold, if (selected) C.OnAccent else C.Text), maxLines = 1)
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
            .clip(RoundedCornerShape(Corner.M))
            .background(
                if (enabled) androidx.compose.ui.graphics.SolidColor(C.Accent)
                else androidx.compose.ui.graphics.SolidColor(C.SurfaceLight)
            )
            .border(1.dp, if (enabled) C.AccentText.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(Corner.M))
            .tap(press, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) C.OnAccent else C.TextMuted
        if (icon != null) {
            Ic(icon, fg, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = inter(17.sp, FontWeight.Bold, fg))
    }
}

/**
 * The secondary button: a soft tint of its colour with a thin edge of the same colour, so it
 * always reads as a button (plain text is kept for real links only).
 */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.TextSecondary, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corner.M)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, if (color == C.TextSecondary) C.Border else color.copy(alpha = 0.45f), shape)
            .semantics { role = Role.Button }
            .tap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = inter(15.sp, FontWeight.SemiBold, color), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
}

/** A thin bar that fills with download progress. */
@Composable
fun ProgressLine(progress: Float) {
    val shown by animateFloatAsState(progress, tween(260), label = "progress")
    Box(Modifier.fillMaxWidth().height(3.dp).background(C.Border)) {
        Box(
            Modifier
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .height(3.dp)
                .background(C.AccentText)
        )
    }
}

/**
 * Options in a row, the chosen one underlined: a 2 dp line in the signature colour glides (with
 * a little overshoot) to it, labels in mono capitals, a hairline under the whole row.
 */
@Composable
fun SlidingSegments(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onSelect: (Int) -> Unit,
) {
    val view = LocalView.current
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 380f), label = "segment")
    val line = C.Border
    val accent = C.AccentText
    BoxWithConstraints(
        modifier
            .height(height.coerceAtLeast(44.dp))
            .drawBehind { drawLine(line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }
    ) {
        val segW = maxWidth / options.size
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset((segW * pos).roundToPx(), 0) }
                .width(segW)
                .height(2.dp)
                .background(accent)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val closeness = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                val color = lerp(C.TextMuted, C.Text, closeness)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { this.selected = i == selected; role = Role.Tab }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) Haptics.segment(view)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(caps(label), style = mono(13.sp, if (i == selected) FontWeight.SemiBold else FontWeight.Medium, color, 0.04f), maxLines = 1)
                }
            }
        }
    }
}

/** A sheet's title row with a round × that slides it closed. */
@Composable
fun SheetTitle(title: String, close: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = heading(24.sp), modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.minimumInteractiveComponentSize().size(40.dp).clip(CircleShape).border(1.dp, C.Border, CircleShape).tap { close() },
            contentAlignment = Alignment.Center,
        ) { Ic(app.dropi.R.drawable.lc_x, C.TextSecondary, 18.dp, contentDescription = tr("Close")) }
    }
}

/** One tile in a [ChoiceGrid]. */
data class Choice(val label: String, @DrawableRes val icon: Int? = null)

/**
 * Options laid out as a grid of tiles that all fit on screen: nothing scrolls or swipes sideways.
 * The chosen tile fills with the accent and pops slightly.
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
    val shape = RoundedCornerShape(Corner.M)
    val fg = if (selected) C.OnAccent else C.TextSecondary
    Column(
        modifier
            .heightIn(min = if (compact) 48.dp else 64.dp)
            .pressed(press)
            .clip(shape)
            .background(if (selected) C.Accent else Color.Transparent)
            .border(1.dp, if (selected) C.AccentText.copy(alpha = 0.45f) else C.Border, shape)
            .semantics { this.selected = selected }
            .tap(press, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (compact) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (c.icon != null && !compact) {
            Ic(c.icon, if (selected) C.OnAccent else C.TextSecondary, 18.dp)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            c.label,
            style = inter(13.sp, FontWeight.SemiBold, if (selected) C.OnAccent else C.Text),
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Switch colours for every toggle: on is the accent; off is a dark track with a clear
 * outline and a light thumb, so it reads as "off" rather than greyed out and disabled.
 */
@Composable
fun dropiSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedTrackColor = C.Accent,
    checkedThumbColor = C.OnAccent,
    checkedBorderColor = C.Accent,
    checkedIconColor = C.Accent,
    uncheckedTrackColor = C.SurfaceLight,
    uncheckedThumbColor = C.TextSecondary,
    uncheckedBorderColor = C.TextMuted.copy(alpha = 0.6f),
    uncheckedIconColor = C.SurfaceLight,
    disabledCheckedTrackColor = C.Accent.copy(alpha = 0.4f),
    disabledCheckedThumbColor = C.OnAccent.copy(alpha = 0.7f),
    disabledCheckedBorderColor = C.Accent.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = C.SurfaceLight.copy(alpha = 0.5f),
    disabledUncheckedThumbColor = C.TextMuted.copy(alpha = 0.5f),
    disabledUncheckedBorderColor = C.TextMuted.copy(alpha = 0.3f),
)
