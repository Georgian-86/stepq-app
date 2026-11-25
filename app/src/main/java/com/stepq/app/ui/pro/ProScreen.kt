package com.stepq.app.ui.pro

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.data.MyPlan
import com.stepq.app.data.PlanFeature
import com.stepq.app.data.Pro
import com.stepq.app.data.Profile
import com.stepq.app.ui.components.GoldButton
import com.stepq.app.ui.components.NightCard
import com.stepq.app.ui.components.PersonAvatar
import com.stepq.app.ui.components.ProBadge
import com.stepq.app.ui.components.UsageMeter
import com.stepq.app.ui.components.planValue
import com.stepq.app.ui.theme.ProColors
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqThemes
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * StepQ Pro. Pro members see their membership: this month's usage, theme, photo, BFF and QRun
 * hosting. Free members see Free vs Pro (straight from the admin-editable limits), what they've
 * used, and a way to ask for Pro.
 */
@Composable
fun ProScreen(
    plan: MyPlan?,
    profile: Profile?,
    onWaitlist: Boolean,
    onJoinWaitlist: () -> Unit,
    onInsights: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink) }
            Text("StepQ Pro", style = StepqType.Title, color = StepqColors.Ink)
        }
        Spacer(Modifier.height(10.dp))
        val p = plan ?: MyPlan.FREE_DEFAULTS
        if (p.isPro) {
            ProMember(p, profile, onInsights, onMessage)
        } else {
            FreeMember(p, onWaitlist, onJoinWaitlist, onInsights)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ProMember(plan: MyPlan, profile: Profile?, onInsights: () -> Unit, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val r = if (bytes == null) Result.failure(IllegalStateException()) else Pro.uploadPhoto(bytes)
            busy = false
            onMessage(if (r.isSuccess) "New profile photo saved." else "Couldn't save that photo. Try another one.")
        }
    }

    NightCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MEMBERSHIP", style = StepqType.Label, color = ProColors.GoldOnNight, modifier = Modifier.weight(1f))
                ProBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(profile?.avatar ?: com.stepq.app.data.AvatarConfig(), 64.dp, profile?.id ?: Pro.myId())
                Column(Modifier.padding(start = 14.dp)) {
                    Text(profile?.displayName ?: "You", style = StepqType.Title, color = StepqColors.White)
                    Text(memberSince(plan.proSince), style = StepqType.Small, color = ProColors.OnNightMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(ProColors.Gold3, ProColors.Gold1, ProColors.Gold3))))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Themes", "Photo", "BFF", "${plan.limit(MyPlan.F.STREAK_RESTORES) ?: "∞"} restores").forEach {
                    Text(it, style = StepqType.SmallStrong, color = ProColors.Gold1, modifier = Modifier.clip(CircleShape).background(StepqColors.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
    Section("This month")
    Meters(plan)
    Section("Make it yours")
    ThemePicker(plan, enabled = plan.has(MyPlan.F.CHANGE_THEME)) { key ->
        scope.launch { Pro.setTheme(key).onFailure { onMessage("Couldn't change the theme. Try again.") } }
    }
    Spacer(Modifier.height(10.dp))
    Line("Profile photo", if (busy) "Saving…" else if (plan.photoPath != null) "Change" else "Add") {
        if (!busy && plan.has(MyPlan.F.UPLOAD_PHOTO)) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    if (plan.photoPath != null) {
        Line("Remove photo", "Use my avatar") {
            scope.launch { Pro.removePhoto().onSuccess { onMessage("Back to your avatar.") } }
        }
    }
    Line("BFF", if (plan.bffId != null) "Pinned · change in Friends" else "Pin one in Friends → Streaks") {}
    Line(
        "Host QRuns",
        when {
            plan.canPostQRun -> "You can post QRuns ✓"
            plan.qrunHostRequestedAt != null -> "Asked · StepQ will reply"
            else -> "Ask StepQ"
        },
        valueColor = if (plan.canPostQRun) StepqColors.Deep else ProColors.GoldText,
    ) {
        if (!plan.canPostQRun && plan.qrunHostRequestedAt == null) {
            scope.launch {
                Pro.requestQRunHost()
                    .onSuccess { onMessage("Asked. StepQ will let you know when you can host.") }
                    .onFailure { onMessage("Couldn't send that. Try again.") }
            }
        }
    }
    Line("Insights", "Up to ${historyLabel(plan.limit(MyPlan.F.ANALYSIS_DAYS))} of steps", onClick = onInsights)
}

@Composable
private fun FreeMember(plan: MyPlan, onWaitlist: Boolean, onJoinWaitlist: () -> Unit, onInsights: () -> Unit) {
    NightCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProBadge()
            Text("Walk further with Pro", style = StepqType.Heading, color = StepqColors.White)
            Text(
                "Your photo, themes, a pinned BFF, more streak restores, every challenge and unlimited posts.",
                style = StepqType.Small,
                color = ProColors.OnNightMuted,
            )
        }
    }
    Section("Free vs Pro")
    PlanTable(plan.features)
    Text(
        "QRuns are hosted by StepQ and by Pro members StepQ approves.",
        style = StepqType.Small,
        color = StepqColors.Muted,
        modifier = Modifier.padding(top = 8.dp),
    )
    Section("Your Free plan this month")
    Meters(plan)
    Spacer(Modifier.height(20.dp))
    if (onWaitlist) {
        Text(
            "You've asked for Pro. We'll tell you as soon as it's yours.",
            style = StepqType.BodyStrong,
            color = ProColors.GoldText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        GoldButton("Ask for StepQ Pro", onJoinWaitlist)
    }
    Spacer(Modifier.height(8.dp))
    Line("Insights", "Last ${historyLabel(plan.limit(MyPlan.F.ANALYSIS_DAYS))}", onClick = onInsights)
}

/** Free vs Pro, one row per feature, in the admin's order. */
@Composable
fun PlanTable(features: List<PlanFeature>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, StepqColors.Line, RoundedCornerShape(16.dp)),
    ) {
        TableRow("", "Free", "Pro", header = true)
        // Post approval is how moderation works, not a perk: it's explained under the table.
        features.filter { it.key != MyPlan.F.POST_APPROVAL }.forEach { f ->
            TableRow(f.label, planValue(f.kind, f.free, f.unit), planValue(f.kind, f.pro, f.unit))
        }
    }
}

@Composable
private fun TableRow(label: String, free: String, pro: String, header: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(if (header) StepqColors.Page else StepqColors.White)
            .semantics(mergeDescendants = true) { if (!header) contentDescription = "$label: Free $free, Pro $pro" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (header) StepqType.Label else StepqType.Small,
            color = StepqColors.Ink,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp),
        )
        Box(Modifier.width(76.dp).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(free, style = if (header) StepqType.Label else StepqType.Small, color = StepqColors.Muted, textAlign = TextAlign.Center)
        }
        Box(
            Modifier.width(80.dp).fillMaxHeight().background(if (header) ProColors.goldFlat else Brush.horizontalGradient(listOf(ProColors.Champagne, ProColors.Champagne))).padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(pro, style = if (header) StepqType.Label else StepqType.SmallStrong, color = if (header) StepqColors.Ink else ProColors.GoldText, textAlign = TextAlign.Center)
        }
    }
    HorizontalDivider(color = StepqColors.Line)
}

@Composable
private fun Meters(plan: MyPlan) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StepqColors.Page).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UsageMeter("Streak restores", plan.usage.streakRestores, plan.limit(MyPlan.F.STREAK_RESTORES), plan.feature(MyPlan.F.STREAK_RESTORES)?.unit)
        UsageMeter("Community posts", plan.usage.communityPosts, plan.limit(MyPlan.F.COMMUNITY_POSTS), plan.feature(MyPlan.F.COMMUNITY_POSTS)?.unit)
        UsageMeter("Water reminders", plan.usage.waterReminders, plan.limit(MyPlan.F.WATER_REMINDERS), plan.feature(MyPlan.F.WATER_REMINDERS)?.unit)
    }
}

@Composable
fun ThemePicker(plan: MyPlan, enabled: Boolean, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StepqThemes.all.forEach { t ->
            val on = plan.activeTheme == t.key
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onPick(t.key) }
                    .semantics { selected = on; contentDescription = "${t.label} theme" }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .then(if (on) Modifier.border(3.dp, ProColors.Gold3, CircleShape) else Modifier)
                        .padding(if (on) 5.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(t.leaf, t.deep))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(t.label, style = if (on) StepqType.SmallStrong else StepqType.Small, color = if (on) StepqColors.Ink else StepqColors.Muted)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = StepqType.Subheading, color = StepqColors.Ink)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Line(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = ProColors.GoldText, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = StepqType.Body, color = StepqColors.Ink, modifier = Modifier.weight(1f))
            Text(value, style = StepqType.SmallStrong, color = valueColor)
        }
        HorizontalDivider(color = StepqColors.Line)
    }
}

fun historyLabel(days: Int?): String = when {
    days == null -> "a full year"
    days >= 365 -> "${days / 365} year" + if (days >= 730) "s" else ""
    else -> "$days days"
}

private fun memberSince(raw: String?): String {
    val date = raw?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() } ?: LocalDate.now()
    return "Member since " + date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}

