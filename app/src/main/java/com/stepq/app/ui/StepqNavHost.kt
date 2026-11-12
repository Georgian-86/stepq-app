package com.stepq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.AuthState
import com.stepq.app.data.ProfileRepository
import com.stepq.app.ui.auth.ForgotPasswordScreen
import com.stepq.app.ui.auth.LoginScreen
import com.stepq.app.ui.auth.SplashScreen
import com.stepq.app.ui.main.MainShell
import com.stepq.app.ui.onboarding.AboutYouScreen
import com.stepq.app.ui.onboarding.AllSetScreen
import com.stepq.app.ui.onboarding.AvatarScreen
import com.stepq.app.ui.onboarding.CheckEmailScreen
import com.stepq.app.ui.onboarding.ConnectStepsScreen
import com.stepq.app.ui.onboarding.GoalScreen
import com.stepq.app.ui.onboarding.NameScreen
import com.stepq.app.ui.onboarding.OnboardingViewModel
import com.stepq.app.ui.onboarding.SignUpAccountScreen
import com.stepq.app.ui.theme.StepqColors
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val LOG_IN = "log-in"
    const val FORGOT_PASSWORD = "forgot-password"
    const val SIGN_UP = "sign-up"
    const val CHECK_EMAIL = "check-email/{email}"
    const val NAME = "onboarding/name"
    const val ABOUT = "onboarding/about"
    const val AVATAR = "onboarding/avatar"
    const val GOAL = "onboarding/goal"
    const val CONNECT = "onboarding/connect"
    const val ALL_SET = "onboarding/all-set"
    const val MAIN = "main"

    fun checkEmail(email: String) = "check-email/${android.net.Uri.encode(email)}"
}

/** Where a session lands: signed out, signed in without a profile yet, or ready. */
private enum class Landing { Loading, SignedOut, Onboarding, Main }

@Composable
fun StepqNavHost(
    auth: AuthRepository,
    profiles: ProfileRepository,
    home: com.stepq.app.data.HomeRepository,
    friends: com.stepq.app.data.FriendsRepository? = null,
    groups: com.stepq.app.data.GroupsRepository? = null,
    challenges: com.stepq.app.data.ChallengesRepository? = null,
    nav: NavHostController = rememberNavController(),
) {
    val authState by auth.state.collectAsState(initial = AuthState.Loading)
    var landing by remember { mutableStateOf(Landing.Loading) }
    val onboarding: OnboardingViewModel = viewModel()
    val scope = rememberCoroutineScope()

    // Each time the auth state changes, work out where the user belongs. A signed-in user
    // with no app.profiles row (new account, or confirmed their email and logged in) goes to
    // onboarding; everyone else signed in goes to the tabs.
    LaunchedEffect(authState) {
        landing = when (authState) {
            AuthState.Loading -> Landing.Loading
            AuthState.SignedOut -> Landing.SignedOut
            AuthState.SignedIn -> profiles.mine().fold(
                onSuccess = { if (it == null) Landing.Onboarding else Landing.Main },
                // Offline or the schema isn't live yet: let them in; tabs show their own errors.
                onFailure = { Landing.Main },
            )
        }
    }

    LaunchedEffect(landing) {
        val current = nav.currentDestination?.route ?: return@LaunchedEffect
        val target = when (landing) {
            Landing.Main -> Routes.MAIN
            Landing.Onboarding -> Routes.NAME
            Landing.SignedOut -> if (current == Routes.MAIN || current.startsWith("onboarding/")) Routes.SPLASH else null
            Landing.Loading -> null
        }
        if (target != null && target != current) nav.navigate(target) { popUpTo(0) { inclusive = true } }
    }

    if (landing == Landing.Loading) {
        Box(Modifier.fillMaxSize().background(StepqColors.Leaf), contentAlignment = Alignment.Center) {
            if (authState == AuthState.SignedIn) CircularProgressIndicator(color = StepqColors.White)
        }
        return
    }

    val start = when (landing) {
        Landing.Main -> Routes.MAIN
        Landing.Onboarding -> Routes.NAME
        else -> Routes.SPLASH
    }

    NavHost(nav, startDestination = start) {
        composable(Routes.SPLASH) {
            SplashScreen(onLogIn = { nav.navigate(Routes.LOG_IN) }, onSignUp = { nav.navigate(Routes.SIGN_UP) })
        }
        composable(Routes.LOG_IN) {
            LoginScreen(
                auth = auth,
                onBack = { nav.popBackStack() },
                onForgotPassword = { nav.navigate(Routes.FORGOT_PASSWORD) },
                onSignUp = { nav.navigate(Routes.SIGN_UP) { popUpTo(Routes.SPLASH) } },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(auth = auth, onBack = { nav.popBackStack() })
        }
        composable(Routes.SIGN_UP) {
            SignUpAccountScreen(
                auth = auth,
                onBack = { nav.popBackStack() },
                onLogIn = { nav.navigate(Routes.LOG_IN) { popUpTo(Routes.SPLASH) } },
                onNeedsConfirmation = { nav.navigate(Routes.checkEmail(it)) { popUpTo(Routes.SPLASH) } },
            )
        }
        composable(Routes.CHECK_EMAIL) { entry ->
            CheckEmailScreen(
                email = entry.arguments?.getString("email").orEmpty(),
                onLogIn = { nav.navigate(Routes.LOG_IN) { popUpTo(Routes.SPLASH) } },
            )
        }
        // A06–A09 back out to the previous step; from A06, back signs out of the new account.
        composable(Routes.NAME) {
            NameScreen(onboarding, profiles, onBack = { scope.launch { auth.logOut() } }) {
                nav.navigate(Routes.ABOUT)
            }
        }
        composable(Routes.ABOUT) {
            AboutYouScreen(onboarding, onBack = { nav.popBackStack() }) { nav.navigate(Routes.AVATAR) }
        }
        composable(Routes.AVATAR) {
            AvatarScreen(onboarding, onBack = { nav.popBackStack() }) { nav.navigate(Routes.GOAL) }
        }
        composable(Routes.GOAL) {
            GoalScreen(onboarding, profiles, onBack = { nav.popBackStack() }) {
                // The profile exists now, so there's no going back into A06–A09.
                nav.navigate(Routes.CONNECT) { popUpTo(0) { inclusive = true } }
            }
        }
        composable(Routes.CONNECT) {
            ConnectStepsScreen(onBack = null) { nav.navigate(Routes.ALL_SET) { popUpTo(0) { inclusive = true } } }
        }
        composable(Routes.ALL_SET) {
            AllSetScreen(onboarding.firstName, onboarding.dailyGoal) {
                nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
            }
        }
        composable(Routes.MAIN) { MainShell(auth, home, friends, groups, challenges, profiles) }
    }
}
