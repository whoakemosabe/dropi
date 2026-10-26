package app.dropi.ui


import androidx.compose.animation.togetherWith

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dropi.R
import app.dropi.tr
import app.dropi.data.Ask
import androidx.compose.ui.platform.LocalContext
import app.dropi.work.Ai
import app.dropi.work.Gemma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Trends › the way into Ask Dropi: a ruled section with one quiet line, like a note waiting to be written. */
@Composable
fun AskEntry(state: AppState) {
    Column(Modifier.fillMaxWidth()) {
        RuleHeader(tr("Ask Dropi"))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { state.askOpen = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Ask about your numbers"), style = inter(15.sp, FontWeight.SemiBold))
                Text(tr("“How was my week?” “Any lows lately?”"), style = inter(13.sp, color = C.TextMuted))
            }
            Spacer(Modifier.width(12.dp))
            Text("→", style = mono(15.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
fun AskSheet(state: AppState) {
    if (!state.askOpen) return
    SlideSheet(onDismiss = { state.askOpen = false }) { close -> AskContent(state, close) }
}

private class Turn(val q: String, a: String?) {
    var a by mutableStateOf(a)
    /** When it was asked, for the logbook margin. */
    val at: Long = System.currentTimeMillis()
}

/**
 * Anything that reads like a dose ("4 units", "2u", "4,5 einingar", "6 ein.") is never shown.
 * The model may answer in Icelandic, so Icelandic unit words and a decimal comma count too.
 */
private val doseLike = Regex(
    "\\b\\d+([.,]\\d+)?\\s*(units?|u(?!\\.\\s*þ)|iu|eining\\w*|ein\\.|a\\.e\\.)(?!\\w)",
    RegexOption.IGNORE_CASE,
)

/** Icelandic dose talk without a number in front ("taktu insúlínskammt"). */
private val doseWords = Regex("insúlínskammt\\w*|skammt\\w* af insúlíni|insúlíneining\\w*", RegexOption.IGNORE_CASE)

@Composable
private fun ColumnScope.AskContent(state: AppState, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<Turn>() }
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) { Ai.refresh(context) }
    var busy by remember { mutableStateOf(false) }
    // The box is always there; without a model ready, sending explains or offers the download.
    var needAi by remember { mutableStateOf(false) }

    fun askTyped(q: String) {
        if (q.isBlank() || busy) return
        if (!Ai.usable) {
            needAi = true
            return
        }
        val turn = Turn(q.trim(), null)
        turns += turn
        text = ""
        busy = true
        scope.launch {
            val facts = withContext(Dispatchers.Default) { Ask.facts(state.askCtx()) }
            val reply = Ai.ask(context, Ask.prompt(q, facts))
            turn.a = when {
                reply == null -> tr("Couldn't answer that just now. Try one of the questions above.")
                doseLike.containsMatchIn(reply) || doseWords.containsMatchIn(reply) -> tr("I can't help with doses. Your care team can.")
                else -> reply
            }
            busy = false
        }
    }

    val fmt = remember(state.zone) { Fmt(context, state.zone) }

    StickySheetBody(tr("Ask Dropi"), close) {
        // Ready-made questions: worked out exactly from the log, on every phone. Ruled rows, like an index.
        Column(Modifier.fillMaxWidth()) {
            RuleHeader(tr("Questions"), Ask.Q.entries.size.toString())
            Ask.Q.entries.forEachIndexed { qi, q ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .ruleTop(qi > 0)
                        .tap { turns += Turn(q.text, Ask.answer(q, state.askCtx())) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(q.text, style = inter(15.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("→", style = mono(15.sp, FontWeight.Medium, C.TextSecondary), modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }

        // Answers, written up like logbook entries: the time in the margin, the question, the answer.
        if (turns.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                RuleHeader(tr("Answers"), turns.size.toString())
                turns.forEachIndexed { ti, t ->
                    // A new question slides in as a logbook entry rather than appearing at once.
                    androidx.compose.runtime.key(t) {
                    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                    androidx.compose.animation.AnimatedVisibility(shown, enter = Motion.foldIn) {
                    Row(
                        Modifier.fillMaxWidth().ruleTop(ti > 0).padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
                    ) {
                        Text(
                            fmt.time(t.at),
                            style = mono(12.sp, color = C.TextMuted),
                            maxLines = 1,
                            modifier = Modifier.width(if (fmt.is24) 48.dp else 72.dp).padding(top = 3.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t.q, style = heading(17.sp))
                            // "Thinking…" gives way to the answer with a fade, and the row grows to fit it.
                            androidx.compose.animation.AnimatedContent(
                                targetState = t.a,
                                transitionSpec = {
                                    androidx.compose.animation.fadeIn(Motion.fade(260)) togetherWith androidx.compose.animation.fadeOut(Motion.fade(120)) using
                                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> Motion.size() }
                                },
                                label = "answer",
                            ) { a ->
                                Text(
                                    a ?: Ai.warmUp ?: tr("Thinking…"),
                                    style = inter(15.sp, color = if (a == null) C.TextMuted else C.Text).copy(lineHeight = 22.sp),
                                )
                            }
                        }
                    }
                    }
                    }
                }
            }
        }

        if (needAi) {
            AiNeededDialog(tr("Asking in your own words"), tr("Ask it"), onDismiss = { needAi = false }, readyIcon = R.drawable.lc_send) {
                needAi = false
                askTyped(text)
            }
        }
        // The input bar: an outlined field with a squared send button, no fill.
        run {
            val canSend = text.isNotBlank() && !busy
            val shape = RoundedCornerShape(Corner.M)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(shape)
                    .border(1.dp, if (text.isNotEmpty()) C.AccentText.copy(alpha = 0.6f) else C.Border, shape)
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (text.isEmpty()) Text(tr("Ask in your own words"), style = inter(15.sp, color = C.TextMuted))
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
                Spacer(Modifier.width(8.dp))
                val btn = RoundedCornerShape(Corner.S)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(btn)
                        .then(
                            if (canSend) Modifier.background(C.Accent).border(1.dp, C.AccentText.copy(alpha = 0.45f), btn)
                            else Modifier.border(1.dp, C.Border, btn)
                        )
                        .tap(enabled = canSend) { askTyped(text) },
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.lc_send, if (canSend) C.OnAccent else C.TextMuted, 18.dp, contentDescription = tr("Ask")) }
            }
        }

        SheetHint(
            when {
                Ai.usable -> tr("Answers come from your own log, worked out on this phone by {0}. Nothing is uploaded.", Ai.name)
                Ai.checking -> tr("Answers come from your own log, worked out on this phone.")
                Gemma.state is Gemma.State.Unsupported ->
                    tr("Typing your own question needs an on-device AI model this phone can't run. The questions above work on any phone.")
                else ->
                    tr("Typing your own questions needs the on-device model; send one and Dropi offers the download. The questions above work now.")
            } + " " + tr("Dropi doesn't give medical or dose advice.")
        )
    }
}
