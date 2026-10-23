package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import app.dropi.work.Updater
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings › Updates › What's changed: every release's highlights on a timeline, newest first. */
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
    val day = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    StickySheetBody("What's changed", close) {
        val list = log
        when {
            list == null && failed -> {
                Text("Couldn't reach GitHub for the list of changes.", style = inter(14.sp, color = C.TextSecondary))
                Chip("Try again", icon = R.drawable.lc_refresh_cw) { attempt++ }
            }
            list == null -> Text("Loading…", style = inter(14.sp, color = C.TextMuted))
            list.isEmpty() -> Text("No releases yet.", style = inter(14.sp, color = C.TextSecondary))
            else -> list.forEachIndexed { i, r ->
                val current = r.version == installed
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // The rail: a dot per release, joined down the page.
                    Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.size(if (current) 12.dp else 8.dp).clip(CircleShape).background(if (current) C.Accent else C.TextMuted))
                        if (i < list.lastIndex) {
                            Box(Modifier.padding(top = 4.dp).width(1.5.dp).weight(1f).background(C.Border))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("v${r.version}", style = numeral(20.sp))
                            Spacer(Modifier.width(10.dp))
                            val d = try { day.format(LocalDate.parse(r.date)) } catch (e: Exception) { "" }
                            Text(d, style = inter(12.sp, color = C.TextMuted), modifier = Modifier.weight(1f))
                            if (current) {
                                Text(
                                    "On your phone",
                                    style = inter(11.sp, FontWeight.SemiBold, C.OnAccent),
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(C.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        val ink = if (current || i == 0) C.Text else C.TextSecondary
                        r.notes.forEach { n ->
                            Row {
                                Text("–", style = inter(14.sp, color = C.TextMuted), modifier = Modifier.width(14.dp))
                                Text(n, style = inter(14.sp, color = ink).copy(lineHeight = 20.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}
