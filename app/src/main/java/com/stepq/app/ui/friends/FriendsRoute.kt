package com.stepq.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.FriendsRepository
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay

/** The Friends tab: Friends leaderboard (A13/A14), Groups and QRun sections. */
@Composable
fun FriendsRoute(repo: FriendsRepository) {
    val vm: FriendsViewModel = viewModel { FriendsViewModel(repo) }
    val state by vm.state.collectAsState()
    var section by rememberSaveable { mutableStateOf(FriendsSection.Friends) }
    var adding by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(StepqColors.White)) {
        if (adding) {
            AddFriendScreen(
                myUsername = state.data?.me?.username.orEmpty(),
                result = state.search,
                onSearch = vm::search,
                onTyping = vm::clearSearch,
                onRequest = vm::sendRequest,
                onAccept = vm::accept,
                onBack = {
                    adding = false
                    vm.clearSearch()
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                FriendsHeader(section, { section = it }, onAddFriend = { adding = true })
                when (section) {
                    FriendsSection.Friends -> FriendsList(
                        state,
                        onRefresh = vm::refresh,
                        onRemind = vm::remind,
                        onAccept = vm::accept,
                        onDecline = vm::decline,
                        onAddFriend = { adding = true },
                    )
                    FriendsSection.Groups -> SectionComingSoon("Groups", "Walk with your office, family or college group. Coming next.")
                    FriendsSection.QRun -> SectionComingSoon("QRun", "Plan a walk or run with people near you. Coming soon.")
                }
            }
        }
        state.toast?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                vm.toastShown()
            }
            Toast(message, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp))
        }
    }
}

/** The dark confirmation bar from A14 ("Water reminder sent to Meera. 4 left today."). */
@Composable
fun Toast(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StepqColors.Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = StepqColors.Mango, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, style = StepqType.SmallStrong, color = StepqColors.White)
    }
}
