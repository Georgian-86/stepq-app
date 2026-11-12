package com.stepq.app.ui.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.FriendToday
import com.stepq.app.data.PersonSummary
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.home.distanceLabel
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/** The three sections under the Friends tab (A13, A15, A18). */
enum class FriendsSection(val label: String) { Friends("Friends"), Groups("Groups"), QRun("QRun") }

/** Title, add-friend button and the Friends / Groups / QRun switch shared by all three sections. */
@Composable
fun FriendsHeader(section: FriendsSection, onSection: (FriendsSection) -> Unit, onAddFriend: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Friends", style = StepqType.Title, color = StepqColors.Ink, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StepqColors.Mint)
                    .clickable(onClickLabel = "Add a friend", role = Role.Button, onClick = onAddFriend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PersonAddAlt, contentDescription = "Add a friend", tint = StepqColors.Ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(StepqColors.Mint)
                .padding(4.dp),
        ) {
            FriendsSection.entries.forEach { s ->
                val selected = s == section
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (selected) StepqColors.White else StepqColors.Mint)
                        .clickable(role = Role.Tab) { onSection(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        s.label,
                        style = if (selected) StepqType.SmallStrong else StepqType.Small,
                        color = if (selected) StepqColors.Ink else StepqColors.Muted,
                    )
                }
            }
        }
    }
}

/** A13 Friends leaderboard and A14 water reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsList(
    state: FriendsUiState,
    onRefresh: () -> Unit,
    onRemind: (PersonSummary) -> Unit,
    onAccept: (PersonSummary) -> Unit,
    onDecline: (PersonSummary) -> Unit,
    onAddFriend: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            val data = state.data
            Spacer(Modifier.height(16.dp))
            when {
                data == null && state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StepqColors.Deep)
                }
                data == null -> Text(
                    state.error ?: "Couldn't load your friends.",
                    style = StepqType.Body,
                    color = StepqColors.TomatoText,
                )
                else -> {
                    WaterBanner(remindersBanner(data.remindersLeft))
                    state.error?.let {
                        Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (data.requests.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("Friend requests")
                        data.requests.forEach { RequestRow(it, onAccept, onDecline) }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Today's steps")
                    Spacer(Modifier.height(4.dp))
                    data.board.forEachIndexed { i, row ->
                        BoardRow(
                            rank = i + 1,
                            row = row,
                            reminded = row.person.id in data.remindedToday,
                            sending = row.person.id in state.sending,
                            onRemind = { onRemind(row.person) },
                        )
                    }
                    if (data.board.size <= 1) {
                        NoFriendsYet(data.me.username, onAddFriend)
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun WaterBanner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(StepqColors.MangoSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = StepqType.Small, color = StepqColors.Ink)
    }
}

@Composable
private fun BoardRow(rank: Int, row: FriendToday, reminded: Boolean, sending: Boolean, onRemind: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(if (row.isMe) StepqColors.Mint else StepqColors.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), style = StepqType.BodyStrong, color = StepqColors.Muted, modifier = Modifier.width(22.dp))
        StepqAvatar(row.person.avatar, 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                if (row.isMe) "You" else row.person.displayName,
                style = StepqType.BodyStrong,
                color = StepqColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val (d, unit) = distanceLabel(row.distanceM / 1000.0)
            Text(
                "@${row.person.username} · $d $unit",
                style = StepqType.Small,
                color = StepqColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatSteps(row.steps), style = StepqType.NumberM, color = StepqColors.Ink)
        if (!row.isMe) {
            Spacer(Modifier.width(12.dp))
            WaterButton(row.person.displayName, reminded, sending, onRemind)
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun WaterButton(name: String, reminded: Boolean, sending: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (reminded) StepqColors.LeafSoft else StepqColors.Mint)
            .clickable(
                enabled = !reminded && !sending,
                onClickLabel = "Send ${name.firstName()} a water reminder",
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            sending -> CircularProgressIndicator(Modifier.size(16.dp), color = StepqColors.Deep, strokeWidth = 2.dp)
            reminded -> Icon(Icons.Outlined.Check, contentDescription = "Reminder sent", tint = StepqColors.Deep, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Outlined.WaterDrop, contentDescription = "Send a water reminder", tint = StepqColors.Deep, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RequestRow(person: PersonSummary, onAccept: (PersonSummary) -> Unit, onDecline: (PersonSummary) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(StepqColors.Page)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepqAvatar(person.avatar, 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(person.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${person.username}", style = StepqType.Small, color = StepqColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SmallPill("Decline", filled = false) { onDecline(person) }
        Spacer(Modifier.width(8.dp))
        SmallPill("Accept", filled = true) { onAccept(person) }
    }
}

@Composable
internal fun SmallPill(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (filled) StepqColors.Deep else StepqColors.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StepqType.SmallStrong, color = if (filled) StepqColors.White else StepqColors.Ink)
    }
}

@Composable
private fun NoFriendsYet(username: String, onAddFriend: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.piko_happy), contentDescription = null, Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text("Walk with friends", style = StepqType.Heading, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add friends by their username to see each other's steps and send water reminders. Yours is @$username.",
            style = StepqType.Body,
            color = StepqColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        StepqButton("Add a friend", onAddFriend)
    }
}

/** Placeholder for sections still being built. */
@Composable
fun SectionComingSoon(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.piko_happy), contentDescription = null, Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = StepqType.Heading, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center)
    }
}
