package com.stepq.app.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
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

    /** Pro state is app-wide; start every screen on no plan and the Leaf theme. */
    @org.junit.Before fun resetPro() {
        com.stepq.app.data.Pro.preview(null)
        com.stepq.app.ui.theme.StepqColors.apply("leaf")
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            StepqTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.stepq.app.ui.components.LocalMotionEnabled provides false, content)
            }
        }
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
    @Test fun a26ConnectedApps() = snap("A26-connected-apps") { ConnectStepsScreen(onBack = {}, fromSettings = true) {} }
    private val me = com.stepq.app.data.Profile(id = "me", displayName = "Riya Sharma", username = "riya.walks", gender = "female", birthdate = "2001-03-14", avatar = AvatarConfig(skin = 2, hair = 1, outfit = 2))
    @Test fun a26EditProfile() = snap("A26-edit-profile") {
        com.stepq.app.ui.profile.EditProfileScreen(me, false, null, { true }, { _, _, _, _ -> }, {})
    }
    @Test fun a26Account() = snap("A26-account") {
        com.stepq.app.ui.profile.AccountScreen("riya@example.com", null, true, {}, {}, {}, {})
    }
    @Test fun a26AccountGoogle() = snap("A26-account-google") {
        com.stepq.app.ui.profile.AccountScreen("riya@gmail.com", null, false, {}, {}, {}, {})
    }
    @Test fun a26ChangePassword() = snap("A26-change-password") {
        com.stepq.app.ui.profile.NewPasswordScreen("Change password", "You'll stay logged in on this phone.", false, null, {}, {})
    }
    @Test fun a03SetNewPassword() = snap("A03-set-new-password") {
        com.stepq.app.ui.profile.NewPasswordScreen("Set a new password", "Choose a new password for riya@example.com.", false, null, {}, null)
    }
    @Test fun a26ChangeEmail() = snap("A26-change-email") {
        com.stepq.app.ui.profile.ChangeEmailScreen("riya@example.com", false, null, null, {}, {})
    }
    @Test fun loadingSkeletons() = snap("Z-loading-friends") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp)) { com.stepq.app.ui.components.SkeletonList() }
    }
    @Test fun lottieFrames() = snap("Z-lottie-frames") {
        androidx.compose.foundation.layout.Row {
            listOf(com.stepq.app.R.raw.lottie_confetti to 0.3f, com.stepq.app.R.raw.lottie_confetti to 0.6f, com.stepq.app.R.raw.lottie_success to 0.7f).forEach { (res, p) ->
                val c = com.airbnb.lottie.compose.rememberLottieComposition(com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(res)).value
                com.airbnb.lottie.compose.LottieAnimation(c, { p }, androidx.compose.ui.Modifier.size(130.dp))
            }
        }
    }
    @Test fun a11AllSet() = snap("A11-all-set") { AllSetScreen("Riya", 8000) {} }
    private fun home(steps: Int, mood: PikoMood, greeting: String = "Good morning") = HomeUiState(
        loading = false, dateLabel = "Saturday, 17 Oct", greeting = greeting, firstName = "Riya", streaks = mapOf("a" to 12, "m" to 4),
        steps = steps, goal = 8000, km = steps * 0.000762, kcal = steps * 38 / 1000, activeMinutes = steps / 120,
        week = listOf(6100, 7800, 4300, 8600, 7100, 5600, steps), weekAverage = 7420,
        friends = listOf(
            FriendRow(1, "Arjun", AvatarConfig(skin = 3, outfit = 3), 9120, false, streak = 12),
            FriendRow(2, "Meera", AvatarConfig(skin = 1, outfit = 2, headband = 2), 7480, false, streak = 4),
            FriendRow(3, "You", AvatarConfig(), steps, true),
        ).sortedByDescending { it.steps }.mapIndexed { i, r -> r.copy(rank = i + 1) },
        hasFriends = true, mood = mood, score = 42, sharedToday = steps >= 8000,
        shareTo = listOf(
            com.stepq.app.data.PersonSummary("a", "Arjun Mehta", "arjun.m", AvatarConfig(skin = 3, outfit = 3)),
            com.stepq.app.data.PersonSummary("m", "Meera Iyer", "meera", AvatarConfig(skin = 1, outfit = 2, style = 1)),
        ),
    )

    private val streakFriends = listOf(
        com.stepq.app.data.PersonSummary("a", "Arjun Mehta", "arjun.m", AvatarConfig(skin = 3, outfit = 3)),
        com.stepq.app.data.PersonSummary("m", "Meera Iyer", "meera", AvatarConfig(skin = 1, outfit = 2, style = 1)),
        com.stepq.app.data.PersonSummary("k", "Kabir Singh", "kabir", AvatarConfig(skin = 2, outfit = 1)),
        com.stepq.app.data.PersonSummary("z", "Zoya Khan", "zoya", AvatarConfig(skin = 4, outfit = 4, headband = 1)),
        com.stepq.app.data.PersonSummary("r", "Rohan Das", "rohan", AvatarConfig(skin = 1, outfit = 3)),
    )
    @Test fun a12dStreaks() = snap("A12d-streaks") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.stepq.app.ui.theme.StepqColors.White)) {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Streaks, {}, {})
            com.stepq.app.ui.home.StreaksScreen(
                home(6240, PikoMood.Nudge).copy(
                    shareTo = streakFriends,
                    streaks = mapOf("a" to 12, "m" to 4, "z" to 104, "k" to 1),
                    sharedTodayWith = setOf("a", "k"),
                ),
                onBack = {}, onShare = {}, showHeader = false,
            )
        }
    }

    @Test fun a12Home() = snap("A12-home") { HomeScreen(home(6240, PikoMood.Nudge), {}, {}, {}) }
    @Test fun a12Loading() = snap("A12-home-loading") { HomeScreen(home(0, PikoMood.Nudge).copy(loading = true), {}, {}, {}) }
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

    private fun suggestion(id: String, name: String, user: String, skin: Int, style: Int, mutual: Long = 0) =
        com.stepq.app.data.Suggestion(id, name, user, AvatarConfig(skin = skin, hair = 1, outfit = 1, style = style), mutual)

    @Test fun a13AddFriend() = snap("A13-add-friend") {
        com.stepq.app.ui.friends.AddFriendScreen(
            myUsername = "riya.walks",
            state = com.stepq.app.ui.friends.AddFriendState(
                query = "ar",
                results = listOf(
                    suggestion("a", "Arjun Mehta", "arjun.m", 4, 0, mutual = 3),
                    suggestion("b", "Aarav Shah", "aarav.walks", 2, 0),
                    suggestion("c", "Sara Arora", "sara.runs", 1, 1, mutual = 1),
                ),
            ),
            relationOf = { if (it == "b") com.stepq.app.ui.friends.Relation.Requested else com.stepq.app.ui.friends.Relation.None },
            onType = {}, onFindContacts = {}, onRequest = {}, onAccept = {}, onInvite = {}, onBack = {},
        )
    }

    @Test fun a13AddFriendSuggestions() = snap("A13-add-friend-suggestions") {
        com.stepq.app.ui.friends.AddFriendScreen(
            myUsername = "riya.walks",
            state = com.stepq.app.ui.friends.AddFriendState(
                mayKnow = listOf(
                    suggestion("d", "Kabir Singh", "kabir.s", 3, 0, mutual = 4),
                    suggestion("e", "Ananya Rao", "ananya", 0, 2, mutual = 2),
                ),
            ),
            relationOf = { com.stepq.app.ui.friends.Relation.None },
            onType = {}, onFindContacts = {}, onRequest = {}, onAccept = {}, onInvite = {}, onBack = {},
        )
    }

    private fun group(id: String, name: String, take: Int, extra: Int) = com.stepq.app.data.GroupToday(
        com.stepq.app.data.GroupRow(id, name, "me", "3f9a1c07b2de"),
        friendsData.board.take(take).mapIndexed { i, m -> m.copy(steps = m.steps + extra, score = 64 - i * 9) },
    )
    private val groupsData = com.stepq.app.data.GroupsData(
        me = friendsData.me,
        groups = listOf(group("g1", "Morning walkers", 5, 0), group("g2", "Office step-up", 6, 2000), group("g3", "Family", 4, -1000)),
        friends = friendsData.board.filterNot { it.isMe }.map { it.person },
        friendIds = friendsData.board.filterNot { it.isMe }.map { it.person.id }.toSet(),
    )

    private fun ago(minutes: Long) = java.time.Instant.now().minusSeconds(minutes * 60).toString()
    private val posts = listOf(
        com.stepq.app.data.Post(
            id = "p1", authorId = "b", authorName = "Meera", authorUsername = "meera_walks",
            authorAvatar = AvatarConfig(skin = 1, hair = 2, outfit = 2, style = 1), kind = "achievement",
            body = "Took the long way home and the sunset was worth every step 🌇", day = LocalDate.now().toString(),
            steps = 11_240, goal = 10_000, createdAt = ago(12), likeCount = 24, replyCount = 5, repostCount = 2, liked = true,
        ),
        com.stepq.app.data.Post(
            id = "p2", authorId = "c", authorName = "Kabir", authorUsername = "kabir.k",
            authorAvatar = AvatarConfig(skin = 3, hair = 1, outfit = 3), body = "Who's up for a 6 AM walk at Cubbon Park tomorrow? First round of chai is on me ☕",
            createdAt = ago(95), likeCount = 8, replyCount = 3, repostedBy = "Sana", repostCount = 1,
        ),
        com.stepq.app.data.Post(
            id = "p3", authorId = "me", authorName = "Riya Sharma", authorUsername = "riya.walks",
            authorAvatar = AvatarConfig(skin = 2, hair = 1, outfit = 2), kind = "achievement",
            body = "Halfway there before lunch!", day = LocalDate.now().toString(), steps = 6_240, goal = 8_000,
            createdAt = ago(60 * 26), likeCount = 3, reposted = true, repostCount = 1,
        ),
    )
    private val communityState = com.stepq.app.ui.community.CommunityUiState(
        me = me,
        feeds = mapOf(com.stepq.app.data.FeedScope.Everyone to com.stepq.app.ui.community.FeedState(posts = posts, loading = false, loaded = true)),
    )
    private val noActions = com.stepq.app.ui.community.PostActions({}, {}, {}, {}, {}, {})

    @Test fun a15Community() = snap("A15-community") {
        androidx.compose.foundation.layout.Column(Modifier.background(com.stepq.app.ui.theme.StepqColors.White)) {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Community, {}, {})
            com.stepq.app.ui.community.CommunityFeed(communityState, noActions, {}, {}, {}, {})
        }
    }

    @Test fun a15CommunityThread() = snap("A15-community-thread") {
        val main = posts[0].copy(depth = 0)
        val replies = listOf(
            com.stepq.app.data.Post(id = "r1", authorId = "me", authorName = "Riya Sharma", authorUsername = "riya.walks", authorAvatar = AvatarConfig(skin = 2, hair = 1, outfit = 2), body = "That's a huge day, well done! 👏", createdAt = ago(8), likeCount = 2, depth = 1, parentId = "p1", parentUsername = "meera_walks"),
            com.stepq.app.data.Post(id = "r2", authorId = "c", authorName = "Kabir", authorUsername = "kabir.k", authorAvatar = AvatarConfig(skin = 3, hair = 1, outfit = 3), body = "Which route? I need a new one.", createdAt = ago(5), replyCount = 1, depth = 1, parentId = "p1", parentUsername = "meera_walks"),
        )
        com.stepq.app.ui.community.ThreadScreen(
            com.stepq.app.ui.community.ThreadState("p1", listOf(main) + replies, loading = false),
            me, noActions, posting = false, error = null, onReply = { _, _ -> }, onTyping = {}, onBack = {},
        )
    }

    @Test fun a15CommunityCompose() = snap("A15-community-compose") {
        com.stepq.app.ui.community.ComposeScreen(me, stepsToday = 8_420, startWithSteps = true, posting = false, error = null, onPost = { _, _ -> }, onTyping = {}, onCancel = {})
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
        weekly = com.stepq.app.data.ChallengesRepository.withRanks(friendsData.board.map { com.stepq.app.data.Ranked(it.person, it.steps * 7, it.isMe) }),
        monthly = com.stepq.app.data.ChallengesRepository.withRanks(friendsData.board.map { com.stepq.app.data.Ranked(it.person, it.steps * 12, it.isMe) }),
        country = "IN",
        weeklyCount = 1240,
        monthlyCount = 2315,
        challenges = listOf(shoes),
    )

    @Test fun a22Weekly() = snap("A22-challenges-weekly") {
        com.stepq.app.ui.challenges.ChallengesScreen(
            com.stepq.app.ui.challenges.ChallengesUiState(loading = false, data = challengesData),
            monthly = false, onPeriod = {}, onRefresh = {}, onOpen = {}, today = LocalDate.of(2026, 11, 12),
        )
    }

    @Test fun a23YourRank() = snap("A23-challenges-your-rank") {
        val board = com.stepq.app.data.ChallengesRepository.withRanks(
            friendsData.board.map { com.stepq.app.data.Ranked(it.person, if (it.isMe) 21000 else it.steps * 12, it.isMe) },
        )
        com.stepq.app.ui.challenges.ChallengesScreen(
            com.stepq.app.ui.challenges.ChallengesUiState(loading = false, data = challengesData.copy(monthly = board)),
            monthly = true, onPeriod = {}, onRefresh = {}, onOpen = {}, today = LocalDate.of(2026, 11, 12),
        )
    }

    @Test fun a24Detail() = snap("A24-challenge-detail") {
        com.stepq.app.ui.challenges.ChallengeDetailScreen(shoes, joining = false, onJoin = {}, onLeave = {}, onBack = {})
    }

    @Test fun a25Joined() = snap("A25-challenge-joined") {
        com.stepq.app.ui.challenges.ChallengeJoinedScreen(shoes.copy(myJoinedAt = java.time.Instant.now()), onDone = {}, onShare = {})
    }

    private val qrunNow = java.time.Instant.parse("2026-10-17T04:00:00Z")
    private val ist = java.time.ZoneId.of("Asia/Kolkata")
    private val morningRun = com.stepq.app.data.QRun(
        id = "q1", title = "Morning run, easy pace", placeName = "Carter Road Promenade, Bandra West", lat = 19.0700, lng = 72.8220,
        startsAtRaw = "2026-10-18T01:00:00Z", createdAtRaw = "2026-10-17T02:00:00Z", createdBy = "arjun", hostName = "Arjun Mehta",
        hostAvatar = AvatarConfig(skin = 4, hair = 1), going = 8,
    )
    private val sunsetWalk = com.stepq.app.data.QRun(
        id = "q2", title = "Sunset walk by the sea", placeName = "Juhu Beach, near Gate 5", lat = 19.0988, lng = 72.8267,
        startsAtRaw = "2026-10-22T12:30:00Z", createdAtRaw = "2026-10-16T05:00:00Z", createdBy = "sana", hostName = "Sana Khan",
        hostAvatar = AvatarConfig(skin = 1, hair = 2, headband = 1, style = 1), going = 3, priceInr = 499,
    )
    private val qrunState = com.stepq.app.ui.qrun.QRunUiState(
        loading = false,
        data = com.stepq.app.data.QRunsData("me", listOf(morningRun, sunsetWalk)),
        here = com.stepq.app.ui.qrun.LatLng(19.0596, 72.8295),
        areaName = "Bandra West, Mumbai",
    )

    @Test fun a18QRuns() = snap("A18-qruns") {
        androidx.compose.foundation.layout.Column {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.QRun, {}, {})
            com.stepq.app.ui.qrun.QRunList(qrunState, locationAllowed = true, onRefresh = {}, onAllowLocation = {}, onJoin = {}, onOpenPlanner = {}, onPost = {}, now = qrunNow, zone = ist)
        }
    }

    @Test fun a19PostQRun() = snap("A19-post-qrun") {
        com.stepq.app.ui.qrun.PostQRunScreen(
            places = com.stepq.app.ui.qrun.NoPlaceSearch, here = null, busy = false, error = null,
            onPost = { _, _, _, _, _ -> }, onTyping = {}, onPickCustomTime = {}, onClose = {},
            today = LocalDate.of(2026, 10, 17),
            initialPlace = "Carter Road",
            initialSuggestions = listOf(
                com.stepq.app.data.Place("Carter Road Promenade, Bandra West"),
                com.stepq.app.data.Place("Carter Road Amphitheatre, Bandra"),
            ),
        )
    }

    @Test fun a20BeforeYouJoin() = snap("A20-before-you-join") {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.stepq.app.ui.qrun.JoinQRunSheet(morningRun, busy = false, onJoin = {}, onCancel = {}, zone = ist)
        }
    }

    @Test fun a21Planner() = snap("A21-event-planner") {
        com.stepq.app.ui.qrun.QRunPlanner(
            qrunState.copy(
                data = com.stepq.app.data.QRunsData("me", listOf(morningRun.copy(joined = true, going = 9), sunsetWalk.copy(createdBy = "me", hostName = "Riya Sharma"))),
                justJoined = "q1",
            ),
            notificationsOn = true, onAddToCalendar = {}, onDirections = {}, onLeave = {}, onCancelRun = {}, onBack = {},
            now = qrunNow, zone = ist,
        )
    }

    private val profileState = com.stepq.app.ui.profile.ProfileUiState(
        loading = false,
        profile = friendsData.me.copy(avatar = AvatarConfig(skin = 2)),
        stats = com.stepq.app.ui.profile.ProfileStats(bestDay = 11830, kmSoFar = 186, score = 48),
    )

    @Test fun a26Profile() = snap("A26-profile") {
        com.stepq.app.ui.profile.ProfileScreen(profileState, "Phone step counter") {}
    }

    /** Frames of Profile -> Account -> back, with motion on, to check the page transitions. */
    @Test fun pageTransitionFrames() {
        var page by androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StepqTheme {
                com.stepq.app.ui.components.PageHost(page, { if (it == null) 0 else 1 }, onBack = { page = null }) { shown ->
                    if (shown == null) {
                        com.stepq.app.ui.profile.ProfileScreen(profileState, "Phone step counter") {}
                    } else {
                        com.stepq.app.ui.profile.AccountScreen("riya@example.com", null, true, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        var n = 0
        fun frame() = compose.onRoot().captureRoboImage("build/outputs/roborazzi/frames/f${(n++).toString().padStart(2, '0')}.png")
        frame()
        page = "account"
        repeat(10) { compose.mainClock.advanceTimeBy(48); frame() }
        compose.mainClock.advanceTimeBy(400); frame()
        page = null
        repeat(10) { compose.mainClock.advanceTimeBy(48); frame() }
        compose.mainClock.advanceTimeBy(400); frame()
    }

    // ------------------------------------------------------------------ StepQ Pro (Figma "4 · StepQ Pro")
    private val freePlan = com.stepq.app.data.MyPlan.FREE_DEFAULTS.copy(
        usage = com.stepq.app.data.PlanUsage(communityPosts = 2, streakRestores = 1, waterReminders = 1),
        monthResetsAt = "2026-12-01",
    )
    private val proPlan = com.stepq.app.data.MyPlan.FREE_DEFAULTS.let { f ->
        f.copy(
            plan = "pro", isPro = true, proSince = "2026-11-01T00:00:00Z", qrunHost = false, theme = "leaf", bffId = "z",
            features = f.features.map { it.copy(mine = it.pro) },
            usage = com.stepq.app.data.PlanUsage(communityPosts = 9, streakRestores = 3, waterReminders = 6),
            monthResetsAt = "2026-12-01",
        )
    }
    private fun withPlan(plan: com.stepq.app.data.MyPlan?, vararg pro: String, content: @Composable () -> Unit): @Composable () -> Unit {
        com.stepq.app.data.Pro.preview(plan, pro.associateWith { com.stepq.app.data.PlanBadge(it, pro = true) })
        return content
    }

    @Test fun p1ProProfile() = snap("P1-profile-pro-member", withPlan(proPlan, "me") {
        com.stepq.app.ui.profile.ProfileScreen(profileState, "Phone step counter", plan = proPlan) {}
    })

    @Test fun p1FreeProfile() = snap("P1b-profile-free", withPlan(freePlan) {
        com.stepq.app.ui.profile.ProfileScreen(profileState, "Phone step counter", plan = freePlan) {}
    })

    @Test fun p2ProMembership() = snap("P2-your-pro-membership", withPlan(proPlan, "me") {
        com.stepq.app.ui.pro.ProScreen(proPlan, profileState.profile, onWaitlist = false, onJoinWaitlist = {}, onInsights = {}, onBack = {}, onMessage = {})
    })

    @Test fun p3FreeVsPro() = snap("P3-free-vs-pro", withPlan(freePlan) {
        com.stepq.app.ui.pro.ProScreen(freePlan, profileState.profile, onWaitlist = false, onJoinWaitlist = {}, onInsights = {}, onBack = {}, onMessage = {})
    })

    @Test fun p4PostLimit() = snap("P4-community-post-limit", withPlan(freePlan.copy(usage = com.stepq.app.data.PlanUsage(communityPosts = 3))) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.stepq.app.ui.theme.StepqColors.White), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.stepq.app.ui.components.PlanLimitSheet(
                com.stepq.app.data.MyPlan.F.COMMUNITY_POSTS,
                freePlan.copy(usage = com.stepq.app.data.PlanUsage(communityPosts = 3)),
                onSeePro = {}, onDismiss = {},
            )
        }
    })

    private fun streaksWith(plan: com.stepq.app.data.MyPlan): @Composable () -> Unit = {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.stepq.app.ui.theme.StepqColors.White)) {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Streaks, {}, {})
            com.stepq.app.ui.home.StreaksScreen(
                home(6240, PikoMood.Nudge).copy(
                    shareTo = streakFriends,
                    streaks = mapOf("a" to 12, "m" to 4, "z" to 104, "k" to 1),
                    sharedTodayWith = setOf("a", "k"),
                ),
                onBack = {}, onShare = {}, showHeader = false,
                plan = plan,
                lost = listOf(com.stepq.app.data.LostStreak("r", 23)),
            )
        }
    }

    @Test fun p5ProStreaks() = snap("P5-streaks-bff-restore", withPlan(proPlan, "z", "a", content = streaksWith(proPlan)))
    @Test fun p5FreeStreaks() = snap("P5b-streaks-free", withPlan(freePlan, "a", content = streaksWith(freePlan)))

    private fun fakeHistory(days: Int): List<com.stepq.app.data.HistoryDay> {
        val today = LocalDate.now()
        return (0 until days).map { i ->
            val d = today.minusDays(i.toLong())
            val steps = 4200 + ((i * 7919) % 7000) + if (d.dayOfWeek == java.time.DayOfWeek.SATURDAY) 2500 else 0
            com.stepq.app.data.HistoryDay(d.toString(), steps, 8000, (steps * 0.76).toInt())
        }
    }

    @Test fun p6Insights() = snap("P6-insights", withPlan(proPlan) {
        com.stepq.app.ui.pro.InsightsScreen(proPlan, 8000, onBack = {}, onLocked = {}, load = { fakeHistory(it) })
    })

    @Test fun p6InsightsFree() = snap("P6b-insights-free", withPlan(freePlan) {
        com.stepq.app.ui.pro.InsightsScreen(freePlan, 8000, onBack = {}, onLocked = {}, load = { fakeHistory(it) })
    })

    @Test fun p7GoldTheme() {
        com.stepq.app.ui.theme.StepqColors.apply("gold")
        try {
            snap("P7-home-gold-theme") { HomeScreen(home(6240, PikoMood.Nudge), {}, {}, {}) }
        } finally {
            com.stepq.app.ui.theme.StepqColors.apply("leaf")
        }
    }

    @Test fun p8MonthlyFree() = snap("P8-challenges-monthly-free", withPlan(freePlan, "a") {
        com.stepq.app.ui.challenges.ChallengesScreen(
            com.stepq.app.ui.challenges.ChallengesUiState(
                loading = false,
                data = challengesData.copy(challenges = challengesData.challenges.map { it.copy(challenge = it.challenge.copy(proOnly = true)) }),
            ),
            monthly = true, onPeriod = {}, onRefresh = {}, onOpen = {}, today = LocalDate.of(2026, 11, 12),
        )
    })

    @Test fun p9CommunityBadges() = snap("P9-community-pro-badges", withPlan(freePlan.copy(usage = com.stepq.app.data.PlanUsage(communityPosts = 1)), "b") {
        androidx.compose.foundation.layout.Column(Modifier.background(com.stepq.app.ui.theme.StepqColors.White)) {
            com.stepq.app.ui.friends.FriendsHeader(com.stepq.app.ui.friends.FriendsSection.Community, {}, {})
            com.stepq.app.ui.community.CommunityFeed(communityState, noActions, {}, {}, {}, {})
        }
    })

    @Test fun a26Goal() = snap("A26-daily-goal") {
        com.stepq.app.ui.profile.EditGoalScreen(8000, saving = false, onSave = {}, onBack = {})
    }

    @Test fun a26GoalTomorrow() = snap("A26-goal-from-tomorrow") {
        com.stepq.app.ui.profile.EditGoalScreen(8000, saving = false, onSave = {}, onBack = {}, pending = 10000)
    }

    // Frame-by-frame recordings of the new animations, turned into GIFs for review.
    private fun recordFrames(dir: String, content: @Composable () -> Unit): (Long) -> Unit {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StepqTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.stepq.app.ui.components.LocalMotionEnabled provides true, content)
            }
        }
        var n = 0
        return { step ->
            compose.mainClock.advanceTimeBy(step)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/anim/$dir/f${(n++).toString().padStart(3, '0')}.png")
        }
    }

    @Test fun animSplash() {
        val frame = recordFrames("splash") { SplashScreen({}, {}) }
        repeat(96) { frame(40) }
    }

    /** Opening the app signed out: the launch screen, then Log in and Sign up slide up. */
    @Test fun animLaunch() {
        var ready by androidx.compose.runtime.mutableStateOf(false)
        val frame = recordFrames("launch") {
            if (ready) SplashScreen({}, {}, intro = false) else SplashScreen({}, {}, showButtons = false)
        }
        repeat(45) { frame(40) }
        ready = true
        repeat(30) { frame(40) }
    }

    @Test fun loaderList() {
        compose.setContent { com.stepq.app.ui.theme.StepqTheme { com.stepq.app.ui.components.SkeletonList() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/Z-loader-list.png")
    }

    @Test fun animLogin() {
        var loading by androidx.compose.runtime.mutableStateOf(false)
        var reveal by androidx.compose.runtime.mutableStateOf(false)
        var home by androidx.compose.runtime.mutableStateOf(false)
        val frame = recordFrames("login") {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                if (home) {
                    HomeScreen(home(6240, PikoMood.Nudge), {}, {}, {})
                } else {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxSize().background(com.stepq.app.ui.theme.StepqColors.White).padding(24.dp),
                    ) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(80.dp))
                        androidx.compose.material3.Text("Welcome back", style = com.stepq.app.ui.theme.StepqType.Title, color = com.stepq.app.ui.theme.StepqColors.Ink)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
                        com.stepq.app.ui.components.UnderlineField(label = "Email", value = "riya@example.com", onValueChange = {})
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        com.stepq.app.ui.components.UnderlineField(label = "Password", value = "walks2026", onValueChange = {}, password = true)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        com.stepq.app.ui.components.StepqButton("Log in", {}, loading = loading)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(40.dp))
                    }
                }
                if (reveal) com.stepq.app.ui.components.SuccessReveal { reveal = false }
            }
        }
        repeat(6) { frame(80) }
        loading = true
        repeat(12) { frame(50) }
        repeat(6) { frame(100) }
        reveal = true
        repeat(10) { frame(50) }
        home = true
        repeat(26) { frame(50) }
        repeat(6) { frame(150) }
    }

    @Test fun animHomeWeekTap() {
        val frame = recordFrames("home") { HomeScreen(home(6240, PikoMood.Nudge), {}, {}, {}) }
        repeat(24) { frame(50) }
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Tuesday", substring = true)).performClick()
        repeat(8) { frame(50) }
        repeat(8) { frame(120) }
    }
}
