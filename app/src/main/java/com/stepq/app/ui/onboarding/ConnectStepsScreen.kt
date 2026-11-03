package com.stepq.app.ui.onboarding

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.stepq.app.health.HealthConnect
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

private val dataTypes = listOf("Steps", "Distance", "Active minutes", "Calories burned")

/** A10 Let Piko count your steps: asks Health Connect for read access. */
@Composable
fun ConnectStepsScreen(onBack: (() -> Unit)?, onDone: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthConnect.permissions)) {
            onDone()
        } else {
            note = "Piko can't count steps without access. You can connect later in Profile → Connected apps."
        }
    }

    fun connect() {
        when (HealthConnect.availability(context)) {
            HealthConnect.Availability.Available -> runCatching { launcher.launch(HealthConnect.permissions) }
                .onFailure { note = "Health Connect didn't open. Open the Health Connect app once, then try again." }
            HealthConnect.Availability.NeedsInstallOrUpdate -> {
                // Before Android 14, Health Connect is a separate free app from Google.
                note = "Your phone needs Google's free Health Connect app to share steps. Install or update it, then come back and tap Connect."
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri())
                            .setPackage("com.android.vending")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            HealthConnect.Availability.Unsupported ->
                note = "This phone doesn't support Health Connect yet, so steps can't be read here."
        }
    }

    OnboardingFrame(
        step = 6,
        title = "Let Piko count your steps",
        subtitle = "Your phone already counts your steps. StepQ reads them from Health Connect on Android and Apple Health on iPhone.",
        onBack = onBack,
        centered = true,
        header = {
            Box(Modifier.size(72.dp).clip(CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
        },
        bottom = {
            StepqButton("Connect", ::connect)
            StepqTextLink("Not now", onDone)
        },
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint).padding(horizontal = 16.dp)) {
            dataTypes.forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = StepqType.Body, color = StepqColors.Ink, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
                }
                if (i < dataTypes.lastIndex) HorizontalDivider(color = StepqColors.Line)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(16.dp))
            Text(
                "We only read activity data. Your health data is never sold.",
                style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.padding(start = 8.dp),
            )
        }
        note?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}
