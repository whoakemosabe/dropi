package app.dropi.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.dropi.MainActivity
import app.dropi.Prefs
import app.dropi.R
import app.dropi.data.Check
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.Reading
import app.dropi.data.Schedule
import app.dropi.data.Status
import app.dropi.tr
import app.dropi.work.Alerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Widgets {
    suspend fun updateAll(context: Context) {
        ReadingWidget().updateAll(context)
    }
}

/**
 * Widget colours for the app's theme setting; "system" follows the phone's day/night. Solid
 * (no see-through) so the wallpaper never sits behind the numbers.
 */
private class Pal(context: Context) {
    private val theme = Prefs(context).theme

    fun cp(dark: Long, light: Long, dracula: Long = dark): ColorProvider = when (theme) {
        "light" -> ColorProvider(Color(light))
        "dark" -> ColorProvider(Color(dark))
        "dracula" -> ColorProvider(Color(dracula))
        else -> androidx.glance.color.ColorProvider(day = Color(light), night = Color(dark))
    }

    val bg = cp(0xFF1A1C23, 0xFFFFFFFF, 0xFF170A0E)
    val surface = cp(0xFF262932, 0xFFE9EEFB, 0xFF261018)
    val text = cp(0xFFF2F3F5, 0xFF0E1638, 0xFFF6E9EC)
    val text2 = cp(0xFFC4C7CF, 0xFF3C4670, 0xFFCDA6B0)
    val muted = cp(0xFF9296A1, 0xFF5A6488, 0xFFA57E8A)
    val accent = cp(0xFFB3A5FF, 0xFF4B3BD6, 0xFFFF5C7A)
    val brand = cp(0xFF6347F0, 0xFF5B47F0, 0xFFA3001B)
    val white = ColorProvider(Color.White)

    fun status(s: Status): ColorProvider = when (s) {
        Status.URGENT_LOW -> cp(0xFFE0306E, 0xFFB0124F, 0xFFD6336C)
        Status.LOW -> cp(0xFFFF7F9C, 0xFFE5486E, 0xFFFF8FB0)
        Status.BELOW_TARGET -> cp(0xFF7EA4F2, 0xFF3D6AC2, 0xFF7FA7D9)
        Status.IN_RANGE -> cp(0xFF22D18A, 0xFF0E9F63, 0xFF50FA7B)
        Status.HIGH -> cp(0xFFF2B632, 0xFFA67C00, 0xFFF2C94C)
        Status.VERY_HIGH -> cp(0xFFF06A2A, 0xFFD9480F, 0xFFE8622C)
    }
}

private class Data(
    val latest: Reading?,
    val next: Check?,
    val unit: GlucoseUnit,
    val targets: app.dropi.data.Targets,
    val clock: DateTimeFormatter,
    val zone: ZoneId,
) {
    fun time(at: Long): String = clock.format(Instant.ofEpochMilli(at).atZone(zone))

    companion object {
        suspend fun load(context: Context): Data {
            val p = Prefs(context)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val readings = try { Db.get(context).since(now - 30 * 86_400_000L) } catch (e: Exception) { emptyList() }
            val meals = try { Db.get(context).mealsSince(now - 86_400_000L) } catch (e: Exception) { emptyList() }
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            return Data(
                latest = readings.lastOrNull(),
                next = Schedule.next(p.slots, readings, now, zone, p.afterMealMinutes, meals),
                unit = p.unit,
                targets = p.targets,
                clock = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH),
                zone = zone,
            )
        }
    }
}

/**
 * The latest reading, big and in plain text colour, with its range as a coloured bar and a word;
 * the next check; and buttons to log a reading or snap a meal. Three layouts by size, so
 * nothing is squeezed: a slim row, a square, and a wide card.
 */
class ReadingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SLIM, SQUARE, TALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { Data.load(context) }
        val pal = Pal(context)
        provideContent { Content(context, data, pal) }
    }

    companion object {
        val SLIM = DpSize(180.dp, 56.dp)
        val SQUARE = DpSize(130.dp, 120.dp)
        val TALL = DpSize(130.dp, 180.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

class ReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingWidget()
}

private fun style(color: ColorProvider, size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight)

private fun intent(context: Context, extra: String, value: String) = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    .putExtra(extra, value)

@Composable
private fun Content(context: Context, d: Data, p: Pal) {
    val size = LocalSize.current
    val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(p.bg)
            .clickable(actionStartActivity(open))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when {
            size.height < 100.dp -> Slim(context, d, p)
            size.width >= 230.dp -> Wide(context, d, p)
            else -> Square(context, d, p, showNext = size.height >= 170.dp, short = size.height < 140.dp)
        }
    }
}

/** The number and its unit, then the range bar and word with the time. */
@Composable
private fun Value(d: Data, p: Pal, big: Int) {
    val r = d.latest
    val status = r?.let { Glucose.status(it.mmol, it.tag, d.targets) }
    if (r == null || status == null) {
        Text(tr("No readings yet"), style = style(p.text, 16, FontWeight.Bold), maxLines = 1)
        Text(tr("Tap + to log one"), style = style(p.text2, 13), maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Glucose.format(r.mmol, d.unit), style = style(p.text, big, FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.width(4.dp))
        Text(d.unit.label, style = style(p.text2, 12, FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.padding(bottom = (big / 6).dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(width = 4.dp, height = 14.dp).cornerRadius(2.dp).background(p.status(status))) {}
        Spacer(GlanceModifier.width(6.dp))
        Text("${status.label} · ${d.time(r.at)}", style = style(p.text2, 13, FontWeight.Medium), maxLines = 1)
    }
}

/** A round button with an icon (and, when there's room, a word). */
@Composable
private fun Action(icon: Int, label: String, filled: Boolean, p: Pal, intent: Intent, showLabel: Boolean, modifier: GlanceModifier, tall: Int = 44) {
    Row(
        modifier
            .height(tall.dp)
            .cornerRadius((tall / 2).dp)
            .background(if (filled) p.brand else p.surface)
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = label,
            colorFilter = ColorFilter.tint(if (filled) p.white else p.text),
            modifier = GlanceModifier.size(20.dp),
        )
        if (showLabel) {
            Spacer(GlanceModifier.width(6.dp))
            Text(label, style = style(if (filled) p.white else p.text, 14, FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun Slim(context: Context, d: Data, p: Pal) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) { Value(d, p, 26) }
        Spacer(GlanceModifier.width(8.dp))
        Action(R.drawable.lc_plus, tr("Log reading"), true, p, intent(context, Alerts.EXTRA_LOG_TAG, "new"), false, GlanceModifier.width(44.dp))
    }
}

@Composable
private fun Square(context: Context, d: Data, p: Pal, showNext: Boolean, short: Boolean) {
    Column(GlanceModifier.fillMaxSize()) {
        Value(d, p, if (short) 30 else 38)
        if (showNext) {
            Spacer(GlanceModifier.height(6.dp))
            Next(d, p)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            val h = if (short) 40 else 44
            Action(R.drawable.lc_plus, tr("Log reading"), true, p, intent(context, Alerts.EXTRA_LOG_TAG, "new"), false, GlanceModifier.defaultWeight(), h)
            Spacer(GlanceModifier.width(8.dp))
            Action(R.drawable.lc_camera, tr("Snap a meal"), false, p, intent(context, Alerts.EXTRA_OPEN_MEAL, "camera"), false, GlanceModifier.defaultWeight(), h)
        }
    }
}

@Composable
private fun Wide(context: Context, d: Data, p: Pal) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Value(d, p, 42)
            Spacer(GlanceModifier.height(4.dp))
            Next(d, p)
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.width(118.dp)) {
            Action(R.drawable.lc_plus, tr("Reading"), true, p, intent(context, Alerts.EXTRA_LOG_TAG, "new"), true, GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(8.dp))
            Action(R.drawable.lc_camera, tr("Meal"), false, p, intent(context, Alerts.EXTRA_OPEN_MEAL, "camera"), true, GlanceModifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Next(d: Data, p: Pal) {
    val n = d.next
    Text(
        // Slot.name is built in English ("Before lunch"); the lookup shows the known names in the app's language.
        if (n != null) tr("Next: {0} {1}", tr(n.slot.name), d.time(n.at)) else tr("No check times set"),
        style = style(p.muted, 12),
        maxLines = 1,
    )
}
