package com.stepq.app.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.stepq.app.ui.auth.ForgotPasswordScreen
import com.stepq.app.ui.auth.LoginScreen
import com.stepq.app.ui.auth.SplashScreen
import com.stepq.app.ui.main.MainShell
import com.stepq.app.ui.theme.StepqTheme
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
    @Test fun tabsHome() = snap("tabs-home") { MainShell(null) }
}
