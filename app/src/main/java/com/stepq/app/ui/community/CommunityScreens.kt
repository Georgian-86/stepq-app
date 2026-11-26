package com.stepq.app.ui.community

import androidx.compose.runtime.collectAsState
import com.stepq.app.ui.theme.ProColors
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.CommunityRepository
import com.stepq.app.data.FeedScope
import com.stepq.app.data.Post
import com.stepq.app.data.Profile
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.appear
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/** The Community section of the Friends tab: a "start a thread" box, feed filters and the feed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeed(
    state: CommunityUiState,
    actions: PostActions,
    onScope: (FeedScope) -> Unit,
    onCompose: (shareSteps: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val feed = state.feed
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.posts.size) { if (nearEnd) onLoadMore() }

    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item(key = "compose") { ComposePrompt(state.me, onCompose) }
            item(key = "scopes") { ScopeChips(state.scope, onScope) }
            item(key = "top-line") { PostDivider() }
            when {
                feed.posts.isEmpty() && feed.loading -> item(key = "loading") {
                    com.stepq.app.ui.components.SkeletonList(rows = 5, withCard = false, modifier = Modifier.padding(20.dp))
                }
                feed.posts.isEmpty() && feed.error != null -> item(key = "error") {
                    Text(feed.error, style = StepqType.Body, color = StepqColors.TomatoText, modifier = Modifier.padding(20.dp))
                }
                feed.posts.isEmpty() -> item(key = "empty") { EmptyFeed(state.scope) { onCompose(false) } }
                else -> {
                    items(feed.posts, key = { "${it.id}:${it.repostedBy.orEmpty()}" }, contentType = { "post" }) { post ->
                        Column(Modifier.animateItem()) {
                            PostRow(post, actions)
                            PostDivider()
                        }
                    }
                    if (feed.loadingMore) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = StepqColors.Deep, strokeWidth = 2.dp)
                        }
                    } else if (feed.end && feed.posts.size > 4) item(key = "end") {
                        Text(
                            "You're all caught up",
                            style = StepqType.Small,
                            color = StepqColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "Start a thread…" with my avatar, Threads style, plus a one-tap way to share today's steps. */
@Composable
private fun ComposePrompt(me: Profile?, onCompose: (Boolean) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StepqDimens.RadiusM))
                .clickable(onClickLabel = "Start a thread", role = Role.Button) { onCompose(false) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.stepq.app.ui.components.PersonAvatar(me?.avatar ?: com.stepq.app.data.AvatarConfig(), 40.dp, me?.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(me?.displayName ?: "You", style = StepqType.BodyStrong, color = StepqColors.Ink)
                Text("Start a thread…", style = StepqType.Body, color = StepqColors.Muted)
                postsLeftLine()?.let { Text(it, style = StepqType.Small, color = ProColors.GoldText) }
            }
            Text(
                "Post",
                style = StepqType.SmallStrong,
                color = StepqColors.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StepqColors.Deep)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(StepqColors.LeafSoft)
                .clickable(onClickLabel = "Share today's steps", role = Role.Button) { onCompose(true) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Share today's steps", style = StepqType.SmallStrong, color = StepqColors.Deep)
        }
    }
}

@Composable
private fun ScopeChips(scope: FeedScope, onScope: (FeedScope) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedScope.entries.forEach { s ->
            val selected = s == scope
            Text(
                s.label,
                style = StepqType.SmallStrong,
                color = if (selected) StepqColors.White else StepqColors.Ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) StepqColors.Ink else StepqColors.White)
                    .border(BorderStroke(1.dp, if (selected) StepqColors.Ink else StepqColors.Line), CircleShape)
                    .clickable(role = Role.Tab) { onScope(s) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EmptyFeed(scope: FeedScope, onCompose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.piko_happy), contentDescription = null, Modifier.size(96.dp).appear())
        Spacer(Modifier.height(12.dp))
        Text(
            when (scope) {
                FeedScope.Mine -> "You haven't posted yet"
                FeedScope.Friends -> "Nothing from friends yet"
                FeedScope.Everyone -> "Be the first to post"
            },
            style = StepqType.Heading,
            color = StepqColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Share a walk, a goal you hit, or what keeps you moving.",
            style = StepqType.Body,
            color = StepqColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        com.stepq.app.ui.components.StepqButton("Start a thread", onCompose, Modifier.width(200.dp))
    }
}

/** A post with the posts it answers above it, and its replies below, plus a reply box. */
@Composable
fun ThreadScreen(
    state: ThreadState,
    me: Profile?,
    actions: PostActions,
    posting: Boolean,
    error: String?,
    onReply: (body: String, onDone: () -> Unit) -> Unit,
    onTyping: () -> Unit,
    onBack: () -> Unit,
    focusReply: Boolean = false,
) {
    BackHandler(onBack = onBack)
    var reply by rememberSaveable(state.postId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusReply, state.main?.id) { if (focusReply && state.main != null) runCatching { focus.requestFocus() } }
    // Replying from this page focuses the box instead of opening another page.
    val here = PostActions(
        onOpen = { if (it.id != state.postId) actions.onOpen(it) },
        onLike = actions.onLike,
        onReply = { if (it.id == state.postId) runCatching { focus.requestFocus() } else actions.onReply(it) },
        onRepost = actions.onRepost,
        onShare = actions.onShare,
        onMore = actions.onMore,
    )
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
            }
            Text("Thread", style = StepqType.Heading, color = StepqColors.Ink)
        }
        PostDivider()
        LazyColumn(Modifier.weight(1f)) {
            val above = state.above
            items(above, key = { "above:${it.id}" }) { p ->
                PostRow(p, here, lineAbove = p != above.first(), lineBelow = true, showReplyingTo = false)
            }
            state.main?.let { main ->
                item(key = "main:${main.id}") {
                    PostRow(main, here, lineAbove = above.isNotEmpty(), showReplyingTo = above.isEmpty(), clickable = false)
                    PostDivider()
                    Text(
                        if (main.replyCount == 0) "Replies" else "Replies · ${compactCount(main.replyCount)}",
                        style = StepqType.SmallStrong,
                        color = StepqColors.Ink,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    PostDivider()
                }
            }
            when {
                state.main == null && state.loading -> item(key = "loading") {
                    com.stepq.app.ui.components.SkeletonList(rows = 3, withCard = false, modifier = Modifier.padding(20.dp))
                }
                state.error != null -> item(key = "error") {
                    Text(state.error, style = StepqType.Body, color = StepqColors.Muted, modifier = Modifier.padding(20.dp))
                }
                state.loading && state.replies.isEmpty() -> item(key = "loading-replies") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = StepqColors.Deep, strokeWidth = 2.dp)
                    }
                }
                state.replies.isEmpty() -> item(key = "no-replies") {
                    Text(
                        "No replies yet. Start the conversation.",
                        style = StepqType.Body,
                        color = StepqColors.Muted,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                else -> items(state.replies, key = { "reply:${it.id}" }) { r ->
                    Column(Modifier.animateItem()) {
                        PostRow(r, here, showReplyingTo = false)
                        if (r.replyCount > 0) {
                            Text(
                                repliesLabel(r.replyCount),
                                style = StepqType.SmallStrong,
                                color = StepqColors.Deep,
                                modifier = Modifier
                                    .padding(start = 68.dp, bottom = 8.dp)
                                    .clip(CircleShape)
                                    .clickable { actions.onOpen(r) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        PostDivider()
                    }
                }
            }
        }
        if (state.main != null) {
            PostDivider()
            AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(error.orEmpty(), style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.stepq.app.ui.components.PersonAvatar(me?.avatar ?: com.stepq.app.data.AvatarConfig(), 32.dp, me?.id)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StepqColors.Mint)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (reply.isEmpty()) {
                        Text("Reply to @${state.main?.authorUsername}…", style = StepqType.Body, color = StepqColors.Muted)
                    }
                    BasicTextField(
                        reply,
                        onValueChange = {
                            if (it.length <= CommunityRepository.MAX_LENGTH) reply = it
                            onTyping()
                        },
                        textStyle = StepqType.Body.copy(color = StepqColors.Ink),
                        cursorBrush = SolidColor(StepqColors.Deep),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.width(6.dp))
                val canSend = reply.isNotBlank() && !posting
                IconButton(onClick = { onReply(reply) { reply = "" } }, enabled = canSend) {
                    if (posting) CircularProgressIndicator(Modifier.size(20.dp), color = StepqColors.Deep, strokeWidth = 2.dp)
                    else Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send reply",
                        tint = if (canSend) StepqColors.Deep else StepqColors.Line,
                    )
                }
            }
        }
    }
}

/** New thread: write up to 500 characters, optionally with today's steps attached. */
@Composable
fun ComposeScreen(
    me: Profile?,
    stepsToday: Int?,
    startWithSteps: Boolean,
    posting: Boolean,
    error: String?,
    onPost: (body: String, shareSteps: Boolean) -> Unit,
    onTyping: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var body by rememberSaveable { mutableStateOf("") }
    var shareSteps by rememberSaveable { mutableStateOf(startWithSteps) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val canPost = (body.isNotBlank() || shareSteps) && body.length <= CommunityRepository.MAX_LENGTH && !posting

    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel",
                style = StepqType.Body,
                color = StepqColors.Ink,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text("New thread", style = StepqType.Subheading, color = StepqColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (canPost) StepqColors.Deep else StepqColors.Line)
                    .clickable(enabled = canPost, role = Role.Button) { onPost(body, shareSteps) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (posting) CircularProgressIndicator(Modifier.size(18.dp), color = StepqColors.White, strokeWidth = 2.dp)
                else Text("Post", style = StepqType.SmallStrong, color = if (canPost) StepqColors.White else StepqColors.Muted)
            }
        }
        PostDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row {
                com.stepq.app.ui.components.PersonAvatar(me?.avatar ?: com.stepq.app.data.AvatarConfig(), 40.dp, me?.id)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.displayName ?: "You", style = StepqType.BodyStrong, color = StepqColors.Ink)
                    Box(Modifier.padding(top = 2.dp)) {
                        if (body.isEmpty()) {
                            Text(if (shareSteps) "Say something about today's walk…" else "What's new?", style = StepqType.Body, color = StepqColors.Muted)
                        }
                        BasicTextField(
                            body,
                            onValueChange = {
                                if (it.length <= CommunityRepository.MAX_LENGTH) body = it
                                onTyping()
                            },
                            textStyle = StepqType.Body.copy(color = StepqColors.Ink),
                            cursorBrush = SolidColor(StepqColors.Deep),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).focusRequester(focus),
                        )
                    }
                    AnimatedVisibility(shareSteps, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        AchievementCard(stepsToday ?: 0, me?.dailyGoal, Modifier.padding(top = 12.dp))
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        if (body.isNotEmpty()) CharacterRing(body.length)
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = StepqType.Small, color = StepqColors.TomatoText)
            }
            postsLeftLine()?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = StepqType.Small, color = ProColors.GoldText)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StepqDimens.RadiusM))
                    .background(StepqColors.Mint)
                    .clickable(role = Role.Switch) { shareSteps = !shareSteps }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = StepqColors.Deep)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Share today's steps", style = StepqType.BodyStrong, color = StepqColors.Ink)
                    Text(
                        stepsToday?.let { "${formatSteps(it)} steps so far" } ?: "Your count from StepQ, so far today",
                        style = StepqType.Small,
                        color = StepqColors.Muted,
                    )
                }
                Switch(
                    checked = shareSteps,
                    onCheckedChange = { shareSteps = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = StepqColors.Deep, checkedThumbColor = StepqColors.White),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Need an idea?", style = StepqType.SmallStrong, color = StepqColors.Muted)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                postPrompts.forEach { prompt ->
                    Text(
                        prompt,
                        style = StepqType.Small,
                        color = StepqColors.Ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(BorderStroke(1.dp, StepqColors.Line), CircleShape)
                            .clickable {
                                body = if (body.isBlank()) prompt else "${body.trimEnd()} $prompt"
                                onTyping()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Everyone on StepQ can see, like and reply to your posts. Be kind.",
                style = StepqType.Small,
                color = StepqColors.Muted,
            )
        }
    }
}

/** Fills as the post nears 500 characters; shows what's left in the last 50. */
@Composable
private fun CharacterRing(length: Int) {
    val max = CommunityRepository.MAX_LENGTH
    val left = max - length
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (left <= 50) {
            Text("$left", style = StepqType.SmallStrong, color = if (left <= 10) StepqColors.TomatoText else StepqColors.Muted)
            Spacer(Modifier.width(6.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(20.dp),
                color = StepqColors.Line,
                strokeWidth = 2.5.dp,
            )
            CircularProgressIndicator(
                progress = { (length.toFloat() / max).coerceIn(0f, 1f) },
                modifier = Modifier.size(20.dp),
                color = when {
                    left <= 10 -> StepqColors.Tomato
                    left <= 50 -> StepqColors.Mango
                    else -> StepqColors.Deep
                },
                strokeWidth = 2.5.dp,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

/** Report reasons, covering what the IT Rules 2021 (rule 3(1)(b)) ask platforms to act on. */
val REPORT_REASONS = listOf(
    "Spam or scam",
    "Harassment or bullying",
    "Hate speech or violence",
    "Nudity or sexual content",
    "Involves a child",
    "Self-harm or suicide",
    "Pretending to be someone else",
    "False or misleading",
    "Something else",
)

/** The "…" menu: copy, share, and delete for my posts, or report and block for others'. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOptionsSheet(
    post: Post,
    mine: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StepqColors.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (reporting) {
                Text("Why are you reporting this post?", style = StepqType.Heading, color = StepqColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your report is private. Our team reviews reports and removes posts that break the community rules. Posts reported by several people are hidden straight away.",
                    style = StepqType.Small, color = StepqColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                REPORT_REASONS.forEach { reason ->
                    SheetRow(Icons.Outlined.Flag, reason) { onReport(reason); onDismiss() }
                }
                return@Column
            }
            SheetGroup {
                SheetRow(Icons.Outlined.ContentCopy, "Copy text") { onCopy(); onDismiss() }
                SheetRow(Icons.Outlined.IosShare, "Share") { onShare(); onDismiss() }
            }
            Spacer(Modifier.height(12.dp))
            SheetGroup {
                if (mine) {
                    SheetRow(
                        Icons.Outlined.DeleteOutline,
                        if (confirmDelete) "Tap again to delete" else "Delete",
                        danger = true,
                    ) {
                        if (confirmDelete) { onDelete(); onDismiss() } else confirmDelete = true
                    }
                } else {
                    SheetRow(Icons.Outlined.Flag, "Report", danger = true) { reporting = true }
                    SheetRow(
                        Icons.Outlined.Block,
                        if (confirmBlock) "Tap again to block @${post.authorUsername}" else "Block @${post.authorUsername}",
                        danger = true,
                    ) {
                        if (confirmBlock) { onBlock(); onDismiss() } else confirmBlock = true
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint)) { content() }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = StepqType.BodyStrong, color = if (danger) StepqColors.TomatoText else StepqColors.Ink, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = if (danger) StepqColors.TomatoText else StepqColors.Ink, modifier = Modifier.size(20.dp))
    }
}

/** "2 of 3 posts left this month" on a limited plan; null when posting is unlimited. */
@Composable
private fun postsLeftLine(): String? {
    val plan by com.stepq.app.data.Pro.plan.collectAsState()
    val p = plan ?: return null
    if (p.feature(com.stepq.app.data.MyPlan.F.COMMUNITY_POSTS) == null) return null
    val limit = p.limit(com.stepq.app.data.MyPlan.F.COMMUNITY_POSTS)
    val left = p.postsLeft
    val review = if (p.has(com.stepq.app.data.MyPlan.F.POST_APPROVAL)) "StepQ checks new posts before they go live." else null
    return when {
        limit == 0 -> "Starting posts is for StepQ Pro members. You can still like and reply."
        limit == null -> review
        left == 0 -> "No new threads left this month. You can still like and reply."
        else -> listOfNotNull("$left of $limit new threads left this month.", review).joinToString(" ")
    }
}
