package com.stepq.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepq.app.data.AvatarConfig
import com.stepq.app.data.Gender
import com.stepq.app.data.Profile
import com.stepq.app.data.isValidEmail
import com.stepq.app.data.isValidPassword
import com.stepq.app.data.isValidUsername
import com.stepq.app.data.normalizeUsername
import com.stepq.app.ui.components.FieldHint
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.onboarding.AvatarPicker
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay

/** A settings page: back arrow, title, scrolling body and a button pinned at the bottom. */
@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
                }
            } else {
                Spacer(Modifier.height(44.dp))
            }
            Text(title, style = StepqType.Title, color = StepqColors.Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = StepqType.Body, color = StepqColors.Muted)
            }
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        if (bottom != null) Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { bottom() }
    }
}

/** What's wrong with the Edit profile form, or null. */
fun editProfileProblem(name: String, username: String): String? = when {
    name.isBlank() -> "Enter your name."
    name.trim().length > 40 -> "Keep your name under 40 characters."
    !isValidUsername(username) -> "Usernames are 3–20 letters, numbers, dots or underscores."
    else -> null
}

/** A26 Edit profile: name, username, gender and avatar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    profile: Profile,
    saving: Boolean,
    error: String?,
    checkUsername: suspend (String) -> Boolean?,
    onSave: (name: String, username: String, avatar: AvatarConfig, gender: String?) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var username by rememberSaveable { mutableStateOf(profile.username) }
    var gender by rememberSaveable { mutableStateOf(profile.gender) }
    var avatar by rememberSaveable(stateSaver = AvatarSaver) { mutableStateOf(profile.avatar) }
    var available by rememberSaveable { mutableStateOf<Boolean?>(true) }
    LaunchedEffect(username) {
        if (username == profile.username) { available = true; return@LaunchedEffect }
        available = null
        if (!isValidUsername(username)) return@LaunchedEffect
        delay(400)
        available = checkUsername(username)
    }
    val problem = editProfileProblem(name, username) ?: if (available == false) "That username is taken." else null
    SettingsPage(
        "Edit profile",
        onBack,
        bottom = {
            error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(bottom = 8.dp)) }
            StepqButton(
                "Save changes",
                { onSave(name, username, avatar, gender) },
                enabled = problem == null && available == true,
                loading = saving,
            )
        },
    ) {
        AvatarPicker(avatar, 120.dp) { avatar = it }
        Spacer(Modifier.height(28.dp))
        UnderlineField(
            "Name",
            name,
            { name = it.take(40) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Username",
            username,
            { username = normalizeUsername(it).take(20) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        )
        when {
            username != profile.username && !isValidUsername(username) ->
                FieldHint("3–20 letters, numbers, dots or underscores.", ok = false)
            username != profile.username && available == true -> FieldHint("@$username is available", ok = true)
            available == false -> FieldHint("@$username is taken", ok = false)
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Gender")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Gender.entries.forEach { g -> StepqChip(g.label, selected = gender == g.value, onClick = { gender = g.value }) }
        }
        if (profile.birthdate != null) {
            Spacer(Modifier.height(20.dp))
            Text("Birthdate can't be changed after sign-up. Email us if it's wrong.", style = StepqType.Small, color = StepqColors.Muted)
        }
    }
}

private val AvatarSaver = androidx.compose.runtime.saveable.Saver<AvatarConfig, List<Int>>(
    save = { listOf(it.skin, it.hair, it.outfit, it.headband) },
    restore = { AvatarConfig(it[0], it[1], it[2], it[3]) },
)

/** A26 Account and security: email, password and deleting the account. */
@Composable
fun AccountScreen(
    email: String?,
    pendingEmail: String?,
    hasPassword: Boolean,
    onChangeEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage("Account", onBack) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(StepqDimens.RadiusM)).background(StepqColors.Mint)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = hasPassword, role = Role.Button, onClick = onChangeEmail).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(22.dp))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("Email", style = StepqType.BodyStrong, color = StepqColors.Ink)
                    Text(email ?: "—", style = StepqType.Small, color = StepqColors.Muted)
                    if (pendingEmail != null) {
                        Text("Waiting for you to confirm $pendingEmail", style = StepqType.Small, color = StepqColors.Deep)
                    }
                }
                if (hasPassword) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StepqColors.Muted)
            }
            if (hasPassword) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(StepqColors.Line))
                AccountRow(Icons.Outlined.Lock, "Change password", "At least 8 characters with a number", onChangePassword)
            }
        }
        if (!hasPassword) {
            Text(
                "You sign in with Google, so your email and password are managed by your Google account.",
                style = StepqType.Small,
                color = StepqColors.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StepqDimens.RadiusM))
                .background(StepqColors.TomatoSoft)
                .clickable(role = Role.Button, onClick = onDelete)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = StepqColors.TomatoText, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text("Delete account", style = StepqType.BodyStrong, color = StepqColors.TomatoText)
                Text("Removes your profile, steps, friends, groups and QRuns for good.", style = StepqType.Small, color = StepqColors.Muted)
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = StepqType.BodyStrong, color = StepqColors.Ink)
            Text(subtitle, style = StepqType.Small, color = StepqColors.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = StepqColors.Muted)
    }
}

/** What's wrong with a new password, or null. */
fun newPasswordProblem(password: String, confirm: String): String? = when {
    !isValidPassword(password) -> "Use at least 8 characters with a number."
    confirm.isNotEmpty() && confirm != password -> "The passwords don't match."
    else -> null
}

/** Change password (from Account), and "Set a new password" after opening a reset link. */
@Composable
fun NewPasswordScreen(
    title: String,
    subtitle: String?,
    busy: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onBack: (() -> Unit)?,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val problem = newPasswordProblem(password, confirm)
    SettingsPage(
        title,
        onBack,
        subtitle = subtitle,
        bottom = {
            StepqButton("Save password", { onSave(password) }, enabled = problem == null && confirm == password, loading = busy)
        },
    ) {
        UnderlineField(
            "New password",
            password,
            { password = it },
            password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        if (password.isNotEmpty() && !isValidPassword(password)) FieldHint("At least 8 characters with a number.", ok = false)
        Spacer(Modifier.height(20.dp))
        UnderlineField(
            "Confirm new password",
            confirm,
            { confirm = it },
            password = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (confirm.isNotEmpty() && confirm != password) FieldHint("The passwords don't match.", ok = false)
        error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
    }
}

/** Change email: Supabase sends a link to the new address, and the change applies once it's opened. */
@Composable
fun ChangeEmailScreen(current: String?, busy: Boolean, error: String?, sentTo: String?, onSave: (String) -> Unit, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    SettingsPage(
        "Change email",
        onBack,
        subtitle = current?.let { "You sign in with $it." },
        bottom = {
            if (sentTo == null) {
                StepqButton(
                    "Send confirmation link",
                    { onSave(email) },
                    enabled = isValidEmail(email) && !email.trim().equals(current, ignoreCase = true),
                    loading = busy,
                )
            } else {
                StepqButton("Done", onBack)
            }
        },
    ) {
        if (sentTo != null) {
            Text(
                "We sent a link to $sentTo. Open it on this phone to finish. Until then you keep signing in with your current email.",
                style = StepqType.Body,
                color = StepqColors.Ink,
            )
        } else {
            UnderlineField(
                "New email",
                email,
                { email = it.trim() },
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            )
            error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

/** Confirm before deleting: type DELETE. */
@Composable
fun DeleteAccountDialog(busy: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete your account?", style = StepqType.Heading) },
        text = {
            Column {
                Text(
                    "This permanently deletes your profile, step history, friends, groups, QRuns and challenge entries. It can't be undone.",
                    style = StepqType.Body,
                )
                Spacer(Modifier.height(16.dp))
                UnderlineField(
                    "Type DELETE to confirm",
                    typed,
                    { typed = it.uppercase().take(6) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                )
                error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed == "DELETE" && !busy) {
                Text(if (busy) "Deleting…" else "Delete forever", color = if (typed == "DELETE") StepqColors.TomatoText else StepqColors.Muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = StepqColors.Ink) } },
        containerColor = StepqColors.White,
    )
}
