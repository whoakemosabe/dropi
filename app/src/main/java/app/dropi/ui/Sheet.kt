package app.dropi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A panel that slides up from the bottom, closed by swiping down anywhere on it.
 *
 * One rule wherever the finger lands: a downward drag first scrolls the content back to its
 * top, and only then pulls the panel down (the panel never moves while the content can still
 * scroll). Let go past a quarter of its height, or flick down, and it closes; otherwise it
 * springs back. Upward drags while it's pulled down push it back up before any scrolling.
 * Tapping the dimmed page or pressing back also closes it. [content] gets a close() that
 * slides it away first (for Save and the × button).
 */
@Composable
fun SlideSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toFloat()
    val maxHeight = with(density) { (screenPx * 0.92f).toDp() }

    var heightPx by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(screenPx) }
    var job by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }

    fun animateTo(target: Float, then: (() -> Unit)? = null, fast: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            animate(
                offsetY, target,
                animationSpec = if (fast) tween(200) else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            ) { v, _ -> offsetY = v }
            then?.invoke()
        }
    }

    fun close() {
        if (closing) return
        closing = true
        animateTo(maxOf(heightPx, 1f) + 40f, then = onDismiss, fast = true)
    }

    fun settle(velocity: Float) {
        if (offsetY > heightPx * 0.25f || velocity > 1600f) close() else animateTo(0f)
    }

    fun dragBy(dy: Float) {
        job?.cancel()
        offsetY = (offsetY + dy).coerceAtLeast(0f)
    }

    // Slide in.
    LaunchedEffect(Unit) { animateTo(0f) }
    BackHandler { close() }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pulled down and dragging up: raise the panel before the content scrolls.
                if (available.y < 0f && offsetY > 0f && source == NestedScrollSource.UserInput) {
                    val used = maxOf(available.y, -offsetY)
                    dragBy(used)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Content is at its top and the finger keeps going down: pull the panel.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            // A fling that runs out at the top doesn't drag the panel down by itself.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(Modifier.fillMaxSize()) {
        val shown = if (heightPx > 0f) (1f - offsetY / heightPx).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * shown))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                // The keyboard (for a note) lifts the whole panel.
                .imePadding()
                // A tablet gets a centred panel rather than one the full width of the screen.
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .onSizeChanged { heightPx = it.height.toFloat() }
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .clip(RoundedCornerShape(topStart = Corner.XL, topEnd = Corner.XL))
                .background(C.Sheet)
                .nestedScroll(connection)
                .draggable(
                    rememberDraggableState { dragBy(it) },
                    Orientation.Vertical,
                    onDragStopped = { v -> settle(v) },
                ),
        ) {
            // Handle.
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(C.TextMuted.copy(alpha = 0.5f)))
            }
            content { close() }
        }
    }
}

/**
 * A sheet's body with its title pinned at the top over a frosted band: the list scrolls under
 * the title, blurred. Dragging anywhere, the title included, still scrolls the list or pulls the
 * sheet closed.
 */
@Composable
fun ColumnScope.StickySheetBody(
    title: String,
    close: () -> Unit,
    /** Pinned under the scrolling part (a Save button), when given. */
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var headerPx by remember { mutableIntStateOf(0) }
    val headerDp = with(density) { headerPx.toDp() }
    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .layerBackdrop(backdrop)
                .background(C.Sheet)
                .verticalScroll(rememberScrollState())
                .then(if (footer == null) Modifier.navigationBarsPadding() else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = headerDp + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        GlassHeader(backdrop, headerDp + 16.dp, Modifier.align(Alignment.TopCenter), fade = 16.dp, base = C.Sheet)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        ) { SheetTitle(title, close) }
    }
    if (footer != null) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
        Box(
            Modifier
                .fillMaxWidth()
                .background(C.Sheet)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        ) { footer() }
    }
}
