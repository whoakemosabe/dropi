package app.dropi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every tr("…") and trn(n, "…", "…") in the app has an Icelandic line in assets/i18n/is.tsv,
 * with the same {0}, {1}… values, and no Kotlin $template inside the English key (it would
 * never match the table).
 */
class TranslationTest {
    private val src = File("src/main/java")
    private val table: Map<String, String> = L.parse(File("src/main/assets/i18n/is.tsv").readText())
    private val placeholder = Regex("""\{(\d+)\}""")

    private data class Key(val file: String, val line: Int, val text: String, val plural: Boolean, val template: Boolean)

    @Test
    fun everyKeyHasAnIcelandicLine() {
        val keys = keys()
        assertTrue("found the tr() calls", keys.size > 10)
        val problems = keys.mapNotNull { k ->
            val isl = table[k.text]
            when {
                k.template -> "${k.file}:${k.line}: Kotlin template inside a tr() key: ${k.text}"
                isl == null -> "${k.file}:${k.line}: no Icelandic for: ${k.text}"
                !k.plural && ph(k.text) != ph(isl) -> "${k.file}:${k.line}: {n} values differ: ${k.text} → $isl"
                k.plural && !(ph(k.text) + "0").containsAll(ph(isl)) -> "${k.file}:${k.line}: unknown {n} in: $isl"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun icelandicPluralRule() {
        assertTrue(L.isOne(1)); assertTrue(L.isOne(21)); assertTrue(L.isOne(101))
        assertFalse(L.isOne(11)); assertFalse(L.isOne(0)); assertFalse(L.isOne(2)); assertFalse(L.isOne(111))
    }

    @Test
    fun englishByDefaultWithValuesFilledIn() {
        assertEquals("Peaked 8.1 at 09:10", tr("Peaked {0} at {1}", "8.1", "09:10"))
        assertEquals("1 reading", trn(1, "{0} reading", "{0} readings"))
        assertEquals("21 readings", trn(21, "{0} reading", "{0} readings"))
        assertEquals("3 checks a day", trn(3, "{0} check a day", "{0} checks a day"))
        assertEquals("{x} stays", L.fill("{x} stays", arrayOf("a")))
    }

    @Test
    fun tableReadsEscapes() {
        val t = L.parse("# comment\nA\\nB\tC\\nD\nTab\\there\tx\\\\y\n")
        assertEquals("C\nD", t["A\nB"])
        assertEquals("x\\y", t["Tab\there"])
    }

    private fun ph(s: String): Set<String> = placeholder.findAll(s).map { it.groupValues[1] }.toSet()

    private fun keys(): List<Key> {
        val out = ArrayList<Key>()
        val call = Regex("""(?<![\w.])(trn?)\(""")
        src.walkTopDown().filter { it.extension == "kt" && it.name != "L.kt" }.forEach { f ->
            val s = f.readText()
            for (m in call.findAll(s)) {
                val line = s.substring(0, m.range.first).count { it == '\n' } + 1
                var i = m.range.last + 1
                val plural = m.groupValues[1] == "trn"
                if (plural) i = skipArg(s, i) ?: continue
                for (n in 0 until (if (plural) 2 else 1)) {
                    i = ws(s, i)
                    // tr(someValue): a stored English value looked up as it is; nothing to check here.
                    if (i >= s.length || s[i] != '"' || s.startsWith("\"\"\"", i)) break
                    val (text, end, template) = literal(s, i)
                    out += Key(f.name, line, text, plural, template)
                    i = ws(s, end)
                    if (plural && i < s.length && s[i] == ',') i++
                }
            }
        }
        return out
    }

    private fun ws(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun skipArg(s: String, from: Int): Int? {
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> { i = literal(s, i).second; continue }
                c in "([{" -> depth++
                c in ")]}" -> if (depth == 0) return null else depth--
                c == ',' && depth == 0 -> return i + 1
            }
            i++
        }
        return null
    }

    /** A Kotlin "…" literal at [from]: its value, the index after it, and whether it has a $template. */
    private fun literal(s: String, from: Int): Triple<String, Int, Boolean> {
        val b = StringBuilder()
        var template = false
        var i = from + 1
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                val d = s[i + 1]
                if (d == 'u') { b.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                b.append(when (d) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> '\b'; else -> d })
                i += 2
                continue
            }
            if (c == '$' && i + 1 < s.length && (s[i + 1] == '{' || s[i + 1].isLetter() || s[i + 1] == '_')) template = true
            if (c == '"') return Triple(b.toString(), i + 1, template)
            b.append(c)
            i++
        }
        return Triple(b.toString(), s.length, template)
    }
}
