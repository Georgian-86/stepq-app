package com.stepq.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
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
import com.stepq.app.health.PhoneStepCounter
import com.stepq.app.ui.components.OnboardingFrame
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqTextLink
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

private val dataTypes = listOf("Steps", "Distance", "Calories burned")

/**
 * A10 Let Piko count your steps. Uses the phone's own step counter (only the "Physical
 * activity" permission, no extra app). Health Connect stays available as an option for people
 * who also want watch and other app data.
 */
@Composable
fun ConnectStepsScreen(onBack: (() -> Unit)?, fromSettings: Boolean = false, onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = remember { PhoneStepCounter(context) }
    var note by remember { mutableStateOf<String?>(null) }
    val denied = "Piko can't count steps without access. You can turn it on later in Settings → Apps → StepQ → Permissions."

    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(HealthConnect.permissions)) onDone() else note = denied
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onDone() else note = denied
    }

    fun connectHealth() {
        when (HealthConnect.availability(context)) {
            HealthConnect.Availability.Available -> runCatching { healthLauncher.launch(HealthConnect.permissions) }
                .onFailure { note = "Health Connect didn't open. Open the Health Connect app once, then try again." }
            HealthConnect.Availability.NeedsInstallOrUpdate -> {
                // Before Android 14, Health Connect is a separate free app from Google.
                note = "Health Connect is a separate free app from Google on this phone. Install it, then come back and try again."
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri())
                            .setPackage("com.android.vending")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            HealthConnect.Availability.Unsupported ->
                note = "This phone doesn't support Health Connect, so steps can't be read here."
        }
    }

    fun connect() {
        when {
            !phone.isAvailable -> connectHealth()
            phone.hasPermission -> onDone()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else -> onDone()
        }
    }

    OnboardingFrame(
        // From Profile → Connected apps it's a settings page, not a sign-up step.
        step = if (fromSettings) null else 6,
        title = "Let Piko count your steps",
        subtitle = "Your phone already counts your steps. Piko reads that count, so there's nothing else to install.",
        onBack = onBack,
        centered = true,
        header = {
            Box(Modifier.size(72.dp).clip(CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
        },
        bottom = {
            StepqButton("Allow step counting", ::connect)
            if (phone.isAvailable) StepqTextLink("Use Health Connect instead", ::connectHealth)
            StepqTextLink(if (fromSettings) "Cancel" else "Not now", onDone)
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
        if (phone.isAvailable) {
            Text(
                "Wear a watch or use another fitness app? Choose Health Connect to bring those steps in too.",
                style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        note?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}
