package com.stepq.app.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.stepq.app.ui.auth.ForgotPasswordScreen
import com.stepq.app.ui.auth.LoginScreen
import com.stepq.app.ui.auth.SplashScreen
import com.stepq.app.ui.theme.StepqTheme
import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.Gender
import com.stepq.app.ui.onboarding.AboutYouScreen
import com.stepq.app.ui.onboarding.AllSetScreen
import com.stepq.app.ui.onboarding.AvatarScreen
import com.stepq.app.ui.onboarding.ConnectStepsScreen
import com.stepq.app.ui.onboarding.GoalScreen
import com.stepq.app.ui.onboarding.NameScreen
import com.stepq.app.ui.onboarding.OnboardingViewModel
import com.stepq.app.ui.onboarding.SignUpAccountScreen
import java.time.LocalDate
import com.stepq.app.ui.home.FriendRow
import com.stepq.app.ui.home.HomeScreen
import com.stepq.app.ui.home.HomeUiState
import com.stepq.app.ui.home.PikoMood
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen at the Figma frame size (390×844) so it can be compared with the design.
 * `./gradlew recordRoborazziDebug` writes the images to app/build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent { StepqTheme(content) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun a01Splash() = snap("A01-splash") { SplashScreen({}, {}) }
    @Test fun a02LogIn() = snap("A02-log-in") { LoginScreen(null, {}, {}, {}) }
    @Test fun a03ForgotPassword() = snap("A03-forgot-password") { ForgotPasswordScreen(null) {} }
    private val riya = OnboardingViewModel().apply {
        fullName = "Riya Sharma"
        gender = Gender.Female
        birthdate = LocalDate.of(2001, 3, 14)
        avatar = AvatarConfig(skin = 2, hair = 0, outfit = 0, headband = 0)
    }

    @Test fun a05SignUp() = snap("A05-sign-up-account") { SignUpAccountScreen(null, {}, {}, {}) }
    @Test fun a06Name() = snap("A06-sign-up-name") { NameScreen(riya, null, {}, {}) }
    @Test fun a07About() = snap("A07-sign-up-about-you") { AboutYouScreen(riya, {}, {}) }
    @Test fun a08Avatar() = snap("A08-avatar") { AvatarScreen(riya, {}, {}) }
    @Test fun a09Goal() = snap("A09-daily-goal") { GoalScreen(riya, null, {}, {}) }
    @Test fun a10Connect() = snap("A10-connect-steps") { ConnectStepsScreen(onBack = {}) {} }
    @Test fun a11AllSet() = snap("A11-all-set") { AllSetScreen("Riya", 8000) {} }
    private fun home(steps: Int, mood: PikoMood, greeting: String = "Good morning") = HomeUiState(
        loading = false, dateLabel = "Saturday, 17 Oct", greeting = greeting, firstName = "Riya", streak = 12,
        steps = steps, goal = 8000, km = steps * 0.000762, kcal = steps * 38 / 1000, activeMinutes = steps / 120,
        week = listOf(6100, 7800, 4300, 8600, 7100, 5600, steps), weekAverage = 7420,
        friends = listOf(
            FriendRow(1, "Arjun", AvatarConfig(skin = 3, outfit = 3), 9120, false),
            FriendRow(2, "Meera", AvatarConfig(skin = 1, outfit = 2, headband = 2), 7480, false),
            FriendRow(3, "You", AvatarConfig(), steps, true),
        ).sortedByDescending { it.steps }.mapIndexed { i, r -> r.copy(rank = i + 1) },
        hasFriends = true, mood = mood,
    )

    @Test fun a12Home() = snap("A12-home") { HomeScreen(home(6240, PikoMood.Nudge), {}, {}, {}) }
    @Test fun a12bGoalReached() = snap("A12b-home-goal-reached") { HomeScreen(home(8000, PikoMood.Cheer), {}, {}, {}) }
    @Test fun a12cRestOfDay() = snap("A12c-home-rest-of-day") { HomeScreen(home(9450, PikoMood.Happy, "Good evening"), {}, {}, {}) }
    @Test fun a12NotConnected() = snap("A12-home-not-connected") {
        HomeScreen(home(0, PikoMood.Nudge).copy(healthConnected = false, friends = emptyList(), hasFriends = false), {}, {}, {})
    }

    private val friendsData = run {
        fun p(id: String, name: String, user: String, skin: Int, hair: Int, outfit: Int) =
            com.stepq.app.data.PersonSummary(id, name, user, AvatarConfig(skin = skin, hair = hair, outfit = outfit))
        val me = com.stepq.app.data.Profile(id = "me", displayName = "Riya Sharma", username = "riya.walks")
        val board = listOf(
            Triple(p("a", "Arjun", "arjun.m", 4, 1, 1), 9120, 7000),
            Triple(p("b", "Meera", "meera_walks", 1, 2, 2), 7480, 5700),
            Triple(p("me", "Riya Sharma", "riya.walks", 2, 0, 0), 6240, 4800),
            Triple(p("c", "Kabir", "kabir.k", 3, 1, 3), 5910, 4500),
            Triple(p("d", "Sana", "sana.s", 1, 3, 4), 4300, 3300),
            Triple(p("e", "Dev", "dev_on_foot", 5, 1, 0), 3050, 2300),
        ).map { (person, steps, m) -> com.stepq.app.data.FriendToday(person, steps, m, isMe = person.id == "me") }
        com.stepq.app.data.FriendsData(me, board, emptyList(), emptySet(), emptySet(), remindersLeft = 5)
    }

    @Composable
    private fun friends(state: com.stepq.app.ui.friends.FriendsUiState) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column {
                com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Friends, {}, {})
                com.stepq.app.ui.friends.FriendsList(state, {}, {}, {}, {}, {})
            }
            state.toast?.let {
                com.stepq.app.ui.friends.Toast(
                    it,
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(20.dp),
                )
            }
        }
    }

    @Test fun a13Friends() = snap("A13-friends") {
        friends(com.stepq.app.ui.friends.FriendsUiState(loading = false, data = friendsData))
    }

    @Test fun a14ReminderSent() = snap("A14-reminder-sent") {
        friends(
            com.stepq.app.ui.friends.FriendsUiState(
                loading = false,
                data = friendsData.copy(remindedToday = setOf("b"), remindersLeft = 4),
                toast = com.stepq.app.ui.friends.reminderSentMessage("Meera", 4),
            ),
        )
    }

    @Test fun a13Requests() = snap("A13-friend-requests") {
        val asking = com.stepq.app.data.PersonSummary("z", "Zoya Khan", "zoya.k", AvatarConfig(skin = 3, hair = 2, outfit = 1))
        friends(
            com.stepq.app.ui.friends.FriendsUiState(
                loading = false,
                data = friendsData.copy(board = friendsData.board.filter { it.isMe }, requests = listOf(asking)),
            ),
        )
    }

    @Test fun a13AddFriend() = snap("A13-add-friend") {
        com.stepq.app.ui.friends.AddFriendScreen(
            myUsername = "riya.walks",
            result = com.stepq.app.ui.friends.SearchResult.Found(
                com.stepq.app.data.PersonSummary("a", "Arjun Mehta", "arjun.m", AvatarConfig(skin = 4, hair = 1, outfit = 1)),
                com.stepq.app.ui.friends.Relation.None,
            ),
            onSearch = {}, onTyping = {}, onRequest = {}, onAccept = {}, onBack = {},
        )
    }

    private fun group(id: String, name: String, take: Int, extra: Int) = com.stepq.app.data.GroupToday(
        com.stepq.app.data.GroupRow(id, name, "me", "3f9a1c07b2de"),
        friendsData.board.take(take).map { it.copy(steps = it.steps + extra) },
    )
    private val groupsData = com.stepq.app.data.GroupsData(
        me = friendsData.me,
        groups = listOf(group("g1", "Morning walkers", 5, 0), group("g2", "Office step-up", 6, 2000), group("g3", "Family", 4, -1000)),
        friends = friendsData.board.filterNot { it.isMe }.map { it.person },
        friendIds = friendsData.board.filterNot { it.isMe }.map { it.person.id }.toSet(),
    )

    @Test fun a15Groups() = snap("A15-groups") {
        androidx.compose.foundation.layout.Column {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Groups, {}, {})
            com.stepq.app.ui.friends.GroupsList(com.stepq.app.ui.friends.GroupsUiState(loading = false, data = groupsData), {}, {}, {}, {}, {})
        }
    }

    @Test fun a16CreateGroup() = snap("A16-create-group") {
        com.stepq.app.ui.friends.CreateGroupScreen(groupsData.friends, busy = false, error = null, onCreate = { _, _ -> }, onTyping = {}, onInvite = {}, onBack = {})
    }

    @Test fun a17Group() = snap("A17-group") {
        com.stepq.app.ui.friends.GroupScreen(
            group = groupsData.groups.first { it.group.id == "g1" },
            myId = "me",
            friendIds = groupsData.friendIds,
            remindedToday = setOf("b"),
            onRemind = {}, onInvite = {}, onLeave = {}, onBack = {},
        )
    }

    private val shoes = com.stepq.app.data.ChallengeCard(
        challenge = com.stepq.app.data.Challenge(
            id = "c1", title = "20% off running shoes", partnerName = "Example partner brand",
            reward = "Everyone who finishes gets a 20% discount code", targetSteps = 50000,
            startsAt = "2026-11-10T00:00:00Z", endsAt = "2026-11-19T18:29:00Z",
        ),
        joined = 1240, myJoinedAt = null, myProgress = 0,
    )
    private val challengesData = com.stepq.app.data.ChallengesData(
        me = friendsData.me,
        weekly = friendsData.board.map { com.stepq.app.data.Ranked(it.person, it.steps * 7, it.isMe) },
        monthly = friendsData.board.map { com.stepq.app.data.Ranked(it.person, it.steps * 12, it.isMe) },
        challenges = listOf(shoes),
    )

    @Test fun a22Weekly() = snap("A22-challenges-weekly") {
        com.stepq.app.ui.challenges.ChallengesScreen(
            com.stepq.app.ui.challenges.ChallengesUiState(loading = false, data = challengesData),
            monthly = false, onPeriod = {}, onRefresh = {}, onOpen = {}, today = LocalDate.of(2026, 11, 12),
        )
    }

    @Test fun a24Detail() = snap("A24-challenge-detail") {
        com.stepq.app.ui.challenges.ChallengeDetailScreen(shoes, joining = false, onJoin = {}, onLeave = {}, onBack = {})
    }

    @Test fun a25Joined() = snap("A25-challenge-joined") {
        com.stepq.app.ui.challenges.ChallengeJoinedScreen(shoes.copy(myJoinedAt = java.time.Instant.now()), onDone = {}, onShare = {})
    }

    private val profileState = com.stepq.app.ui.profile.ProfileUiState(
        loading = false,
        profile = friendsData.me.copy(avatar = AvatarConfig(skin = 2)),
        stats = com.stepq.app.ui.profile.ProfileStats(streak = 12, bestDay = 11830, kmSoFar = 186),
    )

    @Test fun a26Profile() = snap("A26-profile") {
        com.stepq.app.ui.profile.ProfileScreen(profileState, "Phone step counter") {}
    }

    @Test fun a27Pro() = snap("A27-stepq-pro") {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.stepq.app.ui.profile.ProSheet(onWaitlist = false, onNotify = {}, onDismiss = {})
        }
    }

    @Test fun a26Goal() = snap("A26-daily-goal") {
        com.stepq.app.ui.profile.EditGoalScreen(8000, saving = false, onSave = {}, onBack = {})
    }
}
