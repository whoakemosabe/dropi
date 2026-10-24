package app.dropi.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dropi.L

/**
 * English / Íslenska. Each name is always written in its own language, so someone who can't
 * read the current one still finds theirs. Picking one restarts the screen in that language.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val context = LocalContext.current
    val view = LocalView.current
    Column(modifier) {
        SlidingSegments(
            listOf("English", "Íslenska"),
            if (L.icelandic) 1 else 0,
            Modifier.fillMaxWidth(),
            height = height,
        ) { i ->
            val lang = if (i == 1) L.IS else L.EN
            if ((lang == L.IS) == L.icelandic) return@SlidingSegments
            Haptics.segment(view)
            L.set(context, lang, context.findActivity())
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
