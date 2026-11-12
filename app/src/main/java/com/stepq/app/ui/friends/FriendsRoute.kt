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
import com.stepq.app.ui.qrun.QRunSection
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay

/** The Friends tab: Friends leaderboard (A13/A14), Groups and QRun sections. */
@Composable
fun FriendsRoute(
    repo: FriendsRepository,
    groupsRepo: com.stepq.app.data.GroupsRepository,
    qrunsRepo: com.stepq.app.data.QRunsRepository? = null,
) {
    val vm: FriendsViewModel = viewModel { FriendsViewModel(repo) }
    val state by vm.state.collectAsState()
    val groupsVm: GroupsViewModel = viewModel { GroupsViewModel(groupsRepo) }
    val groups by groupsVm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Which full-screen page of the Groups section is open: null, "create", "join" or a group id.
    var groupPage by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf(FriendsSection.Friends) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val qruns = qrunsRepo?.let { QRunSection(it, section == FriendsSection.QRun) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        groupsVm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(StepqColors.White)) {
        val openGroup = groups.data?.groups?.firstOrNull { it.group.id == groupPage }
        if (qruns != null && qruns.page != null) {
            qruns.Page()
        } else if (groupPage == "create") {
            CreateGroupScreen(
                friends = groups.data?.friends.orEmpty(),
                busy = groups.busy,
                error = groups.formError,
                onCreate = { name, ids -> groupsVm.create(name, ids) { groupPage = it } },
                onTyping = groupsVm::clearFormError,
                onInvite = { shareText(context, appInviteText(state.data?.me?.username)) },
                onBack = { groupPage = null },
            )
        } else if (groupPage == "join") {
            JoinGroupScreen(
                busy = groups.busy,
                error = groups.formError,
                onJoin = { code -> groupsVm.join(code) { groupPage = it } },
                onTyping = groupsVm::clearFormError,
                onBack = { groupPage = null },
            )
        } else if (openGroup != null) {
            GroupScreen(
                group = openGroup,
                myId = groups.data?.me?.id.orEmpty(),
                friendIds = groups.data?.friendIds.orEmpty(),
                remindedToday = state.data?.remindedToday.orEmpty(),
                onRemind = vm::remind,
                onInvite = { shareText(context, groupInvite(openGroup)) },
                onLeave = { groupsVm.leave(openGroup.group.id) { groupPage = null } },
                onBack = { groupPage = null },
            )
        } else if (adding) {
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
                    FriendsSection.Groups -> GroupsList(
                        groups,
                        onRefresh = groupsVm::refresh,
                        onCreate = { groupPage = "create" },
                        onJoin = { groupPage = "join" },
                        onOpen = { groupPage = it },
                        onInviteApp = { shareText(context, appInviteText(state.data?.me?.username)) },
                    )
                    FriendsSection.QRun -> qruns?.List()
                        ?: SectionComingSoon("QRun", "Plan a walk or run with people near you. Coming soon.")
                }
            }
        }
        (state.toast ?: groups.toast)?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                vm.toastShown()
                groupsVm.toastShown()
            }
            Toast(message, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp))
        }
        qruns?.Overlays(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

/** The dark confirmation bar from A14 ("Water reminder sent to Meera. 4 left today."). */
@Composable
fun Toast(
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.WaterDrop,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StepqColors.Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = StepqColors.Mango, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, style = StepqType.SmallStrong, color = StepqColors.White)
    }
}
