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

/** What the Add friends page shows: live results while typing, suggestions otherwise. */
data class AddFriendState(
    val query: String = "",
    val searching: Boolean = false,
    /** Null until the first search for [query] returns. */
    val results: List<com.stepq.app.data.Suggestion>? = null,
    val failed: Boolean = false,
    val mayKnow: List<com.stepq.app.data.Suggestion> = emptyList(),
    /** Null until contacts have been read (permission granted and matched). */
    val contacts: List<com.stepq.app.data.Suggestion>? = null,
    val contactsLoading: Boolean = false,
)

data class FriendsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: FriendsData? = null,
    /** A one-off message for the snackbar, e.g. "Water reminder sent to Meera. 4 left today." */
    val toast: String? = null,
    val sending: Set<String> = emptySet(),
    val search: SearchResult = SearchResult.Idle,
    val add: AddFriendState = AddFriendState(),
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
            com.stepq.app.ui.components.ProSheets.show(com.stepq.app.data.MyPlan.F.WATER_REMINDERS)
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
                            toast = reminderSentMessage(friend.displayName, left, d.reminderLimit),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            sending = it.sending - friend.id,
                            toast = if (com.stepq.app.ui.components.ProSheets.showFor(e)) {
                                null
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

    private var searchJob: kotlinx.coroutines.Job? = null

    /** Search as you type, 250 ms after the last key. */
    fun type(query: String) {
        _state.update { it.copy(add = it.add.copy(query = query, failed = false, results = if (query.trim().removePrefix("@").length < 2) null else it.add.results)) }
        searchJob?.cancel()
        val q = query.trim().removePrefix("@")
        if (q.length < 2) {
            _state.update { it.copy(add = it.add.copy(searching = false, results = null)) }
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            _state.update { it.copy(add = it.add.copy(searching = true)) }
            repo.searchPeople(q)
                .onSuccess { list -> _state.update { it.copy(add = it.add.copy(searching = false, results = list)) } }
                .onFailure { _state.update { it.copy(add = it.add.copy(searching = false, failed = true)) } }
        }
    }

    /** Loads "People you may know" when Add friends opens. */
    fun openAdd() {
        _state.update { it.copy(add = AddFriendState(contacts = it.add.contacts)) }
        viewModelScope.launch {
            repo.peopleYouMayKnow().onSuccess { list -> _state.update { it.copy(add = it.add.copy(mayKnow = list)) } }
        }
    }

    /** Matches the phone's contacts (hashed emails) against StepQ accounts. */
    fun loadContacts(hashes: suspend () -> List<String>) {
        _state.update { it.copy(add = it.add.copy(contactsLoading = true)) }
        viewModelScope.launch {
            val list = runCatching { hashes() }.getOrDefault(emptyList())
            repo.matchContacts(list)
                .onSuccess { found -> _state.update { it.copy(add = it.add.copy(contactsLoading = false, contacts = found)) } }
                .onFailure { _state.update { it.copy(add = it.add.copy(contactsLoading = false, contacts = emptyList())) } }
        }
    }

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

fun reminderSentMessage(name: String, left: Int?, limit: Int? = FriendsRepository.FREE_REMINDERS_PER_DAY): String {
    val first = name.firstName()
    return when (left) {
        null -> "Water reminder sent to $first."
        0 -> if (limit != null) "Water reminder sent to $first. That's all $limit for today." else "Water reminder sent to $first."
        else -> "Water reminder sent to $first. $left left today."
    }
}

fun remindersBanner(left: Int?, limit: Int? = FriendsRepository.FREE_REMINDERS_PER_DAY, pro: Boolean = false): String =
    if (left == null) "Water reminders: unlimited${if (pro) " · StepQ Pro" else ""}"
    else "Water reminders left today: $left of ${limit ?: left} · ${if (pro) "StepQ Pro" else "Free plan"}"

internal fun String.firstName() = trim().substringBefore(' ')
