package com.stepq.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.FriendsData
import com.stepq.app.data.FriendsRepository
import com.stepq.app.data.PersonSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the add-friend search found. */
sealed interface SearchResult {
    data object Idle : SearchResult
    data object Searching : SearchResult
    data object NotFound : SearchResult
    data class Found(val person: PersonSummary, val relation: Relation) : SearchResult
    data class Failed(val message: String) : SearchResult
}

enum class Relation { Me, Friend, Requested, AsksYou, None }

data class FriendsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: FriendsData? = null,
    /** A one-off message for the snackbar, e.g. "Water reminder sent to Meera. 4 left today." */
    val toast: String? = null,
    val sending: Set<String> = emptySet(),
    val search: SearchResult = SearchResult.Idle,
)

class FriendsViewModel(private val repo: FriendsRepository) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repo.load()
                .onSuccess { data -> _state.update { it.copy(loading = false, error = null, data = data) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load your friends. Pull to try again.") } }
        }
    }

    fun remind(friend: PersonSummary) {
        val data = _state.value.data ?: return
        if (friend.id in data.remindedToday || friend.id in _state.value.sending) return
        if (data.remindersLeft == 0) {
            _state.update { it.copy(toast = "You've sent all 5 water reminders for today. More with StepQ Pro.") }
            return
        }
        _state.update { it.copy(sending = it.sending + friend.id) }
        viewModelScope.launch {
            repo.sendWaterReminder(data.me.id, friend.id)
                .onSuccess {
                    _state.update { s ->
                        val d = s.data ?: return@update s
                        val left = d.remindersLeft?.minus(1)?.coerceAtLeast(0)
                        s.copy(
                            sending = s.sending - friend.id,
                            data = d.copy(remindedToday = d.remindedToday + friend.id, remindersLeft = left),
                            toast = reminderSentMessage(friend.displayName, left),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            sending = it.sending - friend.id,
                            toast = if (e.message?.contains("5 water reminders") == true) {
                                "You've sent all 5 water reminders for today."
                            } else {
                                "Couldn't send the reminder. Try again."
                            },
                        )
                    }
                }
        }
    }

    fun toastShown() = _state.update { it.copy(toast = null) }

    fun search(username: String) {
        val data = _state.value.data ?: return
        if (username.isBlank()) return
        _state.update { it.copy(search = SearchResult.Searching) }
        viewModelScope.launch {
            repo.findByUsername(username)
                .onSuccess { person ->
                    _state.update {
                        it.copy(
                            search = if (person == null) SearchResult.NotFound else SearchResult.Found(person, relationTo(person.id, data)),
                        )
                    }
                }
                .onFailure { _state.update { it.copy(search = SearchResult.Failed("Couldn't search right now. Try again.")) } }
        }
    }

    fun clearSearch() = _state.update { it.copy(search = SearchResult.Idle) }

    fun sendRequest(person: PersonSummary) {
        val data = _state.value.data ?: return
        viewModelScope.launch {
            repo.sendRequest(data.me.id, person.id)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            data = s.data?.let { d -> d.copy(sentRequestIds = d.sentRequestIds + person.id) },
                            search = SearchResult.Found(person, Relation.Requested),
                            toast = "Friend request sent to ${person.displayName.firstName()}.",
                        )
                    }
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't send the request. Try again.") } }
        }
    }

    fun accept(person: PersonSummary) {
        val data = _state.value.data ?: return
        viewModelScope.launch {
            repo.accept(data.me.id, person.id)
                .onSuccess {
                    _state.update { it.copy(toast = "You and ${person.displayName.firstName()} are friends now.") }
                    refresh()
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't accept. Try again.") } }
        }
    }

    fun decline(person: PersonSummary) {
        val data = _state.value.data ?: return
        viewModelScope.launch {
            repo.remove(data.me.id, person.id)
                .onSuccess { refresh() }
                .onFailure { _state.update { it.copy(toast = "Couldn't decline. Try again.") } }
        }
    }
}

fun relationTo(id: String, data: FriendsData): Relation = when {
    id == data.me.id -> Relation.Me
    data.board.any { it.person.id == id } -> Relation.Friend
    id in data.sentRequestIds -> Relation.Requested
    data.requests.any { it.id == id } -> Relation.AsksYou
    else -> Relation.None
}

fun reminderSentMessage(name: String, left: Int?): String {
    val first = name.firstName()
    return when (left) {
        null -> "Water reminder sent to $first."
        0 -> "Water reminder sent to $first. That's all 5 for today."
        else -> "Water reminder sent to $first. $left left today."
    }
}

fun remindersBanner(left: Int?): String =
    if (left == null) "Water reminders: unlimited · StepQ Pro"
    else "Water reminders left today: $left of ${FriendsRepository.FREE_REMINDERS_PER_DAY} · Free plan"

internal fun String.firstName() = trim().substringBefore(' ')
