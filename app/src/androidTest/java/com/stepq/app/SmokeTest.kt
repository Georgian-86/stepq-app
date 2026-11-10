package com.stepq.app

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real emulator in CI: the app opens without crashing, and with a test account
 * (instrumentation arguments `email` and `password`) it logs in and reaches Home or
 * onboarding. A crash anywhere on the way fails the test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val args = InstrumentationRegistry.getArguments()

    @Test fun opensAndLogsIn() {
        // Signed out shows the splash; a saved session goes straight to Home.
        rule.waitUntilAtLeastOneExists(hasText("Log in") or hasText("This week"), 30_000)

        val email = args.getString("email").orEmpty()
        val password = args.getString("password").orEmpty()
        if (email.isEmpty() || password.isEmpty()) {
            Log.i(TAG, "No test account given: checked the start screen only")
            return
        }
        if (rule.onAllNodesWithText("This week").fetchSemanticsNodes().isNotEmpty()) {
            Log.i(TAG, "Already signed in: reached Home")
            return
        }

        rule.onAllNodesWithText("Log in").onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasSetTextAction(), 10_000)
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(email)
        fields[1].performTextInput(password)
        rule.onAllNodesWithText("Log in").onFirst().performClick()

        // Home for a finished profile, the name step for a new one.
        rule.waitUntilAtLeastOneExists(
            hasText("This week") or hasText("What should Piko call you?"),
            45_000,
        )
        // Let Home load, sync and draw for a while: a crash here fails the run.
        Thread.sleep(10_000)
        rule.waitUntilAtLeastOneExists(hasText("This week") or hasText("What should Piko call you?"), 5_000)
        val home = rule.onAllNodesWithText("This week").fetchSemanticsNodes().isNotEmpty()
        Log.i(TAG, if (home) "Logged in: reached Home" else "Logged in: reached onboarding (name step)")
    }

    private companion object {
        const val TAG = "StepqSmoke"
    }
}
