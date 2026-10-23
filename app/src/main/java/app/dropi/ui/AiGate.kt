package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shown when a feature needs the on-device model and it isn't ready yet. It offers the one-time
 * download, follows it while it runs, or says plainly why this phone can't run one. When the
 * model becomes ready, [onReady] carries on with what the person was doing.
 *
 * [what] finishes "… needs an on-device model", e.g. "Meal photos".
 */
@Composable
fun AiNeededDialog(what: String, readyAction: String, onDismiss: () -> Unit, readyIcon: Int = R.drawable.lc_camera, onReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Gemma.state
    LaunchedEffect(Unit) { Ai.refresh(context) }
    // Follow a download while the window is open.
    LaunchedEffect(g is Gemma.State.Downloading || g is Gemma.State.WaitingForWifi || g == Gemma.State.Verifying) {
        while (Gemma.state is Gemma.State.Downloading || Gemma.state is Gemma.State.WaitingForWifi || Gemma.state == Gemma.State.Verifying) {
            delay(1000)
            Gemma.refresh(context)
        }
    }

    val body = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 22.sp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(C.Card)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(C.Aurora)),
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_message_circle, Color.White, 24.dp) }

            when {
                Ai.usable -> {
                    Text("Ready", style = heading(24.sp))
                    Text("${Ai.name} is on your phone.", style = body)
                    PrimaryButton(readyAction, Modifier.fillMaxWidth(), icon = readyIcon) { onReady() }
                }
                Ai.checking -> {
                    Text("One moment", style = heading(24.sp))
                    Text("Checking what this phone can run…", style = body)
                }
                g is Gemma.State.Unsupported -> {
                    Text("Not on this phone", style = heading(24.sp))
                    Text(
                        "$what needs an on-device AI model, and this phone can't run one: ${g.why}. " +
                            "You can still add foods and carbs yourself.",
                        style = body,
                    )
                    PrimaryButton("OK", Modifier.fillMaxWidth()) { onDismiss() }
                }
                g is Gemma.State.Downloading || g == Gemma.State.Verifying -> {
                    Text("Downloading the model", style = heading(24.sp))
                    Text(
                        if (g is Gemma.State.Downloading) "${Gemma.gb(g.done)} of ${Gemma.gb(Gemma.SIZE)} GB. It carries on in the background, " +
                            "and this works as soon as it's done."
                        else "Almost ready: setting it up on your phone…",
                        style = body,
                    )
                    if (g is Gemma.State.Downloading) ProgressLine((g.done.toFloat() / Gemma.SIZE).coerceIn(0f, 1f))
                    GhostButton("Close", Modifier.fillMaxWidth()) { onDismiss() }
                }
                g == Gemma.State.WaitingForWifi -> {
                    Text("Waiting for Wi-Fi", style = heading(24.sp))
                    Text("The model downloads on Wi-Fi only. It starts by itself when you're connected.", style = body)
                    GhostButton("Close", Modifier.fillMaxWidth()) { onDismiss() }
                }
                else -> {
                    Text("Download the on-device model?", style = heading(24.sp))
                    Text(
                        "$what uses Gemma 4, Google's on-device model. It's a one-time ${Gemma.gb(Gemma.SIZE)} GB download on Wi-Fi. " +
                            "After that everything runs on your phone and nothing is uploaded. You can remove it any time in Settings.",
                        style = body,
                    )
                    if (g is Gemma.State.Failed) Text(g.message, style = inter(14.sp, color = C.High))
                    PrimaryButton("Download (${Gemma.gb(Gemma.SIZE)} GB)", Modifier.fillMaxWidth(), icon = R.drawable.lc_download) {
                        scope.launch { Gemma.download(context) }
                    }
                    GhostButton("Not now", Modifier.fillMaxWidth()) { onDismiss() }
                }
            }
        }
    }
}
