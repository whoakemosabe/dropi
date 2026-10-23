package app.dropi.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.dropi.R
import app.dropi.data.Arrow
import app.dropi.data.Glucose
import app.dropi.data.SensorPoint
import app.dropi.data.Trend
import app.dropi.work.Alerts

private const val MIN = 60_000L

/** The colour for a sensor value, on the fixed consensus bands (sensors have no meal moment). */
fun sensorColor(mmol: Double): Color = when {
    mmol < Glucose.URGENT_LOW -> C.UrgentLow
    mmol < Glucose.LOW -> C.Low
    mmol > Glucose.VERY_HIGH -> C.VeryHigh
    mmol > Glucose.TIR_HIGH -> C.High
    else -> C.InRange
}

fun sensorWords(mmol: Double): String = when {
    mmol < Glucose.URGENT_LOW -> "urgent low"
    mmol < Glucose.LOW -> "low"
    mmol > Glucose.VERY_HIGH -> "very high"
    mmol > Glucose.TIR_HIGH -> "above range"
    else -> "in range"
}

/**
 * The home screen in live mode: the sensor's number set big and left, an arrow tilted to the
 * actual rate of change (not just seven fixed directions), and the last three hours drawn as one
 * line running into a dotted look 20 minutes ahead.
 */
@Composable
fun LiveHero(state: AppState, now: Long, fmt: Fmt) {
    val points = state.sensor
    val last = Trend.latest(points) ?: return
    val slope = Trend.slope(points)
    val arrow = slope?.let { Arrow.of(it) }
    val color = sensorColor(last.mmol)
    val value = Glucose.format(last.mmol, state.unit)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "Live from your sensor: $value ${state.unit.label}, ${arrow?.words ?: "no trend yet"}, " +
                    "${sensorWords(last.mmol)}, ${fmt.ago(last.at, now)}"
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.clip(CircleShape).tap { state.liveOpen = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(color)
                Spacer(Modifier.width(8.dp))
                Text("Live from your sensor · ${fmt.ago(last.at, now)}", style = inter(14.sp, FontWeight.SemiBold, C.TextSecondary))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A small settle each time a new value lands.
                val pop = remember { Animatable(1f) }
                LaunchedEffect(last.at) {
                    pop.snapTo(0.92f)
                    pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
                }
                Text(
                    value,
                    style = numeral(if (state.unit == app.dropi.data.GlucoseUnit.MGDL) 116.sp else 136.sp).copy(lineHeight = 140.sp),
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
                if (slope != null) {
                    Spacer(Modifier.width(4.dp))
                    val tilt = remember { Animatable(0f) }
                    LaunchedEffect(slope) { tilt.animateTo(Trend.degrees(slope), spring(dampingRatio = 0.7f, stiffness = 200f)) }
                    TrendArrow(C.Text, tilt.value, double = arrow == Arrow.UP_FAST || arrow == Arrow.DOWN_FAST)
                }
            }
            Text(
                listOfNotNull(state.unit.label, sensorWords(last.mmol), arrow?.words).joinToString(" · "),
                style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary),
            )
        }
        Spacer(Modifier.height(12.dp))
        RangeRuler(last.mmol, state.unit)
        Spacer(Modifier.height(8.dp))
        // The last three hours in aurora colours, running into a dotted 20 minutes ahead.
        Column(Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            val ahead = Trend.ahead(points, 20)
            Trace(points.filter { it.at >= now - 3 * 60 * MIN }, ahead, now, Modifier.fillMaxWidth().height(116.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("3 h ago", style = inter(11.sp, color = C.TextMuted), modifier = Modifier.weight(1f))
                if (ahead != null) {
                    Text("In 20 min, about ${Glucose.format(ahead, state.unit)}", style = inter(11.sp, FontWeight.Medium, C.TextSecondary))
                } else {
                    Text("Now", style = inter(11.sp, color = C.TextMuted))
                }
            }
        }
    }
}

/**
 * The trend arrow, drawn as heavy as the numeral beside it and tilted to the real rate of change.
 * Fast changes get a second chevron.
 */
@Composable
private fun TrendArrow(color: Color, degrees: Float, double: Boolean) {
    Canvas(Modifier.size(52.dp).rotate(-degrees)) {
        val w = 5.5.dp.toPx()
        val cy = size.height / 2
        val tip = size.width * 0.86f
        val head = size.width * 0.3f
        val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(size.width * 0.12f, cy), Offset(tip - w * 0.4f, cy), strokeWidth = w, cap = StrokeCap.Round)
        val chevron = Path().apply {
            moveTo(tip - head, cy - head)
            lineTo(tip, cy)
            lineTo(tip - head, cy + head)
        }
        drawPath(chevron, color, style = stroke)
        if (double) {
            val back = Path().apply {
                moveTo(tip - head * 1.9f, cy - head)
                lineTo(tip - head * 0.9f, cy)
                lineTo(tip - head * 1.9f, cy + head)
            }
            drawPath(back, color.copy(alpha = 0.5f), style = stroke)
        }
    }
}

/** A dot that breathes while data is live. */
@Composable
private fun LiveDot(color: Color) {
    val t = rememberInfiniteTransition(label = "live")
    val r by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    Canvas(Modifier.size(12.dp)) {
        val c = center
        drawCircle(color.copy(alpha = (1f - r) * 0.5f), radius = size.minDimension / 2 * (0.5f + r * 0.5f), center = c)
        drawCircle(color, radius = size.minDimension / 4, center = c)
    }
}

/** The line: three hours to now, the target band behind it, then 20 minutes of dotted guess. */
@Composable
private fun Trace(points: List<SensorPoint>, ahead: Double?, now: Long, modifier: Modifier) {
    val band = C.InRange
    val lowC = C.Low
    val line = C.Text
    val grid = C.Border
    val cardC = C.Card
    val aurora = C.Aurora
    val from = now - 3 * 60 * MIN
    val to = now + 25 * MIN
    val lo = minOf(2.8, (points.minOfOrNull { it.mmol } ?: 4.0) - 0.6, ahead?.minus(0.4) ?: 99.0)
    val hi = maxOf(12.0, (points.maxOfOrNull { it.mmol } ?: 8.0) + 1.0, ahead?.plus(0.6) ?: 0.0)
    Canvas(modifier) {
        fun x(t: Long) = ((t - from).toFloat() / (to - from)) * size.width
        fun y(v: Double) = (1f - ((v - lo) / (hi - lo)).toFloat()) * size.height
        // Time in range as a soft band, the low line dashed.
        drawRect(band.copy(alpha = 0.08f), Offset(0f, y(Glucose.TIR_HIGH)), androidx.compose.ui.geometry.Size(size.width, y(Glucose.LOW) - y(Glucose.TIR_HIGH)))
        drawLine(
            lowC.copy(alpha = 0.55f), Offset(0f, y(Glucose.LOW)), Offset(size.width, y(Glucose.LOW)),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        // "Now".
        drawLine(grid, Offset(x(now), 0f), Offset(x(now), size.height), strokeWidth = 1.dp.toPx())
        if (points.isEmpty()) return@Canvas
        val path = Path()
        var prev: SensorPoint? = null
        for (p in points.sortedBy { it.at }) {
            // A gap of over 15 minutes breaks the line rather than inventing what happened.
            if (prev == null || p.at - prev.at > 15 * MIN) path.moveTo(x(p.at), y(p.mmol)) else path.lineTo(x(p.at), y(p.mmol))
            prev = p
        }
        drawPath(path, Brush.horizontalGradient(aurora), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = prev!!
        if (ahead != null) {
            drawLine(
                line.copy(alpha = 0.7f), Offset(x(last.at), y(last.mmol)), Offset(x(last.at + 20 * MIN), y(ahead)),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 6.dp.toPx())),
            )
        }
        drawCircle(line.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(x(last.at), y(last.mmol)))
        drawCircle(cardC, radius = 7.dp.toPx(), center = Offset(x(last.at), y(last.mmol)))
        drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(last.at), y(last.mmol)))
    }
}

/** Live mode is on but nothing recent has arrived: one quiet line, tap for setup. */
@Composable
fun LiveWaiting(state: AppState, now: Long, fmt: Fmt) {
    val last = Trend.latest(state.sensor)
    Row(
        Modifier.fillMaxWidth().card().tap { state.liveOpen = true }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_activity, C.TextMuted, 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (last == null) "Live mode is on. Waiting for the first value from your sensor."
            else "Last sensor value came ${fmt.ago(last.at, now)}. Check your CGM app is still sending.",
            style = inter(13.sp, color = C.TextSecondary),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = inter(15.sp, FontWeight.SemiBold))
            if (sub != null) Text(sub, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp))
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                Haptics.toggle(view, it)
                onChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.OnAccent),
        )
    }
}

@Composable
fun LiveSheet(state: AppState) {
    if (!state.liveOpen) return
    SlideSheet(onDismiss = { state.liveOpen = false }) { close -> LiveContent(state, close) }
}

@Composable
private fun ColumnScope.LiveContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow(30_000)
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    var canNotify by remember { mutableStateOf(Alerts.canNotify(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = Alerts.canNotify(context) }
    fun needNotify() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    StickySheetBody("Live sensor", close) {
        SheetSection("Live mode") {
            val last = Trend.latest(state.sensor)
            ToggleRow(
                "Use my sensor's values",
                when {
                    !state.liveOn -> "For Dexcom, Libre and other CGMs, through xDrip+, Juggluco or Health Connect"
                    last == null -> "On. Waiting for the first value"
                    else -> "Last value ${Glucose.format(last.mmol, state.unit)}, ${fmt.ago(last.at, now)}"
                },
                state.liveOn,
            ) { on ->
                state.changeLive(on)
                if (on) needNotify()
            }
        }

        SheetSection("Connect your sensor") {
            Step("xDrip+", "Settings › Inter-app settings › Broadcast locally: on. In Identify receiver, add app.dropi.")
            Step("Juggluco", "Settings › xDrip+ broadcast: on, and add app.dropi as a receiver.")
            Step("Health Connect", "If your CGM app saves to Health Connect, switch on sync in Settings. Values arrive less often this way.")
            Chip("Copy app.dropi", icon = R.drawable.lc_share) {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("Dropi package", context.packageName))
            }
        }

        SheetSection("Notifications") {
            ToggleRow(
                "Live notification",
                "Your value and arrow, kept up to date. On Android 16 it shows as a chip in the status bar.",
                state.liveNotify,
                enabled = state.liveOn,
            ) { on ->
                state.changeLiveNotify(on)
                if (on) needNotify()
            }
            ToggleRow(
                "Heads-up before a low",
                "When the trend points under ${Glucose.format(Glucose.LOW, state.unit)} within 20 minutes, or you're already under it.",
                state.headsUpOn,
                enabled = state.liveOn,
            ) { on ->
                state.changeHeadsUp(on)
                if (on) needNotify()
            }
            if (state.liveOn && !canNotify) {
                SheetHint("Notifications are off for Dropi, so these can't show.")
                Chip("Open notification settings", icon = R.drawable.lc_settings) {
                    try {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } catch (e: Exception) { }
                }
            }
            SheetHint(
                "Dropi's heads-up is an estimate from the sensor trend, not a medical alarm. Keep your CGM app's own alarms on."
            )
        }
    }
}

@Composable
private fun Step(name: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = inter(14.sp, FontWeight.SemiBold))
        Text(text, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp))
    }
}
