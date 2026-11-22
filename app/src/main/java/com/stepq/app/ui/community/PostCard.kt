package com.stepq.app.ui.community

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.Post
import com.stepq.app.ui.components.LocalMotionEnabled
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.animatedProgress
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/** Everything a post row can do; the same set is used in the feed and in a thread. */
class PostActions(
    val onOpen: (Post) -> Unit,
    val onLike: (Post) -> Unit,
    val onReply: (Post) -> Unit,
    val onRepost: (Post) -> Unit,
    val onShare: (Post) -> Unit,
    val onMore: (Post) -> Unit,
)

/**
 * One post, Threads style: avatar on the left (with a line down to the next post when they're
 * part of one conversation), name, age, text, an achievement card for shared steps, and
 * like / reply / repost / share. Double-tap the text to like.
 */
@Composable
fun PostRow(
    post: Post,
    actions: PostActions,
    modifier: Modifier = Modifier,
    lineAbove: Boolean = false,
    lineBelow: Boolean = false,
    showReplyingTo: Boolean = true,
    clickable: Boolean = true,
) {
    var burst by remember { mutableIntStateOf(0) }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClickLabel = "Open thread") { actions.onOpen(post) } else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = if (lineAbove) 0.dp else 12.dp),
    ) {
        post.repostedBy?.let { who ->
            Row(Modifier.padding(start = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Repeat, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("$who reposted", style = StepqType.SmallStrong, color = StepqColors.Muted)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.width(AVATAR), horizontalAlignment = Alignment.CenterHorizontally) {
                if (lineAbove) ThreadLine(Modifier.height(10.dp)) else Spacer(Modifier.height(0.dp))
                StepqAvatar(post.authorAvatar, AVATAR, Modifier.semantics { contentDescription = post.authorName })
                if (lineBelow) {
                    Spacer(Modifier.height(6.dp))
                    ThreadLine(Modifier.weight(1f))
                    Spacer(Modifier.height(2.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(top = if (lineAbove) 10.dp else 0.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.authorName,
                            style = StepqType.BodyStrong,
                            color = StepqColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(" · ${timeAgo(post.createdAt)}", style = StepqType.Small, color = StepqColors.Muted, maxLines = 1)
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "More options", role = Role.Button) { actions.onMore(post) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options", tint = StepqColors.Muted, modifier = Modifier.size(20.dp))
                    }
                }
                if (showReplyingTo && post.parentUsername != null) {
                    Text(
                        buildAnnotatedString {
                            append("Replying to ")
                            withStyle(SpanStyle(color = StepqColors.Deep)) { append("@${post.parentUsername}") }
                        },
                        style = StepqType.Small,
                        color = StepqColors.Muted,
                    )
                }
                Box(
                    Modifier.pointerInput(post.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                burst++
                                if (!post.liked) actions.onLike(post)
                            },
                            onTap = { if (clickable) actions.onOpen(post) },
                        )
                    },
                ) {
                    Column {
                        if (post.body.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(post.body, style = StepqType.Body, color = StepqColors.Ink)
                        }
                        if (post.isAchievement && post.steps != null) {
                            Spacer(Modifier.height(10.dp))
                            AchievementCard(post.steps, post.goal, Modifier.padding(end = 8.dp))
                        }
                    }
                    HeartBurst(burst, Modifier.align(Alignment.Center))
                }
                ActionBar(post, actions)
            }
        }
        if (!lineBelow) Spacer(Modifier.height(4.dp))
    }
}

/** The grey line that joins a post to the replies under it. */
@Composable
private fun ThreadLine(modifier: Modifier = Modifier) {
    Box(modifier.width(2.dp).clip(RoundedCornerShape(1.dp)).background(StepqColors.Line))
}

@Composable
private fun ActionBar(post: Post, actions: PostActions) {
    Row(Modifier.padding(top = 4.dp).offset(x = (-8).dp), verticalAlignment = Alignment.CenterVertically) {
        LikeButton(post.liked, post.likeCount) { actions.onLike(post) }
        ActionIcon(Icons.Outlined.ChatBubbleOutline, "Reply", post.replyCount, StepqColors.Ink) { actions.onReply(post) }
        ActionIcon(
            Icons.Outlined.Repeat,
            if (post.reposted) "Undo repost" else "Repost",
            post.repostCount,
            if (post.reposted) StepqColors.Deep else StepqColors.Ink,
        ) { actions.onRepost(post) }
        ActionIcon(Icons.AutoMirrored.Outlined.Send, "Share", 0, StepqColors.Ink) { actions.onShare(post) }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, count: Int, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .widthIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Count(count, tint)
    }
}

/** The heart: fills red with a springy pop when liked. */
@Composable
private fun LikeButton(liked: Boolean, count: Int, onClick: () -> Unit) {
    val motion = LocalMotionEnabled.current
    val scale = remember { Animatable(1f) }
    var first by remember { mutableIntStateOf(0) }
    LaunchedEffect(liked) {
        if (first++ > 0 && motion && liked) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val tint = if (liked) StepqColors.Tomato else StepqColors.Ink
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = if (liked) "Unlike" else "Like", role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = tint,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        Count(count, tint)
    }
}

/** A count that rolls up or down when it changes. */
@Composable
private fun Count(count: Int, color: Color) {
    val motion = LocalMotionEnabled.current
    AnimatedContent(
        count,
        transitionSpec = {
            if (!motion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            }
        },
        label = "count",
    ) { n ->
        val text = compactCount(n)
        if (text.isNotEmpty()) Text(text, style = StepqType.Small, color = color, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The big heart that pops over a post when it's double-tapped. */
@Composable
private fun HeartBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val motion = LocalMotionEnabled.current
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (!motion) return@LaunchedEffect
        alpha.snapTo(1f)
        scale.snapTo(0.3f)
        scale.animateTo(1.1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        alpha.animateTo(0f, tween(260, delayMillis = 120))
    }
    Icon(
        Icons.Filled.Favorite,
        contentDescription = null,
        tint = StepqColors.Tomato,
        modifier = modifier.size(72.dp).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

/**
 * Shared steps: a green card with the day's count, a bar to the goal and Piko. Goal reached
 * makes it the full brand gradient with a "Goal hit" badge.
 */
@Composable
fun AchievementCard(steps: Int, goal: Int?, modifier: Modifier = Modifier) {
    val reached = goal != null && steps >= goal
    val bg = if (reached) Brush.linearGradient(listOf(StepqColors.Leaf, StepqColors.Deep))
    else Brush.linearGradient(listOf(StepqColors.LeafSoft, StepqColors.Mint))
    val ink = if (reached) StepqColors.White else StepqColors.Ink
    val sub = if (reached) StepqColors.White.copy(alpha = 0.85f) else StepqColors.Muted
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Steps today", style = StepqType.SmallStrong, color = sub)
                }
                Spacer(Modifier.height(2.dp))
                Text(formatSteps(steps), style = StepqType.NumberL, color = ink)
                if (goal != null) {
                    Spacer(Modifier.height(10.dp))
                    val progress = animatedProgress((steps.toFloat() / goal).coerceIn(0f, 1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (reached) StepqColors.White.copy(alpha = 0.3f) else StepqColors.Line),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (reached) StepqColors.Mango else StepqColors.Leaf),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (reached) "Goal of ${formatSteps(goal)} reached" else "${(steps * 100 / goal).coerceAtMost(99)}% of ${formatSteps(goal)}",
                        style = StepqType.Small,
                        color = sub,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(contentAlignment = Alignment.TopEnd) {
                Image(
                    painterResource(if (reached) R.drawable.piko_cheer else R.drawable.piko_happy),
                    contentDescription = null,
                    modifier = Modifier.padding(top = if (reached) 22.dp else 0.dp).size(64.dp),
                )
            }
        }
        if (reached) {
            Text(
                "Goal hit 🎉",
                style = StepqType.Label,
                color = StepqColors.Ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(StepqColors.Mango)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private val AVATAR = 40.dp

/** The thin divider between posts in the feed, indented past the avatar like Threads. */
@Composable
fun PostDivider() {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(StepqColors.Line))
}
