package com.stepq.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.ProfileRepository
import com.stepq.app.health.StepsReader
import com.stepq.app.ui.friends.Toast
import com.stepq.app.ui.onboarding.ConnectStepsScreen
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = com.stepq.app.ui.components.Legal.SUPPORT_EMAIL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(repo: ProfileRepository, auth: AuthRepository?) {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(repo) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val plan by com.stepq.app.data.Pro.plan.collectAsState()
    var limitSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }
    // "Download my data": the JSON waits here while the person picks where to save it.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("no stream") }.isSuccess
            vm.showToast(if (ok) "Saved your StepQ data" else "Couldn't save the file. Try another folder.")
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    fun go(to: String?) { vm.clearForm(); page = to }
    LaunchedEffect(com.stepq.app.ui.components.ProSheets.openHub) {
        if (com.stepq.app.ui.components.ProSheets.openHub) {
            com.stepq.app.ui.components.ProSheets.openHub = false
            page = "pro"
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        com.stepq.app.data.Pro.refreshAsync()
        onPauseOrDispose { }
    }
    val source by produceState("", page) {
        value = when (StepsReader(context.applicationContext).source()) {
            StepsReader.Source.HealthConnect -> "Health Connect"
            StepsReader.Source.Phone -> "Phone step counter"
            StepsReader.Source.None -> "Not connected"
        }
    }

    fun open(uri: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }

    Box(Modifier.fillMaxSize()) {
        com.stepq.app.ui.components.PageHost(
            target = page,
            depth = { when (it) { null -> 0; "password", "email", "insights" -> 2; else -> 1 } },
            onBack = { go(if (page == "password" || page == "email") "account" else null) },
        ) { shown ->
        when (shown) {
            "goal" -> EditGoalScreen(state.profile?.dailyGoal ?: 8000, state.saving, pending = state.profile?.nextDailyGoal, onSave = { vm.saveGoal(it) { page = null } }, onBack = { page = null })
            "pro" -> com.stepq.app.ui.pro.ProScreen(
                plan = plan,
                profile = state.profile,
                onWaitlist = state.onWaitlist,
                onJoinWaitlist = { vm.joinWaitlist { } },
                onInsights = { page = "insights" },
                onBack = { page = null },
                onMessage = { vm.showToast(it) },
            )
            "insights" -> com.stepq.app.ui.pro.InsightsScreen(
                plan,
                state.profile?.dailyGoal ?: 8000,
                onBack = { page = null },
                onLocked = { limitSheet = com.stepq.app.data.MyPlan.F.ANALYSIS_DAYS },
            )
            "connect" -> ConnectStepsScreen(onBack = { page = null }, fromSettings = true) { page = null }
            "edit" -> state.profile?.let { p ->
                EditProfileScreen(
                    p,
                    state.saving,
                    state.formError,
                    checkUsername = { vm.usernameAvailable(it) },
                    onSave = { name, username, avatar, gender -> vm.saveProfile(name, username, avatar, gender) { go(null) } },
                    onBack = { go(null) },
                )
            }
            "account" -> AccountScreen(
                email = auth?.email(),
                pendingEmail = auth?.pendingEmail(),
                hasPassword = auth?.hasPassword() ?: false,
                onChangeEmail = { go("email") },
                onChangePassword = { go("password") },
                onDelete = { vm.clearForm(); confirmDelete = true },
                onBack = { go(null) },
            )
            "password" -> NewPasswordScreen(
                "Change password",
                "You'll stay logged in on this phone.",
                state.saving,
                state.formError,
                onSave = { pw -> auth?.let { vm.changePassword(it, pw) { go("account") } } },
                onBack = { go("account") },
            )
            "privacy" -> {
                LaunchedEffect(Unit) { vm.loadBlocks() }
                PrivacyScreen(
                    exporting = state.exporting,
                    blocks = state.blocks,
                    onExport = {
                        vm.exportData { json ->
                            pendingExport = json
                            saveExport.launch("stepq-data-${java.time.LocalDate.now()}.json")
                        }
                    },
                    onPermissions = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }
                    },
                    onAccount = { go("account") },
                    onUnblock = { vm.unblock(it) },
                    onOpen = { open(it) },
                    onBack = { go(null) },
                )
            }
            "email" -> ChangeEmailScreen(
                auth?.email(),
                state.saving,
                state.formError,
                state.emailSentTo,
                onSave = { e -> auth?.let { vm.changeEmail(it, e) } },
                onBack = { go("account") },
            )
            else -> ProfileScreen(state, source, plan) { action ->
                when (action) {
                    ProfileAction.EditProfile -> go("edit")
                    ProfileAction.Account -> go("account")
                    ProfileAction.Goal -> page = "goal"
                    ProfileAction.ConnectedApps -> page = "connect"
                    ProfileAction.Notifications -> runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                    ProfileAction.Privacy -> go("privacy")
                    ProfileAction.Help -> open("mailto:$SUPPORT_EMAIL?subject=" + Uri.encode("StepQ app feedback"))
                    ProfileAction.LogOut -> confirmLogOut = true
                    ProfileAction.Pro -> page = "pro"
                    ProfileAction.Insights -> page = "insights"
                }
            }
        }
        }
        state.toast?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                vm.toastShown()
            }
            Toast(message, Modifier.align(Alignment.BottomCenter).padding(20.dp))
        }
    }

    limitSheet?.let { key ->
        ModalBottomSheet(onDismissRequest = { limitSheet = null }, containerColor = StepqColors.White) {
            com.stepq.app.ui.components.PlanLimitSheet(key, plan, onSeePro = { limitSheet = null; page = "pro" }, onDismiss = { limitSheet = null })
        }
    }
    state.goalSaved?.let { change ->
        GoalUpdatedDialog(change, onDismiss = { vm.goalSavedSeen() })
    }
    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            title = { Text("Log out of StepQ?", style = StepqType.Heading) },
            text = { Text("Your steps stay saved. Log in again any time.", style = StepqType.Body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogOut = false
                    scope.launch { auth?.logOut() }
                }) { Text("Log out", color = StepqColors.TomatoText) }
            },
            dismissButton = { TextButton(onClick = { confirmLogOut = false }) { Text("Cancel", color = StepqColors.Ink) } },
            containerColor = StepqColors.White,
        )
    }
    if (confirmDelete && auth != null) {
        DeleteAccountDialog(state.saving, state.formError, onConfirm = { vm.deleteAccount(auth) }, onDismiss = { confirmDelete = false })
    }
}
