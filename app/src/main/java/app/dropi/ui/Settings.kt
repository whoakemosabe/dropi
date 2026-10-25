package app.dropi.ui

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
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

    StickySheetBody(tr("Settings"), close) {

        // Always in both languages, so it can be found from either.
        SheetSection("Language · Tungumál") {
            LanguagePicker(Modifier.fillMaxWidth())
        }

        SheetSection(tr("Units")) {
            SlidingSegments(listOf("mmol/L", "mg/dL"), if (unit == GlucoseUnit.MMOL) 0 else 1, Modifier.fillMaxWidth()) {
                state.changeUnit(if (it == 0) GlucoseUnit.MMOL else GlucoseUnit.MGDL)
            }
            SheetHint(tr("Readings are always saved exactly, so switching never changes them."))
        }

        SheetSection(tr("Appearance")) {
            val themes = listOf("system", "dark", "light", "dracula")
            ChoiceGrid(
                listOf(
                    Choice(tr("System"), R.drawable.lc_settings),
                    Choice(tr("Dark"), R.drawable.lc_moon),
                    Choice(tr("Light"), R.drawable.lc_sun),
                    Choice("Dracula", R.drawable.lc_droplet),
                ),
                themes.indexOf(state.theme).coerceAtLeast(0),
                columns = 4,
            ) {
                Haptics.segment(view)
                state.changeTheme(themes[it])
            }
            SettingsToggleRow(tr("Haptics"), null, state.haptics) {
                state.changeHaptics(it)
                Haptics.toggle(view, it)
            }
        }

        SheetSection(tr("Check times")) {
            SettingsRows {
                SettingsActionRow(
                    trn(state.slots.count { it.enabled }, "{0} check a day", "{0} checks a day"),
                    hint = if (state.remindersOn) tr("Reminders on") else tr("Reminders off"),
                    action = tr("Edit"),
                ) {
                    state.settingsOpen = false
                    state.scheduleOpen = true
                }
            }
        }

        SheetSection("Health Connect") {
            HealthConnectRow(state)
        }

        SheetSection(tr("Live sensor")) {
            SettingsRows {
                SettingsActionRow(
                    tr("Dexcom, Libre and other CGMs"),
                    hint = if (state.liveOn) tr("Live mode on") else tr("Live number, trend arrow and a heads-up before lows"),
                    action = if (state.liveOn) tr("Open") else tr("Set up"),
                ) {
                    state.settingsOpen = false
                    state.liveOpen = true
                }
            }
        }

        SheetSection(tr("Share with your care team")) {
            SettingsRows {
                SettingsActionRow(tr("PDF report or CSV"), hint = tr("Summary, time in range and every reading"), action = tr("Share")) {
                    state.settingsOpen = false
                    state.exportOpen = true
                }
            }
        }

        SheetSection(tr("Your targets")) {
            val t = state.targets
            val step = 0.1
            SettingsRows {
                TargetStepper(tr("On waking and before eating: from"), v(t.beforeLow),
                    { state.changeTargets(t.copy(beforeLow = t.beforeLow - step)) },
                    { state.changeTargets(t.copy(beforeLow = t.beforeLow + step)) })
                TargetStepper(tr("On waking and before eating: up to"), v(t.beforeHigh),
                    { state.changeTargets(t.copy(beforeHigh = t.beforeHigh - step)) },
                    { state.changeTargets(t.copy(beforeHigh = t.beforeHigh + step)) })
                TargetStepper(tr("1–2 h after eating: under"), v(t.afterHigh),
                    { state.changeTargets(t.copy(afterHigh = t.afterHigh - step)) },
                    { state.changeTargets(t.copy(afterHigh = t.afterHigh + step)) })
                if (!t.isDefault) SettingsActionRow(tr("Reset to ADA targets")) { state.changeTargets(app.dropi.data.Targets()) }
            }
            SheetHint(
                if (t.isDefault) tr("American Diabetes Association targets for adults, in {0}. Set the ones your care team gave you.", unit.label)
                else tr("Your own targets, in {0}. Colours, insights and reports follow them.", unit.label)
            )
        }

        SheetSection(tr("Fixed thresholds")) {
            SettingsRows {
                TargetLine(tr("Time in range"), "${v(Glucose.LOW)}–${v(Glucose.TIR_HIGH)}", C.InRange)
                TargetLine(tr("Low"), tr("under {0}", v(Glucose.LOW)), C.Low)
                TargetLine(tr("Urgent low"), tr("under {0}", v(Glucose.URGENT_LOW)), C.UrgentLow)
                TargetLine(tr("Very high"), tr("over {0}", v(Glucose.VERY_HIGH)), C.VeryHigh)
            }
            SheetHint(tr("International consensus values, the same for everyone so time in range compares with any clinic's."))
        }

        SheetSection(tr("Backup")) {
            SettingsRows {
                SettingsActionRow(tr("Back up or restore"), hint = tr("A password-locked file you keep in Drive or Files"), action = tr("Open")) {
                    state.settingsOpen = false
                    state.backupOpen = true
                }
            }
        }

        SheetSection(tr("Updates")) {
            val ui = UpdateWatch.ui
            val waiting = UpdateWatch.waitingVersion
            // Seven taps on the version show the hidden Developer section (demo data).
            val taps = remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val status = when {
                ui is UpdateUi.Checking -> tr("Checking…")
                ui is UpdateUi.Downloading -> tr("Downloading… {0}%", (UpdateWatch.progress * 100).toInt())
                ui is UpdateUi.Ready -> tr("Downloaded. Ready to install")
                ui is UpdateUi.Error -> ui.message
                waiting != null -> tr("Dropi {0} is ready", waiting)
                ui is UpdateUi.UpToDate -> tr("You're up to date")
                else -> tr("Checks GitHub every few hours")
            }
            SettingsRows {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .ruleTop()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            taps.intValue++
                            if (taps.intValue >= 7 && !state.devUnlocked) {
                                state.unlockDev(true)
                                Haptics.confirm(view)
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dropi", style = inter(15.sp, FontWeight.SemiBold))
                        Text(status, style = inter(13.sp, color = if (ui is UpdateUi.Error) C.Low else C.TextMuted).copy(lineHeight = 18.sp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Updater.installedVersion(context), style = mono(13.sp, FontWeight.Medium, C.Text), maxLines = 1)
                }
                if (waiting != null || ui is UpdateUi.Ready) {
                    SettingsActionRow(tr("See what's new"), labelColor = C.AccentText) { UpdateWatch.whatsNewOpen.value = true }
                } else {
                    SettingsActionRow(tr("Check now")) { scope.launch { UpdateWatch.checkOnly(context) } }
                }
                SettingsActionRow(tr("What's changed")) {
                    state.settingsOpen = false
                    state.changelogOpen = true
                }
            }
        }

        SheetSection(tr("On-device AI")) { OnDeviceAiSection() }

        if (state.devUnlocked) {
            SheetSection(tr("Developer")) {
                Text(
                    if (state.demo) tr("Demo data is on: you're looking at a made-up month. Your real log is safe and comes back when you leave.")
                    else tr("Load a made-up month of readings, meals and a sensor day to try every screen. It's kept apart from your real log, never synced to Health Connect, and never backed up."),
                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                )
                SettingsRows {
                    if (state.demo) {
                        SettingsActionRow(tr("Leave demo data"), labelColor = C.AccentText) { state.leaveDemo() }
                        SettingsActionRow(tr("Fresh demo month")) { state.loadDemo() }
                    } else {
                        SettingsActionRow(tr("Load demo data"), labelColor = C.AccentText) { state.loadDemo() }
                        SettingsActionRow(tr("Hide developer")) { state.unlockDev(false) }
                    }
                }
            }
        }

        SheetSection(tr("About")) {
            Text(
                tr("Dropi keeps your readings on this phone only. It is a log, not a medical device: it doesn't diagnose or suggest doses. Always follow the plan you agreed with your care team."),
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
        if (!granted.containsAll(app.dropi.work.HealthSync.PERMISSIONS)) state.healthNote = tr("Permission wasn't given")
    }
    val note = when {
        !available && needsInstall -> tr("Install or update Health Connect to use this")
        !available -> tr("Health Connect isn't available on this phone")
        state.healthSync -> state.healthNote ?: tr("Readings go both ways with Health Connect")
        else -> state.healthNote ?: tr("Share readings with apps like a CGM or Google Fit")
    }
    SettingsRows {
        SettingsToggleRow(tr("Sync blood glucose"), note, state.healthSync, enabled = available) { on ->
            Haptics.toggle(view, on)
            if (!on) state.changeHealthSync(false)
            else scope.launch {
                if (app.dropi.work.HealthSync.granted(context)) state.changeHealthSync(true)
                else try { ask.launch(app.dropi.work.HealthSync.PERMISSIONS) } catch (e: Exception) { state.healthNote = tr("Couldn't open Health Connect") }
            }
        }
        if (!available && needsInstall) {
            SettingsActionRow(tr("Get Health Connect")) {
                val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
                } catch (e: Exception) {
                    openLink(context, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                }
            }
        }
        if (state.healthSync) {
            SettingsActionRow(tr("Sync now")) { scope.launch { state.syncHealth() } }
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}

@Composable
fun SheetSection(title: String, content: @Composable () -> Unit) {
    // 1.1: a ruled section (name in mono capitals, a hairline to the edge), no card.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RuleHeader(title, modifier = Modifier.padding(bottom = 0.dp))
        content()
    }
}

/** Rows inside a section, with no gap between them: each row brings its own hairline. */
@Composable
fun SettingsRows(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

/**
 * A ruled settings row that opens something: the label (and a hint) on the left, and on the
 * right what a tap does, in mono capitals with an arrow ("EDIT →"), or just the arrow.
 */
@Composable
fun SettingsActionRow(
    label: String,
    hint: String? = null,
    action: String? = null,
    labelColor: Color = C.Text,
    hintColor: Color = C.TextMuted,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .ruleTop()
            // Screen readers hear the label, then "double-tap to edit" (or open, share…).
            .clickable(onClickLabel = action, role = Role.Button) {
                Haptics.tap(view)
                onClick()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = inter(15.sp, FontWeight.SemiBold, labelColor))
            if (hint != null) Text(hint, style = inter(13.sp, color = hintColor).copy(lineHeight = 18.sp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (action != null) caps(action) + " →" else "→",
            style = mono(12.sp, FontWeight.Medium, C.TextSecondary, 0.06f),
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A ruled on/off row: label and hint on the left, the switch on the right; the whole row toggles. */
@Composable
fun SettingsToggleRow(
    label: String,
    hint: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .ruleTop()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = inter(15.sp, FontWeight.SemiBold, if (enabled) C.Text else C.TextMuted))
            if (hint != null) Text(hint, style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 18.sp))
        }
        Spacer(Modifier.width(12.dp))
        // The row handles the tap (and TalkBack's "switch, on"), so the switch itself is just the picture.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = dropiSwitchColors())
    }
}

/** A fixed threshold: its range square, its name, and the value in mono. */
@Composable
private fun TargetLine(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).ruleTop().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RangeSquare(color)
        Spacer(Modifier.width(12.dp))
        Text(label, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = mono(13.sp, FontWeight.Medium, C.Text), maxLines = 1)
    }
}

/** A target with − and + (0.1 mmol/L a step, shown in the person's unit). */
@Composable
private fun TargetStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).ruleTop().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f).padding(end = 8.dp))
        StepButton("−", tr("Lower {0}", label), onMinus)
        Text(
            value,
            style = mono(15.sp, FontWeight.SemiBold, C.Text),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 4.dp),
        )
        StepButton("+", tr("Raise {0}", label), onPlus)
    }
}

/** A small squared − or + with a full 48 dp touch area. */
@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corner.S)
    Box(
        Modifier
            .size(48.dp)
            .tap(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(shape).border(1.dp, C.Border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = mono(17.sp, FontWeight.Medium, C.Text), modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
fun SheetHint(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 17.sp))
}
