package com.stepq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.stepq.app.ui.StepqNavHost
import com.stepq.app.ui.theme.StepqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = (application as StepqApplication).auth
        setContent {
            StepqTheme { StepqNavHost(auth) }
        }
    }
}
