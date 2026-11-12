package com.stepq.app.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.ChallengesData
import com.stepq.app.data.ChallengesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChallengesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: ChallengesData? = null,
    val joining: Boolean = false,
    val toast: String? = null,
)

class ChallengesViewModel(private val repo: ChallengesRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChallengesUiState())
    val state: StateFlow<ChallengesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repo.load()
                .onSuccess { d -> _state.update { it.copy(loading = false, error = null, data = d) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load challenges. Pull to try again.") } }
        }
    }

    fun join(challengeId: String, onJoined: () -> Unit) {
        val me = _state.value.data?.me ?: return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            repo.join(me.id, challengeId)
                .onSuccess {
                    _state.update { s ->
                        val d = s.data
                        s.copy(
                            joining = false,
                            data = d?.copy(
                                challenges = d.challenges.map {
                                    if (it.challenge.id == challengeId) it.copy(myJoinedAt = Instant.now(), joined = it.joined + 1) else it
                                },
                            ),
                        )
                    }
                    onJoined()
                    refresh()
                }
                .onFailure { _state.update { it.copy(joining = false, toast = "Couldn't join. The challenge may have ended.") } }
        }
    }

    fun leave(challengeId: String) {
        val me = _state.value.data?.me ?: return
        viewModelScope.launch {
            repo.leave(me.id, challengeId)
                .onSuccess {
                    _state.update { it.copy(toast = "You left the challenge.") }
                    refresh()
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't leave. Try again.") } }
        }
    }

    fun toastShown() = _state.update { it.copy(toast = null) }
}

private val IST = com.stepq.app.data.FriendsRepository.IST

/** "Ends in 1d 14h", "Ends in 3h", "Ends in 12m". */
fun endsIn(end: Instant, now: Instant = Instant.now()): String {
    val left = Duration.between(now, end)
    if (left.isNegative || left.isZero) return "Ended"
    val d = left.toDays()
    val h = left.toHours() % 24
    val m = left.toMinutes() % 60
    return when {
        d > 0 -> "Ends in ${d}d ${h}h"
        h > 0 -> "Ends in ${h}h"
        else -> "Ends in ${m.coerceAtLeast(1)}m"
    }
}

/** End of the current week (Sunday 23:59:59) or month, India time. */
fun periodEnd(today: LocalDate, monthly: Boolean): Instant {
    val lastDay = if (monthly) today.withDayOfMonth(today.lengthOfMonth()) else ChallengesRepository.weekStart(today).plusDays(6)
    return lastDay.plusDays(1).atStartOfDay(IST).toInstant()
}

/** "Mon 10 Nov – Sun 16 Nov" or "1 Nov – 30 Nov". */
fun periodLabel(today: LocalDate, monthly: Boolean): String {
    val start = if (monthly) today.withDayOfMonth(1) else ChallengesRepository.weekStart(today)
    val end = if (monthly) today.withDayOfMonth(today.lengthOfMonth()) else start.plusDays(6)
    val f = DateTimeFormatter.ofPattern(if (monthly) "d MMM" else "EEE d MMM", Locale.ENGLISH)
    return "${start.format(f)} – ${end.format(f)} · ranked by total steps"
}

/** "Fri 24 Oct" in India time. */
fun shortDate(i: Instant): String = ZonedDateTime.ofInstant(i, IST).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))

fun daysLeft(end: Instant, now: Instant = Instant.now()): String {
    val days = Duration.between(now, end).toHours().let { (it + 23) / 24 }.coerceAtLeast(0)
    return when (days) {
        0L -> "Ends today"
        1L -> "1 day left"
        else -> "$days days left"
    }
}

fun joinedLabel(n: Long): String = when (n) {
    0L -> "Be the first to join"
    1L -> "1 person has joined"
    else -> "${com.stepq.app.ui.onboarding.formatSteps(n.toInt())} people have joined"
}
