package app.dropi.data

import app.dropi.tr
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Blood glucose units and ranges. Everything is stored in mmol/L; mg/dL is only for showing
 * and typing. 1 mmol/L of glucose is 18.0182 mg/dL (molar mass 180.16 g/mol).
 */
enum class GlucoseUnit(val key: String, val label: String) {
    MMOL("mmol", "mmol/L"),
    MGDL("mgdl", "mg/dL");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: MMOL
    }
}

object Glucose {
    const val MGDL_PER_MMOL = 18.0182

    /** What home meters can show; outside this they read "LO" or "HI". */
    const val MIN_MMOL = 1.1
    const val MAX_MMOL = 33.3

    // International consensus thresholds (ADA / ATTD), in mmol/L.
    const val URGENT_LOW = 3.0 // below 54 mg/dL: level 2 hypoglycaemia
    const val LOW = 3.9 // below 70 mg/dL: level 1 hypoglycaemia
    const val TIR_HIGH = 10.0 // 180 mg/dL: top of the standard time-in-range band
    const val VERY_HIGH = 13.9 // above 250 mg/dL: level 2 hyperglycaemia

    fun toMgdl(mmol: Double) = mmol * MGDL_PER_MMOL
    fun toMmol(mgdl: Double) = mgdl / MGDL_PER_MMOL

    /**
     * For showing: "6.4" (mmol/L, one decimal, as meters show it; "6,4" in Icelandic) or "115"
     * (mg/dL, whole numbers).
     */
    fun format(mmol: Double, unit: GlucoseUnit): String =
        plain(mmol, unit).let { if (app.dropi.L.decimal == '.') it else it.replace('.', app.dropi.L.decimal) }

    /** Always with a point, whatever the language: for CSV files and other machine-read text. */
    fun plain(mmol: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MMOL -> String.format(Locale.ROOT, "%.1f", (mmol * 10).roundToInt() / 10.0)
        GlucoseUnit.MGDL -> toMgdl(mmol).roundToInt().toString()
    }

    /** "4" or "4.5": a plain number with at most one decimal, for doses in exports. */
    fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)

    /** A typed value in [unit] → mmol/L, or null if it isn't a number a meter could show. */
    fun parse(text: String, unit: GlucoseUnit): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val mmol = if (unit == GlucoseUnit.MMOL) v else toMmol(v)
        // A tiny tolerance so "33.3" and "600" (mg/dL) both count as in range.
        return mmol.takeIf { it >= MIN_MMOL - 0.001 && it <= MAX_MMOL + 0.001 }
    }

    /** The meter range in [unit], for the hint under the keypad. */
    fun rangeLabel(unit: GlucoseUnit) = "${format(MIN_MMOL, unit)}–${format(MAX_MMOL, unit)} ${unit.label}"

    /**
     * Keypad input rules: mmol/L takes two digits, a point and one decimal ("12.4"); mg/dL takes
     * up to three digits and no point. Returns the new text, or the old one if the key isn't allowed.
     */
    fun type(current: String, key: Char, unit: GlucoseUnit): String {
        @Suppress("NAME_SHADOWING") val current = current.replace(',', '.')
        if (key == '⌫') return current.dropLast(1)
        if (key == '.' || key == ',') {
            if (unit == GlucoseUnit.MGDL || current.contains('.')) return current
            return if (current.isEmpty()) "0." else "$current."
        }
        if (!key.isDigit()) return current
        val next = if (current == "0") key.toString() else current + key
        val ok = when (unit) {
            GlucoseUnit.MMOL -> {
                val parts = next.split('.')
                parts[0].length <= 2 && (parts.size < 2 || parts[1].length <= 1)
            }
            GlucoseUnit.MGDL -> next.length <= 3
        }
        return if (ok) next else current
    }

    /** Where a reading sits, judged against the target for its moment ([tag]). */
    fun status(mmol: Double, tag: MealTag, targets: Targets = Targets()): Status {
        if (mmol < URGENT_LOW) return Status.URGENT_LOW
        if (mmol < LOW) return Status.LOW
        if (mmol > VERY_HIGH) return Status.VERY_HIGH
        val (lo, hi) = targets.forTag(tag)
        return when {
            mmol < lo -> Status.BELOW_TARGET
            mmol > hi -> Status.HIGH
            else -> Status.IN_RANGE
        }
    }

    /** In the standard 3.9–10.0 mmol/L band (the one "time in range" is measured against). */
    fun inTir(mmol: Double) = mmol >= LOW && mmol <= TIR_HIGH
}

/**
 * Target bands in mmol/L. Defaults are the ADA's for adults with diabetes: 4.4–7.2 before
 * meals (and fasting), under 10.0 one to two hours after starting a meal, 3.9–10.0 otherwise.
 * People set their own with their care team, so these become editable later.
 */
data class Targets(
    val beforeLow: Double = 4.4,
    val beforeHigh: Double = 7.2,
    val afterHigh: Double = 10.0,
    val generalLow: Double = Glucose.LOW,
    val generalHigh: Double = Glucose.TIR_HIGH,
) {
    /** Keeps the bands sensible whatever was saved: low edge under the high one, inside 3.9–13.9. */
    fun safe(): Targets {
        val bl = beforeLow.coerceIn(Glucose.LOW, 7.0)
        val bh = beforeHigh.coerceIn(bl + 0.5, 11.0)
        val ah = afterHigh.coerceIn(6.0, Glucose.VERY_HIGH)
        return copy(beforeLow = r1(bl), beforeHigh = r1(bh), afterHigh = r1(ah))
    }

    private fun r1(v: Double) = Math.round(v * 10) / 10.0

    val isDefault get() = this == Targets()

    fun forTag(tag: MealTag): Pair<Double, Double> = when (tag) {
        MealTag.FASTING, MealTag.BEFORE_MEAL -> beforeLow to beforeHigh
        MealTag.AFTER_MEAL -> generalLow to afterHigh
        else -> generalLow to generalHigh
    }
}

/** [en] is the English word; [label] is in the app's language. */
enum class Status(val en: String) {
    URGENT_LOW("Urgent low"),
    LOW("Low"),
    BELOW_TARGET("Below target"),
    IN_RANGE("In range"),
    HIGH("Above target"),
    VERY_HIGH("Very high");

    val label: String get() = when (this) {
        URGENT_LOW -> tr("Urgent low")
        LOW -> tr("Low")
        BELOW_TARGET -> tr("Below target")
        IN_RANGE -> tr("In range")
        HIGH -> tr("Above target")
        VERY_HIGH -> tr("Very high")
    }

    val isLow get() = this == URGENT_LOW || this == LOW
}
