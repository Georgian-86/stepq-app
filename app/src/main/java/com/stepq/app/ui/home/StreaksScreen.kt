package com.stepq.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
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
    /** False inside the Friends tab, which has its own title and no back. */
    showHeader: Boolean = true,
    onAddFriend: () -> Unit = {},
    plan: com.stepq.app.data.MyPlan? = null,
    lost: List<com.stepq.app.data.LostStreak> = emptyList(),
    restoring: String? = null,
    onRestore: (friendId: String, name: String) -> Unit = { _, _ -> },
    onPinBff: (String?) -> Unit = {},
) {
    if (showHeader) BackHandler(onBack = onBack)
    val groups = streakGroups(state.shareTo, state.streaks, state.sharedTodayWith)
    // null: no sheet; otherwise who's picked when it opens.
    var sheetFor by remember { mutableStateOf<Set<String>?>(null) }
    var pickBff by remember { mutableStateOf(false) }
    val canPin = plan?.has(com.stepq.app.data.MyPlan.F.PIN_BFF) == true
    val top = topStreakFriend(groups, plan?.bffId, canPin)
    Box(Modifier.fillMaxSize().background(StepqColors.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (showHeader) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 20.dp),
        ) {
            if (showHeader) {
                Spacer(Modifier.height(4.dp))
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
                }
                Text("Streaks", style = StepqType.Title, color = StepqColors.Ink)
            }
            Spacer(Modifier.height(12.dp))
            Hero(groups, noFriends = state.shareTo.isEmpty() && !state.loading, onAddFriend = onAddFriend, onKeepAll = { sheetFor = groups.endingTonight.map { it.person.id }.toSet() })
            if (plan != null && state.shareTo.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BffCard(
                    top,
                    canPin = canPin,
                    onShare = { id -> onShare(setOf(id)) },
                    busy = state.sharing,
                    onChange = {
                        if (canPin) pickBff = true
                        else com.stepq.app.ui.components.ProSheets.show(com.stepq.app.data.MyPlan.F.PIN_BFF)
                    },
                )
            }
            val lostRows = lost.mapNotNull { l -> state.shareTo.firstOrNull { it.id == l.friendId }?.let { it to l.streak } }
            if (plan != null && lostRows.isNotEmpty()) {
                val limit = plan.limit(com.stepq.app.data.MyPlan.F.STREAK_RESTORES)
                val used = plan.usage.streakRestores
                Section(
                    "Lost last night 💔",
                    when {
                        plan.feature(com.stepq.app.data.MyPlan.F.STREAK_RESTORES) == null -> "Restore one to bring it back."
                        limit == null -> "Restore any of them. Unlimited restores with StepQ Pro."
                        else -> "$used of $limit restores used this month. Restore one to bring it back."
                    },
                )
                lostRows.forEach { (person, streak) ->
                    LostLine(person, streak, busy = restoring == person.id) { onRestore(person.id, person.displayName) }
                }
            }
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
    if (pickBff) {
        ModalBottomSheet(
            onDismissRequest = { pickBff = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StepqColors.White,
        ) {
            BffPicker(state.shareTo, current = plan?.bffId, streaks = state.streaks, onPick = { id ->
                onPinBff(id)
                pickBff = false
            })
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
private fun Hero(groups: StreakGroups, noFriends: Boolean = false, onAddFriend: () -> Unit = {}, onKeepAll: () -> Unit) {
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
                        noFriends -> "Add a friend to start a streak"
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
        } else if (noFriends) {
            Spacer(Modifier.height(12.dp))
            StepqButton("Add a friend", onAddFriend)
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
        com.stepq.app.ui.components.PersonAvatar(row.person.avatar, 44.dp, row.person.id)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            // The gold ring on the avatar marks Pro here; a badge would squeeze the name next to long streaks.
            Text(row.person.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** The Streaks section of the Friends tab: loads friends and streaks, and shares from here. */
@Composable
fun StreaksSection(repo: com.stepq.app.data.HomeRepository, onAddFriend: () -> Unit) {
    val vm: StreaksViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "streaks") { StreaksViewModel(repo) }
    val state by vm.state.collectAsState()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val plan by com.stepq.app.data.Pro.plan.collectAsState()
    val lost by vm.lost.collectAsState()
    val restoring by vm.restoring.collectAsState()
    StreaksScreen(
        state,
        onBack = {},
        onShare = vm::share,
        onToastShown = vm::toastShown,
        showHeader = false,
        onAddFriend = onAddFriend,
        plan = plan,
        lost = lost,
        restoring = restoring,
        onRestore = vm::restore,
        onPinBff = vm::pinBff,
    )
}

/**
 * The top of Streaks. Pro: my pinned BFF on a night-and-gold card. Free: my longest streak, with a
 * Pro tag on "Pin a BFF".
 */
@Composable
private fun BffCard(top: Pair<StreakRow, Boolean>?, canPin: Boolean, busy: Boolean, onShare: (String) -> Unit, onChange: () -> Unit) {
    val pinned = top?.second == true
    val row = top?.first
    val content: @Composable () -> Unit = {
        val onNight = canPin
        val text = if (onNight) StepqColors.White else StepqColors.Ink
        val muted = if (onNight) com.stepq.app.ui.theme.ProColors.OnNightMuted else StepqColors.Muted
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row != null) {
                com.stepq.app.ui.components.PersonAvatar(row.person.avatar, 52.dp, row.person.id)
            } else {
                Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
                    Text("💛", fontSize = 22.sp)
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when {
                        pinned -> "YOUR BFF"
                        row != null -> "TOP STREAK"
                        else -> "YOUR BFF"
                    },
                    style = StepqType.Label,
                    color = if (onNight) com.stepq.app.ui.theme.ProColors.GoldOnNight else com.stepq.app.ui.theme.ProColors.GoldText,
                )
                Text(row?.person?.displayName ?: "Pin your best friend", style = StepqType.BodyStrong, color = text)
                Text(
                    when {
                        row == null -> "They'll stay at the top of your streaks."
                        row.sharedToday -> "🔥 ${row.streak} · shared today"
                        row.streak > 0 -> "🔥 ${row.streak} · ends tonight"
                        else -> "No streak yet. Share today to start one."
                    },
                    style = StepqType.Small,
                    color = muted,
                )
            }
            if (row != null && !row.sharedToday) {
                SmallPill("Share", filled = true, enabled = !busy, onClick = { onShare(row.person.id) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onChange)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (pinned) "Change BFF" else "Pin a BFF",
                style = StepqType.SmallStrong,
                color = if (onNight) com.stepq.app.ui.theme.ProColors.GoldOnNight else StepqColors.Deep,
            )
            if (!canPin) {
                Spacer(Modifier.width(6.dp))
                com.stepq.app.ui.components.ProTag()
            }
        }
    }
    if (canPin) {
        com.stepq.app.ui.components.NightCard { Column { content() } }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StepqDimens.RadiusM))
                .background(StepqColors.Page)
                .padding(16.dp),
        ) { content() }
    }
}

/** A streak that ended last night, with Restore. */
@Composable
private fun LostLine(person: com.stepq.app.data.PersonSummary, streak: Int, busy: Boolean, onRestore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(com.stepq.app.ui.theme.ProColors.champagne)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${person.displayName}, $streak-day streak ended last night" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.stepq.app.ui.components.PersonAvatar(person.avatar, 44.dp, person.id)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(person.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink)
            Text("Was 🔥 $streak · ended last night", style = StepqType.Small, color = StepqColors.Muted)
        }
        SmallPill(if (busy) "Restoring…" else "Restore", filled = true, enabled = !busy, onClick = onRestore)
    }
}

@Composable
private fun BffPicker(friends: List<com.stepq.app.data.PersonSummary>, current: String?, streaks: Map<String, Int>, onPick: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Pin your BFF", style = StepqType.Heading, color = StepqColors.Ink)
        Text("They stay at the top of your streaks.", style = StepqType.Small, color = StepqColors.Muted)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            friends.sortedByDescending { streaks[it.id] ?: 0 }.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StepqDimens.RadiusS))
                        .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(f.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.stepq.app.ui.components.PersonAvatar(f.avatar, 40.dp, f.id)
                    Text(f.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    streaks[f.id]?.let { Text("🔥 $it", style = StepqType.SmallStrong, color = StepqColors.Ink, modifier = Modifier.padding(end = 8.dp)) }
                    if (f.id == current) Text("Pinned", style = StepqType.SmallStrong, color = com.stepq.app.ui.theme.ProColors.GoldText)
                }
            }
        }
        if (current != null) {
            androidx.compose.material3.TextButton(onClick = { onPick(null) }) {
                Text("Unpin", style = StepqType.SmallStrong, color = StepqColors.TomatoText)
            }
        }
    }
}
