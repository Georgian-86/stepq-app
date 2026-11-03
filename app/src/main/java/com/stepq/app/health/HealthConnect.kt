package com.stepq.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord

/** A10: the read-only Health Connect permissions StepQ asks for. */
object HealthConnect {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    enum class Availability { Available, NeedsInstallOrUpdate, Unsupported }

    fun availability(context: Context): Availability =
        when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrNull()) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NeedsInstallOrUpdate
            else -> Availability.Unsupported
        }

    /**
     * False when Health Connect is missing, not set up yet, or refuses the call: a freshly
     * installed Health Connect can throw here until it has been opened once.
     */
    suspend fun hasAllPermissions(context: Context): Boolean {
        if (availability(context) != Availability.Available) return false
        return try {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                .containsAll(permissions)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("StepqHealth", "Couldn't read Health Connect permissions", e)
            false
        }
    }
}
