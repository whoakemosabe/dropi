package app.dropi

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import app.dropi.widget.Widgets
import app.dropi.work.Alerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dropi's languages: English (written in the code) and Icelandic (`assets/i18n/is.tsv`).
 *
 * Text is written in English where it's used and wrapped: `tr("Before eating")`,
 * `tr("Your last one was at {0}", time)`, `trn(n, "{0} reading", "{0} readings")`. In Icelandic
 * the English text is looked up in the table; anything missing stays English. Works anywhere
 * (screens, notifications, workers, the widget) without a Context, because the table is loaded
 * once when the app starts ([DropiApp]).
 *
 * The choice is kept by Android's per-app language on Android 13+ (so Settings › Apps › Dropi ›
 * Language works too) and in a pref on older phones. "Follow the phone" means Icelandic when the
 * phone's own first language is Icelandic.
 *
 * Never call `tr` where the result is kept for good (an enum's constructor, an `object`'s
 * `val`): it would stay in the language the app started in. Use a getter instead.
 */
object L {
    const val EN = "en"
    const val IS = "is"

    @Volatile
    var icelandic: Boolean = false
        private set

    @Volatile
    private var table: Map<String, String> = emptyMap()
    private var appContext: Context? = null

    private val ICELANDIC: Locale = Locale.forLanguageTag("is-IS")

    /** For dates and month names. */
    val locale: Locale get() = if (icelandic) ICELANDIC else Locale.ENGLISH

    /** The decimal mark numbers are shown with ("5,8" in Icelandic, "5.8" in English). */
    val decimal: Char get() = if (icelandic) ',' else '.'

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    /** Re-reads the choice (after a language change, or a configuration change). */
    fun refresh() {
        val c = appContext ?: return
        val now = effective(c) == IS
        if (now && table.isEmpty()) table = runCatching { load(c) }.getOrDefault(emptyMap())
        icelandic = now
    }

    /** What the person picked: [EN], [IS], or null to follow the phone. */
    fun chosen(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 33) {
            val l = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (l == null || l.isEmpty) null else norm(l[0].language)
        }
        return prefs(context).getString("lang", null)
    }

    /** The language in use: the person's pick, else the phone's. */
    fun effective(context: Context): String = chosen(context) ?: phone()

    /** The phone's own first language, as [IS] or [EN]. */
    fun phone(): String = norm(Resources.getSystem().configuration.locales[0]?.language)

    /**
     * Switches the app's language ([EN], [IS], or null to follow the phone). On Android 13+
     * Android restarts the screen itself; on older phones [activity] is restarted here.
     */
    fun set(context: Context, lang: String?, activity: Activity? = null) {
        val before = icelandic
        prefs(context).edit { if (lang == null) remove("lang") else putString("lang", lang) }
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (lang == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(lang)
        }
        refresh()
        if (before != icelandic) {
            // Notification channel names and the widget follow at once.
            runCatching { Alerts.createChannels(context) }
            val ctx = context.applicationContext
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { Widgets.updateAll(ctx) }
            }
            if (Build.VERSION.SDK_INT < 33) activity?.recreate()
        }
    }

    fun t(en: String): String = if (icelandic) table[en] ?: en else en

    fun t(en: String, args: Array<out Any?>): String = fill(t(en), args)

    /** Picks the singular or plural text for [n], by each language's own rule. */
    fun n(n: Long, one: String, other: String, args: Array<out Any?>): String {
        val singular = if (icelandic) isOne(n) else n == 1L
        val all = arrayOfNulls<Any?>(args.size + 1)
        all[0] = n
        for (i in args.indices) all[i + 1] = args[i]
        return fill(t(if (singular) one else other), all)
    }

    /** Icelandic counts as singular any whole number ending in 1 except those ending in 11 (1, 21, 101). */
    fun isOne(n: Long): Boolean {
        val a = kotlin.math.abs(n)
        return a % 10 == 1L && a % 100 != 11L
    }

    /** Puts the values into {0}, {1}… */
    fun fill(text: String, args: Array<out Any?>): String {
        if (args.isEmpty() || '{' !in text) return text
        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '{') {
                val end = text.indexOf('}', i)
                val idx = if (end > i) text.substring(i + 1, end).toIntOrNull() else null
                if (idx != null && idx in args.indices) {
                    out.append(args[idx].toString())
                    i = end + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** Reads `english<TAB>icelandic` lines; `\n`, `\t` and `\\` are escaped, `#` starts a comment. */
    fun parse(text: String): Map<String, String> {
        val m = HashMap<String, String>(4096)
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val en = unescape(line.substring(0, tab))
            val isl = unescape(line.substring(tab + 1))
            if (isl.isNotEmpty()) m.putIfAbsent(en, isl)
        }
        return m
    }

    fun unescape(s: String): String {
        if ('\\' !in s) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> b.append('\n')
                    't' -> b.append('\t')
                    '\\' -> b.append('\\')
                    else -> { b.append(c); b.append(s[i + 1]) }
                }
                i += 2
            } else {
                b.append(c); i++
            }
        }
        return b.toString()
    }

    private fun load(c: Context): Map<String, String> =
        c.assets.open("i18n/is.tsv").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }

    private fun norm(lang: String?): String = if (lang == IS) IS else EN

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("dropi_lang", Context.MODE_PRIVATE)
}

/** The text in the app's language: `tr("Before eating")`. */
fun tr(en: String): String = L.t(en)

/** The text in the app's language with values put into {0}, {1}…: `tr("Peaked {0} at {1}", v, t)`. */
fun tr(en: String, vararg args: Any?): String = L.t(en, args)

/**
 * Singular or plural by count; the count is {0}, any other values {1}, {2}…:
 * `trn(n, "{0} reading", "{0} readings")`.
 */
fun trn(n: Int, one: String, other: String, vararg args: Any?): String = L.n(n.toLong(), one, other, args)

fun trn(n: Long, one: String, other: String, vararg args: Any?): String = L.n(n, one, other, args)
