package app.dropi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch
import app.dropi.work.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(state: AppState) {
    BackHandler { state.settingsOpen = false }
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val bars = WindowInsets.systemBars.asPaddingValues()
    val unit = state.unit
    fun v(mmol: Double) = Glucose.format(mmol, unit)

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.SurfaceLight).tap { state.settingsOpen = false },
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.lc_chevron_left, C.TextSecondary, 22.dp, contentDescription = "Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = inter(24.sp, FontWeight.ExtraBold))
        }

        Section("Units") {
            SlidingSegments(listOf("mmol/L", "mg/dL"), if (unit == GlucoseUnit.MMOL) 0 else 1, Modifier.fillMaxWidth()) {
                state.setUnit(if (it == 0) GlucoseUnit.MMOL else GlucoseUnit.MGDL)
            }
            Hint("Readings are always saved exactly, so switching never changes them.")
        }

        Section("Appearance") {
            val themes = listOf("system", "dark", "light")
            SlidingSegments(listOf("System", "Dark", "Light"), themes.indexOf(state.theme).coerceAtLeast(0), Modifier.fillMaxWidth()) {
                state.setTheme(themes[it])
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Haptics", style = inter(15.sp, FontWeight.Medium), modifier = Modifier.weight(1f))
                Switch(
                    checked = state.haptics,
                    onCheckedChange = {
                        state.setHaptics(it)
                        Haptics.toggle(view, it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White),
                )
            }
        }

        Section("Targets") {
            TargetLine("Fasting and before meals", "${v(state.targets.beforeLow)}–${v(state.targets.beforeHigh)}")
            TargetLine("1–2 h after a meal", "under ${v(state.targets.afterHigh)}")
            TargetLine("Time in range", "${v(Glucose.LOW)}–${v(Glucose.TIR_HIGH)}")
            TargetLine("Low", "under ${v(Glucose.LOW)}", C.Low)
            TargetLine("Urgent low", "under ${v(Glucose.URGENT_LOW)}", C.UrgentLow)
            TargetLine("Very high", "over ${v(Glucose.VERY_HIGH)}", C.VeryHigh)
            Hint("American Diabetes Association targets for adults, in ${unit.label}. Your care team may set different ones.")
        }

        Section("Updates") {
            val ui = UpdateWatch.ui
            val waiting = UpdateWatch.waitingVersion
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dropi ${Updater.installedVersion(context)}", style = inter(15.sp, FontWeight.SemiBold))
                    Text(
                        when {
                            ui is UpdateUi.Checking -> "Checking…"
                            ui is UpdateUi.Downloading -> "Downloading… ${(UpdateWatch.progress * 100).toInt()}%"
                            ui is UpdateUi.Ready -> "Downloaded. Ready to install"
                            ui is UpdateUi.Error -> ui.message
                            waiting != null -> "Dropi $waiting is ready"
                            ui is UpdateUi.UpToDate -> "You're up to date"
                            else -> "Checks GitHub every few hours"
                        },
                        style = inter(13.sp, color = if (ui is UpdateUi.Error) C.Low else C.TextSecondary),
                    )
                }
                if (waiting != null || ui is UpdateUi.Ready) {
                    Chip("See what's new", selected = true) { UpdateWatch.whatsNewOpen.value = true }
                } else {
                    Chip("Check now", icon = R.drawable.lc_refresh_cw) {
                        scope.launch { UpdateWatch.checkOnly(context) }
                    }
                }
            }
        }

        Section("About") {
            Text(
                "Dropi keeps your readings on this phone only. It is a log, not a medical device: it doesn't diagnose or " +
                    "suggest doses. Always follow the plan you agreed with your care team.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = SectionLabel)
        content()
    }
}

@Composable
private fun TargetLine(label: String, value: String, color: Color = C.Text) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = inter(14.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
        Text(value, style = inter(14.sp, FontWeight.Bold, color))
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(text, style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp))
}
