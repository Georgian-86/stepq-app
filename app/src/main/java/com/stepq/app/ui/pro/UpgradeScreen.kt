package com.stepq.app.ui.pro

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.data.MyPlan
import com.stepq.app.data.PlanFeature
import com.stepq.app.ui.components.Crown
import com.stepq.app.ui.components.GoldButton
import com.stepq.app.ui.theme.ProColors
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/** One reason to go Pro, built from the admin's Free and Pro values. */
data class Perk(val icon: ImageVector, val title: String, val free: String, val pro: String)

/**
 * The perks to show, only where Pro really differs from Free right now (the admin can change any
 * value, so nothing here is hard-coded beyond the wording).
 */
fun upgradePerks(plan: MyPlan): List<Perk> {
    fun f(key: String): PlanFeature? = plan.feature(key)
    fun differs(key: String) = f(key)?.let { it.free != it.pro } == true
    fun on(key: String) = f(key)?.pro?.let { it > 0 } ?: (f(key) != null)
    fun count(key: String, unit: String): String = when (val v = f(key)?.pro) {
        null -> "Unlimited"
        else -> "$v $unit"
    }
    fun freeCount(key: String, unit: String): String = when (val v = f(key)?.free) {
        null -> "Unlimited"
        0 -> "None"
        else -> "$v $unit"
    }
    val out = mutableListOf<Perk>()
    if (differs(MyPlan.F.STREAK_RESTORES)) {
        out += Perk(Icons.Outlined.LocalFireDepartment, "Streak saves", freeCount(MyPlan.F.STREAK_RESTORES, "a month"), count(MyPlan.F.STREAK_RESTORES, "a month"))
    }
    if (differs(MyPlan.F.COMMUNITY_POSTS)) {
        out += Perk(Icons.Outlined.Forum, "Start posts", if (f(MyPlan.F.COMMUNITY_POSTS)?.free == 0) "Like and reply" else freeCount(MyPlan.F.COMMUNITY_POSTS, "a month"), count(MyPlan.F.COMMUNITY_POSTS, "a month"))
    }
    if ((differs(MyPlan.F.UPLOAD_PHOTO) && on(MyPlan.F.UPLOAD_PHOTO)) || (differs(MyPlan.F.CHANGE_THEME) && on(MyPlan.F.CHANGE_THEME))) {
        out += Perk(Icons.Outlined.Palette, "Photo and themes", "Avatar only", "Yours to pick")
    }
    if (differs(MyPlan.F.ALL_CHALLENGES) || differs(MyPlan.F.REWARD_MONTHLY)) {
        out += Perk(Icons.Outlined.EmojiEvents, "Every challenge", "Weekly only", "All + monthly")
    }
    if (differs(MyPlan.F.PIN_BFF) && on(MyPlan.F.PIN_BFF)) {
        out += Perk(Icons.Outlined.Favorite, "Pin a BFF", "Top streak", "Your pick")
    }
    // Insights shows a year at most, so history only counts as a perk when Free gets less.
    if (differs(MyPlan.F.ANALYSIS_DAYS) && (f(MyPlan.F.ANALYSIS_DAYS)?.free ?: 365) < 365) {
        out += Perk(Icons.Outlined.AutoGraph, "Step history", historyLabel(f(MyPlan.F.ANALYSIS_DAYS)?.free), historyLabel(f(MyPlan.F.ANALYSIS_DAYS)?.pro))
    }
    if (differs(MyPlan.F.WATER_REMINDERS)) {
        out += Perk(Icons.Outlined.WaterDrop, "Water reminders", freeCount(MyPlan.F.WATER_REMINDERS, "a day"), count(MyPlan.F.WATER_REMINDERS, "a day"))
    }
    if (differs(MyPlan.F.PLAN_QRUN) && on(MyPlan.F.PLAN_QRUN)) {
        out += Perk(Icons.Outlined.DirectionsRun, "Host QRuns", "Join only", "Yes")
    }
    return out
}

/**
 * The full-screen "Upgrade to StepQ Pro" page: a crown, one line and the button, nothing else.
 * The perks live on [ProBenefitsScreen] ("Explore Pro benefits"). It pops up for Free members
 * (see [UpgradeHost]) and opens from every limit sheet. There are no payments yet, so the
 * button asks StepQ for Pro.
 */
@Composable
fun UpgradeScreen(
    plan: MyPlan,
    asked: Boolean,
    onAsk: () -> Unit,
    onExplore: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ProColors.night),
    ) {
        Glow(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = ProColors.OnNightMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(ProColors.gold)
                    .border(3.dp, ProColors.Gold1.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Crown(52.dp, StepqColors.Ink) }
            Spacer(Modifier.height(28.dp))
            Text(
                "STEPQ PRO",
                style = StepqType.Label,
                color = ProColors.GoldOnNight,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Walk further,\nevery day",
                style = StepqType.Display,
                color = StepqColors.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "More streak saves, your own posts and a crown by your name.",
                style = StepqType.Body,
                color = ProColors.OnNightMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.2f))
            AskBlock(asked, onAsk)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onExplore) {
                Text("Explore Pro benefits  ›", style = StepqType.BodyStrong, color = ProColors.GoldOnNight)
            }
            TextButton(onClick = onClose) {
                Text("Maybe later", style = StepqType.SmallStrong, color = ProColors.OnNightMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** "Explore Pro benefits": every perk where Pro differs from Free, Free next to Pro. */
@Composable
fun ProBenefitsScreen(
    plan: MyPlan,
    asked: Boolean,
    onAsk: () -> Unit,
    onCompare: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(ProColors.night)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.White)
            }
            Text("Pro benefits", style = StepqType.Heading, color = StepqColors.White, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Spacer(Modifier.weight(1f))
                Text("FREE", style = StepqType.Label, color = ProColors.OnNightMuted, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                Row(Modifier.width(92.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Crown(14.dp, ProColors.GoldOnNight)
                    Spacer(Modifier.width(4.dp))
                    Text("PRO", style = StepqType.Label, color = ProColors.GoldOnNight)
                }
            }
            upgradePerks(plan).forEach { PerkRow(it) }
            TextButton(onClick = onCompare, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("See the full Free vs Pro table", style = StepqType.SmallStrong, color = ProColors.GoldOnNight)
            }
            Spacer(Modifier.height(8.dp))
        }
        // Below the list, not over it.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { AskBlock(asked, onAsk) }
    }
}

@Composable
private fun AskBlock(asked: Boolean, onAsk: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (asked) {
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, ProColors.Gold2, CircleShape).padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("You're on the list for Pro", style = StepqType.Button, color = ProColors.GoldOnNight)
            }
            Spacer(Modifier.height(8.dp))
            Text("We'll let you know as soon as Pro is yours.", style = StepqType.Small, color = ProColors.OnNightMuted, textAlign = TextAlign.Center)
        } else {
            GoldButton("Get StepQ Pro", onAsk)
            Spacer(Modifier.height(8.dp))
            Text("Payments open soon. Tap to ask and we'll turn Pro on for you.", style = StepqType.Small, color = ProColors.OnNightMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PerkRow(p: Perk) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, ProColors.Gold2.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(ProColors.Gold2.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(p.icon, contentDescription = null, tint = ProColors.GoldOnNight, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(p.title, style = StepqType.BodyStrong, color = StepqColors.White, modifier = Modifier.weight(1f))
        Text(p.free, style = StepqType.Small, color = ProColors.OnNightMuted, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        Box(
            Modifier.width(92.dp).clip(CircleShape).background(ProColors.Gold2.copy(alpha = 0.18f)).padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Text(p.pro, style = StepqType.SmallStrong, color = ProColors.GoldOnNight, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun Glow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.36f)
        drawCircle(
            Brush.radialGradient(listOf(ProColors.Gold2.copy(alpha = 0.28f), Color.Transparent), center = c, radius = size.width * 0.6f),
            radius = size.width * 0.6f,
            center = c,
        )
    }
}

/**
 * When the upgrade page shows. [show] opens it from anywhere (limit sheets, Streaks). MainShell
 * also opens it by itself for Free members, at most once every `upgrade_popup_days` days (an
 * admin setting; 0 or blank turns it off), never on the first day, and not once they've asked.
 */
object UpgradePrompt {
    var showing by mutableStateOf(false)
    fun show() { showing = true }

    private const val PREFS = "stepq"
    private const val FIRST = "upgrade_first_seen"
    private const val LAST = "upgrade_last_shown"

    /** Whether to pop it up now; records the time if so. */
    fun dueNow(context: android.content.Context, plan: MyPlan, alreadyAsked: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (plan.isPro || alreadyAsked) return false
        val days = plan.feature(MyPlan.F.UPGRADE_POPUP_DAYS)?.mine ?: return false
        if (days <= 0) return false
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val first = prefs.getLong(FIRST, 0L)
        if (first == 0L) {
            prefs.edit().putLong(FIRST, nowMs).apply()
            return false
        }
        val day = 24L * 60 * 60 * 1000
        if (nowMs - first < day) return false
        if (nowMs - prefs.getLong(LAST, 0L) < days * day) return false
        prefs.edit().putLong(LAST, nowMs).apply()
        return true
    }
}

/** Shows the upgrade page over everything, and pops it up on its own when [UpgradePrompt] says so. */
@Composable
fun UpgradeHost(profiles: com.stepq.app.data.ProfileRepository?) {
    val plan by com.stepq.app.data.Pro.plan.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asked by androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(plan != null, plan?.isPro) {
        val p = plan ?: return@LaunchedEffect
        if (p.isPro) return@LaunchedEffect
        asked = profiles?.onProWaitlist()?.getOrNull() ?: false
        if (UpgradePrompt.dueNow(context, p, asked)) UpgradePrompt.show()
    }
    if (!UpgradePrompt.showing) return
    var benefits by androidx.compose.runtime.remember { mutableStateOf(false) }
    val close = {
        UpgradePrompt.showing = false
        benefits = false
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val p = plan ?: MyPlan.FREE_DEFAULTS
        val ask: () -> Unit = {
            scope.launch { profiles?.joinProWaitlist()?.onSuccess { asked = true } }
        }
        if (benefits) {
            ProBenefitsScreen(
                plan = p,
                asked = asked,
                onAsk = ask,
                onCompare = {
                    close()
                    com.stepq.app.ui.components.ProSheets.openHub = true
                },
                onBack = { benefits = false },
            )
        } else {
            UpgradeScreen(p, asked, onAsk = ask, onExplore = { benefits = true }, onClose = close)
        }
    }
}
