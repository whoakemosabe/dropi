package app.dropi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Status

/**
 * Dropi's palette, "Aurora": a near-black northern sky with the aurora's green, cyan and violet
 * as the brand (the glow behind the page, the + button, the live trace). The range colours stay
 * the ones that mean something (green in range, amber high, pink-red low) and always come with
 * words. The light theme is the same sky by day. Plus Dracula: near-black, blood red and a
 * purple glow. [dark] and [dracula] are Compose state, so screens redraw when the theme changes.
 */
object C {
    var dark by mutableStateOf(true)

    /** The Dracula theme (always dark). */
    var dracula by mutableStateOf(false)

    private fun pick(d: Long, l: Long, dr: Long = d) = Color(if (dracula) dr else if (dark) d else l)

    val Bg get() = pick(0xFF060A14, 0xFFEEF3FF, 0xFF0A0406)
    val Card get() = pick(0xFF0E121C, 0xFFFFFFFF, 0xFF170A0E)
    val SurfaceLight get() = pick(0xFF171C2A, 0xFFE3E9FA, 0xFF261018)
    val Border get() = pick(0xFF1F2638, 0xFFCDD6EE, 0xFF3D1621)
    val GlassBorder get() = pick(0x14FFFFFF, 0x14101A40, 0x2EFF2D55)

    /** Brand fill (buttons, selected tiles); [OnAccent] sits on it. */
    val Accent get() = pick(0xFF6E5BFF, 0xFF5B47F0, 0xFFA3001B)
    val AccentGlow get() = pick(0xFF3A7BFF, 0xFF2E6BFF, 0xFF6A1B9A)
    val OnAccent get() = pick(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF)

    /** Brand colour as text or a thin icon: aurora mint (violet by day). */
    val AccentText get() = pick(0xFF7CF5C4, 0xFF4B3BD6, 0xFFFF5C7A)

    /** The aurora itself, for the glow, the + button, the logo and the live trace. */
    val Aurora: List<Color> get() = when {
        dracula -> listOf(Color(0xFFA3001B), Color(0xFFD1123F), Color(0xFF6A1B9A))
        dark -> listOf(Color(0xFF2FE39A), Color(0xFF45D4FF), Color(0xFF785CFF))
        else -> listOf(Color(0xFF12C982), Color(0xFF27B5F0), Color(0xFF6E5BFF))
    }

    val Text get() = pick(0xFFEEF3FF, 0xFF0E1638, 0xFFF6E9EC)
    val TextSecondary get() = pick(0xFFC9D3F0, 0xFF3C4670, 0xFFCDA6B0)
    val TextMuted get() = pick(0xFF8B95B2, 0xFF5A6488, 0xFFA57E8A)

    // Range colours, checked with the dataviz palette validator against each theme's card colour
    // (contrast, colour-blind separation); they always come with a label too.
    val UrgentLow get() = pick(0xFFE0306E, 0xFFB0124F, 0xFFD6336C)
    val Low get() = pick(0xFFFF7F9C, 0xFFE5486E, 0xFFFF8FB0)
    val BelowTarget get() = pick(0xFF7EA4F2, 0xFF3D6AC2, 0xFF7FA7D9)
    val InRange get() = pick(0xFF22D18A, 0xFF0E9F63, 0xFF9F8CFF)
    val High get() = pick(0xFFF2B632, 0xFFA67C00, 0xFFF2C94C)
    val VeryHigh get() = pick(0xFFF06A2A, 0xFFD9480F, 0xFFE8622C)

    /** Text that reads on a solid range colour (the hero slab). */
    fun onStatus(c: Color): Color = if (c.luminance() > 0.33f) Color(0xFF071A2B) else Color.White

    fun status(s: Status): Color = when (s) {
        Status.URGENT_LOW -> UrgentLow
        Status.LOW -> Low
        Status.BELOW_TARGET -> BelowTarget
        Status.IN_RANGE -> InRange
        Status.HIGH -> High
        Status.VERY_HIGH -> VeryHigh
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/**
 * Bricolage Grotesque for the numbers and titles that carry the personality (its ink traps show
 * at size), Inter for everything you read. Three static cuts of the variable font: display
 * (big numbers), title and label.
 */
val BricolageDisplay = FontFamily(Font(R.font.bricolage_display, FontWeight.ExtraBold))
val BricolageTitle = FontFamily(Font(R.font.bricolage_title, FontWeight.Bold))
val BricolageLabel = FontFamily(Font(R.font.bricolage_label, FontWeight.SemiBold))

/**
 * A number set in Bricolage, tabular so live values don't jiggle. Optical sizes by hand: the
 * display cut (tight, deep ink traps) from 40 sp up, the text-size cut below that.
 */
fun numeral(size: TextUnit, color: Color = C.Text): TextStyle {
    val big = size.value >= 40f
    return TextStyle(
        fontFamily = if (big) BricolageDisplay else BricolageTitle, fontSize = size,
        fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Bold, color = color,
        letterSpacing = if (big) (-0.02).em else 0.em, fontFeatureSettings = "tnum",
    )
}

/** A heading in Bricolage. */
fun heading(size: TextUnit, color: Color = C.Text) = TextStyle(
    fontFamily = BricolageTitle, fontSize = size, fontWeight = FontWeight.Bold, color = color, letterSpacing = (-0.01).em,
)

/** Inter with tabular figures, so numbers don't jiggle as they change. Tracking is in em. */
fun inter(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = C.Text, tracking: Float = 0f) =
    TextStyle(
        fontFamily = Inter, fontSize = size, fontWeight = weight, color = color, letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )

/** A section's name: sentence case in Bricolage, not shouted. */
val SectionLabel: TextStyle get() = TextStyle(
    fontFamily = BricolageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.TextSecondary,
)

@Composable
fun DropiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (C.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = C.Accent,
            onPrimary = C.OnAccent,
            background = C.Bg,
            surface = C.Card,
            onSurface = C.Text,
            onBackground = C.Text,
            surfaceVariant = C.SurfaceLight,
            onSurfaceVariant = C.TextSecondary,
            outline = C.Border,
        ),
        content = content,
    )
}
