package com.stepq.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stepq.app.data.BlockedUser
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType

/**
 * Privacy and data: the rights StepQ promises under the DPDP Act (access, correction, erasure,
 * withdrawing consent, grievances) as things you can do from the app, not only by email.
 */
@Composable
fun PrivacyScreen(
    exporting: Boolean,
    blocks: List<BlockedUser>?,
    onExport: () -> Unit,
    onPermissions: () -> Unit,
    onAccount: () -> Unit,
    onUnblock: (BlockedUser) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        "Privacy and data",
        onBack,
        subtitle = "Your name, avatar and step totals are shown to your friends and group mates and in your country's rankings. Your email, birthdate and gender never are.",
    ) {
        SectionLabel("Your data")
        Spacer(Modifier.height(10.dp))
        Group {
            Item(Icons.Outlined.Download, if (exporting) "Preparing your data…" else "Download my data", "A JSON file with your profile, steps, friends, posts and consents", enabled = !exporting, onClick = onExport)
            Divider()
            Item(Icons.Outlined.ManageAccounts, "Correct or delete", "Edit your profile, or delete your account under Account", onClick = onAccount)
            Divider()
            Item(Icons.Outlined.Tune, "App permissions", "Stop StepQ reading steps, contacts or location at any time", onClick = onPermissions)
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Blocked accounts")
        Spacer(Modifier.height(10.dp))
        when {
            blocks == null -> Text("Loading…", style = StepqType.Small, color = StepqColors.Muted)
            blocks.isEmpty() -> Text("You haven't blocked anyone. Block someone from the … menu on their post.", style = StepqType.Small, color = StepqColors.Muted)
            else -> Group {
                blocks.forEachIndexed { i, b ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Block, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(b.displayName, style = StepqType.BodyStrong, color = StepqColors.Ink)
                            Text("@${b.username}", style = StepqType.Small, color = StepqColors.Muted)
                        }
                        TextButton(onClick = { onUnblock(b) }) { Text("Unblock", style = StepqType.SmallStrong, color = StepqColors.Deep) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Policies and help")
        Spacer(Modifier.height(10.dp))
        Group {
            Item(Icons.Outlined.Policy, "Privacy policy", "What we collect, why, and for how long") { onOpen(com.stepq.app.ui.components.Legal.PRIVACY_URL) }
            Divider()
            Item(Icons.Outlined.Description, "Terms of use and community rules", null) { onOpen(com.stepq.app.ui.components.Legal.TERMS_URL) }
            Divider()
            Item(Icons.Outlined.Gavel, "Grievance officer", "Complaints about your data or a post, answered within the legal time limits") { onOpen(com.stepq.app.ui.components.Legal.GRIEVANCE_URL) }
            Divider()
            Item(Icons.Outlined.SupportAgent, "Email us", com.stepq.app.ui.components.Legal.SUPPORT_EMAIL) {
                onOpen("mailto:${com.stepq.app.ui.components.Legal.SUPPORT_EMAIL}?subject=" + android.net.Uri.encode("My StepQ data"))
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint)) { content() }
}

@Composable
private fun Divider() = Spacer(Modifier.fillMaxWidth().height(1.dp).background(StepqColors.Line))

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = StepqType.BodyStrong, color = StepqColors.Ink)
            if (subtitle != null) Text(subtitle, style = StepqType.Small, color = StepqColors.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StepqColors.Muted)
    }
}
