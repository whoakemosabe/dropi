package app.dropi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
 * Dropi's palette: ink and paper neutrals, and the brand itself is just ink (paper-white on the
 * dark theme), so colour belongs to the numbers: the range colours are the only hues on screen.
 * Plus Dracula: near-black, blood red and a purple glow. Range colours carry the meaning
 * (pink-red low, teal in range, amber high; violet in range under Dracula, so it never blurs
 * into the reds) and every text colour clears 4.5:1 on cards. [dark] and [dracula] are Compose
 * state, so screens redraw when the theme changes.
 */
object C {
    var dark by mutableStateOf(true)

    /** The Dracula theme (always dark). */
    var dracula by mutableStateOf(false)

    private fun pick(d: Long, l: Long, dr: Long = d) = Color(if (dracula) dr else if (dark) d else l)

    val Bg get() = pick(0xFF0D0F14, 0xFFF3F4F8, 0xFF0A0406)
    val Card get() = pick(0xFF161922, 0xFFFFFFFF, 0xFF170A0E)
    val SurfaceLight get() = pick(0xFF1F2330, 0xFFE8EAF1, 0xFF261018)
    val Border get() = pick(0xFF2A2F3D, 0xFFD8DBE5, 0xFF3D1621)
    val GlassBorder get() = pick(0x14FFFFFF, 0x1411131A, 0x2EFF2D55)

    /** Brand fill (buttons, the + button, selected tiles); [OnAccent] sits on it. */
    val Accent get() = pick(0xFFE9ECF2, 0xFF15171D, 0xFFA3001B)
    val AccentGlow get() = pick(0xFFFFFFFF, 0xFF30343E, 0xFF6A1B9A)
    val OnAccent get() = pick(0xFF0D0F14, 0xFFFFFFFF, 0xFFFFFFFF)

    /** Brand colour as text or a thin icon. */
    val AccentText get() = pick(0xFFF3F4F8, 0xFF11131A, 0xFFFF5C7A)

    val Text get() = pick(0xFFF3F4F8, 0xFF11131A, 0xFFF6E9EC)
    val TextSecondary get() = pick(0xFFA6ACBD, 0xFF474D5E, 0xFFCDA6B0)
    val TextMuted get() = pick(0xFF8A90A3, 0xFF5E6477, 0xFFA57E8A)

    // Range colours, checked with the dataviz palette validator against each theme's card colour:
    // they stay apart for colour-blind readers too, and always come with a label.
    val UrgentLow get() = pick(0xFFD6336C, 0xFFA3134A, 0xFFD6336C)
    val Low get() = pick(0xFFFF85A8, 0xFFE04A78, 0xFFFF8FB0)
    val BelowTarget get() = pick(0xFF7FA7D9, 0xFF3D6A9E, 0xFF7FA7D9)
    val InRange get() = pick(0xFF22C3C9, 0xFF00939A, 0xFF9F8CFF)
    val High get() = pick(0xFFF4C542, 0xFFB08000, 0xFFF2C94C)
    val VeryHigh get() = pick(0xFFE8622C, 0xFFC23A0A, 0xFFE8622C)

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

/** A big number: Bricolage display, tight, tabular so live values don't jiggle. */
fun numeral(size: TextUnit, color: Color = C.Text) = TextStyle(
    fontFamily = BricolageDisplay, fontSize = size, fontWeight = FontWeight.ExtraBold, color = color,
    letterSpacing = (-0.02).em, fontFeatureSettings = "tnum",
)

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
