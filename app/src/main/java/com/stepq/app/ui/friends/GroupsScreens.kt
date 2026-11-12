package com.stepq.app.ui.friends

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepq.app.data.FriendToday
import com.stepq.app.data.GroupToday
import com.stepq.app.data.PersonSummary
import com.stepq.app.data.groupInviteText
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.home.distanceLabel
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import androidx.compose.foundation.text.KeyboardOptions

/** A15: my groups with today's total, plus create and join. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsList(
    state: GroupsUiState,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpen: (String) -> Unit,
    onInviteApp: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            StepqButton("Create a group", onCreate)
            val data = state.data
            when {
                data == null && state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StepqColors.Deep)
                }
                data == null -> Text(state.error ?: "Couldn't load your groups.", style = StepqType.Body, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 16.dp))
                else -> {
                    state.error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
                    data.groups.forEach { g ->
                        Spacer(Modifier.height(12.dp))
                        GroupCard(g) { onOpen(g.group.id) }
                    }
                    if (data.groups.isEmpty()) {
                        Text(
                            "Make a group for your office, family or college and see everyone's steps together.",
                            style = StepqType.Body,
                            color = StepqColors.Muted,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StepqDimens.RadiusS))
                    .border(BorderStroke(1.dp, StepqColors.Line), RoundedCornerShape(StepqDimens.RadiusS))
                    .clickable(role = Role.Button, onClick = onInviteApp)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Invite friends who are not on StepQ yet with a link.", style = StepqType.Small, color = StepqColors.Muted)
            }
            TextButton(onClick = onJoin, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                Text("Have an invite code? Join a group", style = StepqType.SmallStrong, color = StepqColors.Deep)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun GroupCard(g: GroupToday, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(StepqColors.Mint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarStack(g.members.map { it.person })
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(g.group.name, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(groupSummary(g.members.size, g.totalSteps), style = StepqType.Small, color = StepqColors.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StepqColors.Ink)
    }
}

@Composable
private fun AvatarStack(people: List<PersonSummary>) {
    val shown = people.take(3)
    Box(Modifier.width((36 + 22 * (shown.size - 1).coerceAtLeast(0)).dp).height(36.dp)) {
        shown.forEachIndexed { i, p ->
            Box(
                Modifier
                    .offset(x = (22 * i).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(StepqColors.White)
                    .padding(2.dp),
            ) { StepqAvatar(p.avatar, 32.dp) }
        }
    }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
    }
}

/** A16: name the group and tick friends to add. */
@Composable
fun CreateGroupScreen(
    friends: List<PersonSummary>,
    busy: Boolean,
    error: String?,
    onCreate: (String, Set<String>) -> Unit,
    onTyping: () -> Unit,
    onInvite: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var name by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            BackBar(onBack)
            Text("Create a group", style = StepqType.Title, color = StepqColors.Ink)
            Spacer(Modifier.height(20.dp))
            UnderlineField(
                "Group name",
                name,
                {
                    name = it.take(40)
                    onTyping()
                },
                placeholder = "e.g. Morning walkers",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Add friends")
            Spacer(Modifier.height(4.dp))
            if (friends.isEmpty()) {
                Text(
                    "Add friends first and they'll show up here. You can also invite people with the group's code after creating it.",
                    style = StepqType.Small,
                    color = StepqColors.Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            friends.forEach { f ->
                val on = f.id in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StepqDimens.RadiusS))
                        .clickable(role = Role.Checkbox) { picked = if (on) picked - f.id else picked + f.id }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepqAvatar(f.avatar, 40.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(f.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${f.username}", style = StepqType.Small, color = StepqColors.Muted)
                    }
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (on) StepqColors.Deep else StepqColors.White)
                            .border(BorderStroke(1.5.dp, if (on) StepqColors.Deep else StepqColors.Line), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Outlined.Check, contentDescription = "Added", tint = StepqColors.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StepqDimens.RadiusS))
                    .background(StepqColors.Mint)
                    .clickable(role = Role.Button, onClick = onInvite)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Invite friends not on StepQ yet", style = StepqType.SmallStrong, color = StepqColors.Deep)
            }
            Spacer(Modifier.height(16.dp))
        }
        StepqButton(
            when (picked.size) {
                0 -> "Create group"
                1 -> "Create group · 1 friend"
                else -> "Create group · ${picked.size} friends"
            },
            { onCreate(name, picked) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            enabled = name.isNotBlank(),
            loading = busy,
        )
    }
}

/** A17: one group's leaderboard for today. */
@Composable
fun GroupScreen(
    group: GroupToday,
    myId: String,
    friendIds: Set<String>,
    remindedToday: Set<String>,
    onRemind: (PersonSummary) -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            BackBar(onBack)
            Text(group.group.name, style = StepqType.Title, color = StepqColors.Ink)
            val by = if (group.group.createdBy == myId) "Created by you"
            else group.members.firstOrNull { it.person.id == group.group.createdBy }?.let { "Created by ${it.person.displayName.firstName()}" }
            Text(
                listOfNotNull("${group.members.size} ${if (group.members.size == 1) "member" else "members"}", by).joinToString(" · "),
                style = StepqType.Body,
                color = StepqColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StepqDimens.RadiusM))
                    .background(StepqColors.LeafSoft)
                    .padding(16.dp),
            ) {
                Text("Together today", style = StepqType.Small, color = StepqColors.Ink)
                Text("${formatSteps(group.totalSteps)} steps", style = StepqType.NumberL, color = StepqColors.Ink)
                val (d, unit) = distanceLabel(group.totalDistanceM / 1000.0)
                Text("That is about $d $unit as a group", style = StepqType.Small, color = StepqColors.Muted)
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel("Today's leaderboard")
            group.members.forEachIndexed { i, m -> MemberRow(i + 1, m, m.person.id in friendIds, m.person.id in remindedToday) { onRemind(m.person) } }
            Spacer(Modifier.height(8.dp))
            Text("Invite code: ${group.group.inviteCode}", style = StepqType.Small, color = StepqColors.Muted)
            TextButton(onClick = onLeave, modifier = Modifier.padding(top = 4.dp)) {
                Text("Leave group", style = StepqType.SmallStrong, color = StepqColors.TomatoText)
            }
        }
        StepqButton("Invite friends", onInvite, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = StepqButtonStyle.Outline)
    }
}

@Composable
private fun MemberRow(rank: Int, m: FriendToday, isFriend: Boolean, reminded: Boolean, onRemind: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(if (m.isMe) StepqColors.Mint else StepqColors.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), style = StepqType.BodyStrong, color = StepqColors.Muted, modifier = Modifier.width(22.dp))
        StepqAvatar(m.person.avatar, 40.dp)
        Text(
            if (m.isMe) "You" else m.person.displayName,
            style = StepqType.BodyStrong,
            color = StepqColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Text(formatSteps(m.steps), style = StepqType.NumberM, color = StepqColors.Ink)
        if (!m.isMe && isFriend) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (reminded) StepqColors.LeafSoft else StepqColors.Mint)
                    .clickable(enabled = !reminded, role = Role.Button, onClickLabel = "Send a water reminder", onClick = onRemind),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (reminded) Icons.Outlined.Check else Icons.Outlined.WaterDrop,
                    contentDescription = if (reminded) "Reminder sent" else "Send a water reminder",
                    tint = StepqColors.Deep,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (!m.isMe) {
            Spacer(Modifier.width(50.dp))
        }
    }
}

/** Join a group with the code someone shared. */
@Composable
fun JoinGroupScreen(busy: Boolean, error: String?, onJoin: (String) -> Unit, onTyping: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        BackBar(onBack)
        Text("Join a group", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Enter the invite code a group member shared with you.", style = StepqType.Body, color = StepqColors.Muted)
        Spacer(Modifier.height(28.dp))
        UnderlineField(
            "Invite code",
            code,
            {
                code = it.trim().lowercase().take(20)
                onTyping()
            },
            placeholder = "e.g. 3f9a1c07b2de",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Go),
        )
        error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        StepqButton("Join group", { onJoin(code) }, enabled = code.length >= 6, loading = busy)
    }
}

/** Opens the share sheet with a text, e.g. a group invite. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun appInviteText(username: String?): String =
    "Walk with me on StepQ! Get the app at https://stepq.in" + (username?.let { " and add me: @$it" } ?: "")

fun groupInvite(group: GroupToday) = groupInviteText(group.group.name, group.group.inviteCode)
