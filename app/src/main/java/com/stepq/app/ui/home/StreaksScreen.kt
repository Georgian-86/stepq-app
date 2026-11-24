package com.stepq.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepq.app.R
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.friends.SmallPill
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/**
 * Streaks, Snapchat style. Every friend has their own streak with me: the days in a row I've
 * shared my steps with them. There is no overall streak. Streaks ending tonight (⏳) come first,
 * each with a Share button, then the ones already kept today, then friends with no streak yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onShare: (Set<String>) -> Unit,
    onToastShown: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val groups = streakGroups(state.shareTo, state.streaks, state.sharedTodayWith)
    // null: no sheet; otherwise who's picked when it opens.
    var sheetFor by remember { mutableStateOf<Set<String>?>(null) }
    Box(Modifier.fillMaxSize().background(StepqColors.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
            }
            Text("Streaks", style = StepqType.Title, color = StepqColors.Ink)
            Spacer(Modifier.height(12.dp))
            Hero(groups, onKeepAll = { sheetFor = groups.endingTonight.map { it.person.id }.toSet() })
            if (groups.endingTonight.isNotEmpty()) {
                Section("Ends tonight ⏳", "Share with them today or the streak goes back to 0.")
                groups.endingTonight.forEach { Line(it, state.sharing) { onShare(setOf(it.person.id)) } }
            }
            if (groups.going.isNotEmpty()) {
                Section("Kept today 🔥", "Shared today. Come back tomorrow to keep them going.")
                groups.going.forEach { Line(it, state.sharing) { onShare(setOf(it.person.id)) } }
            }
            if (groups.notStarted.isNotEmpty()) {
                Section("Start a streak", "Share your steps with them today to start one.")
                groups.notStarted.forEach { Line(it, state.sharing) { onShare(setOf(it.person.id)) } }
            }
            Spacer(Modifier.height(16.dp))
            HowItWorks()
            Spacer(Modifier.height(24.dp))
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
    }
    sheetFor?.let { picked ->
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StepqColors.White,
        ) {
            ShareSheet(state, preselected = picked, onSend = { ids ->
                onShare(ids)
                sheetFor = null
            })
        }
    }
}

/** Piko and one line on what needs doing today. */
@Composable
private fun Hero(groups: StreakGroups, onKeepAll: () -> Unit) {
    val ending = groups.endingTonight.size
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(if (ending > 0) StepqColors.MangoSoft else StepqColors.LeafSoft)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(if (ending > 0) R.drawable.piko_nudge else R.drawable.piko_happy),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when {
                        ending == 1 -> "1 streak ends tonight"
                        ending > 1 -> "$ending streaks end tonight"
                        groups.going.isNotEmpty() -> "All your streaks are kept today"
                        else -> "No streaks yet"
                    },
                    style = StepqType.BodyStrong,
                    color = StepqColors.Ink,
                )
                Text(
                    if (ending > 0) "Share your steps with them before midnight." else "Each friend has their own streak with you.",
                    style = StepqType.Small,
                    color = StepqColors.Muted,
                )
            }
        }
        if (ending > 0) {
            Spacer(Modifier.height(12.dp))
            StepqButton(if (ending == 1) "Keep it going" else "Keep all $ending going", onKeepAll)
        }
    }
}

@Composable
private fun Section(title: String, hint: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = StepqType.Subheading, color = StepqColors.Ink)
    Text(hint, style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
}

/** One friend: avatar, name, their streak (🔥 12, 💯 at 100, ⏳ if it ends tonight) and Share. */
@Composable
private fun Line(row: StreakRow, busy: Boolean, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(if (row.endsTonight) StepqColors.MangoSoft else StepqColors.Page)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    row.streak == 0 && !row.sharedToday -> "${row.person.displayName}, no streak yet"
                    row.endsTonight -> "${row.person.displayName}, ${row.streak}-day streak, ends tonight"
                    else -> "${row.person.displayName}, ${row.streak}-day streak, shared today"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepqAvatar(row.person.avatar, 44.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(row.person.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink)
            Text(
                when {
                    row.sharedToday -> "Shared today ✓"
                    row.endsTonight -> "Ends tonight"
                    else -> "@${row.person.username}"
                },
                style = StepqType.Small,
                color = if (row.endsTonight) StepqColors.TomatoText else StepqColors.Muted,
            )
        }
        if (row.streak > 0) {
            Text(streakBadge(row), fontSize = 18.sp, style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.padding(end = 10.dp))
        }
        if (!row.sharedToday) SmallPill("Share", filled = true, enabled = !busy, onClick = onShare)
    }
}

@Composable
private fun HowItWorks() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("How streaks work", style = StepqType.BodyStrong, color = StepqColors.Ink)
        Text("🔥  Share your steps with a friend every day to grow your streak with them.", style = StepqType.Small, color = StepqColors.Muted)
        Text("⏳  Haven't shared with them yet today? The streak ends at midnight.", style = StepqType.Small, color = StepqColors.Muted)
        Text("💯  Reach 100 days in a row together.", style = StepqType.Small, color = StepqColors.Muted)
        Text("Each friend's streak is separate, so you can keep many going at once.", style = StepqType.Small, color = StepqColors.Muted)
    }
}
