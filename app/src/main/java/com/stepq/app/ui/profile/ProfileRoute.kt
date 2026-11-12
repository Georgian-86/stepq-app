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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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

private const val SUPPORT_EMAIL = "connect@stepq.in"
private const val PRIVACY_URL = "https://stepq.in/privacy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(repo: ProfileRepository, auth: AuthRepository?) {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(repo) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var pro by rememberSaveable { mutableStateOf(false) }
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
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
        when (page) {
            "goal" -> EditGoalScreen(state.profile?.dailyGoal ?: 8000, state.saving, onSave = { vm.saveGoal(it) { page = null } }, onBack = { page = null })
            "connect" -> ConnectStepsScreen(onBack = { page = null }, fromSettings = true) { page = null }
            else -> ProfileScreen(state, source) { action ->
                when (action) {
                    ProfileAction.Goal -> page = "goal"
                    ProfileAction.ConnectedApps -> page = "connect"
                    ProfileAction.Notifications -> runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                    ProfileAction.Privacy -> privacy = true
                    ProfileAction.Help -> open("mailto:$SUPPORT_EMAIL?subject=" + Uri.encode("StepQ app feedback"))
                    ProfileAction.LogOut -> confirmLogOut = true
                    ProfileAction.Pro -> pro = true
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

    if (pro) {
        ModalBottomSheet(onDismissRequest = { pro = false }, containerColor = StepqColors.White) {
            ProSheet(state.onWaitlist, onNotify = { vm.joinWaitlist { pro = false } }, onDismiss = { pro = false })
        }
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
    if (privacy) {
        AlertDialog(
            onDismissRequest = { privacy = false },
            title = { Text("Privacy and data", style = StepqType.Heading) },
            text = {
                Text(
                    "Only you, your friends and your group mates see your steps. Read the full policy, or email us to get a copy of your data or delete your account (done within 30 days).",
                    style = StepqType.Body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    privacy = false
                    open(PRIVACY_URL)
                }) { Text("Read the policy", color = StepqColors.Deep) }
            },
            dismissButton = {
                TextButton(onClick = {
                    privacy = false
                    open("mailto:$SUPPORT_EMAIL?subject=" + Uri.encode("Delete or export my StepQ data"))
                }) { Text("Email us", color = StepqColors.Ink) }
            },
            containerColor = StepqColors.White,
        )
    }
}
