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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Status

/**
 * Dropi's palette, "Aurora". Dark: neutral near-black with clear steps from page to sheet to
 * card to tile, one violet accent, and a soft cyan, violet and pink glow (no green, which only
 * ever means "in range"). Light: the same sky by day with the full aurora. The range colours stay
 * the ones that mean something (green in range, amber high, pink-red low) and always come with
 * words. The light theme is the same sky by day. Plus Dracula: near-black, blood red and a
 * purple glow. [dark] and [dracula] are Compose state, so screens redraw when the theme changes.
 */
object C {
    var dark by mutableStateOf(true)

    /** The Dracula theme (always dark). */
    var dracula by mutableStateOf(false)

    private fun pick(d: Long, l: Long, dr: Long = d) = Color(if (dracula) dr else if (dark) d else l)

    val Bg get() = pick(0xFF0B0C10, 0xFFEEF3FF, 0xFF0A0406)
    /** Slide-up sheets: between the page and its cards, so cards inside a sheet still stand out. */
    val Sheet get() = pick(0xFF121318, 0xFFF5F7FF, 0xFF110609)
    val Card get() = pick(0xFF1A1C23, 0xFFFFFFFF, 0xFF170A0E)
    val SurfaceLight get() = pick(0xFF262932, 0xFFE3E9FA, 0xFF261018)
    val Border get() = pick(0xFF383C47, 0xFFCDD6EE, 0xFF3D1621)
    val GlassBorder get() = pick(0x14FFFFFF, 0x14101A40, 0x2EFF2D55)

    /**
     * Brand fill (buttons, selected tiles, the +); [OnAccent] sits on it. By night it's a dark,
     * warm smoke with champagne on it (a watch dial, not a white slab); by day deep navy;
     * Dracula keeps its blood red. Buttons also get a hairline of [AccentText].
     */
    val Accent get() = pick(0xFF2A251B, 0xFF0E1638, 0xFFA3001B)
    val AccentGlow get() = pick(0xFF4A4130, 0xFF2A3566, 0xFF6A1B9A)
    val OnAccent get() = pick(0xFFEBD8AC, 0xFFFFFFFF, 0xFFFFFFFF)

    /** The signature colour for text, lines and thin icons: champagne by night, navy by day, Dracula's pink-red. */
    val AccentText get() = pick(0xFFE2CB97, 0xFF0E1638, 0xFFFF5C7A)

    /** The aurora itself, for the glow, the + button, the logo and the live trace. */
    val Aurora: List<Color> get() = when {
        dracula -> listOf(Color(0xFFA3001B), Color(0xFFD1123F), Color(0xFF6A1B9A))
        dark -> listOf(Color(0xFF45C8FF), Color(0xFF7B61FF), Color(0xFFD45CE0))
        else -> listOf(Color(0xFF12C982), Color(0xFF27B5F0), Color(0xFF6E5BFF))
    }

    val Text get() = pick(0xFFF2F3F5, 0xFF0E1638, 0xFFF6E9EC)
    val TextSecondary get() = pick(0xFFC4C7CF, 0xFF3C4670, 0xFFCDA6B0)
    val TextMuted get() = pick(0xFF9296A1, 0xFF5A6488, 0xFFA57E8A)

    // Range colours, checked with the dataviz palette validator against each theme's card colour
    // (contrast, colour-blind separation); they always come with a label too.
    val UrgentLow get() = pick(0xFFE0306E, 0xFFB0124F, 0xFFD6336C)
    val Low get() = pick(0xFFFF7F9C, 0xFFE5486E, 0xFFFF8FB0)
    val BelowTarget get() = pick(0xFF7EA4F2, 0xFF3D6AC2, 0xFF7FA7D9)
    // Dracula's own green: in range is never the violet the buttons use.
    val InRange get() = pick(0xFF22D18A, 0xFF0E9F63, 0xFF50FA7B)
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

/** A small section name inside a panel: mono, a little tracked (1.1). Sections themselves use RuleHeader. */
val SectionLabel: TextStyle get() = TextStyle(
    fontFamily = PlexMono, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = C.TextSecondary, letterSpacing = 0.08.em,
)

@Composable
fun DropiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (C.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = C.AccentText,
            onPrimary = C.OnAccent,
            background = C.Bg,
            surface = C.Card,
            onSurface = C.Text,
            onBackground = C.Text,
            surfaceVariant = C.SurfaceLight,
            onSurfaceVariant = C.TextSecondary,
            outline = C.Border,
        ),
        // 1.1: small, instrument-like corners on menus, dialogs and pickers too.
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
