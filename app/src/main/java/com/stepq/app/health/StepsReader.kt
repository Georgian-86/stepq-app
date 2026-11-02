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
import java.time.LocalDate
import java.time.Period

/**
 * Reads per-day totals from Health Connect for the last [days] days, today included.
 * Health Connect merges every source (the phone's own counter, watches, Google Fit), and its
 * aggregate removes duplicates between them.
 */
class StepsReader(private val context: Context) {

    suspend fun readDays(userId: String, today: LocalDate, days: Int = 7): List<DailySteps> {
        if (!HealthConnect.hasAllPermissions(context)) return emptyList()
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
}
