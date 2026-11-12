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
            repo.updateGoal(goal)
                .onSuccess {
                    _state.update { s -> s.copy(saving = false, profile = s.profile?.copy(dailyGoal = goal), toast = "Daily goal set to ${com.stepq.app.ui.onboarding.formatSteps(goal)} steps.") }
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

    fun toastShown() = _state.update { it.copy(toast = null) }
}
