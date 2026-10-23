package app.dropi.data

/**
 * Picks the reading out of the text recognised in a photo of a meter. Meters show the number
 * biggest, so the tallest plausible number wins. Times ("12:45"), dates and years are skipped.
 */
object MeterScan {
    /** One piece of recognised text and how tall it was on the photo (pixels). */
    data class Piece(val text: String, val height: Int)

    private val decimal = Regex("""^\d{1,2}[.,]\d$""")
    private val whole = Regex("""^\d{2,3}$""")

    /** OCR often reads a 7-segment display's digits as look-alike letters. */
    private fun clean(s: String) = s.trim()
        .replace('O', '0').replace('o', '0').replace('D', '0')
        .replace('l', '1').replace('I', '1').replace('|', '1')
        .replace('S', '5').replace('B', '8').replace('Z', '2')
        .trim('-', '_', '\'', '"', '*')

    /** The reading in mmol/L, or null if nothing on the photo looks like one. */
    fun pick(pieces: List<Piece>, unit: GlucoseUnit): Double? {
        data class Hit(val mmol: Double, val height: Int, val exact: Boolean)
        val hits = ArrayList<Hit>()
        for (p in pieces) {
            if (p.text.contains(':') || p.text.contains('/')) continue
            for (raw in p.text.split(' ', '\n')) {
                val t = clean(raw)
                when {
                    decimal.matches(t) -> {
                        val v = t.replace(',', '.').toDouble()
                        // A decimal is mmol/L whichever unit is set.
                        if (v >= Glucose.MIN_MMOL && v <= Glucose.MAX_MMOL) hits += Hit(v, p.height, true)
                    }
                    whole.matches(t) -> {
                        val n = t.toInt()
                        if (unit == GlucoseUnit.MGDL) {
                            if (n in 20..600) hits += Hit(Glucose.toMmol(n.toDouble()), p.height, true)
                        } else {
                            // A mmol/L meter whose tiny decimal point wasn't seen: "58" is 5.8.
                            val v = n / 10.0
                            if (n < 1000 && v >= Glucose.MIN_MMOL && v <= Glucose.MAX_MMOL) hits += Hit(v, p.height, false)
                        }
                    }
                }
            }
        }
        // The tallest number on the display; with equal heights, one read with its point wins.
        return hits.maxWithOrNull(compareBy<Hit> { it.height }.thenBy { it.exact })?.mmol
    }
}
