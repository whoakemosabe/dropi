package app.dropi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.data.Ask
import androidx.compose.ui.platform.LocalContext
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Trends › the way into Ask Dropi: one quiet line, like a note waiting to be written. */
@Composable
fun AskEntry(state: AppState) {
    Row(
        Modifier.fillMaxWidth().card().tap { state.askOpen = true }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_message_circle, C.TextSecondary, 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Ask about your numbers", style = inter(15.sp, FontWeight.SemiBold))
            Text("“How was my week?” “Any lows lately?”", style = inter(13.sp, color = C.TextMuted))
        }
        Ic(R.drawable.lc_arrow_right, C.TextMuted, 18.dp)
    }
}

@Composable
fun AskSheet(state: AppState) {
    if (!state.askOpen) return
    SlideSheet(onDismiss = { state.askOpen = false }) { close -> AskContent(state, close) }
}

private class Turn(val q: String, a: String?) {
    var a by mutableStateOf(a)
}

/** Anything that reads like a dose ("4 units", "2u") is never shown. */
private val doseLike = Regex("\\b\\d+(\\.\\d+)?\\s*(units?|u|iu)\\b", RegexOption.IGNORE_CASE)

@Composable
private fun ColumnScope.AskContent(state: AppState, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<Turn>() }
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) { Ai.refresh(context) }
    val canType = Ai.usable
    var busy by remember { mutableStateOf(false) }

    fun askTyped(q: String) {
        if (q.isBlank() || busy) return
        val turn = Turn(q.trim(), null)
        turns += turn
        text = ""
        busy = true
        scope.launch {
            val facts = withContext(Dispatchers.Default) { Ask.facts(state.askCtx()) }
            val reply = Ai.ask(context, Ask.prompt(q, facts))
            turn.a = when {
                reply == null -> "Couldn't answer that just now. Try one of the questions above."
                doseLike.containsMatchIn(reply) -> "I can't help with doses. Your care team can."
                else -> reply
            }
            busy = false
        }
    }

    StickySheetBody("Ask Dropi", close) {
        // Ready-made questions: worked out exactly from the log, on every phone.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Ask.Q.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { q ->
                        Text(
                            q.text,
                            style = inter(14.sp, FontWeight.SemiBold),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(C.SurfaceLight)
                                .tap {
                                    turns += Turn(q.text, Ask.answer(q, state.askCtx()))
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        turns.forEach { t ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.q, style = heading(17.sp))
                val a = t.a
                Text(
                    a ?: Ai.warmUp ?: "Thinking…",
                    style = inter(15.sp, color = if (a == null) C.TextMuted else C.Text).copy(lineHeight = 22.sp),
                )
            }
        }

        if (canType) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.SurfaceLight)
                    .border(1.dp, C.GlassBorder, RoundedCornerShape(18.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (text.isEmpty()) Text("Ask in your own words", style = inter(15.sp, color = C.TextMuted))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(300) },
                        textStyle = inter(15.sp, color = C.Text),
                        cursorBrush = SolidColor(C.AccentText),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { askTyped(text) }),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (text.isNotBlank() && !busy) C.Accent else C.Card)
                        .tap(enabled = text.isNotBlank() && !busy) { askTyped(text) },
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.lc_send, if (text.isNotBlank() && !busy) C.OnAccent else C.TextMuted, 18.dp, contentDescription = "Ask") }
            }
        }

        SheetHint(
            when {
                Ai.usable -> "Answers come from your own log, worked out on this phone by ${Ai.name}. Nothing is uploaded."
                Ai.checking -> "Answers come from your own log, worked out on this phone."
                Gemma.state is Gemma.State.Unsupported ->
                    "Typing your own question needs an on-device AI model this phone can't run. The questions above work on any phone."
                else ->
                    "To type your own questions, get the on-device AI in Settings → On-device AI (2.6 GB, once, on Wi-Fi). The questions above work now."
            } + " Dropi doesn't give medical or dose advice."
        )
    }
}
