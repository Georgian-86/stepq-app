package com.stepq.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.stepq.app.data.DailySteps
import com.stepq.app.data.stepsToKm
import java.time.LocalDate
import java.time.Period

/**
 * Where Home gets steps from. Health Connect when the user connected it (it merges watches and
 * other apps and has history); otherwise the phone's own step counter, which needs no extra app.
 */
class StepsReader(private val context: Context) {
    val phone = PhoneStepCounter(context)

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
        return phoneRows(userId, phone.sync(today).filterKeys { it >= first })
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
                // Estimate at a brisk walking pace; Health Connect gives measured minutes.
                activeMinutes = (steps / STEPS_PER_ACTIVE_MINUTE).coerceIn(0, 1440),
            )
        }

    private suspend fun readHealthConnect(userId: String, today: LocalDate, days: Int): List<DailySteps> {
        val client = HealthConnectClient.getOrCreate(context)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return buckets.map { b ->
            DailySteps(
                userId = userId,
                day = b.startTime.toLocalDate().toString(),
                steps = (b.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt().coerceIn(0, 200_000),
                distanceM = (b.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0).toInt(),
                calories = (b.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toInt(),
                activeMinutes = (b.result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L)
                    .toInt().coerceIn(0, 1440),
            )
        }
    }

    private companion object {
        const val KCAL_PER_STEP = 0.04
        const val STEPS_PER_ACTIVE_MINUTE = 100
    }
}
