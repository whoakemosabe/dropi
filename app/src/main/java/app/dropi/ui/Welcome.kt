package app.dropi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.dropi.R
import app.dropi.tr
import app.dropi.data.GlucoseUnit
import app.dropi.work.Alerts
import app.dropi.work.HealthSync
import app.dropi.work.Ai
import app.dropi.work.Gemma
import app.dropi.work.Nano
import kotlinx.coroutines.launch

private enum class Step { WELCOME, UNITS, NOTIFY, HEALTH, UPDATES, AI, DONE }

/**
 * First run: a short welcome, the unit, then every permission Dropi can use, one at a time with
 * what it's for (notifications, Health Connect, installing its own updates) and the on-device
 * AI download. Each can be skipped; Settings has them all later.
 */
@Composable
fun WelcomeFlow(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.WELCOME) }
    var forward by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { Ai.refresh(context) }

    val hcAvailable = remember { HealthSync.available(context) }
    val hcInstall = remember { HealthSync.needsInstall(context) }

    fun skipped(s: Step): Boolean = when (s) {
        Step.HEALTH -> !hcAvailable && !hcInstall
        Step.AI -> Nano.state == Nano.State.UNAVAILABLE && Gemma.state is Gemma.State.Unsupported
        else -> false
    }
    fun go(delta: Int) {
        var i = step.ordinal + delta
        while (i in Step.entries.indices && skipped(Step.entries[i])) i += delta
        if (i in Step.entries.indices) {
            forward = delta > 0
            step = Step.entries[i]
        }
    }
    BackHandler(enabled = step != Step.WELCOME) { go(-1) }

    // What's been allowed so far, rechecked whenever the person comes back from a system screen.
    var notifyOk by remember { mutableStateOf(Alerts.canNotify(context)) }
    var installOk by remember { mutableStateOf(canInstall(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifyOk = Alerts.canNotify(context)
        installOk = canInstall(context)
    }
    var hcNote by remember { mutableStateOf<String?>(null) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifyOk = Alerts.canNotify(context)
        if (granted) state.changeReminders(true)
        go(1)
    }
    val askHealth = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.containsAll(HealthSync.PERMISSIONS)
        state.changeHealthSync(ok)
        if (ok) go(1) else hcNote = tr("Health Connect access wasn't given. You can turn it on later in Settings.")
    }
    val openInstallSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installOk = canInstall(context)
        if (installOk) go(1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            // Nothing underneath reacts while the welcome is up.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        AuroraGlow(Modifier.fillMaxWidth().height(620.dp))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .wrapContentWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp),
        ) {
            // Progress: one segment per step after the welcome.
            if (step != Step.WELCOME) {
                val steps = Step.entries.filter { it != Step.WELCOME && !skipped(it) }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEach { s ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (s.ordinal <= step.ordinal) C.AccentText else C.Border),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(160)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "welcome",
            ) { s ->
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (s) {
                            Step.WELCOME -> {
                                Badge(R.drawable.lc_droplet, big = true)
                                // First choice: the language, written in both so either reader finds theirs.
                                Text("Language · Tungumál", style = SectionLabel)
                                LanguagePicker(Modifier.fillMaxWidth(), height = 52.dp)
                                Text(tr("Welcome to Dropi"), style = heading(36.sp).copy(lineHeight = 40.sp))
                                Text(
                                    tr("Your blood sugar, logged in seconds. Reminders at your check times, trends that make sense, and what your meals do to you."),
                                    style = body(),
                                )
                                Point(R.drawable.lc_bookmark, tr("Everything stays on this phone. No account, nothing uploaded."))
                                Point(R.drawable.lc_info, tr("Dropi is a log, not a medical device. Follow the plan you agreed with your care team."))
                                Text(tr("Next: a few quick choices and what Dropi needs your OK for."), style = inter(15.sp, color = C.TextMuted))
                            }
                            Step.UNITS -> {
                                Badge(R.drawable.lc_droplet)
                                Title(tr("How does your meter show it?"))
                                Text(tr("Readings are always saved exactly, so you can switch later without changing them."), style = body())
                                Spacer(Modifier.height(4.dp))
                                SlidingSegments(
                                    listOf("mmol/L", "mg/dL"),
                                    if (state.unit == GlucoseUnit.MMOL) 0 else 1,
                                    Modifier.fillMaxWidth(),
                                    height = 52.dp,
                                ) { state.changeUnit(if (it == 0) GlucoseUnit.MMOL else GlucoseUnit.MGDL) }
                                Text(
                                    if (state.unit == GlucoseUnit.MMOL) tr("Like 6.4, used in Europe, the UK, Canada and Australia.")
                                    else tr("Like 115, used in the US and much of Latin America and Asia."),
                                    style = inter(15.sp, color = C.TextMuted),
                                )
                            }
                            Step.NOTIFY -> {
                                Badge(R.drawable.lc_clock)
                                Title(tr("Notifications"))
                                Text(tr("Dropi only uses them for things you'd want to know:"), style = body())
                                Point(R.drawable.lc_clock, tr("Reminders at your check times, and a check after you eat"))
                                Point(R.drawable.lc_circle_alert, tr("A re-check reminder after a low"))
                                Point(R.drawable.lc_activity, tr("Your live sensor value and a heads-up before lows, if you use a CGM"))
                                Point(R.drawable.lc_download, tr("When an update is ready"))
                                if (notifyOk) Allowed(tr("Notifications are on"))
                            }
                            Step.HEALTH -> {
                                Badge(R.drawable.lc_heart_pulse)
                                Title("Health Connect")
                                Text(
                                    tr("Share blood glucose with other apps, both ways. Readings from a CGM or another meter app come into Dropi, and yours go out. Blood glucose only, nothing else."),
                                    style = body(),
                                )
                                if (!hcAvailable) Text(tr("Health Connect needs installing or updating on this phone first."), style = inter(15.sp, color = C.TextMuted))
                                if (state.healthSync) Allowed(tr("Connected"))
                                hcNote?.let { Text(it, style = inter(15.sp, color = C.High)) }
                            }
                            Step.UPDATES -> {
                                Badge(R.drawable.lc_download)
                                Title(tr("Updates in one tap"))
                                Text(
                                    tr("Dropi isn't from the Play Store, so Android asks once whether it may install its own updates. Turn on \"Allow from this source\" on the next screen, then come back."),
                                    style = body(),
                                )
                                Point(R.drawable.lc_check, tr("Only Dropi's own signed updates, checked before installing"))
                                if (installOk) Allowed(tr("Allowed"))
                            }
                            Step.AI -> {
                                Badge(R.drawable.lc_message_circle)
                                Title(tr("On-device AI"))
                                if (Ai.nano || Ai.checking) {
                                    Text(
                                        tr("Your phone can run Gemini Nano, Google's on-device model. Dropi uses it to guess the foods and carbs in a photo of your plate, and to answer questions about your numbers in your own words."),
                                        style = body(),
                                    )
                                    Point(R.drawable.lc_bookmark, tr("It runs on the phone. Photos and questions are never uploaded."))
                                    Point(R.drawable.lc_download, tr("Android downloads it once, in the background."))
                                } else {
                                    Text(
                                        tr("Dropi can download Gemma 4, Google's open on-device model, to guess the foods and carbs in a photo of your plate and answer questions about your numbers in your own words."),
                                        style = body(),
                                    )
                                    Point(R.drawable.lc_bookmark, tr("It runs on the phone. Photos and questions are never uploaded."))
                                    Point(R.drawable.lc_download, tr("A one-time 2.6 GB download, on Wi-Fi only. You can remove it in Settings."))
                                }
                                when {
                                    Nano.state == Nano.State.READY -> Allowed(tr("Gemini Nano is ready"))
                                    Nano.state == Nano.State.DOWNLOADING -> Allowed(tr("Downloading. It'll finish in the background."))
                                    Ai.checking -> Text(tr("Checking this phone…"), style = inter(15.sp, color = C.TextMuted))
                                    Ai.nano -> {}
                                    Gemma.state == Gemma.State.Ready -> Allowed(tr("Gemma is ready"))
                                    Gemma.state is Gemma.State.Downloading || Gemma.state == Gemma.State.Verifying ->
                                        Allowed(tr("Downloading. It'll finish in the background."))
                                    Gemma.state == Gemma.State.WaitingForWifi -> Allowed(tr("Waiting for Wi-Fi, then it downloads by itself"))
                                    Gemma.state is Gemma.State.Failed -> Text((Gemma.state as Gemma.State.Failed).shown, style = inter(15.sp, color = C.High))
                                }
                            }
                            Step.DONE -> {
                                Badge(R.drawable.lc_check, big = true)
                                Title(tr("You're all set"))
                                Text(tr("Here's what Dropi can do on this phone:"), style = body())
                                CheckLine(tr("Reminders and alerts"), notifyOk)
                                if (hcAvailable || hcInstall) CheckLine(tr("Health Connect sync"), state.healthSync)
                                CheckLine(tr("One-tap updates"), installOk)
                                if (!skipped(Step.AI)) {
                                    val g = Gemma.state
                                    CheckLine(
                                        tr("On-device AI"),
                                        Nano.state == Nano.State.READY || Nano.state == Nano.State.DOWNLOADING || g == Gemma.State.Ready ||
                                            g is Gemma.State.Downloading || g == Gemma.State.WaitingForWifi || g == Gemma.State.Verifying,
                                    )
                                }
                                Text(
                                    tr("Anything you skipped is in Settings. Using a CGM? Settings › Live sensor connects it."),
                                    style = inter(15.sp, color = C.TextMuted).copy(lineHeight = 20.sp),
                                )
                            }
                        }
                    }

                    // The step's buttons, always at the bottom.
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (s) {
                            Step.WELCOME -> PrimaryButton(tr("Get started"), Modifier.fillMaxWidth()) { go(1) }
                            Step.UNITS -> PrimaryButton(tr("Continue"), Modifier.fillMaxWidth()) { go(1) }
                            Step.NOTIFY -> {
                                if (notifyOk) {
                                    PrimaryButton(tr("Continue"), Modifier.fillMaxWidth()) {
                                        state.changeReminders(true)
                                        go(1)
                                    }
                                } else {
                                    PrimaryButton(tr("Allow notifications"), Modifier.fillMaxWidth(), icon = R.drawable.lc_check) {
                                        val needsAsk = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                        if (needsAsk) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else openAppNotifications(context)
                                    }
                                    GhostButton(tr("Not now"), Modifier.fillMaxWidth()) { go(1) }
                                }
                            }
                            Step.HEALTH -> {
                                if (state.healthSync) {
                                    PrimaryButton(tr("Continue"), Modifier.fillMaxWidth()) { go(1) }
                                } else if (!hcAvailable) {
                                    PrimaryButton(tr("Get Health Connect"), Modifier.fillMaxWidth(), icon = R.drawable.lc_download) {
                                        openStore(context)
                                    }
                                    GhostButton(tr("Skip"), Modifier.fillMaxWidth()) { go(1) }
                                } else {
                                    PrimaryButton(tr("Connect Health Connect"), Modifier.fillMaxWidth(), icon = R.drawable.lc_heart_pulse) {
                                        scope.launch {
                                            if (HealthSync.granted(context)) {
                                                state.changeHealthSync(true)
                                                go(1)
                                            } else {
                                                try { askHealth.launch(HealthSync.PERMISSIONS) } catch (e: Exception) { hcNote = tr("Couldn't open Health Connect.") }
                                            }
                                        }
                                    }
                                    GhostButton(tr("Skip"), Modifier.fillMaxWidth()) { go(1) }
                                }
                            }
                            Step.UPDATES -> {
                                if (installOk) {
                                    PrimaryButton(tr("Continue"), Modifier.fillMaxWidth()) { go(1) }
                                } else {
                                    PrimaryButton(tr("Allow updates"), Modifier.fillMaxWidth(), icon = R.drawable.lc_download) {
                                        try {
                                            openInstallSettings.launch(
                                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                                            )
                                        } catch (e: Exception) { go(1) }
                                    }
                                    GhostButton(tr("Not now"), Modifier.fillMaxWidth()) { go(1) }
                                }
                            }
                            Step.AI -> {
                                val offerNano = Nano.state == Nano.State.NEEDS_DOWNLOAD
                                val offerGemma = !Ai.nano && !Ai.checking &&
                                    (Gemma.state == Gemma.State.NotDownloaded || Gemma.state is Gemma.State.Failed)
                                when {
                                    offerNano -> {
                                        PrimaryButton(tr("Download Gemini Nano"), Modifier.fillMaxWidth(), icon = R.drawable.lc_download) {
                                            Nano.startDownload()
                                        }
                                        GhostButton(tr("Not now"), Modifier.fillMaxWidth()) { go(1) }
                                    }
                                    offerGemma -> {
                                        PrimaryButton(tr("Download Gemma (2.6 GB)"), Modifier.fillMaxWidth(), icon = R.drawable.lc_download) {
                                            scope.launch { Gemma.download(context) }
                                        }
                                        GhostButton(tr("Not now"), Modifier.fillMaxWidth()) { go(1) }
                                    }
                                    else -> PrimaryButton(tr("Continue"), Modifier.fillMaxWidth()) { go(1) }
                                }
                            }
                            Step.DONE -> PrimaryButton(tr("Start logging"), Modifier.fillMaxWidth(), icon = R.drawable.lc_droplet) {
                                state.finishOnboarding()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun canInstall(context: android.content.Context): Boolean =
    try { context.packageManager.canRequestPackageInstalls() } catch (e: Exception) { false }

private fun openAppNotifications(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    } catch (e: Exception) { }
}

private fun openStore(context: android.content.Context) {
    val uri = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()))
        } catch (e: Exception) { }
    }
}

@Composable
private fun body() = inter(17.sp, color = C.TextSecondary).copy(lineHeight = 24.sp)

@Composable
private fun Title(text: String) {
    Text(text, style = heading(30.sp).copy(lineHeight = 36.sp))
}

/** The step's icon on a tile of aurora light. */
@Composable
private fun Badge(icon: Int, big: Boolean = false) {
    val size = if (big) 72.dp else 56.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (big) 22.dp else 18.dp)).background(Brush.linearGradient(C.Aurora)),
        contentAlignment = Alignment.Center,
    ) { Ic(icon, androidx.compose.ui.graphics.Color.White, if (big) 34.dp else 26.dp) }
}

@Composable
private fun Point(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Ic(icon, C.AccentText, 18.dp, Modifier.padding(top = 4.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(15.sp, color = C.Text).copy(lineHeight = 22.sp))
    }
}

@Composable
private fun Allowed(text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(C.InRange.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_check, C.InRange, 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = inter(15.sp, FontWeight.SemiBold, C.Text))
    }
}

@Composable
private fun CheckLine(label: String, on: Boolean) {
    Row(Modifier.fillMaxWidth().card(RoundedCornerShape(Corner.M)).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (on) {
            Ic(R.drawable.lc_check, C.InRange, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(tr("On"), style = inter(15.sp, FontWeight.SemiBold, C.InRange))
        } else {
            Text(tr("Skipped"), style = inter(15.sp, color = C.TextMuted))
        }
    }
}
