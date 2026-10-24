package app.dropi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.launch

/**
 * The on-device AI download, told in plain words on the home screen, under the update banner:
 * how far along it is, waiting for Wi-Fi, setting up, ready (once, with a way to try it), or
 * what went wrong with a Try again. Nothing is shown before a download starts, or on phones
 * with Gemini Nano.
 */
@Composable
fun AiBanner(state: AppState) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // × on a progress or problem banner hides it until the app is next opened.
    var hidden by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Ai.refresh(context) }

    val g = Gemma.state
    val kind = when {
        Ai.nano -> null
        g is Gemma.State.Downloading -> "progress"
        g == Gemma.State.WaitingForWifi -> "wifi"
        g == Gemma.State.Verifying -> "setup"
        g == Gemma.State.Ready && Gemma.readyBanner -> "ready"
        g is Gemma.State.Failed -> "failed"
        else -> null
    }
    // Downloading, waiting and setting up are one story: hiding one hides them all.
    val group = if (kind == "progress" || kind == "wifi" || kind == "setup") "busy" else kind

    AnimatedVisibility(
        visible = kind != null && hidden != group,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
    ) {
        val done = (g as? Gemma.State.Downloading)?.done ?: 0L
        val title = when (kind) {
            "progress" -> tr("Getting on-device AI…")
            "wifi" -> tr("On-device AI is waiting for Wi-Fi")
            "setup" -> tr("Almost ready")
            "ready" -> tr("On-device AI is ready")
            "failed" -> tr("On-device AI didn't finish")
            else -> ""
        }
        val line = when (kind) {
            "progress" -> tr("{0} of {1} GB. It keeps going if you close Dropi.", Gemma.gb(done), Gemma.gb(Gemma.SIZE))
            "wifi" -> tr("It carries on by itself when you're on Wi-Fi.")
            "setup" -> tr("Setting it up on your phone. This takes about half a minute.")
            "ready" -> tr("Meal photos and questions now work on your phone, with nothing uploaded.")
            "failed" -> (g as? Gemma.State.Failed)?.shown ?: tr("Something went wrong.")
            else -> ""
        }
        val action = when (kind) {
            "ready" -> tr("Try it")
            "failed" -> tr("Try again")
            else -> null
        }
        fun act() {
            when (kind) {
                "ready" -> {
                    Gemma.markReadySeen(context)
                    state.askOpen = true
                }
                "failed" -> scope.launch { Gemma.download(context) }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(Corner.M))
                .background(C.Card)
                .background(auroraWash(0.16f))
                .border(1.dp, auroraEdge(0.55f), RoundedCornerShape(Corner.M))
                .then(if (action != null) Modifier.tap { act() } else Modifier)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(Corner.S)).background(Brush.linearGradient(C.Aurora)),
                    contentAlignment = Alignment.Center,
                ) {
                    Ic(
                        when (kind) {
                            "ready" -> R.drawable.lc_message_circle
                            "failed" -> R.drawable.lc_circle_alert
                            else -> R.drawable.lc_download
                        },
                        C.OnAccent, 16.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = inter(15.sp, FontWeight.Bold, C.Text))
                    Text(line, style = inter(13.sp, color = if (kind == "failed") C.Low else C.TextSecondary))
                }
                if (action != null) {
                    Text(
                        action,
                        style = inter(13.sp, FontWeight.Bold, C.OnAccent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(C.Accent, C.AccentGlow)))
                            .tap { act() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "×",
                    style = inter(22.sp, FontWeight.Light, C.TextMuted),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = tr("Hide")) {
                            Haptics.tap(view)
                            if (kind == "ready") Gemma.markReadySeen(context) else hidden = group
                        }
                        .semantics { contentDescription = tr("Hide") },
                )
            }
            if (kind == "progress" || kind == "setup") {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(end = 8.dp)) { ProgressLine(if (kind == "setup") 1f else done.toFloat() / Gemma.SIZE) }
            }
        }
    }
}
