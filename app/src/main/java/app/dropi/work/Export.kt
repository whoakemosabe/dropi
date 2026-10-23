package app.dropi.work

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import app.dropi.data.Analytics
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.data.Reading
import app.dropi.data.Report
import app.dropi.data.Targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reports to share with a care team: a PDF (summary, time in range, time of day, daily
 * averages and every reading) or a CSV of the readings. Files go to the app's cache and are
 * shared through the system share sheet; nothing is uploaded anywhere.
 */
object Export {
    private val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    private fun dir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Readings as CSV (both units, so any clinic can read it). */
    fun csv(readings: List<Reading>, zone: ZoneId): String {
        val sb = StringBuilder("date,time,mmol_per_l,mg_per_dl,moment,carbs_g,insulin_units,insulin_kind,tablets,exercise_min,foods,note\n")
        readings.sortedBy { it.at }.forEach { r ->
            val t = Instant.ofEpochMilli(r.at).atZone(zone)
            sb.append(t.toLocalDate()).append(',')
                .append(time.format(t)).append(',')
                .append(Glucose.format(r.mmol, GlucoseUnit.MMOL)).append(',')
                .append(Glucose.format(r.mmol, GlucoseUnit.MGDL)).append(',')
                .append(r.tag.label).append(',')
                .append(r.carbs?.toString() ?: "").append(',')
                .append(r.insulin?.let { Glucose.formatNumber(it) } ?: "").append(',')
                .append(r.insulinKind?.label ?: "").append(',')
                .append(if (r.meds) "yes" else "").append(',')
                .append(r.exercise?.toString() ?: "").append(',')
                .append(quote(r.foods.joinToString("; ") { app.dropi.data.Foods.display(it) })).append(',')
                .append(quote(r.note)).append('\n')
        }
        return sb.toString()
    }

    /** Quotes a CSV field when needed, and defuses spreadsheet formulas (=, +, -, @). */
    fun quote(s: String): String {
        var v = s.replace("\r", " ").replace("\n", " ")
        if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
        return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    suspend fun writeCsv(context: Context, readings: List<Reading>, zone: ZoneId, today: LocalDate): File = withContext(Dispatchers.IO) {
        File(dir(context), "dropi-readings-$today.csv").apply { writeText(csv(readings, zone)) }
    }

    suspend fun writePdf(
        context: Context, all: List<Reading>, days: Int, today: LocalDate, zone: ZoneId, unit: GlucoseUnit, targets: Targets,
    ): File = withContext(Dispatchers.IO) {
        val report = Analytics.report(all, days, today, zone)
        val file = File(dir(context), "dropi-report-$today.pdf")
        val pdf = PdfDocument()
        try {
            Writer(pdf, report, unit, targets, zone).write()
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        file
    }

    /** Opens the share sheet for [file]. */
    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Lays the report out on A4 pages, top to bottom, starting a new page when one fills up. */
    private class Writer(
        private val pdf: PdfDocument,
        private val r: Report,
        private val unit: GlucoseUnit,
        private val targets: Targets,
        private val zone: ZoneId,
    ) {
        private val w = 595f
        private val h = 842f
        private val margin = 48f
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private val ink = 0xFF11131A.toInt()
        private val ink2 = 0xFF474D5E.toInt()
        private val muted = 0xFF5E6477.toInt()
        private val line = 0xFFD8DBE5.toInt()

        private fun paint(size: Float, color: Int = ink, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        private fun v(m: Double) = Glucose.format(m, unit)
        private val canvas get() = page!!.canvas

        private fun newPage() {
            page?.let { finish(it) }
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pageNo).create())
            y = margin
        }

        private fun finish(p: PdfDocument.Page) {
            val c = p.canvas
            c.drawText(
                "Dropi is a log, not a medical device. Readings as entered by the person.",
                margin, h - 28f, paint(8f, muted),
            )
            val n = "Page $pageNo"
            val pp = paint(8f, muted)
            c.drawText(n, w - margin - pp.measureText(n), h - 28f, pp)
            pdf.finishPage(p)
        }

        private fun need(space: Float) {
            if (page == null || y + space > h - 60f) newPage()
        }

        private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false, gap: Float = 6f) {
            need(size + gap)
            y += size
            canvas.drawText(s, margin, y, paint(size, color, bold))
            y += gap
        }

        private fun row(cols: List<String>, widths: List<Float>, size: Float = 9f, bold: Boolean = false, color: Int = ink) {
            need(size + 6f)
            y += size
            var x = margin
            val p = paint(size, color, bold)
            cols.forEachIndexed { i, s ->
                // Clip long notes to their column.
                var t = s
                val max = widths[i] - 6f
                while (t.isNotEmpty() && p.measureText(t) > max) t = t.dropLast(1)
                if (t.length < s.length && t.length > 1) t = t.dropLast(1) + "…"
                canvas.drawText(t, x, y, p)
                x += widths[i]
            }
            y += 5f
        }

        private fun rule() {
            need(8f)
            val p = paint(1f, line).apply { strokeWidth = 0.75f }
            canvas.drawLine(margin, y, w - margin, y, p)
            y += 8f
        }

        private fun section(title: String) {
            need(40f)
            y += 14f
            text(title.uppercase(), 9f, muted, bold = true, gap = 4f)
            rule()
        }

        fun write() {
            newPage()
            text("Blood glucose report", 22f, bold = true, gap = 4f)
            text(
                "${day.format(r.from)} – ${day.format(r.to)} · ${r.count} readings on ${r.daysLogged} of ${r.days} days · ${unit.label}",
                10f, ink2, gap = 10f,
            )

            section("Summary")
            val cw = (w - margin * 2) / 4
            val cols = listOf(
                "Average" to (r.mean?.let { v(it) } ?: "—"),
                "Time in range" to (r.bands?.let { "${it.inRange.roundToInt()}%" } ?: "—"),
                "Est. A1C (GMI)" to (r.gmiPercent?.let { String.format(Locale.ROOT, "%.1f%% · %d mmol/mol", it, r.gmiMmolMol!!.roundToInt()) } ?: "not enough data"),
                "Variability (CV)" to (r.cv?.let { "${it.roundToInt()}%" } ?: "—"),
            )
            need(44f)
            cols.forEachIndexed { i, (label, value) ->
                canvas.drawText(label, margin + cw * i, y + 9f, paint(8.5f, muted))
                canvas.drawText(value, margin + cw * i, y + 28f, paint(if (value.length > 12) 10f else 16f, ink, bold = true))
            }
            y += 40f
            text(
                "Lowest ${r.readings.minOfOrNull { it.mmol }?.let { v(it) } ?: "—"} · Highest ${r.readings.maxOfOrNull { it.mmol }?.let { v(it) } ?: "—"} · " +
                    "Low readings (under ${v(Glucose.LOW)}): ${r.lows}",
                9.5f, ink2,
            )

            r.bands?.let { b ->
                section("Readings by range")
                val segs = listOf(
                    Triple("Very low (under ${v(Glucose.URGENT_LOW)})", b.veryLow, 0xFFA3134A.toInt()),
                    Triple("Low (${v(Glucose.URGENT_LOW)}–${v(Glucose.LOW)})", b.low, 0xFFE04A78.toInt()),
                    Triple("In range (${v(Glucose.LOW)}–${v(Glucose.TIR_HIGH)})", b.inRange, 0xFF00939A.toInt()),
                    Triple("High (${v(Glucose.TIR_HIGH)}–${v(Glucose.VERY_HIGH)})", b.high, 0xFFB08000.toInt()),
                    Triple("Very high (over ${v(Glucose.VERY_HIGH)})", b.veryHigh, 0xFFC23A0A.toInt()),
                )
                need(30f)
                var x = margin
                val total = w - margin * 2
                segs.filter { it.second > 0 }.forEach { (_, pct, color) ->
                    val sw = (total * pct / 100.0).toFloat()
                    canvas.drawRoundRect(RectF(x, y, x + sw - 1.5f, y + 12f), 3f, 3f, paint(1f, color).apply { style = Paint.Style.FILL })
                    x += sw
                }
                y += 22f
                segs.forEach { (label, pct, color) ->
                    need(14f)
                    canvas.drawRect(margin, y + 1f, margin + 8f, y + 9f, paint(1f, color).apply { style = Paint.Style.FILL })
                    canvas.drawText(label, margin + 14f, y + 9f, paint(9.5f))
                    val t = "${pct.roundToInt()}%"
                    val p = paint(9.5f, bold = true)
                    canvas.drawText(t, margin + 260f - p.measureText(t), y + 9f, p)
                    y += 14f
                }
                text("Shares of logged readings. Consensus goals: in range over 70%, low under 4%, very low under 1%.", 8.5f, muted)
            }

            if (r.byTag.isNotEmpty()) {
                section("By time of day")
                val widths = listOf(170f, 90f, 90f, 150f)
                row(listOf("Moment", "Readings", "Average", "Target"), widths, 8.5f, bold = true, color = muted)
                r.byTag.forEach { t ->
                    val (lo, hi) = targets.forTag(t.tag)
                    row(listOf(t.tag.label, "${t.count}", v(t.average), "${v(lo)}–${v(hi)}"), widths, 9.5f)
                }
            }

            if (r.byDay.isNotEmpty()) {
                section("Daily averages")
                val widths = listOf(170f, 90f, 90f, 150f)
                row(listOf("Day", "Readings", "Average", "Lowest – highest"), widths, 8.5f, bold = true, color = muted)
                r.byDay.reversed().forEach { d ->
                    row(listOf(day.format(d.day), "${d.count}", v(d.average), "${v(d.min)} – ${v(d.max)}"), widths, 9.5f)
                }
            }

            section("All readings")
            val widths = listOf(92f, 40f, 52f, 80f, 235f)
            row(listOf("Day", "Time", unit.label, "Moment", "Details and note"), widths, 8.5f, bold = true, color = muted)
            r.readings.reversed().forEach { rd ->
                val t = Instant.ofEpochMilli(rd.at).atZone(zone)
                val extra = listOf(rd.detailsText(), rd.note).filter { it.isNotBlank() }.joinToString(" · ")
                row(listOf(shortDay.format(t) + " " + t.year, time.format(t), v(rd.mmol), rd.tag.label, extra), widths, 9f)
            }
            page?.let { finish(it) }
            page = null
        }
    }
}
