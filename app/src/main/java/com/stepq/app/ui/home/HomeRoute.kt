package com.stepq.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.HomeRepository
import com.stepq.app.health.StepsReader
import com.stepq.app.ui.onboarding.ConnectStepsScreen

/** Wires Home to its data: syncs Health Connect and reloads every time the app comes back. */
@Composable
fun HomeRoute(repo: HomeRepository, onSeeAllFriends: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: HomeViewModel = viewModel { HomeViewModel(repo, StepsReader(context), context) }
    val state by vm.state.collectAsState()
    var connecting by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    if (connecting) {
        ConnectStepsScreen(onBack = { connecting = false }) {
            connecting = false
            vm.refresh()
        }
    } else {
        HomeScreen(state, onRefresh = vm::refresh, onConnectSteps = { connecting = true }, onSeeAllFriends = onSeeAllFriends)
    }
}
