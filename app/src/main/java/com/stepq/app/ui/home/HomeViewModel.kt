package com.stepq.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.HomeRepository
import com.stepq.app.health.StepsReader
import com.stepq.app.data.DailySteps
import kotlinx.coroutines.Job
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
    /** I've shared my steps with at least one friend today. */
    val sharedToday: Boolean = false,
    /**
     * My streak with each friend, by friend id (only those above 0). Streaks are per friend:
     * there is no overall streak, and several can be going at once.
     */
    val streaks: Map<String, Int> = emptyMap(),
    /** One point per day the goal was reached. */
    val score: Int = 0,
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
    /** Friends I can share my steps with. */
    val shareTo: List<com.stepq.app.data.PersonSummary> = emptyList(),
    val sharing: Boolean = false,
    val toast: String? = null,
)

class HomeViewModel(
    private val repo: HomeRepository,
    private val reader: StepsReader,
    private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val prefs = appContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    private var data: com.stepq.app.data.HomeData? = null
    private var connected = false
    private var live: Job? = null
    private var unsaved: List<DailySteps> = emptyList()
    private var lastUpload = 0L

    /** Pulls the last week from the step source, saves it, then reloads Home. Called on resume. */
    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val source = reader.source()
            connected = source != StepsReader.Source.None
            val first = repo.load(today)
            val me = first.getOrNull()?.me
            var local: List<DailySteps> = emptyList()
            if (connected && me != null) {
                local = runCatching { reader.readDays(me.id, today) }
                    .onFailure { android.util.Log.w("StepqHealth", "Couldn't read steps", it) }
                    .getOrDefault(emptyList())
                repo.saveDays(local).onFailure { android.util.Log.w("StepqHealth", "Couldn't save steps", it) }
            }
            val result = if (connected && me != null) repo.load(today) else first
            result
                .onSuccess { fetched ->
                    // Show what the phone counted even if the server copy is behind.
                    val loaded = fetched?.let { it.copy(myDays = overlayDays(it.myDays, local)) }
                    if (loaded != null) {
                        data = loaded
                        _state.value = build(loaded, today, connected)
                        if (source == StepsReader.Source.Phone) startLive()
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = "Couldn't load your steps. Pull to try again.")
                }
        }
    }

    /**
     * While Home is on screen, follows the phone's step counter so the ring moves as you walk.
     * Saves to the server at most once a minute, and when Home leaves the screen.
     */
    private fun startLive() {
        if (live?.isActive == true) return
        live = viewModelScope.launch {
            reader.phone.counterUpdates().collect { counter ->
                val current = data ?: return@collect
                val today = LocalDate.now()
                val rows = reader.phoneRows(current.me.id, reader.phone.record(current.me.id, counter, today))
                val merged = current.copy(myDays = overlayDays(current.myDays, rows))
                data = merged
                _state.value = build(merged, today, connected)
                unsaved = rows.filter { it.day == today.toString() }
                if (System.currentTimeMillis() - lastUpload > UPLOAD_EVERY_MS) upload()
            }
        }
    }

    /** Stops following the counter and saves what's pending. Called when Home is paused. */
    fun stopLive() {
        live?.cancel()
        live = null
        upload()
    }

    private fun upload() {
        val rows = unsaved
        if (rows.isEmpty()) return
        unsaved = emptyList()
        lastUpload = System.currentTimeMillis()
        viewModelScope.launch {
            repo.saveDays(rows).onFailure { android.util.Log.w("StepqHealth", "Couldn't save steps", it) }
        }
    }

    private fun build(data: com.stepq.app.data.HomeData, today: LocalDate, connected: Boolean): HomeUiState {
        val goal = data.me.dailyGoal
        val todayRow = data.myDays.firstOrNull { it.day == today.toString() }
        val steps = todayRow?.steps ?: 0
        val week = weekBars(data.myDays, today)
        val mood = when {
            steps < goal -> PikoMood.Nudge
            // Kept per account, so a second account on the phone still gets its own cheer.
            prefs.getString("${KEY_CELEBRATED}_${data.me.id}", null) != today.toString() -> {
                prefs.edit().putString("${KEY_CELEBRATED}_${data.me.id}", today.toString()).apply()
                PikoMood.Cheer
            }
            else -> PikoMood.Happy
        }
        return HomeUiState(
            loading = false,
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)),
            greeting = greeting(LocalTime.now()),
            firstName = data.me.displayName.substringBefore(' '),
            sharedToday = data.streaks.orEmpty().values.any { it.sharedToday },
            streaks = streakCounts(data),
            score = data.score ?: com.stepq.app.data.goalDays(data.myDays, goal),
            steps = steps,
            goal = goal,
            km = (todayRow?.distanceM ?: 0) / 1000.0,
            kcal = todayRow?.calories ?: 0,
            activeMinutes = todayRow?.activeMinutes ?: 0,
            week = week,
            weekAverage = weekAverage(week),
            friends = friendRows(data, steps),
            hasFriends = data.friendsToday.isNotEmpty(),
            mood = mood,
            healthConnected = connected,
            shareTo = data.friendsToday.map { it.first }.sortedBy { it.displayName },
        )
    }

    private fun friendRows(data: com.stepq.app.data.HomeData, mySteps: Int): List<FriendRow> = friendsToday(
        me = "You" to data.me.avatar,
        mySteps = mySteps,
        friends = data.friendsToday.map { (p, s) -> Triple(p.displayName.substringBefore(' '), p.avatar, s) },
        size = 5,
        streaks = data.friendsToday.map { (p, _) -> data.streaks?.get(p.id)?.streak ?: 0 },
    )

    private fun streakCounts(data: com.stepq.app.data.HomeData): Map<String, Int> =
        data.streaks.orEmpty().mapValues { it.value.streak }.filterValues { it > 0 }

    /** Shares today's steps with [friendIds]; the first share of the day to a friend grows my streak with them. */
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
                    val updated = data?.let { it.copy(streaks = streaks ?: it.streaks) }
                    data = updated
                    // Only one friend picked: name my streak with them.
                    val only = friendIds.singleOrNull()
                    val withThem = only?.let { streaks?.get(it)?.streak } ?: 0
                    val firstToThemToday = only != null && sent > 0 && streaks != null && before?.get(only)?.sharedToday != true
                    _state.value = _state.value.copy(
                        sharing = false,
                        sharedToday = sent > 0 || s.sharedToday,
                        streaks = updated?.let { streakCounts(it) } ?: s.streaks,
                        friends = updated?.let { friendRows(it, _state.value.steps) } ?: s.friends,
                        toast = shareToast(names, s.steps, withThem, firstToday = firstToThemToday),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(sharing = false, toast = "Couldn't share your steps. Try again.")
                }
        }
    }

    fun toastShown() {
        _state.value = _state.value.copy(toast = null)
    }

    private companion object {
        const val KEY_CELEBRATED = "celebrated_day"
        const val UPLOAD_EVERY_MS = 60_000L
    }
}

