package app.dropi.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

/** Widget colours for the app's theme setting; "system" follows the phone's day/night. */
private class Pal(context: Context) {
    private val theme = Prefs(context).theme

    fun cp(dark: Long, light: Long, dracula: Long = dark): ColorProvider = when (theme) {
        "light" -> ColorProvider(Color(light))
        "dark" -> ColorProvider(Color(dark))
        "dracula" -> ColorProvider(Color(dracula))
        else -> androidx.glance.color.ColorProvider(day = Color(light), night = Color(dark))
    }

    val bg = cp(0xF20E121C, 0xF5FFFFFF, 0xF2170A0E)
    val text = cp(0xFFEEF3FF, 0xFF0E1638, 0xFFF6E9EC)
    val text2 = cp(0xFFC9D3F0, 0xFF3C4670, 0xFFCDA6B0)
    val muted = cp(0xFF8B95B2, 0xFF5A6488, 0xFFA57E8A)
    val accent = cp(0xFF7CF5C4, 0xFF4B3BD6, 0xFFFF5C7A)
    val brand = cp(0xFF6E5BFF, 0xFF5B47F0, 0xFFA3001B)
    val white = ColorProvider(Color.White)

    fun status(s: Status): ColorProvider = when (s) {
        Status.URGENT_LOW -> cp(0xFFE0306E, 0xFFB0124F, 0xFFD6336C)
        Status.LOW -> cp(0xFFFF7F9C, 0xFFE5486E, 0xFFFF8FB0)
        Status.BELOW_TARGET -> cp(0xFF7EA4F2, 0xFF3D6AC2, 0xFF7FA7D9)
        Status.IN_RANGE -> cp(0xFF22D18A, 0xFF0E9F63, 0xFF9F8CFF)
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
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            return Data(
                latest = readings.lastOrNull(),
                next = Schedule.next(p.slots, readings, now, zone),
                unit = p.unit,
                targets = p.targets,
                clock = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH),
                zone = zone,
            )
        }
    }
}

/** The latest reading in its range colour, the next planned check, and a + to log. */
class ReadingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { Data.load(context) }
        val pal = Pal(context)
        provideContent { Content(context, data, pal) }
    }
}

class ReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingWidget()
}

private fun style(color: ColorProvider, size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight)

@Composable
private fun Content(context: Context, d: Data, p: Pal) {
    val wide = LocalSize.current.width >= 220.dp
    val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val log = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(Alerts.EXTRA_LOG_TAG, "new")
    val r = d.latest
    val status = r?.let { Glucose.status(it.mmol, it.tag, d.targets) }

    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(p.bg)
            .clickable(actionStartActivity(open))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(R.drawable.ic_stat_drop),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(p.accent),
                    modifier = GlanceModifier.size(14.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text("DROPI", style = style(p.muted, 11, FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Box(
                    GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(p.brand)
                        .clickable(actionStartActivity(log)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.lc_plus),
                        contentDescription = "Log reading",
                        colorFilter = ColorFilter.tint(p.white),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (r == null || status == null) {
                Text("No readings yet", style = style(p.text, 15, FontWeight.Bold))
                Text("Tap + to log one", style = style(p.text2, 12))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Glucose.format(r.mmol, d.unit), style = style(p.status(status), if (wide) 40 else 34, FontWeight.Bold))
                    Spacer(GlanceModifier.width(4.dp))
                    Text(d.unit.label, style = style(p.muted, 11, FontWeight.Medium), modifier = GlanceModifier.padding(bottom = 6.dp))
                }
                Text("${status.label} · ${d.time(r.at)}", style = style(p.text2, 12, FontWeight.Medium), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            val n = d.next
            Text(
                if (n != null) "Next: ${n.slot.name} ${d.time(n.at)}" else "No check times set",
                style = style(p.muted, 11),
                maxLines = 1,
            )
        }
    }
}
