package com.stepq.app.ui.qrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.Place
import com.stepq.app.data.QRun
import com.stepq.app.data.QRunsData
import com.stepq.app.data.QRunsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class QRunUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: QRunsData? = null,
    /** Where the phone is, when location is allowed, and the area's name for the header. */
    val here: LatLng? = null,
    val areaName: String? = null,
    val busy: Boolean = false,
    val formError: String? = null,
    val toast: String? = null,
    /** The QRun just joined or posted, for the "You're in!" banner on the planner. */
    val justJoined: String? = null,
)

class QRunViewModel(private val repo: QRunsRepository) : ViewModel() {
    private val _state = MutableStateFlow(QRunUiState())
    val state: StateFlow<QRunUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repo.load()
                .onSuccess { d -> _state.update { it.copy(loading = false, error = null, data = d) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load QRuns. Pull to try again.") } }
        }
    }

    fun setLocation(here: LatLng?, area: String?) = _state.update { it.copy(here = here, areaName = area) }

    /** A19. Calls [onPosted] with the new QRun's id once it's saved and the list reloaded. */
    fun post(title: String, place: Place, date: LocalDate?, time: LocalTime?, zone: ZoneId, onPosted: (String) -> Unit) {
        val problem = postProblem(title, place.name, date, time, zone, Instant.now())
        if (problem != null || date == null || time == null) {
            _state.update { it.copy(formError = problem) }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            repo.post(title, place, date.atTime(time).atZone(zone).toInstant())
                .onSuccess { id ->
                    reload()
                    _state.update { it.copy(busy = false, justJoined = id) }
                    onPosted(id)
                }
                .onFailure { _state.update { it.copy(busy = false, formError = "Couldn't post your QRun. Check the time and try again.") } }
        }
    }

    /** A20 → A21. */
    fun join(run: QRun, onJoined: () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.join(run.id)
                .onSuccess {
                    reload()
                    _state.update { it.copy(busy = false, justJoined = run.id) }
                    onJoined()
                }
                .onFailure { _state.update { it.copy(busy = false, toast = "Couldn't join right now. Try again.") } }
        }
    }

    fun leave(run: QRun) = change({ repo.leave(run.id) }, "You left ${run.title}.")

    fun cancel(run: QRun) = change({ repo.cancel(run.id) }, "${run.title} is cancelled.")

    private fun change(call: suspend () -> Result<Unit>, done: String) {
        viewModelScope.launch {
            call()
                .onSuccess {
                    reload()
                    _state.update { it.copy(toast = done, justJoined = null) }
                }
                .onFailure { _state.update { it.copy(toast = "Couldn't do that right now. Try again.") } }
        }
    }

    private suspend fun reload() {
        repo.load().onSuccess { d -> _state.update { it.copy(data = d, error = null, loading = false) } }
    }

    fun clearFormError() = _state.update { it.copy(formError = null) }
    fun bannerShown() = _state.update { it.copy(justJoined = null) }
    fun toastShown() = _state.update { it.copy(toast = null) }
}
