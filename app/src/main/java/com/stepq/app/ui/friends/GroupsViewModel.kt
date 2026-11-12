package com.stepq.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.GroupsData
import com.stepq.app.data.GroupsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: GroupsData? = null,
    val busy: Boolean = false,
    val formError: String? = null,
    val toast: String? = null,
)

class GroupsViewModel(private val repo: GroupsRepository) : ViewModel() {
    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repo.load()
                .onSuccess { d -> _state.update { it.copy(loading = false, error = null, data = d) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load your groups. Pull to try again.") } }
        }
    }

    /** A16. Calls [onCreated] with the new group's id. */
    fun create(name: String, friendIds: Set<String>, onCreated: (String) -> Unit) {
        val me = _state.value.data?.me ?: return
        val problem = groupNameProblem(name)
        if (problem != null) {
            _state.update { it.copy(formError = problem) }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            repo.create(me.id, name, friendIds)
                .onSuccess { g ->
                    _state.update { it.copy(busy = false) }
                    refresh()
                    onCreated(g.id)
                }
                .onFailure { _state.update { it.copy(busy = false, formError = "Couldn't create the group. Try again.") } }
        }
    }

    fun join(code: String, onJoined: (String) -> Unit) {
        if (code.isBlank()) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            repo.join(code)
                .onSuccess { id ->
                    if (id == null) {
                        _state.update { it.copy(busy = false, formError = "No group has that code. Check it with whoever invited you.") }
                    } else {
                        _state.update { it.copy(busy = false, toast = "You joined the group.") }
                        refresh()
                        onJoined(id)
                    }
                }
                .onFailure { _state.update { it.copy(busy = false, formError = "Couldn't join right now. Try again.") } }
        }
    }

    fun leave(groupId: String, onLeft: () -> Unit) {
        val me = _state.value.data?.me ?: return
        viewModelScope.launch {
            repo.leave(groupId, me.id)
                .onSuccess {
                    _state.update { it.copy(toast = "You left the group.") }
                    refresh()
                    onLeft()
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't leave the group. Try again.") } }
        }
    }

    fun clearFormError() = _state.update { it.copy(formError = null) }
    fun toastShown() = _state.update { it.copy(toast = null) }
}

fun groupNameProblem(name: String): String? = when {
    name.isBlank() -> "Give your group a name."
    name.trim().length > 40 -> "Keep the name under 40 characters."
    else -> null
}

fun groupSummary(members: Int, steps: Int): String =
    "$members ${if (members == 1) "member" else "members"} · ${com.stepq.app.ui.onboarding.formatSteps(steps)} steps together today"
