package com.stepq.app.ui.challenges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.ChallengeCard
import com.stepq.app.data.ChallengesRepository
import com.stepq.app.data.Ranked
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import java.time.LocalDate

/** A22 weekly and A23 monthly rankings, plus challenges to join. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    state: ChallengesUiState,
    monthly: Boolean,
    onPeriod: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    today: LocalDate = LocalDate.now(com.stepq.app.data.FriendsRepository.IST),
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
            Text("Challenges", style = StepqType.Title, color = StepqColors.Ink)
            Spacer(Modifier.height(16.dp))
            Switch(monthly, onPeriod)
            Spacer(Modifier.height(16.dp))
            PeriodCard(today, monthly)
            val data = state.data
            when {
                data == null && state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StepqColors.Deep)
                }
                data == null -> Text(state.error ?: "Couldn't load challenges.", style = StepqType.Body, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 16.dp))
                else -> {
                    val board = if (monthly) data.monthly else data.weekly
                    Spacer(Modifier.height(20.dp))
                    Podium(board.take(3))
                    val myRank = board.indexOfFirst { it.isMe }
                    if (myRank >= 3) {
                        Spacer(Modifier.height(12.dp))
                        RankRow(myRank + 1, board[myRank], highlight = true)
                    }
                    board.drop(3).forEachIndexed { i, r -> if (!r.isMe) RankRow(i + 4, r, highlight = false) }
                    if (board.size <= 1) {
                        Text(
                            "Add friends or join a group to compete with them here.",
                            style = StepqType.Small,
                            color = StepqColors.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Challenges to join", style = StepqType.Subheading, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                        val open = data.challenges.count { it.myJoinedAt == null }
                        if (data.challenges.isNotEmpty()) Text("$open open", style = StepqType.Small, color = StepqColors.Muted)
                    }
                    if (data.challenges.isEmpty()) {
                        Text(
                            "No partner challenges right now. New ones show up here.",
                            style = StepqType.Small,
                            color = StepqColors.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    data.challenges.forEach { c ->
                        Spacer(Modifier.height(12.dp))
                        ChallengeRow(c) { onOpen(c.challenge.id) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Switch(monthly: Boolean, onPeriod: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StepqColors.Mint)
            .padding(4.dp),
    ) {
        listOf(false to "Weekly", true to "Monthly").forEach { (m, label) ->
            val selected = m == monthly
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) StepqColors.White else StepqColors.Mint)
                    .clickable(role = Role.Tab) { onPeriod(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (selected) StepqType.SmallStrong else StepqType.Small, color = if (selected) StepqColors.Ink else StepqColors.Muted)
            }
        }
    }
}

@Composable
private fun PeriodCard(today: LocalDate, monthly: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(StepqColors.MangoSoft)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (monthly) "This month" else "This week", style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.weight(1f))
            Text(
                endsIn(periodEnd(today, monthly)),
                style = StepqType.SmallStrong,
                color = StepqColors.Ink,
                modifier = Modifier.clip(CircleShape).background(StepqColors.Mango).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(periodLabel(today, monthly), style = StepqType.Small, color = StepqColors.Muted)
    }
}

@Composable
private fun Podium(top: List<Ranked>) {
    // Order on the podium: 2nd, 1st, 3rd.
    val order = listOf(1, 0, 2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { i ->
            val r = top.getOrNull(i)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (r != null) {
                    StepqAvatar(r.person.avatar, if (i == 0) 52.dp else 44.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (r.isMe) "You" else r.person.displayName.substringBefore(' '),
                        style = StepqType.SmallStrong,
                        color = StepqColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatSteps(r.steps), style = StepqType.Small, color = StepqColors.Muted)
                    Spacer(Modifier.height(8.dp))
                }
                val h: Dp = when (i) { 0 -> 78.dp; 1 -> 56.dp; else -> 42.dp }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(StepqDimens.RadiusS))
                        .background(if (i == 0) StepqColors.Mango else StepqColors.Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((i + 1).toString(), style = StepqType.NumberM, color = StepqColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, r: Ranked, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(StepqDimens.RadiusS))
            .background(if (highlight) StepqColors.Mint else StepqColors.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank.toString(), style = StepqType.BodyStrong, color = StepqColors.Muted, modifier = Modifier.width(26.dp))
        StepqAvatar(r.person.avatar, 32.dp)
        Text(
            if (r.isMe) "You" else r.person.displayName,
            style = StepqType.BodyStrong,
            color = StepqColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Text(formatSteps(r.steps), style = StepqType.NumberM, color = StepqColors.Ink)
    }
}

@Composable
private fun ChallengeRow(c: ChallengeCard, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .border(BorderStroke(1.dp, StepqColors.Line), RoundedCornerShape(StepqDimens.RadiusM))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(StepqDimens.RadiusS)).background(StepqColors.MangoSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.TrackChanges, contentDescription = null, tint = StepqColors.Ink) }
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(c.challenge.title, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(challengeSubtitle(c), style = StepqType.Small, color = StepqColors.Muted)
        }
        if (c.myJoinedAt == null) {
            Box(
                Modifier.clip(CircleShape).background(StepqColors.Deep).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text("Join", style = StepqType.SmallStrong, color = StepqColors.White) }
        } else {
            Text("Joined", style = StepqType.SmallStrong, color = StepqColors.Deep)
        }
    }
}

fun challengeSubtitle(c: ChallengeCard): String {
    val goal = c.challenge.targetSteps?.let { "Walk ${formatSteps(it)} steps by ${shortDate(ChallengesRepository.parseInstant(c.challenge.endsAt))}" }
        ?: "Ends ${shortDate(ChallengesRepository.parseInstant(c.challenge.endsAt))}"
    val who = if (c.joined > 0) " · ${formatSteps(c.joined.toInt())} joined" else ""
    return goal + who
}

/** A24: a challenge's details and the Join button. */
@Composable
fun ChallengeDetailScreen(card: ChallengeCard, joining: Boolean, onJoin: () -> Unit, onLeave: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = card.challenge
    val end = ChallengesRepository.parseInstant(c.endsAt)
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusL)).background(StepqColors.MangoSoft).padding(20.dp),
            ) {
                c.partnerName?.let {
                    Text(it, style = StepqType.SmallStrong, color = StepqColors.Ink, modifier = Modifier.clip(CircleShape).background(StepqColors.White).padding(horizontal = 12.dp, vertical = 6.dp))
                    Spacer(Modifier.height(12.dp))
                }
                Text(c.title, style = StepqType.Title, color = StepqColors.Ink)
                c.targetSteps?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Walk ${formatSteps(it)} steps by ${shortDate(end)}", style = StepqType.BodyStrong, color = StepqColors.Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Fact(Icons.Outlined.CalendarMonth, "Ends ${shortDate(end)} · ${daysLeft(end)}")
            Fact(Icons.Outlined.Group, joinedLabel(card.joined))
            c.reward?.let { Fact(Icons.Outlined.CardGiftcard, it) }
            c.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = StepqType.Body, color = StepqColors.Ink)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("How it works")
            Check("Steps you walk from the day you join count automatically.")
            Check("One reward per person.")
            if (card.myJoinedAt != null) {
                Spacer(Modifier.height(16.dp))
                Progress(card)
                TextButton(onClick = onLeave) { Text("Leave challenge", style = StepqType.SmallStrong, color = StepqColors.TomatoText) }
            }
        }
        if (card.myJoinedAt == null) {
            StepqButton("Join challenge", onJoin, Modifier.padding(horizontal = 20.dp, vertical = 16.dp), loading = joining)
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = StepqType.Body, color = StepqColors.Ink)
    }
}

@Composable
private fun Check(text: String) {
    Row(Modifier.padding(top = 10.dp)) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = StepqType.Small, color = StepqColors.Ink)
    }
}

@Composable
private fun Progress(card: ChallengeCard) {
    val target = card.challenge.targetSteps ?: return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.White).border(BorderStroke(1.dp, StepqColors.Line), RoundedCornerShape(StepqDimens.RadiusM)).padding(16.dp)) {
        Row {
            Text("${formatSteps(card.myProgress.coerceAtMost(target))} of ${formatSteps(target)} steps", style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.weight(1f))
            Text(daysLeft(ChallengesRepository.parseInstant(card.challenge.endsAt)), style = StepqType.Small, color = StepqColors.Muted)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (card.myProgress.toFloat() / target).coerceIn(0.02f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = StepqColors.Leaf,
            trackColor = StepqColors.Mint,
            drawStopIndicator = {},
        )
    }
}

/** A25: joined. */
@Composable
fun ChallengeJoinedScreen(card: ChallengeCard, onDone: () -> Unit, onShare: () -> Unit) {
    BackHandler(onBack = onDone)
    val c = card.challenge
    Column(
        Modifier.fillMaxSize().background(StepqColors.LeafSoft).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.piko_cheer), contentDescription = null, Modifier.size(160.dp))
        Spacer(Modifier.height(20.dp))
        Text("You're in!", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(10.dp))
        val end = shortDate(ChallengesRepository.parseInstant(c.endsAt))
        Text(
            listOfNotNull(
                c.targetSteps?.let { "Walk ${formatSteps(it)} steps by $end. Steps from today count." }
                    ?: "You've joined ${c.title}. It ends $end.",
                c.reward,
            ).joinToString("\n"),
            style = StepqType.Body,
            color = StepqColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Progress(card)
        Spacer(Modifier.weight(1f))
        StepqButton("Back to challenges", onDone)
        TextButton(onClick = onShare, modifier = Modifier.padding(bottom = 12.dp)) {
            Text("Share with friends", style = StepqType.SmallStrong, color = StepqColors.Deep)
        }
    }
}
