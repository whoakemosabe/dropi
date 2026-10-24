package app.dropi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.dropi.R
import app.dropi.data.MealTag
import app.dropi.work.UpdateUi
import app.dropi.work.UpdateWatch

@Composable
fun DropiRoot(state: AppState) {
    // Coming back to the app: reload (the day may have changed) and look for updates.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.onOpen() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { state.onLeave() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!state.ready) return@Box

        // The page is recorded so the sticky header can blur it as it scrolls underneath.
        val pageBackdrop = rememberLayerBackdrop()
        val density = LocalDensity.current
        var headerPx by remember { mutableIntStateOf(0) }
        val headerDp = with(density) { headerPx.toDp() }
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            Box(Modifier.fillMaxSize().background(C.Bg))
            AuroraGlow(Modifier.fillMaxWidth().height(560.dp))
            CompositionLocalProvider(LocalHeaderHeight provides headerDp) {
                // The four pages, cross-fading; back on any of them returns to Today.
                Crossfade(state.tab, animationSpec = tween(220), label = "tab") { t ->
                    when (t) {
                        Tab.TODAY -> TodayPage(state)
                        Tab.TRENDS -> TrendsPage(state)
                        Tab.MEALS -> MealsPage(state)
                        Tab.MORE -> MorePage(state)
                    }
                }
            }
        }
        if (state.tab != Tab.TODAY) BackHandler { state.tab = Tab.TODAY }

        // Sticky header: logo, title and Settings over a frosted band (the fade reaches a little
        // past the header so there's no hard edge).
        GlassHeader(pageBackdrop, headerDp + HEADER_FADE, Modifier.align(Alignment.TopCenter), fade = HEADER_FADE)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .blockTouches()
        ) { TopBar(state) }

        if (C.dracula) BloodDrips(Modifier.align(Alignment.TopCenter))

        // The tab bar floats over the page, above the navigation bar, with the big + in the middle.
        AnimatedVisibility(
            visible = !state.sheetOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { TabBar(state, pageBackdrop) }

        // "Reading deleted · Undo", above the tab bar for a few seconds after a delete.
        val deleted = state.lastDeleted
        val deletedMeal = state.lastDeletedMeal
        LaunchedEffect(deleted, deletedMeal) {
            if (deleted != null || deletedMeal != null) {
                // Long enough to reach with TalkBack.
                kotlinx.coroutines.delay(10_000)
                if (state.lastDeleted == deleted) state.lastDeleted = null
                if (state.lastDeletedMeal == deletedMeal) state.lastDeletedMeal = null
            }
        }
        AnimatedVisibility(
            visible = (deleted != null || deletedMeal != null) && state.editing == null && !app.dropi.work.MealWork.open,
            enter = fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 118.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.Text)
                    .semantics(mergeDescendants = false) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (deletedMeal != null) "${deletedMeal.name} deleted" else "Reading deleted",
                    style = inter(14.sp, FontWeight.SemiBold, C.Bg), modifier = Modifier.weight(1f),
                )
                Text(
                    "Undo",
                    style = inter(14.sp, FontWeight.Bold, C.Bg),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .tap { if (state.lastDeletedMeal != null) state.undoDeleteMeal() else state.undoDelete() }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        }

        // "Breakfast logged" after a meal is saved.
        val mealNote = state.mealNote
        LaunchedEffect(mealNote) {
            if (mealNote != null) {
                kotlinx.coroutines.delay(4_000)
                if (state.mealNote == mealNote) state.mealNote = null
            }
        }
        AnimatedVisibility(
            visible = mealNote != null && !state.sheetOpen,
            enter = fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                mealNote ?: "",
                style = inter(14.sp, FontWeight.SemiBold, C.Bg),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 118.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.Text)
                    .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        SettingsSheet(state)
        ScheduleSheet(state)
        ExportSheet(state)
        BackupSheet(state)
        HistorySheet(state)
        LiveSheet(state)
        FoodsSheet(state)
        AskSheet(state)
        ChangelogSheet(state)
        ReportSheet(state)
        AddPanel(state)
        LogSheetHost(state)
        // Over the log sheet, so a meal opened from a notification or shortcut is in front and
        // back closes it first.
        MealSheet(state)
        MealLaunchers(state)

        // First run: welcome, unit and every permission, over everything else.
        if (!state.onboarded) WelcomeFlow(state)

        // After saving a low or very high reading: what to do now.
        state.alertFor?.let { r ->
            GuidanceDialog(r, state.status(r), state.unit, onRecheck = { state.remindRecheck(r) }) { state.alertFor = null }
        }

        // A reminder notification or the widget's + asked for the log sheet.
        val want = Launch.logTag.value
        LaunchedEffect(want) {
            if (want != null) {
                Launch.logTag.value = null
                state.settingsOpen = false
                state.scheduleOpen = false
                state.exportOpen = false
                state.backupOpen = false
                state.liveOpen = false
                state.askOpen = false
                state.foodsOpen = false
                state.changelogOpen = false
                state.historyOpen = false
                state.addOpen = false
                app.dropi.work.MealWork.open = false
                state.startNew(if (want == "new") null else MealTag.fromKey(want))
            }
        }

        val wantTrends = Launch.openTrends.value
        LaunchedEffect(wantTrends) {
            if (wantTrends) {
                Launch.openTrends.value = false
                state.closeSheets()
                state.trendsDays = 7
                state.tab = Tab.TRENDS
            }
        }

        val askCtx = LocalContext.current
        val wantAsk = Launch.openAsk.value
        LaunchedEffect(wantAsk) {
            if (wantAsk) {
                Launch.openAsk.value = false
                app.dropi.work.Gemma.markReadySeen(askCtx)
                state.askOpen = true
            }
        }

        // The "What's new" window: from the banner, Settings or the update notification.
        val ctx = LocalContext.current
        val ui = UpdateWatch.ui
        val version = UpdateWatch.waitingVersion ?: (ui as? UpdateUi.Ready)?.version
        if (UpdateWatch.whatsNewOpen.value && version != null) {
            val notes = remember(version) { UpdateWatch.notes(ctx) }
            val behind = remember(version) { UpdateWatch.behind(ctx) }
            WhatsNewDialog(version, notes, behind) { UpdateWatch.whatsNewOpen.value = false }
        }
    }
}

enum class Tab { TODAY, TRENDS, MEALS, MORE }

/** How far the header's frost fades out below it; pages start below that, so at rest nothing is blurred. */
val HEADER_FADE = 20.dp

/** The sticky header: the drop logo, the page title and date, and Settings. */
@Composable
private fun TopBar(state: AppState) {
    val context = LocalContext.current
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val now = rememberNow(60_000)
    val today = java.time.Instant.ofEpochMilli(now).atZone(state.zone).toLocalDate()
    val (title, sub) = when (state.tab) {
        Tab.TODAY -> "Dropi" to fmt.longDate(today)
        Tab.TRENDS -> {
            val df = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
            "Trends" to "${df.format(today.minusDays((state.trendsDays - 1).toLong()))} – ${df.format(today)}"
        }
        Tab.MEALS -> "Meals" to "What you ate, and what it did"
        Tab.MORE -> "More" to "Everything else, one tap away"
    }
    Row(
        Modifier
            .statusBarsPadding()
            // Under Dracula the dripping edge needs a little room above the title.
            .padding(start = 16.dp, end = 16.dp, top = if (C.dracula) 30.dp else 8.dp, bottom = 10.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(C.Aurora)),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_droplet, C.OnAccent, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = heading(26.sp))
            Text(sub, style = inter(13.sp, FontWeight.Medium, C.TextSecondary))
        }
        val press = rememberPress()
        Box(
            Modifier
                .pressed(press)
                .size(48.dp)
                .clip(CircleShape)
                .background(C.SurfaceLight.copy(alpha = 0.8f))
                .tap(press) { state.settingsOpen = true },
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_settings, C.TextSecondary, 20.dp, contentDescription = "Settings") }
    }
}

/**
 * The bottom bar: two frosted wings (the same blur and tint as the header) curving in around a
 * big floating + with a halo, two pages in each: Today and Trends on the left, Meals and More on
 * the right. Nothing joins the wings, so the page shows through the gaps.
 */
@Composable
private fun TabBar(state: AppState, backdrop: LayerBackdrop) {
    val view = LocalView.current
    fun go(t: Tab) {
        if (state.tab != t) Haptics.segment(view)
        state.tab = t
    }
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .height(104.dp),
    ) {
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wing(left = true, backdrop = backdrop, modifier = Modifier.weight(1f)) {
                TabItem("Today", R.drawable.lc_droplet, state.tab == Tab.TODAY, Modifier.weight(1f)) { go(Tab.TODAY) }
                TabItem("Trends", R.drawable.lc_chart, state.tab == Tab.TRENDS, Modifier.weight(1f)) { go(Tab.TRENDS) }
            }
            // Room for the orb between the wings' curved inner edges.
            Spacer(Modifier.width(ORB_GAP))
            Wing(left = false, backdrop = backdrop, modifier = Modifier.weight(1f)) {
                TabItem("Meals", R.drawable.lc_utensils, state.tab == Tab.MEALS, Modifier.weight(1f)) { go(Tab.MEALS) }
                TabItem("More", R.drawable.lc_layout_grid, state.tab == Tab.MORE, Modifier.weight(1f)) { go(Tab.MORE) }
            }
        }
        // The halo, then the + itself.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .border(1.5.dp, C.AccentText.copy(alpha = 0.45f), CircleShape)
        )
        val plus = rememberPress()
        Box(
            Modifier
                .align(Alignment.Center)
                .pressed(plus)
                .size(80.dp)
                .shadow(18.dp, CircleShape, ambientColor = C.Aurora[1], spotColor = C.Aurora[2])
                .clip(CircleShape)
                .background(Brush.linearGradient(C.Aurora))
                .tap(plus) { state.addOpen = true },
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_plus, C.OnAccent, 32.dp, contentDescription = "Add a reading or meal") }
    }
}

/** Space between the two wings: the orb's width less how far the wings' curves reach in. */
private val ORB_GAP = 72.dp

/**
 * One wing: round on the outside, and on the inside a concave curve that follows the orb (a
 * circle of 48 dp about the orb's centre), so the pair hugs the + with an even 8 dp gap.
 */
private class WingShape(private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 48.dp.toPx() }
        val h = size.height
        val w = size.width
        val reach = kotlin.math.sqrt(r * r - (h / 2) * (h / 2)) // how far the curve's tips sit from the orb centre
        val p = Path()
        if (left) {
            val cx = w + reach
            p.moveTo(h / 2, 0f)
            p.lineTo(w, 0f)
            p.arcTo(Rect(cx - r, h / 2 - r, cx + r, h / 2 + r), 180f + Math.toDegrees(kotlin.math.atan2(h / 2.0, reach.toDouble())).toFloat(),
                -2f * Math.toDegrees(kotlin.math.atan2(h / 2.0, reach.toDouble())).toFloat(), false)
            p.lineTo(h / 2, h)
            p.arcTo(Rect(0f, 0f, h, h), 90f, 180f, false)
        } else {
            val cx = -reach
            p.moveTo(0f, 0f)
            p.lineTo(w - h / 2, 0f)
            p.arcTo(Rect(w - h, 0f, w, h), -90f, 180f, false)
            p.lineTo(0f, h)
            p.arcTo(Rect(cx - r, h / 2 - r, cx + r, h / 2 + r), Math.toDegrees(kotlin.math.atan2(h / 2.0, reach.toDouble())).toFloat(),
                -2f * Math.toDegrees(kotlin.math.atan2(h / 2.0, reach.toDouble())).toFloat(), false)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
private fun Wing(
    left: Boolean,
    backdrop: LayerBackdrop,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = remember(left) { WingShape(left) }
    Row(
        modifier
            .height(64.dp)
            .frosted(backdrop, shape)
            .border(1.5.dp, C.Text.copy(alpha = 0.22f), shape)
            .clip(shape)
            .semantics { isTraversalGroup = true }
            // Keep the items clear of the curved inner edge and the round outer one.
            .padding(start = if (left) 6.dp else 20.dp, end = if (left) 20.dp else 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One page in a wing: its icon over a short name, on a lit capsule when it's the page showing. */
@Composable
private fun TabItem(label: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) C.AccentText else C.TextSecondary
    val press = rememberPress()
    Column(
        modifier
            .fillMaxHeight()
            .pressed(press)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(listOf(C.Aurora[0].copy(alpha = 0.24f), C.Aurora[2].copy(alpha = 0.24f)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
            )
            .then(if (selected) Modifier.border(1.dp, C.AccentText.copy(alpha = 0.6f), RoundedCornerShape(50)) else Modifier)
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.Tab }
            .tap(press, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ic(icon, tint, 20.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, style = inter(11.sp, if (selected) FontWeight.Bold else FontWeight.SemiBold, tint), maxLines = 1, softWrap = false)
    }
}

/** Requests from outside the app (a reminder, the widget) waiting for the screen. */
object Launch {
    /** A MealTag name, or "new" to let the time of day pick the moment. */
    val logTag = mutableStateOf<String?>(null)

    /** The weekly recap asked for Trends (7 days). */
    val openTrends = mutableStateOf(false)

    /** A meal action asked for from outside the + panel: "camera", "pick", "words", "label", "draft", "shared:<uri>". */
    val mealAction = mutableStateOf<String?>(null)

    /** The "on-device AI is ready" notification or banner asked for Ask Dropi. */
    val openAsk = mutableStateOf(false)
}
