package app.dropi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import app.dropi.R
import app.dropi.data.Agp
import app.dropi.data.Foods
import app.dropi.data.Glucose
import app.dropi.data.MealHistory
import app.dropi.data.SensorPoint
import app.dropi.data.MealTag
import app.dropi.data.Reading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

/**
 * Every reading on a real time scale that zooms: a day, 3 days, a week or a month by button, or
 * any span by pinching, and back in time by dragging sideways. Close in, each reading is a dot
 * (tap it for the details); far out, each day becomes a bar from its lowest to its highest with
 * its average. Overlays: personal targets, meals with their rise, and the sensor trace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeChart(state: AppState, fmt: Fmt, now: Long) {
    val zone = state.zone
    val unit = state.unit
    val readings = state.readings
    val sensor = state.sensor
    val presets = listOf(DAY, 3 * DAY, 7 * DAY, 30 * DAY)
    val earliest = (readings.firstOrNull()?.at ?: now - DAY).coerceAtMost(now - DAY)
    // The view: its right edge and how much time it shows.
    var end by remember { mutableLongStateOf(now + HOUR) }
    var span by remember { mutableLongStateOf(DAY) }
    // The tapped reading, by id, looked up fresh so an edit or delete shows straight away.
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = selectedId?.let { id -> readings.firstOrNull { it.id == id } }
    var showTargets by remember { mutableStateOf(false) }
    var showMeals by remember { mutableStateOf(true) }
    var showSensor by remember { mutableStateOf(true) }
    // The chart's width, so the key knows whether days are drawn as bars (same rule as the chart).
    var boxW by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Keeps the view inside the data: never wider than all of it, never past now or before the
    // first reading. Written so the limits can't cross (zooming out past the data once crashed).
    fun clamp() {
        val latest = now + HOUR
        val maxSpan = maxOf(6 * HOUR, latest - earliest)
        span = span.coerceIn(6 * HOUR, maxSpan)
        val lo = earliest + span
        end = if (lo >= latest) latest else end.coerceIn(lo, latest)
    }
    val start = end - span
    val visible = readings.filter { it.at in start..end }
    val maxV = maxOf(14.0, (visible.maxOfOrNull { it.mmol } ?: 0.0) + 1.0).coerceAtMost(Glucose.MAX_MMOL)
    val minV = 2.0

    val band = C.InRange.copy(alpha = 0.10f)
    val grid = C.Border
    val muted = C.TextMuted
    val ring = C.Card
    val textCol = C.TextSecondary
    val meal = C.AccentText
    val beforeBand = C.AccentText.copy(alpha = 0.08f)
    val trace = C.Aurora[1]
    val joinLine = C.TextSecondary.copy(alpha = 0.45f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted, fontWeight = FontWeight.Medium)
    val clock = remember { DateTimeFormatter.ofPattern(if (fmt.is24) "HH:mm" else "ha", Locale.ENGLISH) }
    val weekday = remember { DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH) }
    val dayMonth = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
    val meals = remember(state.meals, state.measuredById) { state.meals.associateWith { state.outcome(it) } }

    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every reading", style = SectionLabel, modifier = Modifier.weight(1f))
            Text(rangeText(start, end, zone, fmt), style = inter(13.sp, FontWeight.Medium, C.TextMuted))
        }
        val chosen = presets.withIndex().minByOrNull { (_, p) -> abs(kotlin.math.ln(p.toDouble() / span)) }?.index ?: 0
        SlidingSegments(listOf("Day", "3 days", "Week", "Month"), chosen, Modifier.fillMaxWidth()) {
            span = presets[it]
            end = now + HOUR
            clamp()
            selectedId = null
        }

        val axisW = 30.dp
        val plotH = 220.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(plotH + 22.dp)
                .onSizeChanged { boxW = it.width }
                .semantics {
                    contentDescription = "Readings from ${rangeText(start, end, zone, fmt)}: ${visible.size} readings" +
                        (visible.maxByOrNull { it.mmol }?.let { ", highest ${Glucose.format(it.mmol, unit)}" } ?: "") +
                        (visible.minByOrNull { it.mmol }?.let { ", lowest ${Glucose.format(it.mmol, unit)}" } ?: "") +
                        ". Pinch to zoom, drag sideways to move in time."
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Two fingers zoom around where they are; one finger dragged sideways moves in
                    // time. An up-and-down drag is left to the page so it still scrolls.
                    .pointerInput(earliest, now) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var total = Offset.Zero
                            var sideways: Boolean? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.filter { it.pressed }
                                if (down.isEmpty()) break
                                val plotW = size.width - axisW.toPx()
                                if (down.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f && zoom > 0f) {
                                        val cx = ((event.calculateCentroid().x - axisW.toPx()) / plotW).coerceIn(0f, 1f)
                                        val at = end - span + (span * cx).toLong()
                                        span = (span / zoom).toLong()
                                        clamp()
                                        end = at + (span * (1 - cx)).toLong()
                                        clamp()
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val c = down[0]
                                    val d = c.positionChange()
                                    total += d
                                    if (sideways == null && total.getDistance() > viewConfiguration.touchSlop) sideways = abs(total.x) > abs(total.y)
                                    if (sideways == true) {
                                        end -= (span * (d.x / plotW)).toLong()
                                        clamp()
                                        c.consume()
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(visible, start, end) {
                        detectTapGestures { p ->
                            val left = axisW.toPx()
                            val plotW = size.width - left
                            val h = plotH.toPx()
                            fun xOf(t: Long) = left + plotW * ((t - start).toFloat() / span)
                            fun yOf(m: Double) = h * (1f - ((m - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f))
                            val near = visible.minByOrNull { abs(xOf(it.at) - p.x) + abs(yOf(it.mmol) - p.y) * 0.5f }
                            selectedId = near?.takeIf { abs(xOf(it.at) - p.x) < 28.dp.toPx() }?.id
                        }
                    },
            ) {
                val left = axisW.toPx()
                val plotW = size.width - left
                val h = plotH.toPx()
                fun xOf(t: Long) = left + plotW * ((t - start).toFloat() / span)
                fun yOf(m: Double) = h * (1f - ((m - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f))
                val perDayPx = plotW * DAY / span
                val asBars = perDayPx < 40.dp.toPx()

                // Target band (3.9–10.0) and its edges; personal before-meal band and after-meal limit.
                drawRect(band, Offset(left, yOf(Glucose.TIR_HIGH)), Size(plotW, yOf(Glucose.LOW) - yOf(Glucose.TIR_HIGH)))
                listOf(Glucose.LOW, Glucose.TIR_HIGH).forEach { g ->
                    drawLine(grid, Offset(left, yOf(g)), Offset(size.width, yOf(g)), strokeWidth = 1.dp.toPx())
                }
                if (showTargets) {
                    val t = state.targets
                    drawRect(beforeBand, Offset(left, yOf(t.beforeHigh)), Size(plotW, yOf(t.beforeLow) - yOf(t.beforeHigh)))
                    drawLine(
                        meal.copy(alpha = 0.6f), Offset(left, yOf(t.afterHigh)), Offset(size.width, yOf(t.afterHigh)),
                        strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }

                // Time grid and labels that suit the zoom: hours, weekdays or dates.
                val step = when {
                    span <= 30 * HOUR -> if (plotW / (span / (3 * HOUR)) > 42.dp.toPx()) 3 * HOUR else 6 * HOUR
                    span <= 4 * DAY -> 12 * HOUR
                    span <= 10 * DAY -> DAY
                    span <= 40 * DAY -> 7 * DAY
                    else -> 14 * DAY
                }
                val firstDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                var tick = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
                if (step >= 7 * DAY) {
                    // Weeks start on Monday.
                    val dow = firstDay.dayOfWeek.value - 1
                    tick -= dow * DAY
                }
                while (tick <= end) {
                    if (tick >= start) {
                        val x = xOf(tick)
                        val local = Instant.ofEpochMilli(tick).atZone(zone)
                        val midnight = local.hour == 0
                        drawLine(grid.copy(alpha = if (midnight) 0.9f else 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                        val label = when {
                            step < DAY && !midnight -> clock.format(local)
                            step <= DAY -> weekday.format(local)
                            else -> dayMonth.format(local)
                        }
                        val tl = measurer.measure(label, labelStyle.copy(color = if (midnight) textCol else muted))
                        val lx = (x - tl.size.width / 2f).coerceIn(left, size.width - tl.size.width)
                        drawText(tl, topLeft = Offset(lx, h + 4.dp.toPx()))
                    }
                    tick += step
                }

                // The sensor trace behind the dots.
                if (showSensor && !asBars) {
                    val pts = sensorIn(sensor, start, end)
                    if (pts.size >= 2) {
                        val path = Path()
                        var prev = Long.MIN_VALUE
                        var lastX = Float.NEGATIVE_INFINITY
                        val minStep = 1.5.dp.toPx()
                        pts.forEach { p ->
                            val x = xOf(p.at); val y = yOf(p.mmol)
                            // A gap of more than 20 minutes breaks the line rather than bridging it.
                            if (p.at - prev > 20 * 60_000L) { path.moveTo(x, y); lastX = x }
                            // Zoomed out, points closer than a pixel or two add nothing: skip them.
                            else if (x - lastX >= minStep) { path.lineTo(x, y); lastX = x }
                            prev = p.at
                        }
                        drawPath(path, trace.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                if (asBars) {
                    // One bar a day: lowest to highest, a dot at the average.
                    val days = readings.filter { it.at in start - DAY..end }.groupBy { it.date(zone) }
                    val w = (perDayPx * 0.45f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                    // The daily averages joined by a smooth line, under the bars.
                    val avgs = days.entries.sortedBy { it.key }.map { (day, rs) ->
                        Offset(xOf(day.atStartOfDay(zone).toInstant().toEpochMilli() + DAY / 2), yOf(rs.map { it.mmol }.average()))
                    }
                    drawPath(smoothPath(avgs), joinLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    days.forEach { (day, rs) ->
                        val mid = day.atStartOfDay(zone).toInstant().toEpochMilli() + DAY / 2
                        if (mid !in start..end) return@forEach
                        val x = xOf(mid)
                        val lo = rs.minOf { it.mmol }; val hi = rs.maxOf { it.mmol }; val avg = rs.map { it.mmol }.average()
                        drawRoundRect(
                            textCol.copy(alpha = 0.35f), Offset(x - w / 2, yOf(hi)), Size(w, (yOf(lo) - yOf(hi)).coerceAtLeast(w)),
                            cornerRadius = CornerRadius(w / 2),
                        )
                        drawCircle(ring, w / 2 + 2.dp.toPx(), Offset(x, yOf(avg)))
                        drawCircle(C.status(Glucose.status(avg, MealTag.OTHER, state.targets)), w / 2 + 0.5f, Offset(x, yOf(avg)))
                    }
                } else {
                    // Meals: a small plate mark on the bottom edge at the time eaten, and a dashed line
                    // from there (at the starting value) up to the peak after.
                    if (showMeals) {
                        meals.forEach { (r, out) ->
                            if (r.at !in start..end) return@forEach
                            val x = xOf(r.at)
                            drawRoundRect(meal, Offset(x - 5.dp.toPx(), h - 12.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
                            out?.let { o ->
                                drawLine(
                                    meal.copy(alpha = 0.55f), Offset(xOf(r.at), yOf(o.start)), Offset(xOf(o.peakAt), yOf(o.peak)),
                                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                                )
                            }
                        }
                    }
                    // Smaller dots as more days share the width, so a week doesn't clump.
                    val rad = when {
                        perDayPx > 160.dp.toPx() -> 5.dp.toPx()
                        perDayPx > 80.dp.toPx() -> 4.dp.toPx()
                        else -> 3.dp.toPx()
                    }
                    // The readings joined by a smooth line (not through a long overnight gap). Left
                    // out where the sensor trace already draws the line.
                    val sensorHere = showSensor && sensorIn(sensor, start, end).isNotEmpty()
                    if (!sensorHere) {
                        val pts = readings.filter { it.at in start - 12 * HOUR..end + 12 * HOUR }.sortedBy { it.at }
                        var run = ArrayList<Offset>()
                        var prev = Long.MIN_VALUE
                        for (rd in pts) {
                            if (rd.at - prev > 10 * HOUR && run.isNotEmpty()) {
                                drawPath(smoothPath(run), joinLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                                run = ArrayList()
                            }
                            run += Offset(xOf(rd.at), yOf(rd.mmol))
                            prev = rd.at
                        }
                        if (run.size >= 2) drawPath(smoothPath(run), joinLine, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    visible.forEach { rd ->
                        val c = Offset(xOf(rd.at), yOf(rd.mmol))
                        drawCircle(ring, rad + (if (rad < 4.dp.toPx()) 1.5f else 2f).dp.toPx(), c)
                        drawCircle(C.status(Glucose.status(rd.mmol, rd.tag, state.targets)), rad, c)
                    }
                    selected?.takeIf { it.at in start..end }?.let { s ->
                        drawCircle(textCol, rad + 6.dp.toPx(), Offset(xOf(s.at), yOf(s.mmol)), style = Stroke(2.dp.toPx()))
                    }
                }

                // Value labels on the left.
                listOf(Glucose.LOW, Glucose.TIR_HIGH, maxV).forEach { g ->
                    val tl = measurer.measure(Glucose.format(g, unit).removeSuffix(".0"), labelStyle)
                    drawText(tl, topLeft = Offset(0f, (yOf(g) - tl.size.height / 2f).coerceIn(0f, h - tl.size.height)))
                }
            }
        }

        // Overlays, as switches that wrap (never a sideways-scrolling row).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Your targets", selected = showTargets) { showTargets = !showTargets }
            Chip("Meals", icon = R.drawable.lc_utensils, selected = showMeals) { showMeals = !showMeals }
            if (sensor.isNotEmpty()) Chip("Sensor", icon = R.drawable.lc_activity, selected = showSensor) { showSensor = !showSensor }
        }

        // The key: only what's on the chart right now.
        val zoomedOut = with(density) { (boxW - 30.dp.toPx()) * DAY / span < 40.dp.toPx() }
        val inView = sensorIn(sensor, start, end).isNotEmpty()
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Corner.S))
                .background(C.SurfaceLight.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyRow(KeyMark.BAND, C.InRange, "Shaded: in range, 3.9–10.0")
            if (zoomedOut) {
                KeyRow(KeyMark.BAR, C.TextSecondary, "Bar: one day, lowest to highest; dot is the average")
                KeyRow(KeyMark.LINE, C.TextSecondary.copy(alpha = 0.6f), "Grey line: joins each day's average")
            } else {
                KeyRow(KeyMark.DOT, C.InRange, "Dot: a reading, coloured by range (tap one)")
                if (!(showSensor && inView)) KeyRow(KeyMark.LINE, C.TextSecondary.copy(alpha = 0.6f), "Grey line: joins your readings in time order")
            }
            if (showTargets) {
                val t = state.targets
                KeyRow(KeyMark.BAND, C.AccentText, "Light band: your before-eating target, ${Glucose.format(t.beforeLow, unit)}–${Glucose.format(t.beforeHigh, unit)}")
                KeyRow(KeyMark.FLAT_DASH, C.AccentText, "Flat dashes: your after-eating limit, ${Glucose.format(t.afterHigh, unit)}")
            }
            if (showMeals && !zoomedOut) {
                KeyRow(KeyMark.SQUARE, C.AccentText, "Square: a meal, when you ate it")
                KeyRow(KeyMark.SLOPE_DASH, C.AccentText, "Sloped dashes: from the meal up to its peak")
            }
            if (showSensor && inView && !zoomedOut) KeyRow(KeyMark.LINE, C.Aurora[1], "Coloured line: sensor values")
        }

        val s = selected
        if (s != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(C.status(state.status(s))))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Glucose.format(s.mmol, unit)} ${unit.label} · ${s.tag.label}", style = inter(15.sp, FontWeight.SemiBold))
                    Text(
                        listOf(fmt.dayTime(s.at), state.linkText(s).orEmpty(), s.detailsText()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = inter(13.sp, color = C.TextSecondary),
                    )
                }
                Chip("Edit", icon = R.drawable.lc_pencil) { state.editing = s }
            }
        } else {
            Text(
                "Pinch to zoom, drag sideways to go back in time, tap a dot for details." +
                    if (span > 10 * DAY) " Zoomed out, each day is a bar from its lowest to highest reading." else "",
                style = inter(13.sp, FontWeight.Medium, C.TextMuted).copy(lineHeight = 17.sp),
            )
        }
    }
}

private fun rangeText(start: Long, end: Long, zone: ZoneId, fmt: Fmt): String {
    val a = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val b = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
    val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    return if (a == b) DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(a) else "${f.format(a)} – ${f.format(b)}"
}

/**
 * Typical day: all the period's days laid over one 24-hour clock. The line is the middle value
 * each hour; the darker band holds half the readings, the lighter one 8 in 10.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypicalDay(state: AppState, report: app.dropi.data.Report) {
    val zone = state.zone
    val unit = state.unit
    val from = report.from.atStartOfDay(zone).toInstant().toEpochMilli()
    val hours = remember(report, state.sensor) { Agp.profile(report.readings, state.sensor.filter { it.at >= from }, zone) }
    val have = hours.filterNotNull()
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Typical day", style = SectionLabel)
        if (have.size < 6) {
            Text(
                "Every day of the period laid over one day shows your patterns. It needs readings at more times of day, so keep logging.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
            return@Column
        }
        val hi = have.maxBy { it.p50 }
        val lo = have.minBy { it.p50 }
        val maxV = maxOf(14.0, have.maxOf { it.p90 } + 1.0)
        val minV = 2.0
        val band = C.InRange.copy(alpha = 0.10f)
        val outer = C.Aurora[2].copy(alpha = 0.16f)
        val inner = C.Aurora[1].copy(alpha = 0.32f)
        val line = C.Text
        val grid = C.Border
        val muted = C.TextMuted
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 10.sp, color = muted, fontWeight = FontWeight.Medium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics {
                    contentDescription = "Typical day. Highest around ${hi.hour}:00 at ${Glucose.format(hi.p50, unit)}, " +
                        "lowest around ${lo.hour}:00 at ${Glucose.format(lo.p50, unit)}."
                },
        ) {
            val left = 30.dp.toPx()
            val h = size.height - 18.dp.toPx()
            val plotW = size.width - left
            fun xOf(hour: Double) = left + plotW * (hour / 24.0).toFloat()
            fun yOf(m: Double) = h * (1f - ((m - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f))
            drawRect(band, Offset(left, yOf(Glucose.TIR_HIGH)), Size(plotW, yOf(Glucose.LOW) - yOf(Glucose.TIR_HIGH)))
            listOf(Glucose.LOW, Glucose.TIR_HIGH).forEach { g -> drawLine(grid, Offset(left, yOf(g)), Offset(size.width, yOf(g)), 1.dp.toPx()) }
            // Draw each run of hours that have data as its own band and line (no bridging gaps).
            val runs = ArrayList<List<Agp.Hour>>()
            var cur = ArrayList<Agp.Hour>()
            hours.forEach { hr -> if (hr == null) { if (cur.isNotEmpty()) { runs += cur; cur = ArrayList() } } else cur += hr }
            if (cur.isNotEmpty()) runs += cur
            fun bandPath(run: List<Agp.Hour>, top: (Agp.Hour) -> Double, bottom: (Agp.Hour) -> Double): Path {
                val p = Path()
                run.forEachIndexed { i, hr -> val x = xOf(hr.hour + 0.5); if (i == 0) p.moveTo(x, yOf(top(hr))) else p.lineTo(x, yOf(top(hr))) }
                run.asReversed().forEach { hr -> p.lineTo(xOf(hr.hour + 0.5), yOf(bottom(hr))) }
                p.close()
                return p
            }
            runs.forEach { run ->
                if (run.size >= 2) {
                    drawPath(bandPath(run, { it.p90 }, { it.p10 }), outer)
                    drawPath(bandPath(run, { it.p75 }, { it.p25 }), inner)
                    val p = Path()
                    run.forEachIndexed { i, hr -> val x = xOf(hr.hour + 0.5); if (i == 0) p.moveTo(x, yOf(hr.p50)) else p.lineTo(x, yOf(hr.p50)) }
                    drawPath(p, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    val hr = run[0]
                    drawCircle(line, 3.dp.toPx(), Offset(xOf(hr.hour + 0.5), yOf(hr.p50)))
                }
            }
            listOf(0, 6, 12, 18, 24).forEach { hh ->
                val x = xOf(hh.toDouble())
                drawLine(grid.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                val tl = measurer.measure(if (hh == 24) "" else "%02d:00".format(hh), labelStyle)
                drawText(tl, topLeft = Offset((x - tl.size.width / 2f).coerceIn(left, size.width - tl.size.width), h + 4.dp.toPx()))
            }
            listOf(Glucose.LOW, Glucose.TIR_HIGH, maxV).forEach { g ->
                val tl = measurer.measure(Glucose.format(g, unit).removeSuffix(".0"), labelStyle)
                drawText(tl, topLeft = Offset(0f, (yOf(g) - tl.size.height / 2f).coerceIn(0f, h - tl.size.height)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend(C.Text, "Middle value", line = true)
            Legend(C.Aurora[1].copy(alpha = 0.5f), "Half your readings")
            Legend(C.Aurora[2].copy(alpha = 0.3f), "8 in 10")
        }
        Text(
            "Highest around ${"%02d:00".format(hi.hour)} (${Glucose.format(hi.p50, unit)}), lowest around ${"%02d:00".format(lo.hour)} (${Glucose.format(lo.p50, unit)}).",
            style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
        )
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, text: String, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(if (line) 3.dp else 10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = inter(12.sp, color = C.TextMuted))
    }
}

private enum class KeyMark { BAND, DOT, BAR, SQUARE, LINE, FLAT_DASH, SLOPE_DASH }

/** One line of the chart's key: a small drawn sample, then what it means. */
@Composable
private fun KeyRow(mark: KeyMark, color: androidx.compose.ui.graphics.Color, text: String) {
    val ring = C.Card
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Canvas(Modifier.width(26.dp).height(16.dp)) {
            val w = size.width; val h = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            when (mark) {
                KeyMark.BAND -> drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, h * 0.15f), Size(w, h * 0.7f), CornerRadius(3.dp.toPx()))
                KeyMark.DOT -> { drawCircle(ring, 6.dp.toPx(), Offset(w / 2, h / 2)); drawCircle(color, 4.dp.toPx(), Offset(w / 2, h / 2)) }
                KeyMark.BAR -> {
                    drawRoundRect(color.copy(alpha = 0.35f), Offset(w / 2 - 3.dp.toPx(), 0f), Size(6.dp.toPx(), h), CornerRadius(3.dp.toPx()))
                    drawCircle(C.InRange, 3.5.dp.toPx(), Offset(w / 2, h / 2))
                }
                KeyMark.SQUARE -> drawRoundRect(color, Offset(w / 2 - 5.dp.toPx(), h / 2 - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), CornerRadius(3.dp.toPx()))
                KeyMark.LINE -> drawLine(color, Offset(0f, h * 0.7f), Offset(w, h * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                KeyMark.FLAT_DASH -> drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                KeyMark.SLOPE_DASH -> drawLine(color, Offset(0f, h * 0.9f), Offset(w, h * 0.1f), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 18.sp))
    }
}

/**
 * A smooth line through [pts] (in x order) that never overshoots: monotone cubic curves
 * (Fritsch–Carlson), so a smooth join never draws a peak or dip that wasn't measured.
 */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    val n = pts.size
    val d = FloatArray(n - 1) { i -> val dx = pts[i + 1].x - pts[i].x; if (dx == 0f) 0f else (pts[i + 1].y - pts[i].y) / dx }
    val m = FloatArray(n)
    m[0] = d[0]; m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]; val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    for (i in 0 until n - 1) {
        val p0 = pts[i]; val p1 = pts[i + 1]
        val dx = (p1.x - p0.x) / 3f
        path.cubicTo(p0.x + dx, p0.y + m[i] * dx, p1.x - dx, p1.y - m[i + 1] * dx, p1.x, p1.y)
    }
    return path
}

/** The sensor points from [start] to [end], found by binary search in the time-ordered list (a view, no copy). */
private fun sensorIn(sensor: List<SensorPoint>, start: Long, end: Long): List<SensorPoint> {
    fun firstAtOrAfter(t: Long): Int {
        var lo = 0; var hi = sensor.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (sensor[mid].at < t) lo = mid + 1 else hi = mid }
        return lo
    }
    val a = firstAtOrAfter(start)
    val b = firstAtOrAfter(end + 1)
    return if (a >= b) emptyList() else sensor.subList(a, b)
}
