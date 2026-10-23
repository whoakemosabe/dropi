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
 * Dropi's palette: calm ink and paper neutrals with a periwinkle brand colour, so it suits
 * anyone. Range colours carry the meaning (red low, green in range, amber high) and every text
 * colour clears 4.5:1 on cards in both themes. [dark] is Compose state, so screens redraw.
 */
object C {
    var dark by mutableStateOf(true)

    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    val Bg get() = pick(0xFF0D0F14, 0xFFF3F4F8)
    val Card get() = pick(0xFF161922, 0xFFFFFFFF)
    val SurfaceLight get() = pick(0xFF1F2330, 0xFFE8EAF1)
    val Border get() = pick(0xFF2A2F3D, 0xFFD8DBE5)
    val GlassBorder get() = pick(0x14FFFFFF, 0x1411131A)

    /** Brand fill (buttons, the log button, selected chips): white text sits on it. */
    val Accent get() = Color(0xFF4F5BEA)
    val AccentGlow get() = Color(0xFF7A86FF)

    /** Brand colour as text or a thin icon. */
    val AccentText get() = pick(0xFF9AA5FF, 0xFF3B47C9)

    val Text get() = pick(0xFFF3F4F8, 0xFF11131A)
    val TextSecondary get() = pick(0xFFA6ACBD, 0xFF474D5E)
    val TextMuted get() = pick(0xFF8A90A3, 0xFF5E6477)

    val UrgentLow get() = pick(0xFFFF5A6A, 0xFFC4162A)
    val Low get() = pick(0xFFFF8A94, 0xFFD0263A)
    val BelowTarget get() = pick(0xFF5CC8FF, 0xFF0B6FA8)
    val InRange get() = pick(0xFF3DDC97, 0xFF0A7A4B)
    val High get() = pick(0xFFFFC24B, 0xFF8F5B00)
    val VeryHigh get() = pick(0xFFFF9248, 0xFFB34700)

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

/** Inter with tabular figures, so numbers don't jiggle as they change. Tracking is in em. */
fun inter(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = C.Text, tracking: Float = 0f) =
    TextStyle(
        fontFamily = Inter, fontSize = size, fontWeight = weight, color = color, letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )

/** Small uppercase section label. */
val SectionLabel: TextStyle get() = inter(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f)

@Composable
fun DropiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (C.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = C.Accent,
            onPrimary = Color.White,
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
