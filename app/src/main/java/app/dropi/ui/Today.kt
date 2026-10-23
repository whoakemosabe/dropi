package app.dropi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Glucose
import app.dropi.data.Reading
import app.dropi.data.Stats
import app.dropi.data.Status
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/** The home screen: the latest reading on a range gauge, today's numbers, and the log. */
@Composable
fun TodayPage(state: AppState) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    val zone = state.zone
    val fmt = remember(zone) { Fmt(context, zone) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val all = state.readings
    val todays = Stats.onDay(all, today, zone)
    val summary = Stats.summary(todays)
    val streak = Stats.streak(all, today, zone)
    val latest = all.lastOrNull()
    val earlier = all.filter { it.date(zone) != today }.takeLast(30).reversed()

    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = bars.calculateTopPadding() + 8.dp,
            // Room for the floating log button over the navigation bar.
            bottom = bars.calculateBottomPadding() + 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(fmt.longDate(today)) { state.settingsOpen = true } }
        item(key = "banner") {
            Column {
                UpdateBanner()
                CrashCard(state)
            }
        }
        item(key = "hero") { Hero(state, latest, fmt, now, streak) }
        item(key = "stats") { StatRow(state, summary) }
        item(key = "today-label") {
            Text("TODAY", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        if (todays.isEmpty()) {
            item(key = "today-empty") {
                Text(
                    if (all.isEmpty()) "Your readings will appear here. Tap Log reading to add your first one."
                    else "Nothing logged yet today.",
                    style = inter(14.sp, color = C.TextSecondary),
                    modifier = Modifier.fillMaxWidth().card().padding(18.dp),
                )
            }
        } else {
            items(todays.reversed(), key = { "r${it.id}" }) { r -> ReadingRow(state, r, fmt.time(r.at)) }
        }
        if (earlier.isNotEmpty()) {
            item(key = "earlier-label") {
                Text("EARLIER", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            items(earlier, key = { "r${it.id}" }) { r -> ReadingRow(state, r, fmt.dayTime(r.at, now)) }
        }
        item(key = "footer") {
            Text(
                "Dropi is a log, not a medical device. Follow the plan you agreed with your care team.",
                style = inter(11.sp, color = C.TextMuted),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Header(date: String, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(C.Accent),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_droplet, Color.White, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Dropi", style = inter(24.sp, FontWeight.ExtraBold, C.Text, -0.02f))
            Text(date, style = inter(13.sp, FontWeight.Medium, C.TextMuted))
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(C.SurfaceLight).tap(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_settings, C.TextSecondary, 20.dp, contentDescription = "Settings") }
    }
}

/** The latest reading in the middle of a gauge coloured by range: low, in range, high, very high. */
@Composable
private fun Hero(state: AppState, latest: Reading?, fmt: Fmt, now: Long, streak: Int) {
    Column(
        Modifier.fillMaxWidth().card().padding(vertical = 22.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val status = latest?.let { state.status(it) }
        val color = status?.let { C.status(it) } ?: C.TextMuted
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Gauge(latest?.mmol, color, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (latest == null) {
                    Ic(R.drawable.lc_droplet, C.AccentText, 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("No readings yet", style = inter(15.sp, FontWeight.SemiBold, C.TextSecondary))
                } else {
                    // A gentle pop each time a new number lands.
                    val pop = remember { Animatable(1f) }
                    LaunchedEffect(latest.id) {
                        pop.snapTo(0.86f)
                        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
                    }
                    Text(
                        Glucose.format(latest.mmol, state.unit),
                        style = inter(58.sp, FontWeight.ExtraBold, C.Text, -0.03f),
                        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                    )
                    Text(state.unit.label, style = inter(13.sp, FontWeight.SemiBold, C.TextMuted))
                    Spacer(Modifier.height(8.dp))
                    StatusPill(status!!)
                }
            }
        }
        if (latest != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(latest.tag.icon(), C.TextMuted, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text("${latest.tag.label} · ${fmt.ago(latest.at, now)}", style = inter(13.sp, FontWeight.Medium, C.TextSecondary))
            }
        }
        if (streak >= 2) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(C.High.copy(alpha = 0.12f))
                    .border(1.dp, C.High.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_flame, C.High, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text("$streak-day logging streak", style = inter(12.sp, FontWeight.SemiBold, C.High))
            }
        }
    }
}

@Composable
fun StatusPill(status: Status) {
    val c = C.status(status)
    Text(
        status.label,
        style = inter(12.sp, FontWeight.Bold, c),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * A 270° gauge from 2 to 20 mmol/L with the range bands drawn faintly, and a bright marker at
 * the value that glides to each new reading.
 */
@Composable
private fun Gauge(mmol: Double?, color: Color, modifier: Modifier) {
    val lo = 2.0
    val hi = 20.0
    fun frac(v: Double) = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    val target = mmol?.let { frac(it) }
    val pos by animateFloatAsState(target ?: 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "gauge")
    val appear by animateFloatAsState(if (target != null) 1f else 0f, tween(400), label = "marker")
    val bands = listOf(
        Triple(lo, Glucose.LOW, C.Low),
        Triple(Glucose.LOW, Glucose.TIR_HIGH, C.InRange),
        Triple(Glucose.TIR_HIGH, Glucose.VERY_HIGH, C.High),
        Triple(Glucose.VERY_HIGH, hi, C.VeryHigh),
    )
    val track = C.SurfaceLight
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2 + 8.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        val start = 135f
        val sweep = 270f
        drawArc(track, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        // Range bands, with a hair of space between them.
        val gap = 1.5f
        bands.forEach { (a, b, c) ->
            val s = start + sweep * frac(a)
            val e = start + sweep * frac(b)
            drawArc(c.copy(alpha = 0.32f), s + gap, (e - s - gap * 2).coerceAtLeast(0f), false, topLeft, arcSize, style = Stroke(stroke))
        }
        // The filled part up to the reading, in its colour.
        if (appear > 0f) {
            drawArc(
                color.copy(alpha = 0.9f * appear), start, sweep * pos, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians((start + sweep * pos).toDouble())
            val r = arcSize.width / 2
            val c = Offset(size.width / 2 + (r * cos(angle)).toFloat(), size.height / 2 + (r * sin(angle)).toFloat())
            drawCircle(color.copy(alpha = 0.25f * appear), radius = stroke * 1.15f, center = c)
            drawCircle(Color.White.copy(alpha = appear), radius = stroke * 0.42f, center = c)
        }
    }
}

@Composable
private fun StatRow(state: AppState, s: app.dropi.data.Summary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Average", s.average?.let { Glucose.format(it, state.unit) } ?: "—", state.unit.label, Modifier.weight(1f))
        StatTile(
            "In range", s.inRangePct?.let { "$it%" } ?: "—", if (state.unit == app.dropi.data.GlucoseUnit.MGDL) "70–180" else "3.9–10.0",
            Modifier.weight(1f),
            valueColor = s.inRangePct?.let { if (it >= 70) C.InRange else if (it >= 50) C.High else C.Low },
        )
        StatTile("Readings", "${s.count}", if (s.lows > 0) "${s.lows} low" else "today", Modifier.weight(1f), subColor = if (s.lows > 0) C.Low else null)
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier,
    valueColor: Color? = null,
    subColor: Color? = null,
) {
    Column(modifier.card(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 14.dp)) {
        Text(label.uppercase(), style = SectionLabel, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, style = inter(22.sp, FontWeight.ExtraBold, valueColor ?: C.Text), maxLines = 1)
        Text(sub, style = inter(11.sp, FontWeight.Medium, subColor ?: C.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReadingRow(state: AppState, r: Reading, time: String) {
    val status = state.status(r)
    val c = C.status(status)
    Row(
        Modifier
            .fillMaxWidth()
            .card(RoundedCornerShape(18.dp))
            .tap { state.editing = r }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(RoundedCornerShape(50)).background(c))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(r.tag.icon(), C.TextMuted, 13.dp)
                Spacer(Modifier.width(6.dp))
                Text(r.tag.label, style = inter(14.sp, FontWeight.SemiBold, C.Text))
            }
            Text(
                if (r.note.isNotBlank()) "$time · ${r.note}" else time,
                style = inter(12.sp, color = C.TextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Glucose.format(r.mmol, state.unit), style = inter(20.sp, FontWeight.ExtraBold, c))
            Text(status.label, style = inter(11.sp, FontWeight.Medium, C.TextMuted))
        }
    }
}

/** After a crash: what went wrong, with a Copy button so it can be pasted to Claude. */
@Composable
fun CrashCard(state: AppState) {
    val text = state.lastCrash ?: return
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .border(1.dp, C.Low.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Dropi crashed last time", style = inter(14.sp, FontWeight.Bold, C.Low))
        Text(
            text.lines().filter { it.isNotBlank() }.take(8).joinToString("\n"),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 10.sp,
                color = C.TextSecondary,
                lineHeight = 13.sp,
            ),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Copy details", icon = R.drawable.lc_bookmark) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Dropi crash", text))
            }
            Chip("Dismiss") { state.dismissCrash() }
        }
    }
}
