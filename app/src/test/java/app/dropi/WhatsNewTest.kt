package app.dropi

import app.dropi.work.Updater
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewTest {
    private val body = """
        Tap **dropi.apk** below.

        ### Highlights
        - Grid pop
        - Charts match

        ### What's new since v1.0.66
        - Commit title one

        **Live feed check**

        - ✅ Calendar
    """.trimIndent()

    @Test
    fun highlightsWinOverCommitTitles() {
        assertEquals(listOf("Grid pop", "Charts match"), Updater.whatsNew(body))
        val plain = body.replace("### Highlights\n- Grid pop\n- Charts match\n\n", "")
        assertEquals(listOf("Commit title one"), Updater.whatsNew(plain))
    }

    @Test
    fun everythingSinceTheInstalledVersion() {
        fun rel(tag: String, note: String) = """{"tag_name":"$tag","body":"### Highlights\n- $note\n"}"""
        val json = "[" + listOf(rel("v1.0.70", "C"), rel("v1.0.69", "B"), rel("v1.0.68", "A")).joinToString(",") + "]"
        assertEquals(listOf("C", "B"), Updater.notesSince(json, "1.0.68"))
        assertEquals(2, Updater.countSince(json, "1.0.68"))
        assertEquals(0, Updater.countSince(json, "1.0.70"))
    }
}
