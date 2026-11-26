package com.stepq.app.ui.main

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.AuthRepository
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Friends("Friends", Icons.Outlined.Group),
    Challenges("Challenges", Icons.Outlined.EmojiEvents),
    Profile("Profile", Icons.Outlined.Person),
}

/** The four-tab shell from A12/A13/A22/A26. Friends, Challenges and Profile land in later PRs. */
@Composable
fun MainShell(
    auth: AuthRepository?,
    home: com.stepq.app.data.HomeRepository?,
    friends: com.stepq.app.data.FriendsRepository? = null,
    groups: com.stepq.app.data.GroupsRepository? = null,
    challenges: com.stepq.app.data.ChallengesRepository? = null,
    profiles: com.stepq.app.data.ProfileRepository? = null,
    qruns: com.stepq.app.data.QRunsRepository? = null,
    community: com.stepq.app.data.CommunityRepository? = null,
) {
    var tab by rememberSaveable { mutableTabState() }
    // Set when Home's streak card is tapped: the Friends tab opens on its Streaks section.
    var friendsStart by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.stepq.app.ui.friends.FriendsSection?>(null) }
    val scope = rememberCoroutineScope()
    AskForNotificationsOnce()
    val pushRoute by com.stepq.app.push.PushRoute.pending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pushRoute) {
        com.stepq.app.push.PushRoute.tabFor(pushRoute)?.let {
            tab = it
            com.stepq.app.push.PushRoute.pending.value = null
        }
    }
    Scaffold(
        containerColor = StepqColors.White,
        bottomBar = {
            Column {
                HorizontalDivider(color = StepqColors.Line)
                NavigationBar(containerColor = StepqColors.White, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, style = StepqType.Small) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StepqColors.Deep,
                                selectedTextColor = StepqColors.Deep,
                                unselectedIconColor = StepqColors.Muted,
                                unselectedTextColor = StepqColors.Muted,
                                indicatorColor = StepqColors.Mint,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Pages inside a tab pad for the status bar themselves; consuming the shell's padding stops it being added twice.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            val motion = com.stepq.app.ui.components.LocalMotionEnabled.current
            androidx.compose.animation.AnimatedContent(
                tab,
                transitionSpec = { com.stepq.app.ui.components.tabTransition(targetState.ordinal > initialState.ordinal, motion) },
                label = "tabs",
            ) { current ->
            when (current) {
                Tab.Home -> if (home != null) {
                    com.stepq.app.ui.home.HomeRoute(
                        home,
                        onSeeAllFriends = { tab = Tab.Friends },
                        onOpenStreaks = {
                            friendsStart = com.stepq.app.ui.friends.FriendsSection.Streaks
                            tab = Tab.Friends
                        },
                    )
                } else {
                    ComingSoon("Home", "Your steps, streak and friends today.")
                }
                Tab.Friends -> if (friends != null) {
                    com.stepq.app.ui.friends.FriendsRoute(
                        friends, community, qruns,
                        homeRepo = home,
                        startSection = friendsStart,
                        onStartSectionUsed = { friendsStart = null },
                    )
                } else {
                    ComingSoon("Friends", "Friends, the community and QRuns.")
                }
                Tab.Challenges -> if (challenges != null) {
                    com.stepq.app.ui.challenges.ChallengesRoute(challenges)
                } else {
                    ComingSoon("Challenges", "Weekly and monthly leaderboards.")
                }
                Tab.Profile -> if (profiles != null) {
                    com.stepq.app.ui.profile.ProfileRoute(profiles, auth)
                } else {
                    ComingSoon("Profile", "Your goal, connected apps and settings.") {
                        StepqButton("Log out", { scope.launch { auth?.logOut() } }, style = StepqButtonStyle.Outline)
                    }
                }
            }
            }
        }
    }
    // "Upgrade to StepQ Pro" on a limit sheet opens the upgrade page.
    com.stepq.app.ui.components.ProSheetHost(onSeePro = { com.stepq.app.ui.pro.UpgradePrompt.show() })
    com.stepq.app.ui.pro.UpgradeHost(profiles)
    // "See everything in Free vs Pro" sets openHub; the Profile tab opens its Pro page and clears it.
    androidx.compose.runtime.LaunchedEffect(com.stepq.app.ui.components.ProSheets.openHub) {
        if (com.stepq.app.ui.components.ProSheets.openHub) tab = Tab.Profile
    }
}

private fun mutableTabState() = androidx.compose.runtime.mutableStateOf(Tab.Home)

@Composable
private fun ComingSoon(title: String, body: String, extra: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.piko_happy), contentDescription = null, Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(body, style = StepqType.Body, color = StepqColors.Muted, textAlign = TextAlign.Center)
        if (extra != null) {
            Spacer(Modifier.height(32.dp))
            extra()
        }
    }
}

/** Asks once (Android 13+) so water reminders from friends and QRun reminders can be shown. */
@Composable
private fun AskForNotificationsOnce() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("stepq", android.content.Context.MODE_PRIVATE)
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
