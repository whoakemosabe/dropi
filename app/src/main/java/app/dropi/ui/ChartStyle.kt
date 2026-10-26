package app.dropi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One chart style for every chart in the app (Trends, Today, the meal screen, live mode), from the
 * "Dropi chart style" canvas. The rule: colour means range only (green in range, amber high, pink
 * low, blue below target). Everything else is ink, grey or champagne.
 *
 * Draw with these tokens and helpers rather than picking colours or widths in a chart, so a new
 * chart matches the others without thinking about it.
 */
object Chart {
    // ---- Lines ----
    /** The main line: readings joined, the typical day's middle value, a meal's expected curve. */
    val ink: Color get() = C.TextSecondary
    val lineW = 2.dp

    /** Sensor values: a thin light line, so the dots (which carry the colour) stay on top. */
    val sensor: Color get() = C.Text.copy(alpha = 0.55f)
    val sensorW = 1.25.dp

    /** Comparisons (last week, a week row's days): muted, thin, dashed. */
    val faint: Color get() = C.TextMuted
    val faintW = 1.dp
    fun faintDash(d: DrawScope) = with(d) { PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) }

    /** Meals: the square at the time eaten and the dashed line up to the peak. */
    val meal: Color get() = C.AccentText
    val mealRise: Color get() = C.AccentText.copy(alpha = 0.6f)
    val mealRiseW = 1.5.dp
    fun mealDash(d: DrawScope) = with(d) { PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) }

    /** "Now" on a time axis. */
    val now: Color get() = C.AccentText

    // ---- Ground ----
    /** In range, 3.9–10: the same soft green on every chart, with hairlines at both edges. */
    val band: Color get() = C.InRange.copy(alpha = 0.10f)
    val rule: Color get() = C.Border

    /** A spread of values (typical day): ink, darker for half the readings, lighter for 8 in 10. */
    val spreadHalf: Color get() = C.Text.copy(alpha = 0.14f)
    val spreadMost: Color get() = C.Text.copy(alpha = 0.07f)

    /** Outside the stretch you opened (the evening before a day): the page laid over it. */
    val outside: Color get() = C.Bg.copy(alpha = 0.55f)
    val outsideEdge: Color get() = C.TextSecondary.copy(alpha = 0.6f)

    // ---- Marks ----
    /** A reading's dot, coloured by range, with a ring of the page around it. */
    val dot = 4.dp
    val dotRing = 2.dp
    /** In the small day rows. */
    val rowDot = 3.dp
    val rowRing = 1.5.dp

    // ---- Labels ----
    /** Axis labels: Plex Mono 12, muted; midnight and day names one step brighter. */
    val label: TextStyle get() = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = C.TextMuted)
    val labelStrong: TextStyle get() = label.copy(color = C.TextSecondary)

    // ---- Drawing helpers ----
    fun DrawScope.line(): Stroke = Stroke(lineW.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun DrawScope.sensorLine(): Stroke = Stroke(sensorW.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun DrawScope.faintLine(): Stroke = Stroke(faintW.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = faintDash(this))

    /** The in-range band between [yHigh] (10.0) and [yLow] (3.9), with its two hairlines. */
    fun DrawScope.rangeGround(left: Float, right: Float, yHigh: Float, yLow: Float) {
        drawRect(band, Offset(left, yHigh), Size(right - left, yLow - yHigh))
        val w = 1.dp.toPx()
        drawLine(rule, Offset(left, yHigh), Offset(right, yHigh), strokeWidth = w)
        drawLine(rule, Offset(left, yLow), Offset(right, yLow), strokeWidth = w)
    }

    /** A reading: its range colour inside a ring of [ring] colour (the page or card behind it). */
    fun DrawScope.readingDot(color: Color, center: Offset, ring: Color, small: Boolean = false) {
        val r = (if (small) rowDot else dot).toPx()
        drawCircle(ring, r + (if (small) rowRing else dotRing).toPx(), center)
        drawCircle(color, r, center)
    }

    /** The ring around a tapped dot. */
    fun DrawScope.tappedRing(center: Offset) {
        drawCircle(C.TextSecondary, (dot + 3.dp).toPx(), center, style = Stroke(2.dp.toPx()))
    }

    /** A meal at the time eaten: a 10 dp champagne square. */
    fun DrawScope.mealMark(x: Float, bottom: Float, alpha: Float = 1f) {
        val s = 10.dp.toPx()
        drawRoundRect(meal.copy(alpha = meal.alpha * alpha), Offset(x - s / 2, bottom - s - 2.dp.toPx()), Size(s, s), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
    }
}
