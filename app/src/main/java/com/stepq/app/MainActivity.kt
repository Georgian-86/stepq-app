package com.stepq.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stepq.app.data.Supabase
import com.stepq.app.debug.CrashLog
import com.stepq.app.debug.CrashReportScreen
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
        val lastCrash = CrashLog.read(this)
        setContent {
            var crash by remember { mutableStateOf(lastCrash) }
            StepqTheme {
                val report = crash
                if (report != null) {
                    CrashReportScreen(report) {
                        CrashLog.clear(this)
                        crash = null
                    }
                } else {
                    StepqNavHost(app.auth, app.profiles, app.home, app.friends, app.groups, app.challenges)
                }
            }
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
