package app.dropi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.dropi.R
import app.dropi.data.MealTag
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch

@Composable
fun DropiRoot(state: AppState) {
    // Coming back to the app: reload (the day may have changed) and look for updates.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.onOpen() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!state.ready) return@Box

        // Today and Trends, cross-fading; back on Trends returns to Today.
        Crossfade(state.tab, animationSpec = tween(220), label = "tab") { t ->
            when (t) {
                Tab.TODAY -> TodayPage(state)
                Tab.TRENDS -> TrendsPage(state)
            }
        }
        if (state.tab != Tab.TODAY) BackHandler { state.tab = Tab.TODAY }

        if (C.dracula) BloodDrips(Modifier.align(Alignment.TopCenter))

        // The tab bar floats over the page, above the navigation bar, with the big + in the middle.
        AnimatedVisibility(
            visible = state.editing == null && !state.settingsOpen && !state.scheduleOpen && !state.exportOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { TabBar(state) }

        SettingsSheet(state)
        ScheduleSheet(state)
        ExportSheet(state)
        LogSheetHost(state)

        // After saving a low or very high reading: what to do now.
        state.alertFor?.let { r ->
            GuidanceDialog(r, state.status(r), state.unit, onRecheck = { state.remindRecheck(r) }) { state.alertFor = null }
        }

        // A reminder notification or the widget's + asked for the log sheet.
        val want = Launch.logTag.value
        LaunchedEffect(want) {
            if (want != null) {
                Launch.logTag.value = null
                state.settingsOpen = false
                state.scheduleOpen = false
                state.exportOpen = false
                state.startNew(if (want == "new") null else MealTag.fromKey(want))
            }
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

enum class Tab { TODAY, TRENDS }

@Composable
private fun TabBar(state: AppState) {
    val view = LocalView.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(32.dp))
            .background(C.Card)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(32.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("Today", R.drawable.lc_droplet, state.tab == Tab.TODAY, Modifier.weight(1f)) {
            if (state.tab != Tab.TODAY) Haptics.segment(view)
            state.tab = Tab.TODAY
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(C.Accent, C.AccentGlow)))
                .tap { state.startNew() },
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_plus, Color.White, 26.dp, contentDescription = "Log reading") }
        TabItem("Trends", R.drawable.lc_chart, state.tab == Tab.TRENDS, Modifier.weight(1f)) {
            if (state.tab != Tab.TRENDS) Haptics.segment(view)
            state.tab = Tab.TRENDS
        }
    }
}

@Composable
private fun TabItem(label: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) C.AccentText else C.TextMuted
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ic(icon, tint, 22.dp)
        Text(label, style = inter(11.sp, FontWeight.SemiBold, tint))
    }
}

/** Requests from outside the app (a reminder, the widget) waiting for the screen. */
object Launch {
    /** A MealTag name, or "new" to let the time of day pick the moment. */
    val logTag = mutableStateOf<String?>(null)
}
