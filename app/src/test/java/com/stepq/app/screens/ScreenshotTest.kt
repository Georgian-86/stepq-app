package com.stepq.app.screens

import androidx.compose.runtime.Composable
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
}
