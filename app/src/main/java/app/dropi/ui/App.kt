package app.dropi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.dropi.R
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch

@Composable
fun DropiRoot(state: AppState) {
    // Coming back to the app: reload (the day may have changed) and look for updates.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.onOpen() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!state.ready) return@Box

        TodayPage(state)

        // The log button floats over the list, above the navigation bar.
        AnimatedVisibility(
            visible = state.editing == null && !state.settingsOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PrimaryButton(
                "Log reading",
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                icon = R.drawable.lc_plus,
            ) { state.startNew() }
        }

        AnimatedVisibility(
            visible = state.settingsOpen,
            enter = slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180)),
        ) { SettingsPage(state) }

        LogSheetHost(state)

        // After saving a low or very high reading: what to do now.
        state.alertFor?.let { r ->
            GuidanceDialog(r, state.status(r), state.unit) { state.alertFor = null }
        }

        // The "What's new" window: from the banner, Settings or the update notification.
        val ctx = LocalContext.current
        val ui = UpdateWatch.ui
        val version = UpdateWatch.waitingVersion ?: (ui as? UpdateUi.Ready)?.version
        if (UpdateWatch.whatsNewOpen.value && version != null) {
            val notes = remember(version) { UpdateWatch.notes(ctx) }
            val behind = remember(version) { UpdateWatch.behind(ctx) }
            WhatsNewDialog(version, notes, behind) { UpdateWatch.whatsNewOpen.value = false }
        }
    }
}
