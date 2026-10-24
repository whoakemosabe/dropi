package app.dropi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.work.Diag

/**
 * More › Report a problem: what happened in the person's words, plus what Dropi knows about
 * what went wrong (app and phone, the on-device AI, the last label scan, recent problems, the
 * last crash). Sent through the share sheet or copied. No readings or meals go in it.
 */
@Composable
fun ReportSheet(state: AppState) {
    if (!state.reportOpen) return
    SlideSheet(onDismiss = { state.reportOpen = false }) { close -> ReportContent(close) }
}

@Composable
private fun ColumnScope.ReportContent(close: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    StickySheetBody(tr("Report a problem"), close) {
        Text(
            tr("Say what didn't work, then send it. Dropi adds what it knows about the problem, so it can be fixed without guessing."),
            style = inter(15.sp, color = C.TextSecondary).copy(lineHeight = 20.sp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .clip(RoundedCornerShape(Corner.M))
                .background(C.SurfaceLight)
                .border(1.dp, C.GlassBorder, RoundedCornerShape(Corner.M))
                .padding(16.dp),
        ) {
            if (note.isEmpty()) Text(tr("e.g. I scanned the skyr label and it said it couldn't read the numbers"), style = inter(15.sp, color = C.TextMuted))
            BasicTextField(
                value = note,
                onValueChange = { note = it.take(2000) },
                textStyle = inter(15.sp, color = C.Text),
                cursorBrush = SolidColor(C.AccentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("What happened") },
            )
        }
        Text(
            tr("Also included: the app version and phone, the on-device AI's state and which chip it runs on, your settings, the text from the last label scan, recent problems and the last crash. Not included: your readings, meals or notes."),
            style = inter(13.sp, color = C.TextMuted).copy(lineHeight = 17.sp),
        )
        PrimaryButton(tr("Send report"), Modifier.fillMaxWidth(), icon = R.drawable.lc_send) {
            val text = Diag.report(context, note)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Dropi problem report")
                .putExtra(Intent.EXTRA_TEXT, text)
            try { context.startActivity(Intent.createChooser(send, tr("Send the report"))) } catch (e: Exception) { copy(context, text); copied = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (copied) tr("Copied") else tr("Copy instead"), Modifier.weight(1f), color = C.AccentText) {
                copy(context, Diag.report(context, note)); copied = true
            }
            GhostButton(if (preview) tr("Hide what's sent") else tr("See what's sent"), Modifier.weight(1f), color = C.AccentText) { preview = !preview }
        }
        if (preview) {
            SelectionContainer {
                Text(
                    Diag.report(context, note),
                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 17.sp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Corner.M)).background(C.Card).padding(12.dp),
                )
            }
        }
        GhostButton(tr("Clear the problem list"), Modifier.fillMaxWidth()) { Diag.clear(context); preview = false }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Dropi problem report", text))
}
