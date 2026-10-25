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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dropi.R
import app.dropi.tr
import app.dropi.trn
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
                .card(RoundedCornerShape(Corner.M))
                .tap { act() }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.lc_download, C.AccentText, 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Dropi {0} is ready", shownVersion.orEmpty()), style = inter(15.sp, FontWeight.SemiBold, C.Text))
                    Text(
                        when (ui) {
                            UpdateUi.Idle, UpdateUi.UpToDate, is UpdateUi.Available -> tr("See what's new")
                            UpdateUi.Checking -> tr("Checking…")
                            is UpdateUi.Downloading -> tr("Downloading… {0}%", (UpdateWatch.progress * 100).toInt())
                            is UpdateUi.Ready -> ui.note ?: tr("Downloaded. Tap to install")
                            is UpdateUi.Error -> ui.message
                        },
                        style = if (ui is UpdateUi.Downloading) mono(13.sp, color = C.TextSecondary) else inter(13.sp, color = C.TextSecondary),
                    )
                }
                if (ui !is UpdateUi.Downloading) {
                    BannerAction(if (ui is UpdateUi.Ready) tr("Install") else tr("Update")) { act() }
                }
                Text(
                    "×",
                    style = inter(22.sp, FontWeight.Light, C.TextMuted),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = tr("Hide this update")) {
                            Haptics.tap(view)
                            UpdateWatch.bannerHidden = shownVersion
                        }
                        .semantics { contentDescription = tr("Hide") },
                )
            }
            if (ui is UpdateUi.Downloading) {
                Spacer(Modifier.height(12.dp))
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
    val lines = notes.ifEmpty { listOf(tr("Fixes and improvements")) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(Corner.XL)
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
                .card(shape),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ic(R.drawable.lc_zap, C.AccentText, 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        caps(tr("Update ready")),
                        style = mono(12.sp, FontWeight.Medium, C.AccentText, 0.08f),
                        modifier = Modifier.weight(1f),
                    )
                    if (behind > 1) {
                        Text(
                            caps(if (behind >= 30) tr("30+ updates behind") else trn(behind, "{0} update behind", "{0} updates behind")),
                            style = mono(12.sp, FontWeight.SemiBold, C.High, 0.04f),
                            modifier = Modifier
                                .border(1.dp, C.High.copy(alpha = 0.5f), RoundedCornerShape(Corner.S))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    if (behind > 1) tr("What's new since your version") else tr("What's new in v{0}", version),
                    style = heading(22.sp),
                )
                if (behind > 1) Text(tr("Installing v{0} brings you fully up to date.", version), style = inter(13.sp, color = C.TextSecondary))
            }

            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                lines.forEachIndexed { i, n -> Highlight(n, i) }
            }

            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (ui) {
                    is UpdateUi.Downloading -> {
                        Text(
                            tr("Downloading… {0}%", (UpdateWatch.progress * 100).toInt()),
                            style = mono(13.sp, FontWeight.Medium, C.TextSecondary),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        ProgressLine(UpdateWatch.progress)
                        GhostButton(tr("Keep using Dropi"), Modifier.fillMaxWidth()) { onClose() }
                    }
                    is UpdateUi.Ready -> {
                        ui.note?.let {
                            Text(it, style = inter(13.sp, color = C.High), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        PrimaryButton(tr("Install"), Modifier.fillMaxWidth(), icon = R.drawable.lc_check) {
                            Haptics.confirm(view)
                            scope.launch { UpdateWatch.tap(context) }
                        }
                        GhostButton(tr("Later"), Modifier.fillMaxWidth()) { onClose() }
                    }
                    else -> {
                        if (ui is UpdateUi.Error) {
                            Text(ui.message, style = inter(13.sp, color = C.Low), modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        PrimaryButton(
                            if (ui is UpdateUi.Error) tr("Try again") else tr("Update now"),
                            Modifier.fillMaxWidth(),
                            icon = R.drawable.lc_download,
                            enabled = ui !is UpdateUi.Checking,
                        ) {
                            scope.launch {
                                val ok = UpdateWatch.tap(context)
                                if (ok) Haptics.confirm(view) else if (UpdateWatch.ui is UpdateUi.Error) Haptics.reject(view)
                            }
                        }
                        GhostButton(tr("Later"), Modifier.fillMaxWidth()) { onClose() }
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
    // A logbook line: its number in mono, the note beside it, a hairline above.
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = a.value
                translationX = (1f - a.value) * 16.dp.toPx()
            }
            .ruleTop()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            style = mono(12.sp, FontWeight.Medium, C.TextMuted),
            modifier = Modifier.padding(top = 2.dp).width(24.dp).clearAndSetSemantics {},
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = inter(15.sp, color = C.Text).copy(lineHeight = 20.sp))
    }
}

/**
 * A banner's one action: a small button in the dark accent with the champagne hairline, a full
 * 48 dp to tap around it.
 */
@Composable
internal fun BannerAction(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Corner.S)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(C.Accent)
            .border(1.dp, C.AccentText.copy(alpha = 0.4f), shape)
            .tap { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(caps(label), style = mono(12.sp, FontWeight.SemiBold, C.OnAccent, 0.06f), maxLines = 1)
    }
}

/** 1.1: no gradients. Once the aurora wash on banners; now a faint solid tint of the signature colour. */
internal fun auroraWash(alpha: Float): Brush = SolidColor(C.AccentText.copy(alpha = alpha * 0.5f))

/** 1.1: no gradients. Once an aurora-coloured edge; now a plain hairline. */
internal fun auroraEdge(alpha: Float): Brush = SolidColor(C.Border.copy(alpha = (alpha + 0.1f).coerceAtMost(1f)))
