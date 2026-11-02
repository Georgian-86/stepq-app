package com.stepq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.AuthState
import com.stepq.app.ui.auth.ForgotPasswordScreen
import com.stepq.app.ui.auth.LoginScreen
import com.stepq.app.ui.auth.SplashScreen
import com.stepq.app.ui.main.MainShell
import com.stepq.app.ui.theme.StepqColors

object Routes {
    const val SPLASH = "splash"
    const val LOG_IN = "log-in"
    const val FORGOT_PASSWORD = "forgot-password"
    const val SIGN_UP = "sign-up"
    const val MAIN = "main"
}

@Composable
fun StepqNavHost(auth: AuthRepository, nav: NavHostController = rememberNavController()) {
    val state by auth.state.collectAsState(initial = AuthState.Loading)

    // Signed-in users go straight to the tabs; signing out returns to the splash.
    LaunchedEffect(state) {
        when (state) {
            AuthState.SignedIn -> nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
            AuthState.SignedOut -> if (nav.currentDestination?.route == Routes.MAIN) {
                nav.navigate(Routes.SPLASH) { popUpTo(0) { inclusive = true } }
            }
            AuthState.Loading -> Unit
        }
    }

    if (state == AuthState.Loading) {
        Box(Modifier.fillMaxSize().background(StepqColors.Leaf))
        return
    }

    NavHost(nav, startDestination = if (state == AuthState.SignedIn) Routes.MAIN else Routes.SPLASH) {
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
            // A05–A11 sign-up and onboarding come in the next PR.
            SplashScreen(onLogIn = { nav.navigate(Routes.LOG_IN) }, onSignUp = {})
        }
        composable(Routes.MAIN) { MainShell(auth) }
    }
}
