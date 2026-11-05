package com.stepq.app.ui.home

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
) {
    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().background(StepqColors.White)) {
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
            ScorePill(state, Modifier.align(Alignment.CenterHorizontally))
            if (!state.healthConnected) {
                Spacer(Modifier.height(16.dp))
                ConnectCard(onConnectSteps)
            }
            state.error?.let {
                Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Outlined.Route, "%.1f".format(Locale.US, state.km), "km", Modifier.weight(1f))
                StatCard(Icons.Outlined.LocalFireDepartment, state.kcal.toString(), "kcal", Modifier.weight(1f))
                StatCard(Icons.Outlined.Schedule, state.activeMinutes.toString(), "active min", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = StepqType.Subheading, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                Text("avg ${formatSteps(state.weekAverage)}", style = StepqType.SmallStrong, color = StepqColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            WeekBars(state.week, state.goal)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Friends today", style = StepqType.Subheading, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                StepqTextLink(if (state.hasFriends) "See all" else "Add friends", onSeeAllFriends)
            }
            state.friends.forEach { FriendLine(it) }
            Spacer(Modifier.height(16.dp))
        }
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
        if (state.streak > 0) {
            Row(
                Modifier.clip(CircleShape).background(StepqColors.Mango).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${state.streak}-day streak", style = StepqType.SmallStrong, color = StepqColors.Ink)
            }
        }
    }
}

@Composable
private fun Ring(state: HomeUiState) {
    val progress = if (state.goal <= 0) 0f else (state.steps.toFloat() / state.goal).coerceIn(0f, 1f)
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
            Text(formatSteps(state.steps), style = StepqType.NumberL, color = StepqColors.Ink)
            Text(
                if (state.steps >= state.goal) "steps today" else "of ${formatSteps(state.goal)} steps",
                style = StepqType.Small, color = StepqColors.Muted,
            )
        }
        Image(
            painterResource(
                when (state.mood) {
                    PikoMood.Nudge -> R.drawable.piko_nudge
                    PikoMood.Cheer -> R.drawable.piko_cheer
                    PikoMood.Happy -> R.drawable.piko_happy
                },
            ),
            contentDescription = "Piko",
            modifier = Modifier.size(64.dp).align(Alignment.BottomEnd).offset(x = (-40).dp, y = (-16).dp),
        )
    }
}

@Composable
private fun ScorePill(state: HomeUiState, modifier: Modifier = Modifier) {
    val tail = when {
        state.steps < state.goal -> " · ${formatSteps(state.goal - state.steps)} to go"
        state.mood == PikoMood.Cheer -> " · goal reached"
        else -> " · +${formatSteps(state.steps - state.goal)} past goal"
    }
    Text(
        buildAnnotatedString {
            append("StepQ score  ")
            withStyle(SpanStyle(fontWeight = StepqType.BodyStrong.fontWeight)) { append(state.score.toString()) }
            withStyle(SpanStyle(color = StepqColors.Muted)) { append(tail) }
        },
        style = StepqType.Small,
        color = StepqColors.Ink,
        modifier = modifier.clip(CircleShape).background(StepqColors.MangoSoft).padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        week.forEachIndexed { i, steps ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                val fraction = ((steps ?: 0).toFloat() / max).coerceIn(0.06f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                steps == null -> StepqColors.Mint
                                i == todayIndex -> StepqColors.Deep
                                else -> StepqColors.Leaf
                            },
                        ),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dayLetters.forEach {
            Text(it, style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

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
        Text(formatSteps(row.steps), style = StepqType.NumberM, color = StepqColors.Ink)
    }
}
