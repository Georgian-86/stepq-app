package com.stepq.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.stepq.app.data.Supabase
import com.stepq.app.ui.StepqNavHost
import com.stepq.app.ui.theme.StepqTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthLink(intent)
        val app = application as StepqApplication
        setContent {
            StepqTheme { StepqNavHost(app.auth, app.profiles, app.home) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthLink(intent)
    }

    /** stepq://auth-callback from the confirmation email: Supabase signs the user in. */
    private fun handleAuthLink(intent: Intent?) {
        if (intent?.data?.scheme != Supabase.DEEPLINK_SCHEME) return
        Supabase.client?.handleDeeplinks(intent, {}, {
            Log.w("StepqAuth", "Confirmation link failed", it)
        })
    }
}
