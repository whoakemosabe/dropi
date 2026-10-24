package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.dropi.R
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings → On-device AI. Phones with Gemini Nano just say so. Others can download Gemma
 * (2.6 GB, once, on Wi-Fi) here, watch it come in, and remove it again to get the space back.
 */
@Composable
fun OnDeviceAiSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    val g = Gemma.state

    LaunchedEffect(Unit) { Ai.refresh(context) }
    // Follow a download while Settings is open (Android's download manager does the work).
    LaunchedEffect(g is Gemma.State.Downloading || g is Gemma.State.WaitingForWifi) {
        while (Gemma.state is Gemma.State.Downloading || Gemma.state is Gemma.State.WaitingForWifi) {
            delay(1000)
            Gemma.refresh(context)
        }
    }

    val body = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            Ai.nano -> Text(
                "Gemini Nano is on this phone. Meal photos and typed questions work here, with nothing uploaded.",
                style = body,
            )
            Ai.checking || g is Gemma.State.Checking -> Text("Checking…", style = body)
            g is Gemma.State.Unsupported -> Text(
                "This phone doesn't have Gemini Nano, and Dropi's own model can't run here: ${g.why}. " +
                    "Meal photos and typed questions are hidden. Everything else works.",
                style = body,
            )
            g is Gemma.State.NotDownloaded || g is Gemma.State.Failed -> {
                Text(
                    "This phone doesn't have Gemini Nano. Dropi can download Google's Gemma 4 model instead: " +
                        "2.6 GB, once, on Wi-Fi. It runs on this phone, so meal photos and questions are never uploaded.",
                    style = body,
                )
                if (g is Gemma.State.Failed) Text(g.message, style = inter(13.sp, color = C.Low))
                Chip(if (g is Gemma.State.Failed) "Try again" else "Download (2.6 GB)", icon = R.drawable.lc_download, selected = true) {
                    scope.launch { Gemma.download(context) }
                }
            }
            g is Gemma.State.WaitingForWifi || g is Gemma.State.Downloading -> {
                val done = (g as? Gemma.State.Downloading)?.done ?: 0L
                Text(
                    if (g is Gemma.State.WaitingForWifi) "Waiting for Wi-Fi. The download carries on when you're connected."
                    else "Downloading Gemma… ${Gemma.gb(done)} of ${Gemma.gb(Gemma.SIZE)} GB. It keeps going if you close Dropi.",
                    style = body,
                )
                ProgressLine(done.toFloat() / Gemma.SIZE)
                Chip("Cancel", icon = R.drawable.lc_x) { scope.launch { Gemma.remove(context) } }
            }
            g is Gemma.State.Verifying -> Text("Almost ready: setting up on-device AI on your phone (about half a minute)…", style = body)
            g is Gemma.State.Ready -> {
                Text(
                    "Gemma is ready. Meal photos and typed questions work on this phone, with nothing uploaded. " +
                        "It uses 2.6 GB of storage.",
                    style = body,
                )
                // Which chip it runs on: the graphics chip is several times faster than the main processor.
                val runsOn = Gemma.backendName ?: Gemma.lastBackend(context)
                Text(
                    if (runsOn != null) "Runs on $runsOn." else "Runs on: shown after its first answer.",
                    style = inter(13.sp, FontWeight.Medium, if (runsOn?.contains("CPU") == true) C.High else C.TextSecondary),
                )
                Row {
                    Chip(if (confirmRemove) "Tap again to remove" else "Remove model", icon = R.drawable.lc_trash, selected = confirmRemove) {
                        if (confirmRemove) scope.launch { Gemma.remove(context); confirmRemove = false }
                        else confirmRemove = true
                    }
                }
            }
        }
    }
}
