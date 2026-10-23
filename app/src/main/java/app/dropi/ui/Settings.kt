package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import app.dropi.R
import app.dropi.data.Glucose
import app.dropi.data.GlucoseUnit
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch
import app.dropi.work.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsSheet(state: AppState) {
    if (!state.settingsOpen) return
    SlideSheet(onDismiss = { state.settingsOpen = false }) { close -> SettingsContent(state, close) }
}

@Composable
private fun ColumnScope.SettingsContent(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val unit = state.unit
    fun v(mmol: Double) = Glucose.format(mmol, unit)

    StickySheetBody("Settings", close) {

        SheetSection("Units") {
            SlidingSegments(listOf("mmol/L", "mg/dL"), if (unit == GlucoseUnit.MMOL) 0 else 1, Modifier.fillMaxWidth()) {
                state.changeUnit(if (it == 0) GlucoseUnit.MMOL else GlucoseUnit.MGDL)
            }
            SheetHint("Readings are always saved exactly, so switching never changes them.")
        }

        SheetSection("Appearance") {
            val themes = listOf("system", "dark", "light", "dracula")
            ChoiceGrid(
                listOf(
                    Choice("System", R.drawable.lc_settings),
                    Choice("Dark", R.drawable.lc_moon),
                    Choice("Light", R.drawable.lc_sun),
                    Choice("Dracula", R.drawable.lc_droplet),
                ),
                themes.indexOf(state.theme).coerceAtLeast(0),
                columns = 4,
            ) {
                Haptics.segment(view)
                state.changeTheme(themes[it])
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Haptics", style = inter(15.sp, FontWeight.Medium), modifier = Modifier.weight(1f))
                Switch(
                    checked = state.haptics,
                    onCheckedChange = {
                        state.changeHaptics(it)
                        Haptics.toggle(view, it)
                    },
                    colors = dropiSwitchColors(),
                )
            }
        }

        SheetSection("Check times") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${plural(state.slots.count { it.enabled }, "check")} a day", style = inter(15.sp, FontWeight.SemiBold))
                    Text(if (state.remindersOn) "Reminders on" else "Reminders off", style = inter(13.sp, color = C.TextSecondary))
                }
                Chip("Edit", icon = R.drawable.lc_clock) {
                    state.settingsOpen = false
                    state.scheduleOpen = true
                }
            }
        }

        SheetSection("Health Connect") {
            HealthConnectRow(state)
        }

        SheetSection("Live sensor") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dexcom, Libre and other CGMs", style = inter(15.sp, FontWeight.SemiBold))
                    Text(
                        if (state.liveOn) "Live mode on" else "Live number, trend arrow and a heads-up before lows",
                        style = inter(13.sp, color = C.TextSecondary),
                    )
                }
                Chip(if (state.liveOn) "Open" else "Set up", icon = R.drawable.lc_activity) {
                    state.settingsOpen = false
                    state.liveOpen = true
                }
            }
        }

        SheetSection("Share with your care team") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PDF report or CSV", style = inter(15.sp, FontWeight.SemiBold))
                    Text("Summary, time in range and every reading", style = inter(13.sp, color = C.TextSecondary))
                }
                Chip("Share", icon = R.drawable.lc_share) {
                    state.settingsOpen = false
                    state.exportOpen = true
                }
            }
        }

        SheetSection("Your targets") {
            val t = state.targets
            val step = 0.1
            TargetStepper("Fasting and before meals: from", v(t.beforeLow),
                { state.changeTargets(t.copy(beforeLow = t.beforeLow - step)) },
                { state.changeTargets(t.copy(beforeLow = t.beforeLow + step)) })
            TargetStepper("Fasting and before meals: up to", v(t.beforeHigh),
                { state.changeTargets(t.copy(beforeHigh = t.beforeHigh - step)) },
                { state.changeTargets(t.copy(beforeHigh = t.beforeHigh + step)) })
            TargetStepper("1–2 h after a meal: under", v(t.afterHigh),
                { state.changeTargets(t.copy(afterHigh = t.afterHigh - step)) },
                { state.changeTargets(t.copy(afterHigh = t.afterHigh + step)) })
            SheetHint(
                if (t.isDefault) "American Diabetes Association targets for adults, in ${unit.label}. Set the ones your care team gave you."
                else "Your own targets, in ${unit.label}. Colours, insights and reports follow them."
            )
            if (!t.isDefault) Chip("Reset to ADA targets") { state.changeTargets(app.dropi.data.Targets()) }
        }

        SheetSection("Fixed thresholds") {
            TargetLine("Time in range", "${v(Glucose.LOW)}–${v(Glucose.TIR_HIGH)}", C.InRange)
            TargetLine("Low", "under ${v(Glucose.LOW)}", C.Low)
            TargetLine("Urgent low", "under ${v(Glucose.URGENT_LOW)}", C.UrgentLow)
            TargetLine("Very high", "over ${v(Glucose.VERY_HIGH)}", C.VeryHigh)
            SheetHint("International consensus values, the same for everyone so time in range compares with any clinic's.")
        }

        SheetSection("Backup") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Back up or restore", style = inter(15.sp, FontWeight.SemiBold))
                    Text("A password-locked file you keep in Drive or Files", style = inter(13.sp, color = C.TextSecondary))
                }
                Chip("Open", icon = R.drawable.lc_download) {
                    state.settingsOpen = false
                    state.backupOpen = true
                }
            }
        }

        SheetSection("Updates") {
            val ui = UpdateWatch.ui
            val waiting = UpdateWatch.waitingVersion
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Seven taps on the version show the hidden Developer section (demo data).
                var taps by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Dropi ${Updater.installedVersion(context)}",
                        style = inter(15.sp, FontWeight.SemiBold),
                        modifier = Modifier.clickable(
                            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            taps++
                            if (taps >= 7 && !state.devUnlocked) {
                                state.unlockDev(true)
                                Haptics.confirm(view)
                            }
                        },
                    )
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
            Chip("What's changed", icon = R.drawable.lc_history) {
                state.settingsOpen = false
                state.changelogOpen = true
            }
        }

        SheetSection("On-device AI") { OnDeviceAiSection() }

        if (state.devUnlocked) {
            SheetSection("Developer") {
                Text(
                    if (state.demo) "Demo data is on: you're looking at a made-up month. Your real log is safe and comes back when you leave."
                    else "Load a made-up month of readings, meals and a sensor day to try every screen. It's kept apart from your real log, never synced to Health Connect, and never backed up.",
                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.demo) {
                        Chip("Leave demo data", icon = R.drawable.lc_x, selected = true) { state.leaveDemo() }
                        Chip("Fresh demo month", icon = R.drawable.lc_refresh_cw) { state.loadDemo() }
                    } else {
                        Chip("Load demo data", icon = R.drawable.lc_download, selected = true) { state.loadDemo() }
                        Chip("Hide developer") { state.unlockDev(false) }
                    }
                }
            }
        }

        SheetSection("About") {
            Text(
                "Dropi keeps your readings on this phone only. It is a log, not a medical device: it doesn't diagnose or " +
                    "suggest doses. Always follow the plan you agreed with your care team.",
                style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
            )
        }
    }
}

/**
 * Two-way blood glucose sync: switching on asks Health Connect for read and write permission
 * (blood glucose only). Older phones may need the Health Connect app first.
 */
@Composable
private fun HealthConnectRow(state: AppState) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val available = remember { app.dropi.work.HealthSync.available(context) }
    val needsInstall = remember { app.dropi.work.HealthSync.needsInstall(context) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        state.changeHealthSync(granted.containsAll(app.dropi.work.HealthSync.PERMISSIONS))
        if (!granted.containsAll(app.dropi.work.HealthSync.PERMISSIONS)) state.healthNote = "Permission wasn't given"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Sync blood glucose", style = inter(15.sp, FontWeight.SemiBold))
            Text(
                when {
                    !available && needsInstall -> "Install or update Health Connect to use this"
                    !available -> "Health Connect isn't available on this phone"
                    state.healthSync -> state.healthNote ?: "Readings go both ways with Health Connect"
                    else -> state.healthNote ?: "Share readings with apps like a CGM or Google Fit"
                },
                style = inter(13.sp, color = C.TextSecondary),
            )
        }
        Switch(
            checked = state.healthSync,
            enabled = available,
            onCheckedChange = { on ->
                Haptics.toggle(view, on)
                if (!on) state.changeHealthSync(false)
                else scope.launch {
                    if (app.dropi.work.HealthSync.granted(context)) state.changeHealthSync(true)
                    else try { ask.launch(app.dropi.work.HealthSync.PERMISSIONS) } catch (e: Exception) { state.healthNote = "Couldn't open Health Connect" }
                }
            },
            colors = dropiSwitchColors(),
        )
    }
    if (!available && needsInstall) {
        Chip("Get Health Connect", icon = R.drawable.lc_download) {
            val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
            try {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
            } catch (e: Exception) {
                openLink(context, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
            }
        }
    }
    if (state.healthSync) {
        Chip("Sync now", icon = R.drawable.lc_refresh_cw) { scope.launch { state.syncHealth() } }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}

@Composable
fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = SectionLabel)
        content()
    }
}

@Composable
private fun TargetLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = inter(14.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
        Text(value, style = inter(14.sp, FontWeight.Bold, C.Text))
    }
}

/** A target with − and + (0.1 mmol/L a step, shown in the person's unit). */
@Composable
private fun TargetStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = inter(14.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
        Chip("−") { onMinus() }
        Text(value, style = inter(16.sp, FontWeight.Bold, C.Text), modifier = Modifier.padding(horizontal = 8.dp))
        Chip("+") { onPlus() }
    }
}

@Composable
fun SheetHint(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(text, style = inter(12.sp, color = C.TextMuted).copy(lineHeight = 17.sp))
}
