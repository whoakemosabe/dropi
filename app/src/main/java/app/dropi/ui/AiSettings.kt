package app.dropi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.dropi.tr
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings → On-device AI. Gemma (2.6 GB, once, on Wi-Fi) can be downloaded here, watched as it
 * comes in, and removed again. Phones that also have Gemini Nano get a Gemma / Gemini Nano switch.
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Both models on the phone: the person picks which one does the work.
        if (Ai.canPick) {
            Text(tr("Model in use"), style = inter(15.sp, FontWeight.SemiBold))
            SlidingSegments(listOf("Gemma", "Gemini Nano"), if (Ai.pick == Ai.Pick.NANO) 1 else 0, Modifier.fillMaxWidth()) {
                Ai.choose(context, if (it == 1) Ai.Pick.NANO else Ai.Pick.GEMMA)
            }
            Text(
                if (Ai.pick == Ai.Pick.NANO) tr("Gemini Nano comes with your phone and uses less memory. Its answers aren't held to a fixed shape, so meal and label numbers can come back less reliably.")
                else tr("Gemma is Dropi's own download. Its meal and label answers are held to a fixed shape, so they come back more reliably."),
                style = body,
            )
        }
        when {
            Ai.checking -> Text(tr("Checking…"), style = body)
            // Nano is there and Gemma can't run on this phone: Nano does the work.
            Ai.nanoThere && g is Gemma.State.Unsupported -> Text(
                tr("Gemini Nano is on this phone. Meal photos and typed questions work here, with nothing uploaded."),
                style = body,
            )
            g is Gemma.State.Unsupported -> Text(
                tr("This phone doesn't have Gemini Nano, and Dropi's own model can't run here: {0}. Meal photos and typed questions are hidden. Everything else works.", g.shownWhy),
                style = body,
            )
            g is Gemma.State.NotDownloaded || g is Gemma.State.Failed -> {
                Text(
                    if (Ai.nanoThere) tr("Gemini Nano is on this phone. You can also download Google's Gemma 4 model: 2.6 GB, once, on Wi-Fi. Its meal and label answers come back more reliably, and it runs on this phone too.")
                    else tr("This phone doesn't have Gemini Nano. Dropi can download Google's Gemma 4 model instead: 2.6 GB, once, on Wi-Fi. It runs on this phone, so meal photos and questions are never uploaded."),
                    style = body,
                )
                if (g is Gemma.State.Failed) Text(g.shown, style = inter(13.sp, color = C.Low))
                SettingsRows {
                    SettingsActionRow(if (g is Gemma.State.Failed) tr("Try again") else tr("Download (2.6 GB)"), labelColor = C.AccentText, first = true) {
                        scope.launch { Gemma.download(context) }
                    }
                }
            }
            g is Gemma.State.WaitingForWifi || g is Gemma.State.Downloading -> {
                val done = (g as? Gemma.State.Downloading)?.done ?: 0L
                Text(
                    if (g is Gemma.State.WaitingForWifi) tr("Waiting for Wi-Fi. The download carries on when you're connected.")
                    else tr("Downloading Gemma… {0} of {1} GB. It keeps going if you close Dropi.", Gemma.gb(done), Gemma.gb(Gemma.SIZE)),
                    style = body,
                )
                ProgressLine(done.toFloat() / Gemma.SIZE)
                SettingsRows {
                    SettingsActionRow(tr("Cancel"), first = true) { scope.launch { Gemma.remove(context) } }
                }
            }
            g is Gemma.State.Verifying -> Text(tr("Almost ready: setting up on-device AI on your phone (about half a minute)…"), style = body)
            g is Gemma.State.Ready -> {
                Text(
                    if (Ai.nano) tr("Gemma is downloaded but not in use. It uses 2.6 GB of storage.")
                    else tr("Gemma is ready. Meal photos and typed questions work on this phone, with nothing uploaded. It uses 2.6 GB of storage."),
                    style = body,
                )
                // Which chip it runs on: the graphics chip is several times faster than the main processor.
                val runsOn = Gemma.backendName ?: Gemma.lastBackend(context)
                Text(
                    if (runsOn != null) tr("Runs on {0}.", Gemma.backendShown(runsOn)) else tr("Runs on: shown after its first answer."),
                    style = mono(13.sp, FontWeight.Medium, if (runsOn?.contains("CPU") == true) C.High else C.TextSecondary),
                )
                SettingsRows {
                    SettingsActionRow(
                        if (confirmRemove) tr("Tap again to remove") else tr("Remove model"),
                        labelColor = if (confirmRemove) C.AccentText else C.Text,
                        first = true,
                    ) {
                        if (confirmRemove) scope.launch { Gemma.remove(context); confirmRemove = false }
                        else confirmRemove = true
                    }
                }
            }
        }
    }
}
