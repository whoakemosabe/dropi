package app.dropi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.semantics.heading
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
import androidx.compose.foundation.layout.offset
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
import app.dropi.tr
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
            // The glow takes the colour of the latest reading's range (Dracula keeps its own).
            val latest = state.readings.lastOrNull()
            AuroraGlow(Modifier.fillMaxWidth().height(560.dp), tint = if (!C.dracula && latest != null) C.status(state.status(latest)) else null)
            CompositionLocalProvider(LocalHeaderHeight provides headerDp) {
                // The four pages slide a little in the direction of the tab (Today → Trends moves
                // left) while cross-fading; back on any of them returns to Today.
                androidx.compose.animation.AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        val shift = { w: Int -> w / 10 }
                        (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { shift(it) * dir } +
                            fadeIn(tween(220, delayMillis = 40))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -shift(it) * dir } +
                                fadeOut(tween(160)))
                    },
                    label = "tab",
                ) { t ->
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

        // The tab bar, docked to the bottom edge with the + in its cradle.
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
                    .padding(start = 24.dp, end = 24.dp, bottom = TAB_BAR_CLEARANCE + 8.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corner.M))
                    .background(C.Text)
                    .semantics(mergeDescendants = false) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (deletedMeal != null) tr("{0} deleted", mealName(deletedMeal.name)) else tr("Reading deleted"),
                    style = inter(15.sp, FontWeight.SemiBold, C.Bg), modifier = Modifier.weight(1f),
                )
                Text(
                    tr("Undo"),
                    style = inter(15.sp, FontWeight.Bold, C.Bg),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Corner.S))
                        .tap { if (state.lastDeletedMeal != null) state.undoDeleteMeal() else state.undoDelete() }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
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
                style = inter(15.sp, FontWeight.SemiBold, C.Bg),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = TAB_BAR_CLEARANCE + 8.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corner.M))
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
        InsightsSheet(state)
        MyFoodsSheet(state)
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
                state.insightsOpen = false
                state.myFoodsOpen = false
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

/**
 * The sticky header, one line: the page's name (on Today, the date) and one fact on the right
 * (the next check, today's carbs, the dates shown, the version). No logo, no gear: Settings is in
 * More.
 */
@Composable
private fun TopBar(state: AppState) {
    val context = LocalContext.current
    val fmt = remember(state.zone) { Fmt(context, state.zone) }
    val now = rememberNow(60_000)
    val today = java.time.Instant.ofEpochMilli(now).atZone(state.zone).toLocalDate()
    var ring = false
    val (title, meta) = when (state.tab) {
        Tab.TODAY -> {
            val next = app.dropi.data.Schedule.next(state.slots, state.readings, now, state.zone, state.afterMealMinutes, state.meals)
            ring = next != null
            // The next check while there is one; after the last, how today went.
            val todays = app.dropi.data.Stats.onDay(state.readings, today, state.zone)
            val inRange = app.dropi.data.Stats.summary(todays).inRangePct
            dayPattern("EEE d MMM").format(today).cap() to when {
                next?.state == app.dropi.data.CheckState.DUE -> tr("due now")
                next != null && next.at - now < 16 * 3_600_000L && java.time.Instant.ofEpochMilli(next.at).atZone(state.zone).toLocalDate() == today ->
                    tr("next {0}", fmt.time(next.at))
                inRange != null -> { ring = false; tr("{0}% in range today", inRange) }
                next != null -> tr("next {0}", fmt.time(next.at))
                else -> null
            }
        }
        Tab.TRENDS -> {
            val df = dayPattern("d MMM")
            tr("Trends") to "${df.format(today.minusDays((state.trendsDays - 1).toLong()))} – ${df.format(today)}"
        }
        Tab.MEALS -> {
            val carbs = state.meals.filter { java.time.Instant.ofEpochMilli(it.at).atZone(state.zone).toLocalDate() == today }.sumOf { it.carbs }
            tr("Meals") to (if (carbs > 0) tr("{0} g carbs today", app.dropi.data.Grams.text(carbs)) else null)
        }
        Tab.MORE -> tr("More") to "v" + app.dropi.work.Updater.installedVersion(context)
    }
    Row(
        Modifier
            .statusBarsPadding()
            // Under Dracula the dripping edge needs a little room above the title.
            .padding(start = 20.dp, end = 20.dp, top = if (C.dracula) 30.dp else 12.dp, bottom = 12.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = heading(26.sp), modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1)
        if (meta != null) {
            if (ring) {
                Box(Modifier.size(8.dp).border(1.5.dp, C.TextSecondary, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(meta, style = mono(13.sp, color = C.TextSecondary), maxLines = 1)
        }
    }
}

/**
 * The bottom bar, docked to the bottom edge (option A, "Cradle", from the "Dropi bottom bar
 * options" canvas): one frosted bar the full width of the screen, running under the navigation
 * bar, with a curved cradle carved out of its middle. The + sits in the cradle, half above the
 * bar. Today and Trends on the left, Meals and More on the right.
 */
@Composable
private fun TabBar(state: AppState, backdrop: LayerBackdrop) {
    val view = LocalView.current
    fun go(t: Tab) {
        if (state.tab != t) Haptics.segment(view)
        state.tab = t
    }
    val shape = remember { CradleShape(CRADLE_R, CRADLE_SHOULDER) }
    // Room above the bar for the half of the + that rises out of the cradle.
    Box(Modifier.fillMaxWidth().padding(top = PLUS / 2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .frosted(backdrop, shape, base = C.Sheet)
                .border(1.dp, C.Border, shape)
                .blockTouches()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .height(BAR_H)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp)
                    .semantics { isTraversalGroup = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(tr("Today"), R.drawable.lc_droplet, state.tab == Tab.TODAY, Modifier.weight(1f)) { go(Tab.TODAY) }
                TabItem(tr("Trends"), R.drawable.lc_chart, state.tab == Tab.TRENDS, Modifier.weight(1f)) { go(Tab.TRENDS) }
                // The cradle: nothing here but the + above it.
                Spacer(Modifier.width(CRADLE_R * 2 + CRADLE_SHOULDER * 2))
                TabItem(tr("Meals"), R.drawable.lc_utensils, state.tab == Tab.MEALS, Modifier.weight(1f)) { go(Tab.MEALS) }
                TabItem(tr("More"), R.drawable.lc_layout_grid, state.tab == Tab.MORE, Modifier.weight(1f)) { go(Tab.MORE) }
            }
        }
        // The + itself: solid champagne-on-smoke, centred on the bar's top edge, in the cradle.
        val plus = rememberPress()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -PLUS / 2)
                .pressed(plus)
                .size(PLUS)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(C.Accent)
                .border(1.5.dp, C.AccentText.copy(alpha = 0.6f), CircleShape)
                .tap(plus) { state.addOpen = true },
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.lc_plus, C.OnAccent, 26.dp, contentDescription = tr("Add a reading or meal")) }
    }
}

/** The bar's height (above the navigation bar), the + and the cradle carved around it. */
private val BAR_H = 64.dp
private val PLUS = 60.dp
private val CRADLE_R = 38.dp
private val CRADLE_SHOULDER = 10.dp

/** How far pages scroll clear of the bar at the bottom (the bar, plus the + rising out of it). */
val TAB_BAR_CLEARANCE = 96.dp

/**
 * A full-width bar with a round cradle carved out of the middle of its top edge: a half circle of
 * [radius] below the edge, eased into the edge by soft shoulders of [shoulder] each side.
 */
private class CradleShape(private val radius: androidx.compose.ui.unit.Dp, private val shoulder: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val sh = with(density) { shoulder.toPx() }
        val w = size.width; val h = size.height; val cx = w / 2
        // Where the shoulders meet the circle: 18° below the edge each side.
        val a = Math.toRadians(18.0)
        val px = (r * kotlin.math.cos(a)).toFloat(); val py = (r * kotlin.math.sin(a)).toFloat()
        val p = Path()
        p.moveTo(0f, 0f)
        p.lineTo(cx - r - sh, 0f)
        p.cubicTo(cx - r - sh * 0.3f, 0f, cx - px - sh * 0.15f, py * 0.35f, cx - px, py)
        p.arcTo(Rect(cx - r, -r, cx + r, r), 180f - 18f, -(180f - 36f), false)
        p.cubicTo(cx + px + sh * 0.15f, py * 0.35f, cx + r + sh * 0.3f, 0f, cx + r + sh, 0f)
        p.lineTo(w, 0f)
        p.lineTo(w, h)
        p.lineTo(0f, h)
        p.close()
        return Outline.Generic(p)
    }
}

/** One page in a wing: its icon over a short name, on a lit capsule when it's the page showing. */
@Composable
private fun TabItem(label: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // The lit capsule and the label fade between tabs rather than jumping.
    val tint by androidx.compose.animation.animateColorAsState(if (selected) C.Text else C.TextMuted, tween(200), label = "tab-tint")
    val lit by androidx.compose.animation.core.animateFloatAsState(if (selected) 0.09f else 0f, tween(220), label = "tab-lit")
    val press = rememberPress()
    Column(
        modifier
            .fillMaxHeight()
            .pressed(press)
            .semantics(mergeDescendants = true) { this.selected = selected; role = Role.Tab }
            .tap(press, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The chosen page's icon sits on a small lit pill.
        Box(
            Modifier.size(width = 52.dp, height = 28.dp).clip(RoundedCornerShape(50)).background(C.Text.copy(alpha = lit)),
            contentAlignment = Alignment.Center,
        ) { Ic(icon, tint, 20.dp) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = inter(12.sp, if (selected) FontWeight.Bold else FontWeight.SemiBold, tint), maxLines = 1, softWrap = false)
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
