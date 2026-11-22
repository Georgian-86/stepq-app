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
import com.stepq.app.ui.community.CommunitySection
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay

/** The Friends tab: Friends leaderboard (A13/A14), the Community feed and QRun sections. */
@Composable
fun FriendsRoute(
    repo: FriendsRepository,
    communityRepo: com.stepq.app.data.CommunityRepository? = null,
    qrunsRepo: com.stepq.app.data.QRunsRepository? = null,
) {
    val vm: FriendsViewModel = viewModel { FriendsViewModel(repo) }
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var section by rememberSaveable { mutableStateOf(FriendsSection.Friends) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val qruns = qrunsRepo?.let { QRunSection(it, section == FriendsSection.QRun) }
    val community = communityRepo?.let { CommunitySection(it, section == FriendsSection.Community) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(StepqColors.White)) {
        // One key per full-screen page, so each page can still draw while it slides away.
        val key = when {
            qruns != null && qruns.page != null -> "qrun:${qruns.page}"
            community != null && community.page != null -> "community:${community.page}"
            adding -> "add"
            else -> "root"
        }
        com.stepq.app.ui.components.PageHost(
            target = key,
            depth = { if (it == "root") 0 else 1 },
            onBack = {
                when {
                    key.startsWith("qrun:") -> qruns?.close()
                    key.startsWith("community:") -> community?.back()
                    key == "add" -> {
                        adding = false
                        vm.clearSearch()
                    }
                }
            },
        ) { shown ->
        if (shown.startsWith("qrun:")) {
            qruns?.Page(shown.removePrefix("qrun:"))
        } else if (shown.startsWith("community:")) {
            community?.Page(shown.removePrefix("community:"))
        } else if (shown == "add") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val loadContacts = { vm.loadContacts { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.stepq.app.data.Contacts.emailHashes(context) } } }
            val contactsPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { granted -> if (granted) loadContacts() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                vm.openAdd()
                // Already allowed earlier: show contacts straight away.
                if (state.add.contacts == null && androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_CONTACTS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) loadContacts()
            }
            AddFriendScreen(
                myUsername = state.data?.me?.username.orEmpty(),
                state = state.add,
                relationOf = { id -> state.data?.let { relationTo(id, it) } ?: Relation.None },
                onType = vm::type,
                onFindContacts = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) },
                onRequest = vm::sendRequest,
                onAccept = vm::accept,
                onInvite = { shareText(context, appInviteText(state.data?.me?.username)) },
                onBack = {
                    adding = false
                    vm.type("")
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                FriendsHeader(section, { section = it }, onAddFriend = { adding = true })
                val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
                androidx.compose.animation.AnimatedContent(
                    section,
                    transitionSpec = { com.stepq.app.ui.components.lateralTransition(targetState.ordinal > initialState.ordinal, motion) },
                    label = "friendsSection",
                ) { sec ->
                when (sec) {
                    FriendsSection.Friends -> FriendsList(
                        state,
                        onRefresh = vm::refresh,
                        onRemind = vm::remind,
                        onAccept = vm::accept,
                        onDecline = vm::decline,
                        onAddFriend = { adding = true },
                    )
                    FriendsSection.Community -> community?.Feed()
                        ?: SectionComingSoon("Community", "Share your walks and wins with everyone on StepQ.")
                    FriendsSection.QRun -> qruns?.List()
                        ?: SectionComingSoon("QRun", "Plan a walk or run with people near you. Coming soon.")
                }
                }
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
        qruns?.Overlays(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp))
        community?.Overlays(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp))
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
