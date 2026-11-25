package com.stepq.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.HomeData
import com.stepq.app.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Streaks section on the Friends tab. Reads what Home reads (friends, today's steps and my
 * streak with each friend) without touching the step counter, and shares steps from here.
 */
class StreaksViewModel(private val repo: HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var data: HomeData? = null

    /** Streaks that ended last night and can still be restored (StepQ Pro restores). */
    private val _lost = MutableStateFlow<List<com.stepq.app.data.LostStreak>>(emptyList())
    val lost: StateFlow<List<com.stepq.app.data.LostStreak>> = _lost.asStateFlow()
    private val _restoring = MutableStateFlow<String?>(null)
    val restoring: StateFlow<String?> = _restoring.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            repo.load(today)
                .onSuccess { loaded -> loaded?.let { data = it; _state.value = build(it, today, _state.value) } }
                .onFailure { _state.value = _state.value.copy(loading = false, error = "Couldn't load your streaks. Try again.") }
            _lost.value = com.stepq.app.data.Pro.lostStreaks(today)
        }
    }

    /** Brings back yesterday's streak with a friend, using one of this month's restores. */
    fun restore(friendId: String, name: String) {
        if (_restoring.value != null) return
        val plan = com.stepq.app.data.Pro.plan.value
        if (plan?.restoresLeft == 0) {
            com.stepq.app.ui.components.ProSheets.show(com.stepq.app.data.MyPlan.F.STREAK_RESTORES)
            return
        }
        _restoring.value = friendId
        viewModelScope.launch {
            com.stepq.app.data.Pro.restoreStreak(friendId, LocalDate.now())
                .onSuccess { left ->
                    _lost.value = _lost.value.filterNot { it.friendId == friendId }
                    _state.value = _state.value.copy(toast = restoreToast(name.substringBefore(' '), left))
                    refresh()
                }
                .onFailure { e ->
                    if (!com.stepq.app.ui.components.ProSheets.showFor(e)) {
                        _state.value = _state.value.copy(toast = "Couldn't restore that streak. Try again.")
                    }
                }
            _restoring.value = null
        }
    }

    /** Pins a friend to the top of Streaks (Pro), or unpins with null. */
    fun pinBff(friendId: String?) {
        viewModelScope.launch {
            com.stepq.app.data.Pro.setBff(friendId)
                .onSuccess { _state.value = _state.value.copy(toast = if (friendId == null) "BFF unpinned" else "Pinned as your BFF") }
                .onFailure { e ->
                    if (!com.stepq.app.ui.components.ProSheets.showFor(e)) {
                        _state.value = _state.value.copy(toast = "Couldn't pin your BFF. Try again.")
                    }
                }
        }
    }

    fun share(friendIds: Set<String>) {
        val s = _state.value
        if (friendIds.isEmpty() || s.sharing) return
        _state.value = s.copy(sharing = true)
        viewModelScope.launch {
            val today = LocalDate.now()
            repo.shareSteps(friendIds, s.steps, today)
                .onSuccess { (sent, streaks) ->
                    val names = s.shareTo.filter { it.id in friendIds }.map { it.displayName.substringBefore(' ') }
                    val before = data?.streaks
                    data = data?.let { it.copy(streaks = streaks ?: it.streaks) }
                    val only = friendIds.singleOrNull()
                    val withThem = only?.let { streaks?.get(it)?.streak } ?: 0
                    val firstToThemToday = only != null && sent > 0 && streaks != null && before?.get(only)?.sharedToday != true
                    val rebuilt = data?.let { build(it, today, _state.value) } ?: _state.value
                    _state.value = rebuilt.copy(
                        sharing = false,
                        sharedToday = sent > 0 || s.sharedToday,
                        sharedTodayWith = rebuilt.sharedTodayWith + s.sharedTodayWith + (if (sent > 0) friendIds else emptySet()),
                        toast = shareToast(names, s.steps, withThem, firstToday = firstToThemToday),
                    )
                }
                .onFailure { _state.value = _state.value.copy(sharing = false, toast = "Couldn't share your steps. Try again.") }
        }
    }

    fun toastShown() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun build(d: HomeData, today: LocalDate, old: HomeUiState): HomeUiState {
        val streaks = d.streaks.orEmpty()
        return old.copy(
            loading = false,
            error = null,
            steps = d.myDays.firstOrNull { it.day == today.toString() }?.steps ?: 0,
            shareTo = d.friendsToday.map { it.first }.sortedBy { it.displayName },
            hasFriends = d.friendsToday.isNotEmpty(),
            streaks = streaks.mapValues { it.value.streak }.filterValues { it > 0 },
            sharedTodayWith = streaks.values.filter { it.sharedToday }.map { it.friendId }.toSet(),
            sharedToday = streaks.values.any { it.sharedToday },
        )
    }
}

fun restoreToast(first: String, left: Int?): String =
    "Your streak with $first is back. " + when (left) {
        null -> "Keep it going today."
        0 -> "That was your last restore this month."
        1 -> "1 restore left this month."
        else -> "$left restores left this month."
    }
