package com.stepq.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.DailySteps
import com.stepq.app.data.Profile
import com.stepq.app.data.ProfileRepository
import com.stepq.app.ui.home.streak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProfileStats(val streak: Int, val bestDay: Int, val kmSoFar: Int)

data class ProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val profile: Profile? = null,
    val stats: ProfileStats = ProfileStats(0, 0, 0),
    val onWaitlist: Boolean = false,
    val saving: Boolean = false,
    val toast: String? = null,
    /** Error shown on the Edit profile / Account pages. */
    val formError: String? = null,
    /** Address a change-email link was sent to. */
    val emailSentTo: String? = null,
)

fun profileStats(days: List<DailySteps>, goal: Int, today: LocalDate): ProfileStats = ProfileStats(
    streak = streak(days, goal, today),
    bestDay = days.maxOfOrNull { it.steps } ?: 0,
    kmSoFar = days.sumOf { it.distanceM } / 1000,
)

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val profile = repo.mine()
            val days = repo.myDays().getOrDefault(emptyList())
            val waitlist = repo.onProWaitlist().getOrDefault(false)
            profile
                .onSuccess { p ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            profile = p,
                            stats = profileStats(days, p?.dailyGoal ?: 8000, LocalDate.now()),
                            onWaitlist = waitlist,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load your profile. Try again.") } }
        }
    }

    fun saveGoal(goal: Int, onSaved: () -> Unit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = _state.value.profile?.dailyGoal ?: goal
            repo.updateGoal(goal, current)
                .onSuccess {
                    val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
                    _state.update { s ->
                        s.copy(
                            saving = false,
                            profile = s.profile?.copy(
                                nextDailyGoal = goal.takeIf { it != current },
                                nextGoalFrom = tomorrow.takeIf { goal != current },
                            ),
                            toast = if (goal == current) "Your goal stays at ${com.stepq.app.ui.onboarding.formatSteps(goal)} steps."
                            else "From tomorrow your goal is ${com.stepq.app.ui.onboarding.formatSteps(goal)} steps.",
                        )
                    }
                    onSaved()
                }
                .onFailure { _state.update { it.copy(saving = false, toast = "Couldn't save your goal. Try again.") } }
        }
    }

    fun joinWaitlist(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.joinProWaitlist()
                .onSuccess {
                    _state.update { it.copy(onWaitlist = true, toast = "We'll let you know when Pro launches.") }
                    onDone()
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't add you to the list. Try again.") } }
        }
    }

    fun saveProfile(name: String, username: String, avatar: com.stepq.app.data.AvatarConfig, gender: String?, onSaved: () -> Unit) {
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            repo.updateProfile(name.trim(), username, avatar, gender)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            saving = false,
                            profile = s.profile?.copy(displayName = name.trim(), username = username, avatar = avatar, gender = gender),
                            toast = "Profile saved.",
                        )
                    }
                    onSaved()
                }
                .onFailure { e ->
                    val taken = e.message.orEmpty().let { "duplicate" in it || "23505" in it }
                    _state.update { it.copy(saving = false, formError = if (taken) "That username was just taken. Try another." else "Couldn't save. Check your connection and try again.") }
                }
        }
    }

    suspend fun usernameAvailable(name: String): Boolean? = repo.usernameAvailable(name).getOrNull()

    fun changePassword(auth: com.stepq.app.data.AuthRepository, password: String, onDone: () -> Unit) {
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            auth.updatePassword(password)
                .onSuccess {
                    _state.update { it.copy(saving = false, toast = "Password changed.") }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(saving = false, formError = authErrorMessage(e)) } }
        }
    }

    fun changeEmail(auth: com.stepq.app.data.AuthRepository, email: String) {
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            auth.updateEmail(email.trim())
                .onSuccess { _state.update { it.copy(saving = false, emailSentTo = email.trim()) } }
                .onFailure { e -> _state.update { it.copy(saving = false, formError = authErrorMessage(e)) } }
        }
    }

    fun deleteAccount(auth: com.stepq.app.data.AuthRepository) {
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            auth.deleteAccount()
                .onSuccess { _state.update { it.copy(saving = false) } }
                .onFailure { _state.update { it.copy(saving = false, formError = "Couldn't delete your account. Check your connection and try again.") } }
        }
    }

    fun clearForm() = _state.update { it.copy(formError = null, emailSentTo = null) }

    fun toastShown() = _state.update { it.copy(toast = null) }
}

/** A friendly message for a Supabase Auth error. */
fun authErrorMessage(e: Throwable): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "same_password" in m || "different from the old" in m -> "Pick a password you haven't used here before."
        "weak_password" in m || "password should" in m -> "That password is too weak. Use at least 8 characters with a number."
        "email_exists" in m || "already been registered" in m || "already registered" in m -> "That email already has a StepQ account."
        "reauthentication" in m -> "For safety, log out and log in again, then change your password."
        "rate" in m && "limit" in m -> "Too many tries. Wait a few minutes and try again."
        "invalid" in m && "email" in m -> "That email doesn't look right."
        else -> "Something went wrong. Check your connection and try again."
    }
}
