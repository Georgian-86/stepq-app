package com.stepq.app.ui.qrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stepq.app.R
import com.stepq.app.data.Place
import com.stepq.app.data.QRun
import com.stepq.app.ui.components.SectionLabel
import com.stepq.app.ui.components.StepqAvatar
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqChip
import com.stepq.app.ui.components.UnderlineField
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqDimens
import com.stepq.app.ui.theme.StepqType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A18: QRuns near you, nearest first, with Join and the + to post one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRunList(
    state: QRunUiState,
    locationAllowed: Boolean,
    onRefresh: () -> Unit,
    onAllowLocation: () -> Unit,
    onJoin: (QRun) -> Unit,
    onOpenPlanner: () -> Unit,
    onPost: () -> Unit,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val data = state.data
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !locationAllowed, role = Role.Button, onClick = onAllowLocation),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.NearMe, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                !locationAllowed -> "Allow location to see QRuns near you"
                                state.areaName != null -> "Near ${state.areaName} · nearest first"
                                state.here != null -> "Near you · nearest first"
                                else -> "Finding where you are…"
                            },
                            style = StepqType.SmallStrong,
                            color = StepqColors.Deep,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val mine = data?.let { plannerSplit(it.runs, it.myId, now).first.size } ?: 0
                    if (mine > 0) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(StepqColors.Mint)
                                .clickable(role = Role.Button, onClick = onOpenPlanner)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = StepqColors.Ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("My plans · $mine", style = StepqType.SmallStrong, color = StepqColors.Ink)
                        }
                    }
                }
                when {
                    data == null && state.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = StepqColors.Deep)
                    }
                    data == null -> Text(state.error ?: "Couldn't load QRuns.", style = StepqType.Body, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 16.dp))
                    else -> {
                        state.error?.let { Text(it, style = StepqType.Small, color = StepqColors.TomatoText, modifier = Modifier.padding(top = 8.dp)) }
                        val runs = listOrder(data.runs, state.here, now)
                        runs.forEach { run ->
                            Spacer(Modifier.height(14.dp))
                            QRunCard(run, run.distanceFrom(state.here), hosting = run.createdBy == data.myId, now, zone, { onJoin(run) }, onOpenPlanner)
                        }
                        if (runs.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.foundation.Image(
                                    painterResource(R.drawable.piko_nudge),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("No QRuns coming up yet", style = StepqType.Heading, color = StepqColors.Ink)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Post a walk or run, pick a public place and a time, and anyone on StepQ can join you.",
                                    style = StepqType.Body,
                                    color = StepqColors.Muted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(StepqColors.Deep)
                .clickable(onClickLabel = "Post a QRun", role = Role.Button, onClick = onPost),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Post a QRun", tint = StepqColors.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun QRunCard(run: QRun, km: Double?, hosting: Boolean, now: Instant, zone: ZoneId, onJoin: () -> Unit, onOpenPlanner: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .border(BorderStroke(1.5.dp, StepqColors.Line), RoundedCornerShape(StepqDimens.RadiusM))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(StepqColors.Mint), contentAlignment = Alignment.Center) {
                StepqAvatar(run.hostAvatar, 34.dp)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    if (hosting) "You posted" else "${run.hostName.substringBefore(' ')} posted",
                    style = StepqType.SmallStrong,
                    color = StepqColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(postedAgo(run.createdAt, now), style = StepqType.Small, color = StepqColors.Muted)
            }
            if (km != null) {
                Text(
                    kmLabel(km),
                    style = StepqType.SmallStrong,
                    color = StepqColors.Deep,
                    modifier = Modifier.clip(CircleShape).background(StepqColors.Mint).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(run.title, style = StepqType.Subheading, color = StepqColors.Ink)
        Spacer(Modifier.height(8.dp))
        IconLine(Icons.Outlined.LocationOn, run.placeName)
        Spacer(Modifier.height(6.dp))
        IconLine(Icons.Outlined.Event, cardWhen(run.startsAt, zone))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(goingLabel(run.going), style = StepqType.Small, color = StepqColors.Muted, modifier = Modifier.weight(1f))
            when {
                hosting -> SmallButton("Hosting", filled = false, onClick = onOpenPlanner)
                run.joined -> SmallButton("Going", filled = false, check = true, onClick = onOpenPlanner)
                else -> SmallButton("Join", filled = true, onClick = onJoin)
            }
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = StepqColors.Ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = StepqColors.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = StepqType.Small, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, check: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (filled) StepqColors.Deep else StepqColors.Mint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = StepqType.Button, color = if (filled) StepqColors.White else StepqColors.Deep)
    }
}

/** Times offered on A19; "Custom" opens the clock. */
val QUICK_TIMES = listOf(LocalTime.of(5, 30), LocalTime.of(6, 0), LocalTime.of(6, 30), LocalTime.of(18, 0))

/** A19: name it, where, date and time. */
@Composable
fun PostQRunScreen(
    places: PlaceSearch,
    here: LatLng?,
    busy: Boolean,
    error: String?,
    onPost: (title: String, place: Place, date: LocalDate?, time: LocalTime?) -> Unit,
    onTyping: () -> Unit,
    onPickCustomTime: ((LocalTime) -> Unit) -> Unit,
    onClose: () -> Unit,
    today: LocalDate = LocalDate.now(),
    initialPlace: String = "",
    initialSuggestions: List<Place> = emptyList(),
) {
    BackHandler(onBack = onClose)
    var title by rememberSaveable { mutableStateOf("") }
    var placeText by rememberSaveable { mutableStateOf(initialPlace) }
    var picked by remember { mutableStateOf<Place?>(null) }
    var suggestions by remember { mutableStateOf(initialSuggestions) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var time by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }

    LaunchedEffect(placeText, picked) {
        if (picked != null && picked?.name == placeText) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        if (placeText == initialPlace && initialSuggestions.isNotEmpty()) return@LaunchedEffect
        delay(450)
        suggestions = places.search(placeText, here)
    }

    Column(Modifier.fillMaxSize().background(StepqColors.White).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            IconButton(onClick = onClose, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = StepqColors.Ink)
            }
            Text("Post a QRun", style = StepqType.Title, color = StepqColors.Ink)
            Spacer(Modifier.height(16.dp))
            UnderlineField(
                "What",
                title,
                {
                    title = it.take(60)
                    onTyping()
                },
                placeholder = "e.g. Morning run, easy pace",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(18.dp))
            UnderlineField(
                "Where",
                placeText,
                {
                    placeText = it.take(120)
                    picked = null
                    onTyping()
                },
                placeholder = "A public place, e.g. Carter Road",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StepqDimens.RadiusS))
                        .background(StepqColors.Mint)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    suggestions.forEachIndexed { i, p ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(StepqColors.Line))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    picked = p
                                    placeText = p.name
                                    onTyping()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = if (i == 0) StepqColors.Deep else StepqColors.Muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, style = if (i == 0) StepqType.SmallStrong else StepqType.Small, color = StepqColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            MonthCalendar(month, today, date, onMonth = { month = it }) {
                date = it
                onTyping()
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Time")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_TIMES.forEach { t ->
                    StepqChip(timeLabel(t), selected = time == t, onClick = {
                        time = t
                        onTyping()
                    })
                }
                val custom = time?.takeIf { it !in QUICK_TIMES }
                StepqChip(custom?.let(::timeLabel) ?: "Custom", selected = custom != null, onClick = {
                    onPickCustomTime { t ->
                        time = t
                        onTyping()
                    }
                })
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                error ?: "Meet somewhere public. Past dates are greyed out and can't be picked.",
                style = StepqType.Small,
                color = if (error != null) StepqColors.TomatoText else StepqColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            StepqButton(
                "Post QRun",
                { onPost(title, picked?.takeIf { it.name == placeText } ?: Place(placeText.trim()), date, time) },
                enabled = title.isNotBlank() && placeText.isNotBlank() && date != null && time != null,
                loading = busy,
            )
        }
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
private fun MonthCalendar(month: YearMonth, today: LocalDate, selected: LocalDate?, onMonth: (YearMonth) -> Unit, onPick: (LocalDate) -> Unit) {
    val first = YearMonth.from(today)
    val last = YearMonth.from(today.plusDays(QRUN_MAX_DAYS_AHEAD))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Date", Modifier.weight(1f))
        IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > first, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month", tint = if (month > first) StepqColors.Ink else StepqColors.Line)
        }
        Text(monthTitle.format(month), style = StepqType.SmallStrong, color = StepqColors.Ink)
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < last, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month", tint = if (month < last) StepqColors.Ink else StepqColors.Line)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, style = StepqType.Small, color = StepqColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    monthCells(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            week.forEach { day ->
                Box(Modifier.weight(1f).aspectRatio(1.45f), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val can = pickable(day, today)
                        val on = day == selected
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (on) StepqColors.Deep else Color.Transparent)
                                .then(if (day == today && !on) Modifier.border(BorderStroke(1.5.dp, StepqColors.Deep), CircleShape) else Modifier)
                                .clickable(enabled = can, role = Role.Button) { onPick(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = if (on) StepqType.BodyStrong else StepqType.Body,
                                color = when {
                                    on -> StepqColors.White
                                    can -> StepqColors.Ink
                                    else -> StepqColors.Line
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A20: the safety note you agree to before joining. */
@Composable
fun JoinQRunSheet(run: QRun, busy: Boolean, onJoin: () -> Unit, onCancel: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(StepqColors.MangoSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = StepqColors.Ink)
        }
        Spacer(Modifier.height(14.dp))
        Text("Before you join", style = StepqType.Heading, color = StepqColors.Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "QRuns are posted by people on StepQ. StepQ doesn't check these events and isn't responsible for them. " +
                "Meet in public places, tell someone where you're going, and join at your own risk.",
            style = StepqType.Body,
            color = StepqColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StepqDimens.RadiusS))
                .background(StepqColors.Mint)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = StepqColors.Deep, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(sheetSummary(run, zone), style = StepqType.SmallStrong, color = StepqColors.Ink)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Checkbox) { agreed = !agreed }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (agreed) StepqColors.Deep else StepqColors.White)
                    .border(BorderStroke(1.5.dp, if (agreed) StepqColors.Deep else StepqColors.Line), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (agreed) Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("I understand and want to join", style = StepqType.Body, color = StepqColors.Ink)
        }
        Spacer(Modifier.height(14.dp))
        StepqButton("Join QRun", onJoin, enabled = agreed, loading = busy)
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) {
            Text("Cancel", style = StepqType.SmallStrong, color = StepqColors.Deep)
        }
    }
}

/** A21: the QRuns I'm going to or hosting, with calendar and directions. */
@Composable
fun QRunPlanner(
    state: QRunUiState,
    notificationsOn: Boolean,
    onAddToCalendar: (QRun) -> Unit,
    onDirections: (QRun) -> Unit,
    onLeave: (QRun) -> Unit,
    onCancelRun: (QRun) -> Unit,
    onBack: () -> Unit,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    BackHandler(onBack = onBack)
    var past by rememberSaveable { mutableStateOf(false) }
    val data = state.data
    val (upcoming, done) = data?.let { plannerSplit(it.runs, it.myId, now) } ?: (emptyList<QRun>() to emptyList())
    val banner = state.justJoined?.let { id -> data?.runs?.firstOrNull { it.id == id } }
    Column(
        Modifier
            .fillMaxSize()
            .background(StepqColors.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink)
        }
        if (banner != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(StepqColors.White)
                    .padding(14.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(StepqColors.Leaf), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.piko_cheer), contentDescription = null, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("StepQ · now", style = StepqType.Small, color = StepqColors.Muted)
                    Text(joinedMessage(banner, zone, notificationsOn), style = StepqType.SmallStrong, color = StepqColors.Ink)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Text("Event planner", style = StepqType.Title, color = StepqColors.Ink)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepqChip("Upcoming", selected = !past, onClick = { past = false })
            StepqChip("Past", selected = past, onClick = { past = true })
        }
        val list = if (past) done else upcoming
        list.forEach { run ->
            Spacer(Modifier.height(14.dp))
            PlannerCard(run, hosting = run.createdBy == data?.myId, past, zone, onAddToCalendar, onDirections, onLeave, onCancelRun)
        }
        if (list.isEmpty()) {
            Text(
                if (past) "QRuns you've been to will show here." else "Join a QRun or post your own and it'll show here.",
                style = StepqType.Body,
                color = StepqColors.Muted,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PlannerCard(
    run: QRun,
    hosting: Boolean,
    past: Boolean,
    zone: ZoneId,
    onAddToCalendar: (QRun) -> Unit,
    onDirections: (QRun) -> Unit,
    onLeave: (QRun) -> Unit,
    onCancelRun: (QRun) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StepqDimens.RadiusM))
            .background(StepqColors.Mint)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plannerWhen(run.startsAt, zone), style = StepqType.SmallStrong, color = StepqColors.Deep, modifier = Modifier.weight(1f))
            Text(
                when {
                    hosting && past -> "Hosted"
                    hosting -> "Hosting"
                    past -> "Went"
                    else -> "Going"
                },
                style = StepqType.SmallStrong,
                color = StepqColors.Ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (hosting) StepqColors.MangoSoft else StepqColors.LeafSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(run.title, style = StepqType.Subheading, color = StepqColors.Ink)
        Spacer(Modifier.height(6.dp))
        IconLine(
            Icons.Outlined.LocationOn,
            "${shortPlace(run.placeName)} · Hosted by ${if (hosting) "you" else run.hostName.substringBefore(' ')}",
            StepqColors.Muted,
        )
        if (past) {
            Spacer(Modifier.height(6.dp))
            Text(goingLabel(run.going).replace("going", "went"), style = StepqType.Small, color = StepqColors.Muted)
        } else {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("Add to calendar", Modifier.weight(1f)) { onAddToCalendar(run) }
                OutlinePill("Directions", Modifier.weight(1f)) { onDirections(run) }
            }
            TextButton(
                onClick = { if (!confirm) confirm = true else if (hosting) onCancelRun(run) else onLeave(run) },
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    when {
                        confirm && hosting -> "Tap again to cancel it for everyone"
                        confirm -> "Tap again to leave"
                        hosting -> "Cancel this QRun"
                        else -> "Can't make it? Leave"
                    },
                    style = StepqType.SmallStrong,
                    color = StepqColors.TomatoText,
                )
            }
            if (confirm) {
                LaunchedEffect(Unit) {
                    delay(4_000)
                    confirm = false
                }
            }
        }
    }
}

@Composable
private fun OutlinePill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(StepqColors.White)
            .border(BorderStroke(1.5.dp, StepqColors.Line), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StepqType.BodyStrong, color = StepqColors.Ink, maxLines = 1)
    }
}
