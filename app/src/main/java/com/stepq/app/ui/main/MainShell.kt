package com.stepq.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
) {
    var tab by rememberSaveable { mutableTabState() }
    val scope = rememberCoroutineScope()
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Home -> if (home != null) {
                    com.stepq.app.ui.home.HomeRoute(home, onSeeAllFriends = { tab = Tab.Friends })
                } else {
                    ComingSoon("Home", "Your steps, streak and friends today.")
                }
                Tab.Friends -> if (friends != null) {
                    com.stepq.app.ui.friends.FriendsRoute(friends)
                } else {
                    ComingSoon("Friends", "Friends, groups and QRuns.")
                }
                Tab.Challenges -> ComingSoon("Challenges", "Weekly and monthly leaderboards.")
                Tab.Profile -> ComingSoon("Profile", "Your goal, connected apps and settings.") {
                    StepqButton("Log out", { scope.launch { auth?.logOut() } }, style = StepqButtonStyle.Outline)
                }
            }
        }
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
