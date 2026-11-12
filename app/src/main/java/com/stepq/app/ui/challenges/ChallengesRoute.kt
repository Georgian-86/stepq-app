package com.stepq.app.ui.challenges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.ChallengesRepository
import com.stepq.app.ui.friends.Toast
import com.stepq.app.ui.friends.shareText
import kotlinx.coroutines.delay

@Composable
fun ChallengesRoute(repo: ChallengesRepository) {
    val vm: ChallengesViewModel = viewModel { ChallengesViewModel(repo) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var monthly by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var justJoined by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        val card = state.data?.challenges?.firstOrNull { it.challenge.id == open }
        when {
            card != null && justJoined -> ChallengeJoinedScreen(
                card,
                onDone = {
                    justJoined = false
                    open = null
                },
                onShare = { shareText(context, "I joined \"${card.challenge.title}\" on StepQ. Join me: https://stepq.in") },
            )
            card != null -> ChallengeDetailScreen(
                card,
                joining = state.joining,
                onJoin = { vm.join(card.challenge.id) { justJoined = true } },
                onLeave = { vm.leave(card.challenge.id) },
                onBack = { open = null },
            )
            else -> ChallengesScreen(state, monthly, { monthly = it }, vm::refresh, onOpen = { open = it })
        }
        state.toast?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                vm.toastShown()
            }
            Toast(message, Modifier.align(Alignment.BottomCenter).padding(20.dp))
        }
    }
}
