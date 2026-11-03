package com.stepq.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.HomeRepository
import com.stepq.app.health.HealthConnect
import com.stepq.app.health.StepsReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val dateLabel: String = "",
    val greeting: String = "",
    val firstName: String = "",
    val streak: Int = 0,
    val steps: Int = 0,
    val goal: Int = 8000,
    val km: Double = 0.0,
    val kcal: Int = 0,
    val activeMinutes: Int = 0,
    val week: List<Int?> = List(7) { null },
    val weekAverage: Int = 0,
    val friends: List<FriendRow> = emptyList(),
    val hasFriends: Boolean = false,
    val mood: PikoMood = PikoMood.Nudge,
    val healthConnected: Boolean = true,
) {
    val score: Int get() = stepqScore(steps, goal)
}

class HomeViewModel(
    private val repo: HomeRepository,
    private val reader: StepsReader,
    private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val prefs = appContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    /** Pulls the last week from Health Connect, saves it, then reloads Home. Called on resume. */
    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val connected = HealthConnect.hasAllPermissions(appContext)
            val first = repo.load(today)
            val me = first.getOrNull()?.me
            if (connected && me != null) {
                runCatching { reader.readDays(me.id, today) }
                    .onFailure { android.util.Log.w("StepqHealth", "Couldn't read steps", it) }
                    .getOrNull()?.let { repo.saveDays(it) }
            }
            val result = if (connected && me != null) repo.load(today) else first
            result
                .onSuccess { data -> if (data != null) _state.value = build(data, today, connected) }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = "Couldn't load your steps. Pull to try again.")
                }
        }
    }

    private fun build(data: com.stepq.app.data.HomeData, today: LocalDate, connected: Boolean): HomeUiState {
        val goal = data.me.dailyGoal
        val todayRow = data.myDays.firstOrNull { it.day == today.toString() }
        val steps = todayRow?.steps ?: 0
        val week = weekBars(data.myDays, today)
        val mood = when {
            steps < goal -> PikoMood.Nudge
            prefs.getString(KEY_CELEBRATED, null) != today.toString() -> {
                prefs.edit().putString(KEY_CELEBRATED, today.toString()).apply()
                PikoMood.Cheer
            }
            else -> PikoMood.Happy
        }
        return HomeUiState(
            loading = false,
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)),
            greeting = greeting(LocalTime.now()),
            firstName = data.me.displayName.substringBefore(' '),
            streak = streak(data.myDays, goal, today),
            steps = steps,
            goal = goal,
            km = (todayRow?.distanceM ?: 0) / 1000.0,
            kcal = todayRow?.calories ?: 0,
            activeMinutes = todayRow?.activeMinutes ?: 0,
            week = week,
            weekAverage = weekAverage(week),
            friends = friendsToday(
                me = "You" to data.me.avatar,
                mySteps = steps,
                friends = data.friendsToday.map { (p, s) -> Triple(p.displayName.substringBefore(' '), p.avatar, s) },
            ),
            hasFriends = data.friendsToday.isNotEmpty(),
            mood = mood,
            healthConnected = connected,
        )
    }

    private companion object {
        const val KEY_CELEBRATED = "celebrated_day"
    }
}

