package app.dropi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch
import kotlinx.coroutines.launch

/**
 * F1 Pro's update banner across the top when a new version is waiting. It appears on its own;
 * Update opens the "What's new" window (download and install happen there), and once the
 * download is in, a tap on the banner installs. × hides it until the next version.
 */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val v = UpdateWatch.waitingVersion
    val ui = UpdateWatch.ui
    val readyVersion = (ui as? UpdateUi.Ready)?.version
    val shownVersion = v ?: readyVersion
    AnimatedVisibility(
        visible = shownVersion != null && UpdateWatch.bannerHidden != shownVersion,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
    ) {
        LaunchedEffect(Unit) { Haptics.settle(view) }
        fun act() {
            if (ui is UpdateUi.Ready) {
                scope.launch { UpdateWatch.tap(context) }
            } else {
                UpdateWatch.whatsNewOpen.value = true
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Card)
                .background(Brush.horizontalGradient(listOf(C.Accent.copy(alpha = 0.18f), C.AccentGlow.copy(alpha = 0.06f))))
                .border(1.dp, C.Accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .tap { act() }
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent),
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.lc_download, C.OnAccent, 16.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dropi $shownVersion is ready", style = inter(14.sp, FontWeight.Bold, C.Text))
                    Text(
                        when (ui) {
                            UpdateUi.Idle, UpdateUi.UpToDate, is UpdateUi.Available -> "See what's new"
                            UpdateUi.Checking -> "Checking…"
                            is UpdateUi.Downloading -> "Downloading… ${(UpdateWatch.progress * 100).toInt()}%"
                            is UpdateUi.Ready -> ui.note ?: "Downloaded. Tap to install"
                            is UpdateUi.Error -> ui.message
                        },
                        style = inter(12.sp, color = C.TextSecondary),
                    )
                }
                if (ui !is UpdateUi.Downloading) {
                    Text(
                        if (ui is UpdateUi.Ready) "Install" else "Update",
                        style = inter(13.sp, FontWeight.Bold, C.OnAccent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(C.Accent)
                            .tap { act() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "×",
                    style = inter(22.sp, FontWeight.Light, C.TextMuted),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Hide this update") {
                            Haptics.tap(view)
                            UpdateWatch.bannerHidden = shownVersion
                        }
                        .semantics { contentDescription = "Hide" },
                )
            }
            if (ui is UpdateUi.Downloading) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(end = 8.dp)) { ProgressLine(UpdateWatch.progress) }
            }
        }
    }
}

/**
 * "What's new in v1.0.12": the waiting version's highlights (all of them since your version,
 * if you skipped some), then Update now → download progress → Install, all in the window.
 * Closing it keeps the download going; the banner carries on from there.
 */
@Composable
fun WhatsNewDialog(version: String, notes: List<String>, behind: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ui = UpdateWatch.ui
    val lines = notes.ifEmpty { listOf("Fixes and improvements") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(24.dp)
        // The card rises in, then the lines arrive one after another.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 24.dp.toPx()
                    scaleX = 0.96f + 0.04f * enter.value
                    scaleY = scaleX
                }
                .clip(shape)
                .background(C.Card)
                .border(1.dp, C.Accent.copy(alpha = 0.45f), shape),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(C.Accent.copy(alpha = 0.26f), Color.Transparent)))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(C.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Ic(R.drawable.lc_zap, C.OnAccent, 16.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text("Update ready", style = SectionLabel.copy(color = C.AccentText), modifier = Modifier.weight(1f))
                    if (behind > 1) {
                        Text(
                            (if (behind >= 30) "30+" else "$behind") + " updates behind",
                            style = inter(11.sp, FontWeight.SemiBold, C.High),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(C.High.copy(alpha = 0.12f))
                                .border(1.dp, C.High.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    if (behind > 1) "What's new since your version" else "What's new in v$version",
                    style = heading(22.sp),
                )
                if (behind > 1) Text("Installing v$version brings you fully up to date.", style = inter(12.sp, color = C.TextSecondary))
            }

            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lines.forEachIndexed { i, n -> Highlight(n, i) }
            }

            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (ui) {
                    is UpdateUi.Downloading -> {
                        Text(
                            "Downloading… ${(UpdateWatch.progress * 100).toInt()}%",
                            style = inter(13.sp, FontWeight.SemiBold, C.TextSecondary),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        ProgressLine(UpdateWatch.progress)
                        GhostButton("Keep using Dropi", Modifier.fillMaxWidth()) { onClose() }
                    }
                    is UpdateUi.Ready -> {
                        ui.note?.let {
                            Text(it, style = inter(12.sp, color = C.High), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        PrimaryButton("Install", Modifier.fillMaxWidth(), icon = R.drawable.lc_check) {
                            Haptics.confirm(view)
                            scope.launch { UpdateWatch.tap(context) }
                        }
                        GhostButton("Later", Modifier.fillMaxWidth()) { onClose() }
                    }
                    else -> {
                        if (ui is UpdateUi.Error) {
                            Text(ui.message, style = inter(12.sp, color = C.Low), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        PrimaryButton(
                            if (ui is UpdateUi.Error) "Try again" else "Update now",
                            Modifier.fillMaxWidth(),
                            icon = R.drawable.lc_download,
                            enabled = ui !is UpdateUi.Checking,
                        ) {
                            scope.launch {
                                val ok = UpdateWatch.tap(context)
                                if (ok) Haptics.confirm(view) else if (UpdateWatch.ui is UpdateUi.Error) Haptics.reject(view)
                            }
                        }
                        GhostButton("Later", Modifier.fillMaxWidth()) { onClose() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Highlight(text: String, index: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(280, delayMillis = 180 + index * 70, easing = FastOutSlowInEasing)) }
    Row(
        Modifier.graphicsLayer {
            alpha = a.value
            translationX = (1f - a.value) * 16.dp.toPx()
        },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(RoundedCornerShape(50)).background(C.Accent))
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(14.sp, color = C.Text).copy(lineHeight = 20.sp))
    }
}
