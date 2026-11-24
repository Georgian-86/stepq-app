package com.stepq.app.ui.home

import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.stepq.app.ui.components.AnimatedSteps
import com.stepq.app.ui.components.LottieOnce
import com.stepq.app.ui.components.SkeletonBlock
import com.stepq.app.ui.components.SkeletonRow
import com.stepq.app.ui.components.animatedProgress
import com.stepq.app.ui.components.appear
import com.stepq.app.ui.components.bob
import com.stepq.app.ui.components.zoomPulse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import java.util.Locale

/** A12 Home, A12b goal reached and A12c rest of day, from one state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onConnectSteps: () -> Unit,
    onSeeAllFriends: () -> Unit,
    onShare: (Set<String>) -> Unit = {},
    onToastShown: () -> Unit = {},
) {
    var sharing by remember { mutableStateOf(false) }
    val goalReached = !state.loading && state.goal > 0 && state.steps >= state.goal
    var celebrate by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(goalReached) {
        // Confetti once a day, the first time Home shows the goal reached.
        if (!goalReached) return@LaunchedEffect
        val prefs = context.getSharedPreferences("stepq", android.content.Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("celebrated_day", null) != today) {
            prefs.edit().putString("celebrated_day", today).apply()
            celebrate = true
        }
    }
    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().background(StepqColors.White)) {
        if (state.loading && state.steps == 0) {
            HomeSkeleton()
            return@PullToRefreshBox
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Header(state)
            Spacer(Modifier.height(16.dp))
            Ring(state)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.appear(1)) { ShareCard(state, onOpen = { sharing = true }, onAddFriends = onSeeAllFriends) }
            if (!state.healthConnected) {
                Spacer(Modifier.height(16.dp))
                ConnectCard(onConnectSteps)
            }
            state.error?.let {
                Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (distance, unit) = distanceLabel(state.km)
                StatCard(Icons.Outlined.Route, distance, unit, Modifier.weight(1f))
                StatCard(Icons.Outlined.LocalFireDepartment, state.kcal.toString(), "kcal", Modifier.weight(1f))
                StatCard(Icons.Outlined.Schedule, state.activeMinutes.toString(), "active min", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = StepqType.Subheading, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                Text("avg ${formatSteps(state.weekAverage)}", style = StepqType.SmallStrong, color = StepqColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.appear(2)) { WeekBars(state.week, state.goal) }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Friends today", style = StepqType.Subheading, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                StepqTextLink(if (state.hasFriends) "See all" else "Add friends", onSeeAllFriends)
            }
            if (!state.hasFriends) {
                Text(
                    "Add friends to see their steps today next to yours.",
                    style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            state.friends.forEachIndexed { i, f -> Box(Modifier.appear(3 + i)) { FriendLine(f) } }
            Spacer(Modifier.height(16.dp))
        }
        state.toast?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3_000)
                onToastShown()
            }
            com.stepq.app.ui.friends.Toast(
                message,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
                icon = Icons.Outlined.Whatshot,
            )
        }
        if (celebrate) {
            LottieOnce(R.raw.lottie_confetti, Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter)) { celebrate = false }
        }
    }
    if (sharing) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { sharing = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StepqColors.White,
        ) {
            ShareSheet(state, onSend = { ids ->
                onShare(ids)
                sharing = false
            })
        }
    }
}

/** While today's steps load: walking Piko in the middle of the page. */
@Composable
private fun HomeSkeleton() {
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        com.stepq.app.ui.components.PikoLoader(label = "Counting your steps…", pikoSize = 130.dp)
    }
}

@Composable
private fun Header(state: HomeUiState) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(state.dateLabel, style = StepqType.Small, color = StepqColors.Muted)
            Text(
                if (state.mood == PikoMood.Nudge) "${state.greeting}, ${state.firstName}" else "Goal reached, ${state.firstName}!",
                style = StepqType.Heading, color = StepqColors.Ink,
            )
        }
        // No overall streak here: streaks are per friend, shown next to each friend below.
        ScoreBadge(state.score)
    }
}

@Composable
private fun Ring(state: HomeUiState) {
    val progress = animatedProgress(if (state.goal <= 0) 0f else (state.steps.toFloat() / state.goal).coerceIn(0f, 1f), durationMs = 1200)
    Box(
        Modifier.fillMaxWidth().height(210.dp).semantics {
            contentDescription = "${formatSteps(state.steps)} of ${formatSteps(state.goal)} steps"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(190.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(StepqColors.Mint, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(StepqColors.Leaf, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedSteps(state.steps, StepqType.NumberL, StepqColors.Ink)
            Text(
                if (state.steps >= state.goal) "steps today" else "of ${formatSteps(state.goal)} steps",
                style = StepqType.Small, color = StepqColors.Muted,
            )
        }
        androidx.compose.animation.Crossfade(
            state.mood,
            Modifier.size(84.dp).align(Alignment.BottomEnd).offset(x = (-28).dp, y = (-6).dp).zoomPulse().bob(),
            label = "piko",
        ) { mood ->
            Image(
                painterResource(
                    when (mood) {
                        PikoMood.Nudge -> R.drawable.piko_nudge
                        PikoMood.Cheer -> R.drawable.piko_cheer
                        PikoMood.Happy -> R.drawable.piko_happy
                    },
                ),
                contentDescription = "Piko",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The share card under the ring: share today's steps with friends to keep your streak with
 * each of them going (Snapchat style). There's no overall streak, so no number here; each
 * friend's streak shows next to them. Once shared, it says so and offers to share again.
 */
@Composable
private fun ShareCard(state: HomeUiState, onOpen: () -> Unit, onAddFriends: () -> Unit) {
    val noFriends = state.shareTo.isEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(if (state.sharedToday) StepqColors.LeafSoft else StepqColors.MangoSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(
                when {
                    state.sharedToday -> "Shared your steps today"
                    state.streaks.isNotEmpty() -> "Share today to keep your streaks going"
                    else -> "Start a streak with a friend"
                },
                style = StepqType.BodyStrong,
                color = StepqColors.Ink,
            )
            Text(
                when {
                    noFriends -> "Add a friend, then share your steps with them every day."
                    state.sharedToday -> "Each friend you share with keeps your streak with them going."
                    else -> "Every friend you share with daily has their own streak."
                },
                style = StepqType.Small,
                color = StepqColors.Muted,
            )
        }
        com.stepq.app.ui.friends.SmallPill(
            when {
                noFriends -> "Add friends"
                state.sharedToday -> "Share again"
                else -> "Share"
            },
            filled = !state.sharedToday,
            enabled = !state.sharing,
            onClick = if (noFriends) onAddFriends else onOpen,
        )
    }
}

/** Pick friends to share today's steps with. Everyone is picked to start with. */
@Composable
private fun ShareSheet(state: HomeUiState, onSend: (Set<String>) -> Unit) {
    var picked by remember(state.shareTo) { mutableStateOf(state.shareTo.map { it.id }.toSet()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Text("Share today's steps", style = StepqType.Heading, color = StepqColors.Ink)
        Text(
            "${formatSteps(state.steps)} steps so far. Share with a friend each day to keep your streak with them going.",
            style = StepqType.Small,
            color = StepqColors.Muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            state.shareTo.forEach { p ->
                val on = p.id in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StepqDimens.RadiusS))
                        .clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { picked = if (on) picked - p.id else picked + p.id }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepqAvatar(p.avatar, 40.dp)
                    Text(p.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    state.streaks[p.id]?.let { StreakChip(it, Modifier.padding(end = 10.dp)) }
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (on) StepqColors.Deep else StepqColors.White)
                            .border(1.5.dp, if (on) StepqColors.Deep else StepqColors.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StepqButton(
            when (picked.size) {
                0 -> "Pick a friend"
                1 -> "Send to 1 friend"
                else -> "Send to ${picked.size} friends"
            },
            { onSend(picked) },
            enabled = picked.isNotEmpty(),
            loading = state.sharing,
        )
    }
}

/** The StepQ score in the top-right corner, with a shoe. */
@Composable
private fun ScoreBadge(score: Int) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(StepqColors.MangoSoft)
            .semantics(mergeDescendants = true) { contentDescription = "StepQ score $score" }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_shoe), contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(score.toString(), style = StepqType.SmallStrong, color = StepqColors.Ink)
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint).padding(16.dp),
    ) {
        Text("Piko can't see your steps yet", style = StepqType.BodyStrong, color = StepqColors.Ink)
        Text("Allow step counting so your walks count.", style = StepqType.Small, color = StepqColors.Muted)
        Spacer(Modifier.height(12.dp))
        StepqButton("Connect steps", onConnect)
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = StepqType.NumberM, color = StepqColors.Ink)
        Text(label, style = StepqType.Small, color = StepqColors.Muted)
    }
}

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun WeekBars(week: List<Int?>, goal: Int) {
    val max = maxOf(goal, week.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1)
    val todayIndex = week.indexOfLast { it != null }
    // Tapping a day shows its steps above the bar; tapping it again hides them.
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, steps ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected == i,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
                ) {
                    Text(
                        if (steps == null) "—" else formatSteps(steps),
                        style = StepqType.SmallStrong,
                        color = StepqColors.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StepqColors.Ink)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, steps ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Show steps") {
                        selected = if (selected == i) null else i
                    }
                    .semantics { contentDescription = "${dayNames[i]}: ${if (steps == null) "no steps yet" else "${formatSteps(steps)} steps"}" },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = animatedProgress(((steps ?: 0).toFloat() / max).coerceIn(0.06f, 1f), durationMs = 700, delayMs = i * 60)
                    .coerceAtLeast(0.06f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                steps == null -> StepqColors.Mint
                                selected == i -> StepqColors.Ink
                                i == todayIndex -> StepqColors.Deep
                                else -> StepqColors.Leaf
                            },
                        ),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dayLetters.forEachIndexed { i, letter ->
            Text(
                letter,
                style = if (selected == i) StepqType.SmallStrong else StepqType.Small,
                color = if (selected == i) StepqColors.Ink else StepqColors.Muted,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
private fun FriendLine(row: FriendRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(if (row.isMe) StepqColors.Mint else StepqColors.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.rank.toString(), style = StepqType.BodyStrong, color = StepqColors.Muted, modifier = Modifier.width(24.dp))
        StepqAvatar(row.avatar, 34.dp)
        Text(row.name, style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
        if (row.streak > 0) StreakChip(row.streak, Modifier.padding(end = 10.dp))
        Text(formatSteps(row.steps), style = StepqType.NumberM, color = StepqColors.Ink)
    }
}

/** My streak with one friend: a flame and the days in a row. */
@Composable
private fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(StepqColors.MangoSoft)
            .semantics(mergeDescendants = true) { contentDescription = "$days-day streak" }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(days.toString(), style = StepqType.SmallStrong, color = StepqColors.Ink)
    }
}
