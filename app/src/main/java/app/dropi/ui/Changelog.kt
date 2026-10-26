package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.work.Updater
import java.time.LocalDate

/** Settings › Updates › What's changed: every release's highlights as a dated log, newest first. */
@Composable
fun ChangelogSheet(state: AppState) {
    if (!state.changelogOpen) return
    SlideSheet(onDismiss = { state.changelogOpen = false }) { close -> ChangelogContent(close) }
}

@Composable
private fun ColumnScope.ChangelogContent(close: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { Updater.installedVersion(context) }
    var log by remember { mutableStateOf<List<Updater.Logged>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        val got = Updater.fetchChangelog()
        if (got == null) failed = true else log = got
    }
    val day = remember { dayPattern("d MMM yyyy") }

    StickySheetBody(tr("What's changed"), close) {
        val list = log
        when {
            list == null && failed -> {
                Text(tr("Couldn't reach GitHub for the list of changes."), style = inter(15.sp, color = C.TextSecondary))
                Chip(tr("Try again"), icon = R.drawable.lc_refresh_cw) { attempt++ }
            }
            list == null -> Text(caps(tr("Loading…")), style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.06f))
            list.isEmpty() -> Text(tr("No releases yet."), style = inter(15.sp, color = C.TextSecondary))
            // A dated log, newest first: version and date on a ruled line, the notes under it.
            else -> Column(Modifier.fillMaxWidth()) {
                RuleHeader(tr("Releases"), list.size.toString())
                list.forEachIndexed { i, r ->
                    val current = r.version == installed
                    Column(
                        Modifier.fillMaxWidth().ruleTop(i > 0).padding(top = 14.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("v${r.version}", style = numeral(20.sp))
                            if (current) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    caps(tr("On your phone")),
                                    style = mono(12.sp, FontWeight.SemiBold, C.OnAccent, 0.04f),
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Corner.S))
                                        .background(C.Accent)
                                        .border(1.dp, C.AccentText.copy(alpha = 0.45f), RoundedCornerShape(Corner.S))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
                            val d = try { day.format(LocalDate.parse(r.date)) } catch (e: Exception) { "" }
                            Text(caps(d), style = mono(12.sp, FontWeight.Medium, C.TextMuted, 0.04f), maxLines = 1)
                        }
                        val ink = if (current || i == 0) C.Text else C.TextSecondary
                        r.notes.forEach { n ->
                            Row {
                                Text("–", style = mono(15.sp, color = C.TextMuted), modifier = Modifier.width(16.dp))
                                Text(n, style = inter(15.sp, color = ink).copy(lineHeight = 20.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}
