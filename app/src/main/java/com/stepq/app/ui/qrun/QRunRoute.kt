package com.stepq.app.ui.qrun

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stepq.app.data.QRun
import com.stepq.app.data.QRunsRepository
import com.stepq.app.ui.friends.Toast
import com.stepq.app.ui.theme.StepqColors
import kotlinx.coroutines.delay
import java.time.LocalTime

/** The QRun section of the Friends tab (A18–A21): its list, its full-screen pages and its sheet. */
class QRunSection internal constructor(
    private val vm: QRunViewModel,
    private val state: State<QRunUiState>,
    private val pageState: MutableState<String?>,
    private val joining: MutableState<String?>,
    private val places: AndroidPlaces,
    private val locationAllowed: State<Boolean>,
    private val askLocation: () -> Unit,
    private val askNotifications: () -> Unit,
) {
    /** null for the list, "post" for A19, "planner" for A21. */
    val page: String? get() = pageState.value

    @Composable
    fun List() {
        QRunList(
            state.value,
            locationAllowed = locationAllowed.value,
            onRefresh = vm::refresh,
            onAllowLocation = askLocation,
            onJoin = { joining.value = it.id },
            onOpenPlanner = { pageState.value = PLANNER },
            onPost = {
                vm.clearFormError()
                pageState.value = POST
            },
        )
    }

    @Composable
    fun Page() {
        val context = LocalContext.current
        val s = state.value
        when (page) {
            POST -> PostQRunScreen(
                places = places,
                here = s.here,
                busy = s.busy,
                error = s.formError,
                onPost = { title, place, date, time ->
                    vm.post(title, place, date, time, java.time.ZoneId.systemDefault()) {
                        askNotifications()
                        pageState.value = PLANNER
                    }
                },
                onTyping = vm::clearFormError,
                onPickCustomTime = { set -> pickTime(context, set) },
                onClose = { pageState.value = null },
            )
            PLANNER -> QRunPlanner(
                s,
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                onAddToCalendar = { addToCalendar(context, it) },
                onDirections = { openUrl(context, directionsUrl(it)) },
                onLeave = vm::leave,
                onCancelRun = vm::cancel,
                onBack = {
                    vm.bannerShown()
                    pageState.value = null
                },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Overlays(toastModifier: Modifier) {
        val s = state.value
        val run = joining.value?.let { id -> s.data?.runs?.firstOrNull { it.id == id } }
        if (run != null) {
            ModalBottomSheet(
                onDismissRequest = { joining.value = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StepqColors.White,
            ) {
                JoinQRunSheet(
                    run,
                    busy = s.busy,
                    onJoin = {
                        vm.join(run) {
                            joining.value = null
                            askNotifications()
                            pageState.value = PLANNER
                        }
                    },
                    onCancel = { joining.value = null },
                )
            }
        }
        s.toast?.let { message ->
            LaunchedEffect(message) {
                delay(3_000)
                vm.toastShown()
            }
            Toast(message, toastModifier, icon = Icons.Outlined.Event)
        }
    }

    companion object {
        const val POST = "post"
        const val PLANNER = "planner"
    }
}

/** Sets up the QRun section; [visible] is whether its list is on screen (it then asks where you are). */
@Composable
fun QRunSection(repo: QRunsRepository, visible: Boolean): QRunSection {
    val context = LocalContext.current
    val vm: QRunViewModel = viewModel { QRunViewModel(repo) }
    val state = vm.state.collectAsState()
    val page = rememberSaveable { mutableStateOf<String?>(null) }
    val joining = rememberSaveable { mutableStateOf<String?>(null) }
    val places = remember { AndroidPlaces(context.applicationContext) }
    val allowed = remember { mutableStateOf(places.hasLocationPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed.value = it }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LifecycleResumeEffect(Unit) {
        allowed.value = places.hasLocationPermission()
        vm.refresh()
        onPauseOrDispose { }
    }
    // Ask once, the first time the QRun list is opened; after that the header line asks.
    LaunchedEffect(visible) {
        if (visible && !allowed.value && !asked) {
            asked = true
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    LaunchedEffect(allowed.value) {
        if (!allowed.value) return@LaunchedEffect
        val here = places.here()
        vm.setLocation(here, here?.let { places.areaName(it) })
    }
    val data = state.value.data
    LaunchedEffect(data) {
        if (data != null) QRunReminders.sync(context.applicationContext, data.runs, data.myId)
    }

    return remember(vm) {
        QRunSection(
            vm, state, page, joining, places, allowed,
            askLocation = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            askNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

private fun pickTime(context: Context, set: (LocalTime) -> Unit) {
    val now = LocalTime.now().plusHours(1)
    TimePickerDialog(context, { _, h, m -> set(LocalTime.of(h, m)) }, now.hour, (now.minute / 15) * 15, false).show()
}

private fun addToCalendar(context: Context, run: QRun) {
    val start = run.startsAt.toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, run.title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, run.placeName)
        .putExtra(CalendarContract.Events.DESCRIPTION, "QRun on StepQ, hosted by ${run.hostName}.")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60_000)
    runCatching { context.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(context, "No calendar app found.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
