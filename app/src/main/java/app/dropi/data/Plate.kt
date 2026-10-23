package app.dropi.data

/** What the on-device model made of a photo of a meal: its guess, for the person to check. */
data class PlateGuess(val foods: List<String>, val carbs: Int?)

object Plate {
    const val PROMPT =
        "This is a photo of a meal. List the foods you can see, using short everyday names, and estimate " +
            "the total grams of carbohydrate on the plate. Reply in exactly this format and nothing else:\n" +
            "FOODS: food one; food two\nCARBS: number"

    /** Reads the model's reply; tolerant of extra words, bullets and "about 45 g". */
    fun parse(text: String?): PlateGuess? {
        if (text.isNullOrBlank()) return null
        var foods = emptyList<String>()
        var carbs: Int? = null
        for (raw in text.lines()) {
            val line = raw.trim().trimStart('-', '*', '•').trim()
            when {
                line.startsWith("FOODS", ignoreCase = true) -> {
                    foods = line.substringAfter(':', "").split(';', ',')
                        .map { Foods.normalize(it.trim().trimEnd('.')) }
                        .filter { it.length in 2..40 && it != "none" && it != "unknown" }
                        .distinct().take(8)
                }
                line.startsWith("CARBS", ignoreCase = true) -> {
                    carbs = Regex("\\d{1,3}").find(line.substringAfter(':', ""))?.value?.toIntOrNull()?.takeIf { it in 0..400 }
                }
            }
        }
        if (foods.isEmpty() && carbs == null) return null
        // Rounded to 5 g, the same steps as the carbs stepper.
        return PlateGuess(foods, carbs?.let { ((it + 2) / 5) * 5 })
    }
}
