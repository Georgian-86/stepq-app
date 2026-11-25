package com.stepq.app.ui.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/** What a Profile row opens. */
enum class ProfileAction { EditProfile, Account, Goal, ConnectedApps, Notifications, Privacy, Help, LogOut, Pro, Insights }

/** A26 Profile. */
@Composable
fun ProfileScreen(state: ProfileUiState, stepSource: String, plan: com.stepq.app.data.MyPlan? = null, onAction: (ProfileAction) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Profile", style = StepqType.Title, color = StepqColors.Ink)
        val p = state.profile
        if (p == null) {
            if (state.loading) {
                ProfileSkeleton()
            } else {
                Text(state.error ?: "Couldn't load your profile.", style = StepqType.Body, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 16.dp))
            }
        } else {
            Spacer(Modifier.height(16.dp))
            if (plan?.isPro == true) {
                ProHero(p, state.stats)
                Spacer(Modifier.height(12.dp))
                com.stepq.app.ui.components.ProRow("Your Pro membership", "Themes, photo, BFF, restores and more", { onAction(ProfileAction.Pro) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.stepq.app.ui.components.PersonAvatar(p.avatar, 84.dp, p.id)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(p.displayName, style = StepqType.Heading, color = StepqColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${p.username}", style = StepqType.Body, color = StepqColors.Muted)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Free plan",
                            style = StepqType.SmallStrong,
                            color = StepqColors.Deep,
                            modifier = Modifier.clip(CircleShape).background(StepqColors.LeafSoft).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                com.stepq.app.ui.components.ProRow("StepQ Pro", "Your photo, themes, a BFF and unlimited posts", { onAction(ProfileAction.Pro) })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(formatSteps(state.stats.score), "StepQ score", Modifier.weight(1f))
                    Stat(formatSteps(state.stats.bestDay), "best day", Modifier.weight(1f))
                    Stat(formatSteps(state.stats.kmSoFar), "km so far", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Item(Icons.Outlined.Edit, "Edit profile", "Name, username, avatar") { onAction(ProfileAction.EditProfile) }
            Item(Icons.Outlined.ManageAccounts, "Account", "Email, password, delete account") { onAction(ProfileAction.Account) }
            Item(Icons.Outlined.Insights, "Insights", "Your steps over time") { onAction(ProfileAction.Insights) }
            Item(Icons.Outlined.TrackChanges, "Daily goal", "${formatSteps(p.dailyGoal)} steps" + (p.nextDailyGoal?.let { " · ${formatSteps(it)} from tomorrow" } ?: "")) { onAction(ProfileAction.Goal) }
            Item(Icons.Outlined.Link, "Connected apps", stepSource) { onAction(ProfileAction.ConnectedApps) }
            Item(Icons.Outlined.NotificationsNone, "Notifications") { onAction(ProfileAction.Notifications) }
            Item(Icons.Outlined.Shield, "Privacy and data") { onAction(ProfileAction.Privacy) }
            Item(Icons.AutoMirrored.Outlined.HelpOutline, "Help and feedback") { onAction(ProfileAction.Help) }
            Item(Icons.AutoMirrored.Outlined.Logout, "Log out", color = StepqColors.TomatoText, chevron = false) { onAction(ProfileAction.LogOut) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pro members' profile header: night-green card, gold ring, Pro badge and stats inside. */
@Composable
private fun ProHero(p: com.stepq.app.data.Profile, stats: ProfileStats) {
    com.stepq.app.ui.components.NightCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.stepq.app.ui.components.PersonAvatar(p.avatar, 88.dp, p.id)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(p.displayName, style = StepqType.Heading, color = StepqColors.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${p.username}", style = StepqType.Small, color = com.stepq.app.ui.theme.ProColors.OnNightMuted)
                    Spacer(Modifier.height(6.dp))
                    com.stepq.app.ui.components.ProBadge()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(formatSteps(stats.score) to "StepQ score", formatSteps(stats.bestDay) to "best day", formatSteps(stats.kmSoFar) to "km so far").forEach { (v, l) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(StepqColors.White.copy(alpha = 0.08f)).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(v, style = StepqType.NumberM, color = StepqColors.White)
                        Text(l, style = StepqType.Small, color = com.stepq.app.ui.theme.ProColors.OnNightMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = StepqType.NumberM, color = StepqColors.Ink)
        Text(label, style = StepqType.Small, color = StepqColors.Muted)
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, value: String? = null, color: Color = StepqColors.Ink, chevron: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = StepqType.Body, color = color, modifier = Modifier.weight(1f))
            value?.let { Text(it, style = StepqType.Small, color = StepqColors.Muted) }
            if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StepqColors.Ink)
        }
        HorizontalDivider(color = StepqColors.Line)
    }
}

/** Change the daily goal from Profile. */
@Composable
fun EditGoalScreen(current: Int, saving: Boolean, onSave: (Int) -> Unit, onBack: () -> Unit, pending: Int? = null) {
    BackHandler(onBack = onBack)
    var goal by rememberSaveable { mutableIntStateOf(pending ?: current) }
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(4.dp))
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink) }
        Text("Daily goal", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Each day you reach it, your StepQ score goes up by 1.", style = StepqType.Body, color = StepqColors.Muted)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            RoundButton(Icons.Outlined.Remove, "Fewer steps", goal > GOAL_MIN) { goal = (goal - GOAL_STEP).coerceAtLeast(GOAL_MIN) }
            Column(Modifier.width(180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatSteps(goal), style = StepqType.NumberXL, color = StepqColors.Ink)
                Text("steps a day", style = StepqType.Small, color = StepqColors.Muted)
            }
            RoundButton(Icons.Outlined.Add, "More steps", goal < GOAL_MAX) { goal = (goal + GOAL_STEP).coerceAtMost(GOAL_MAX) }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5000, 8000, 10000, 12000).forEach { preset ->
                val on = preset == goal
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (on) StepqColors.Deep else StepqColors.Mint)
                        .clickable(role = Role.RadioButton) { goal = preset }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(formatSteps(preset), style = StepqType.SmallStrong, color = if (on) StepqColors.White else StepqColors.Ink)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (goal == current) "Today's goal is ${formatSteps(current)} steps."
            else "Starts tomorrow. Today you're still aiming for ${formatSteps(current)} steps, so today still counts towards your StepQ score at that goal.",
            style = StepqType.Small,
            color = StepqColors.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        StepqButton(
            if (goal == current && pending != null) "Keep today's goal" else "Save",
            { onSave(goal) },
            Modifier.padding(vertical = 16.dp),
            enabled = goal != (pending ?: current),
            loading = saving,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (enabled) StepqColors.Mint else StepqColors.Page)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) StepqColors.Ink else StepqColors.Line) }
}

const val GOAL_MIN = 1000
const val GOAL_MAX = 100000
const val GOAL_STEP = 500

@Composable
private fun ProfileSkeleton() {
    com.stepq.app.ui.components.PikoLoader(Modifier.padding(top = 60.dp), pikoSize = 120.dp)
}
