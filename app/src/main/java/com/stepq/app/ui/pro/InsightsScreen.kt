package com.stepq.app.ui.pro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stepq.app.data.HistoryDay
import com.stepq.app.data.MyPlan
import com.stepq.app.data.Pro
import com.stepq.app.ui.components.NightCard
import com.stepq.app.ui.components.ProTag
import com.stepq.app.ui.onboarding.formatSteps
import com.stepq.app.ui.theme.ProColors
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType
import java.time.LocalDate

/** Summary numbers for a stretch of days. */
data class InsightStats(val average: Int, val goalDays: Int, val days: Int, val best: Int, val km: Int, val bestWeekday: String?)

fun insightStats(history: List<HistoryDay>, fallbackGoal: Int, span: Int): InsightStats {
    val avg = if (span > 0) history.sumOf { it.steps } / span else 0
    val best = history.maxByOrNull { it.steps }
    val weekday = history.filter { it.steps > 0 }
        .groupBy { runCatching { LocalDate.parse(it.day).dayOfWeek }.getOrNull() }
        .filterKeys { it != null }
        .maxByOrNull { (_, v) -> v.sumOf { it.steps } / v.size }?.key
    return InsightStats(
        average = avg,
        goalDays = history.count { it.steps >= (it.goal ?: fallbackGoal) },
        days = span,
        best = best?.steps ?: 0,
        km = history.sumOf { it.distanceM } / 1000,
        bestWeekday = weekday?.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
    )
}

/**
 * Insights: my steps over 7, 30, 90 days or a year. How far back I can go is my plan's
 * analysis_days (the server caps it too); longer ranges show a Pro tag.
 */
@Composable
fun InsightsScreen(
    plan: MyPlan?,
    goal: Int,
    onBack: () -> Unit,
    onLocked: () -> Unit,
    load: suspend (Int) -> List<HistoryDay> = { Pro.history(it).getOrDefault(emptyList()) },
) {
    BackHandler(onBack = onBack)
    val allowed = plan?.limit(MyPlan.F.ANALYSIS_DAYS).let { if (plan?.feature(MyPlan.F.ANALYSIS_DAYS) == null) 7 else it }
    val ranges = listOf(7 to "7D", 30 to "30D", 90 to "90D", 365 to "1Y")
    var span by rememberSaveable { mutableStateOf(if ((allowed ?: 365) >= 30) 30 else 7) }
    var history by remember { mutableStateOf<List<HistoryDay>?>(null) }
    LaunchedEffect(span) {
        history = null
        history = load(span)
    }
    Column(
        Modifier.fillMaxSize().background(StepqColors.White).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = StepqColors.Ink) }
            Text("Insights", style = StepqType.Title, color = StepqColors.Ink)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranges.forEach { (days, label) ->
                val locked = allowed != null && days > allowed
                val on = span == days
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) ProColors.goldFlat else Brush.horizontalGradient(listOf(StepqColors.Mint, StepqColors.Mint)))
                        .clickable(role = Role.RadioButton) { if (locked) onLocked() else span = days }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = StepqType.SmallStrong, color = StepqColors.Ink)
                    if (locked) {
                        Spacer(Modifier.width(6.dp))
                        ProTag()
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val h = history.orEmpty()
        val stats = insightStats(h, goal, span)
        NightCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DAILY AVERAGE · ${if (span == 365) "1 YEAR" else "$span DAYS"}", style = StepqType.Label, color = ProColors.GoldOnNight)
                Text(if (history == null) "…" else "${formatSteps(stats.average)} steps", style = StepqType.NumberL, color = StepqColors.White)
                Bars(h, span, goal)
                Text("Gold bars: goal reached", style = StepqType.Small, color = ProColors.OnNightMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("${stats.goalDays}/${stats.days}", "goal days", Modifier.weight(1f))
            Tile(formatSteps(stats.best), "best day", Modifier.weight(1f))
            Tile("${formatSteps(stats.km)} km", "walked", Modifier.weight(1f))
        }
        stats.bestWeekday?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                "Your strongest day is $it. Keep it going, and add a short walk on your quieter days.",
                style = StepqType.Small,
                color = StepqColors.Ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StepqColors.LeafSoft).padding(14.dp),
            )
        }
        if (allowed != null && allowed < 365) {
            Spacer(Modifier.height(12.dp))
            Text("Your plan shows the last ${historyLabel(allowed)}. Pro shows up to a year.", style = StepqType.Small, color = StepqColors.Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bars(history: List<HistoryDay>, span: Int, goal: Int) {
    val today = LocalDate.now()
    val byDay = history.associateBy { it.day }
    // A year is shown as weeks so the bars stay readable.
    val values: List<Pair<Int, Boolean>> = if (span > 90) {
        (0 until 52).map { w ->
            val days = (0 until 7).map { today.minusDays((51 - w) * 7L + (6 - it)) }
            val rows = days.mapNotNull { byDay[it.toString()] }
            val avg = if (rows.isEmpty()) 0 else rows.sumOf { it.steps } / 7
            avg to (avg >= goal)
        }
    } else {
        (0 until span).map { i ->
            val d = today.minusDays((span - 1 - i).toLong()).toString()
            val r = byDay[d]
            (r?.steps ?: 0) to ((r?.steps ?: 0) >= (r?.goal ?: goal))
        }
    }
    val max = (values.maxOfOrNull { it.first } ?: 0).coerceAtLeast(goal).toFloat()
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Steps per day, ${values.count { it.second }} days at goal" },
    ) {
        val gap = if (values.size > 40) 1.5f else 3f
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, (v, hit) ->
            val bh = if (v == 0) 3f else (v / max) * size.height
            val x = i * (w + gap)
            drawRoundRect(
                brush = if (hit) Brush.verticalGradient(listOf(ProColors.Gold1, ProColors.Gold3)) else Brush.verticalGradient(listOf(StepqColors.White.copy(alpha = 0.28f), StepqColors.White.copy(alpha = 0.18f))),
                topLeft = Offset(x, size.height - bh),
                size = Size(w, bh),
                cornerRadius = CornerRadius(w / 3, w / 3),
            )
        }
        val gy = size.height - (goal / max) * size.height
        drawLine(ProColors.Gold2.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f)
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(StepqColors.Page).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = StepqType.NumberM, color = StepqColors.Ink)
        Text(label, style = StepqType.Small, color = StepqColors.Muted)
    }
}
