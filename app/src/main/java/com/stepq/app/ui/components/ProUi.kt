package com.stepq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.MyPlan
import com.stepq.app.data.Pro
import com.stepq.app.ui.theme.ProColors
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/** The Pro mark: a small gold coin with a crown. No text, so it reads the same in any language. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    val size = if (small) 18.dp else 24.dp
    Box(
        modifier
            .size(size)
            .shadow(3.dp, CircleShape, ambientColor = ProColors.Gold3, spotColor = ProColors.Gold3)
            .clip(CircleShape)
            .background(ProColors.gold)
            .semantics { contentDescription = "StepQ Pro member" },
        contentAlignment = Alignment.Center,
    ) {
        Crown(size * 0.62f, StepqColors.Ink)
    }
}

/** A Pro badge for [userId], shown only if they're Pro (fetched and cached in [Pro]). */
@Composable
fun ProBadgeFor(userId: String?, modifier: Modifier = Modifier) {
    if (userId == null) return
    LaunchedEffect(userId) { Pro.want(userId) }
    if (Pro.badges[userId]?.pro == true) ProBadge(modifier, small = true)
}

@Composable
fun Crown(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.12f, h * 0.78f)
            lineTo(w * 0.88f, h * 0.78f)
            lineTo(w * 0.94f, h * 0.30f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.18f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.06f, h * 0.30f)
            close()
        }
        drawPath(p, color)
    }
}

/**
 * An avatar that knows about Pro: a friend's photo when they've set one, and a gold ring for
 * Pro members. [userId] null draws the plain avatar.
 */
@Composable
fun PersonAvatar(config: AvatarConfig, size: Dp, userId: String?, modifier: Modifier = Modifier) {
    if (userId != null) LaunchedEffect(userId) { Pro.want(userId) }
    val badge = userId?.let { Pro.badges[it] }
    val path = badge?.photoPath
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) { value = path?.let { Pro.photo(it) } }
    if (badge?.pro == true) {
        Box(modifier.size(size).clip(CircleShape).background(ProColors.ring), contentAlignment = Alignment.Center) {
            val inner = size - (if (size >= 64.dp) 6.dp else 4.dp)
            Box(Modifier.size(inner).clip(CircleShape).background(StepqColors.White), contentAlignment = Alignment.Center) {
                Face(config, inner - 2.dp, photo)
            }
        }
    } else {
        Face(config, size, photo, modifier)
    }
}

@Composable
private fun Face(config: AvatarConfig, size: Dp, photo: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier = Modifier) {
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
    } else {
        StepqAvatar(config, size, modifier)
    }
}

/** The night-green card Pro screens use. */
@Composable
fun NightCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = ProColors.Night2, spotColor = ProColors.Gold3)
            .clip(RoundedCornerShape(24.dp))
            .background(ProColors.night)
            .border(1.dp, ProColors.Gold2.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
    ) {
        // Soft gold glow in the corner.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(ProColors.Gold2.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(this.size.width * 0.95f, 0f),
                    radius = this.size.width * 0.55f,
                ),
                radius = this.size.width * 0.55f,
                center = Offset(this.size.width * 0.95f, 0f),
            )
        }
        Box(Modifier.padding(18.dp)) { content() }
    }
}

/** Gold button for Pro calls to action. */
@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(6.dp, CircleShape, ambientColor = ProColors.Gold3, spotColor = ProColors.Gold3)
            .clip(CircleShape)
            .background(ProColors.goldFlat)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StepqType.Button, color = StepqColors.Ink)
    }
}

/** "3 of 8 used" with a gold bar; unlimited shows a full bar and "Unlimited". */
@Composable
fun UsageMeter(label: String, used: Int, limit: Int?, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = StepqType.BodyStrong, color = StepqColors.Ink, modifier = Modifier.weight(1f))
            Text(
                when {
                    limit == null -> "Unlimited"
                    limit == 0 -> "Pro only"
                    else -> "$used of $limit used" + (unit?.let { " ${unitShort(it)}" } ?: "")
                },
                style = StepqType.SmallStrong,
                color = if (limit == null) ProColors.GoldText else if (limit > 0 && used >= limit) StepqColors.TomatoText else StepqColors.Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        val frac = when {
            limit == null -> 1f
            limit == 0 -> 0f
            else -> (used.toFloat() / limit).coerceIn(0f, 1f)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(StepqColors.Line)) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(ProColors.goldFlat))
        }
    }
}

private fun unitShort(unit: String) = when (unit) {
    "a month" -> "this month"
    "a day" -> "today"
    else -> unit
}

/** How a plan value reads in the Free vs Pro table: ✓, —, "3 a day", "Unlimited". */
fun planValue(kind: String, value: Int?, unit: String?): String = when {
    kind == "switch" -> if (value == 1) "✓" else "—"
    value == null -> "Unlimited"
    value == 0 -> "—"
    unit == "days" -> if (value >= 365) "${value / 365} yr" else "$value days"
    unit == "a month" -> "$value/mo"
    unit == "a day" -> "$value/day"
    else -> value.toString()
}

/**
 * The sheet shown when someone runs into a plan limit, or taps something Pro-only. [onSeePro]
 * opens the StepQ Pro page.
 */
@Composable
fun PlanLimitSheet(
    featureKey: String,
    plan: MyPlan?,
    onSeePro: () -> Unit,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val f = plan?.feature(featureKey)
    val used = when (featureKey) {
        MyPlan.F.COMMUNITY_POSTS -> plan?.usage?.communityPosts
        MyPlan.F.STREAK_RESTORES -> plan?.usage?.streakRestores
        MyPlan.F.WATER_REMINDERS -> plan?.usage?.waterReminders
        else -> null
    }
    val (title, body) = limitCopy(featureKey, f?.mine, plan?.isPro == true)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(ProColors.gold), contentAlignment = Alignment.Center) {
            if (used != null && f?.mine != null) {
                Text("$used/${f.mine}", style = StepqType.NumberM, color = StepqColors.Ink)
            } else {
                Crown(28.dp, StepqColors.Ink)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = StepqType.Heading, color = StepqColors.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        if (actionLabel != null && onAction != null) {
            GoldButton(actionLabel, onAction)
        } else if (plan?.isPro != true) {
            GoldButton("Upgrade to StepQ Pro", onSeePro)
        }
        TextButton(onClick = onDismiss) { Text(if (plan?.isPro == true) "OK" else "Not now", style = StepqType.SmallStrong, color = StepqColors.Deep) }
    }
}

fun limitCopy(key: String, limit: Int?, isPro: Boolean): Pair<String, String> = when (key) {
    MyPlan.F.COMMUNITY_POSTS ->
        if (limit == 0) "Posting is a Pro feature" to "You can still like and reply to anyone. StepQ Pro members post as much as they like."
        else "You've used your $limit posts this month" to "You can still like and reply to anyone. New posts open again on the 1st, or go Pro to post as much as you like."
    MyPlan.F.WATER_REMINDERS -> "That's your $limit water reminders for today" to "They open again at midnight. StepQ Pro sends as many as you like."
    MyPlan.F.STREAK_RESTORES ->
        if (limit == 0) "Streak restores are a Pro feature" to "Pro members can bring back a streak they missed yesterday."
        else "You've used your $limit restores this month" to (if (isPro) "They come back on the 1st." else "They come back on the 1st. Pro members get 8 a month.")
    MyPlan.F.PLAN_QRUN ->
        if (isPro) "Ask StepQ to host QRuns" to "QRuns are hosted by StepQ and by Pro members StepQ approves. Ask, and we'll let you know."
        else "Hosting QRuns is for Pro members" to "QRuns are posted by StepQ and by Pro members StepQ approves. You can join any QRun."
    MyPlan.F.UPLOAD_PHOTO -> "Profile photos are a Pro feature" to "Put your own photo on your profile, streaks and leaderboards. Friends see it; everyone else sees your avatar."
    MyPlan.F.CHANGE_THEME -> "Themes are a Pro feature" to "Pick Ocean, Grape, Sunset or Gold for the whole app."
    MyPlan.F.PIN_BFF -> "Pin a BFF with Pro" to "Free shows your top streak first. Pro lets you pin one best friend to the top."
    MyPlan.F.ALL_CHALLENGES -> "This challenge is for Pro members" to "StepQ Pro opens every challenge, including the Pro-only ones."
    MyPlan.F.ANALYSIS_DAYS -> "See further back with Pro" to "Free shows the last $limit days. Pro shows up to a year of your steps."
    MyPlan.F.REWARD_MONTHLY, MyPlan.F.REWARD_YEARLY -> "Monthly and yearly rewards are for Pro" to "Everyone can win the weekly reward. Pro members are also eligible for the monthly and yearly ones."
    else -> "A StepQ Pro feature" to "Upgrade to StepQ Pro to use it."
}

/** A small crown mark for locked rows and chips. */
@Composable
fun ProTag(modifier: Modifier = Modifier) = ProBadge(modifier, small = true)

/** A gold-trimmed champagne row that opens the Pro page. */
@Composable
fun ProRow(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ProColors.champagne)
            .border(1.dp, ProColors.Gold2.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sparkle(22.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = StepqType.BodyStrong, color = StepqColors.Ink)
            Text(subtitle, style = StepqType.Small, color = ProColors.GoldText)
        }
        Text("›", style = StepqType.Heading, color = ProColors.Gold3)
    }
}

@Composable
fun Sparkle(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        fun star(cx: Float, cy: Float, r: Float, c: Color) {
            val p = Path().apply {
                moveTo(cx, cy - r); lineTo(cx + r * 0.25f, cy - r * 0.25f); lineTo(cx + r, cy)
                lineTo(cx + r * 0.25f, cy + r * 0.25f); lineTo(cx, cy + r); lineTo(cx - r * 0.25f, cy + r * 0.25f)
                lineTo(cx - r, cy); lineTo(cx - r * 0.25f, cy - r * 0.25f); close()
            }
            drawPath(p, c)
        }
        star(w * 0.42f, w * 0.42f, w * 0.38f, ProColors.Gold3)
        star(w * 0.8f, w * 0.78f, w * 0.18f, ProColors.Gold2)
    }
}

/**
 * One place any screen can ask for the "you've hit a limit" sheet. MainShell shows it, and
 * "See StepQ Pro" opens the Pro page on the Profile tab.
 */
object ProSheets {
    /** The feature key whose sheet is showing, or null. */
    var showing by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Set when "See StepQ Pro" is tapped; ProfileRoute opens its Pro page and clears it. */
    var openHub by androidx.compose.runtime.mutableStateOf(false)

    fun show(featureKey: String) { showing = featureKey }

    /** Shows the sheet if [e] is a plan_limit error. Returns true if it did. */
    fun showFor(e: Throwable?): Boolean {
        val key = e?.let { com.stepq.app.data.planLimitKey(it) } ?: return false
        showing = key
        Pro.refreshAsync()
        return true
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProSheetHost(onSeePro: () -> Unit) {
    val key = ProSheets.showing ?: return
    val plan by Pro.plan.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asked by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    val close: () -> Unit = { ProSheets.showing = null }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = close, containerColor = StepqColors.White) {
        // A Pro member who can't host yet can ask StepQ from the sheet.
        val askHost = key == MyPlan.F.PLAN_QRUN && plan?.isPro == true && plan?.has(MyPlan.F.PLAN_QRUN) == true &&
            plan?.qrunHost != true
        val requested = plan?.qrunHostRequestedAt != null || asked
        PlanLimitSheet(
            key, plan,
            onSeePro = { close(); onSeePro() },
            onDismiss = close,
            actionLabel = if (askHost) (if (requested) "Asked. We'll let you know" else "Ask to host QRuns") else null,
            onAction = when {
                askHost && !requested -> {
                    {
                        asked = true
                        scope.launch { Pro.requestQRunHost() }
                        Unit
                    }
                }
                askHost -> close
                else -> null
            },
        )
    }
}
