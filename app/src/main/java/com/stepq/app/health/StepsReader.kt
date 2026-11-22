package com.stepq.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.stepq.app.data.DailySteps
import com.stepq.app.data.stepsToKm
import com.stepq.app.ui.home.estimatedActiveMinutes
import java.time.LocalDate
import java.time.Period

/**
 * Where Home gets steps from. Health Connect when the user connected it (it merges watches and
 * other apps and has history); otherwise the phone's own step counter, which needs no extra app.
 */
class StepsReader(private val context: Context) {
    private val accounts = StepAccounts(context)
    val phone = PhoneStepCounter(context, accounts)

    enum class Source { HealthConnect, Phone, None }

    suspend fun source(): Source = when {
        HealthConnect.hasAllPermissions(context) -> Source.HealthConnect
        phone.isReady -> Source.Phone
        else -> Source.None
    }

    suspend fun readDays(userId: String, today: LocalDate, days: Int = 7): List<DailySteps> =
        when (source()) {
            Source.HealthConnect -> readHealthConnect(userId, today, days)
            Source.Phone -> readPhone(userId, today, days)
            Source.None -> emptyList()
        }

    private suspend fun readPhone(userId: String, today: LocalDate, days: Int): List<DailySteps> {
        val first = today.minusDays((days - 1).toLong()).toString()
        return phoneRows(userId, phone.sync(userId, today).filterKeys { it >= first })
    }

    /** Rows for days counted by the phone's step counter. */
    fun phoneRows(userId: String, days: Map<String, Int>): List<DailySteps> =
        days.map { (day, steps) ->
            DailySteps(
                userId = userId,
                day = day,
                steps = steps.coerceIn(0, 200_000),
                distanceM = (stepsToKm(steps) * 1000).toInt(),
                // Rough walking estimate; Health Connect gives real numbers when connected.
                calories = (steps * KCAL_PER_STEP).toInt(),
                // Estimate at a walking pace; Health Connect gives measured minutes.
                activeMinutes = estimatedActiveMinutes(steps),
            )
        }

    /**
     * Health Connect holds everything walked on this phone. An account that first signed in
     * here after another one only gets steps from that moment on (see [StepAccounts]).
     */
    private suspend fun readHealthConnect(userId: String, today: LocalDate, days: Int): List<DailySteps> {
        val client = HealthConnectClient.getOrCreate(context)
        val windows = HealthWindows.of(today, days, accounts.since(userId))
        val metrics = setOf(
            StepsRecord.COUNT_TOTAL,
            DistanceRecord.DISTANCE_TOTAL,
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
        )
        val rows = mutableListOf<DailySteps>()
        windows.partial?.let { (from, to) ->
            val r = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(from, to)))
            rows += row(userId, from.toLocalDate().toString(), r)
        }
        windows.wholeDaysFrom?.let { from ->
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(from, windows.end),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).forEach { b -> rows += row(userId, b.startTime.toLocalDate().toString(), b.result) }
        }
        return rows
    }

    private fun row(userId: String, day: String, r: AggregationResult) = DailySteps(
        userId = userId,
        day = day,
        steps = (r[StepsRecord.COUNT_TOTAL] ?: 0L).toInt().coerceIn(0, 200_000),
        distanceM = (r[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0).toInt(),
        calories = (r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toInt(),
        activeMinutes = (r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L)
            .toInt().coerceIn(0, 1440),
    )

    private companion object {
        const val KCAL_PER_STEP = 0.04
    }
}
