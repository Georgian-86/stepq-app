package com.stepq.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * Reads the phone's own step counter (no extra app needed). Needs the "Physical activity"
 * permission on Android 10 and later. See [StepLedger] for how readings become days.
 *
 * Each StepQ account on the phone has its own ledger, so logging in to a different account
 * starts at 0 instead of carrying on with the previous account's steps (see [StepAccounts]).
 */
class PhoneStepCounter(context: Context, private val accounts: StepAccounts = StepAccounts(context)) {
    private val context = context.applicationContext
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val prefs = context.getSharedPreferences("phone_steps", Context.MODE_PRIVATE)

    val isAvailable: Boolean
        get() = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    val hasPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    val isReady: Boolean get() = isAvailable && hasPermission

    /** Takes a reading for [userId] and returns their steps per day for the days kept so far. */
    suspend fun sync(userId: String, today: LocalDate): Map<String, Int> {
        if (!isReady) return load(userId).days
        val counter = readCounter() ?: return load(userId).days
        return record(userId, counter, today)
    }

    /** Adds one counter value to [userId]'s ledger and returns their steps per day. */
    @Synchronized
    fun record(userId: String, counter: Long, today: LocalDate): Map<String, Int> {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        // Steps since the phone booted only count for the first account used on it. A later
        // account starts from its first reading, so it doesn't inherit someone else's walk.
        val firstAccount = accounts.since(userId) == 0L
        val bootedToday = firstAccount &&
            Instant.ofEpochMilli(bootTime).atZone(ZoneId.systemDefault()).toLocalDate() == today
        val ledger = load(userId).record(counter, bootTime, today, bootedToday)
        save(userId, ledger)
        return ledger.days
    }

    /** Every new counter value while collected (a few per second while walking). */
    fun counterUpdates(): Flow<Long> = callbackFlow {
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null || !hasPermission) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toLong())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching { sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
            .getOrDefault(false)
        if (!ok) close()
        awaitClose { sensors.unregisterListener(listener) }
    }

    /** The counter's current total. The sensor sends its value soon after a listener joins. */
    private suspend fun readCounter(): Long? {
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensors.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val ok = runCatching {
                    sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }.getOrElse {
                    Log.w("StepqSteps", "Couldn't listen to the step counter", it)
                    false
                }
                if (!ok && cont.isActive) cont.resume(null)
                cont.invokeOnCancellation { sensors.unregisterListener(listener) }
            }
        }
    }

    private fun load(userId: String): StepLedger {
        val json = prefs.getString(keyFor(userId), null) ?: adoptLegacy(userId) ?: return StepLedger()
        return runCatching {
            val o = JSONObject(json)
            val days = o.getJSONObject("days").let { d -> d.keys().asSequence().associateWith { d.getInt(it) } }
            StepLedger(
                lastValue = if (o.has("last")) o.getLong("last") else null,
                bootTimeMs = o.optLong("boot"),
                days = days,
            )
        }.getOrDefault(StepLedger())
    }

    /**
     * Before ledgers were kept per account there was one for the whole phone. It belongs to the
     * first account that counts here after the update; any other account starts fresh.
     */
    private fun adoptLegacy(userId: String): String? {
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return null
        if (accounts.since(userId) != 0L) return null
        prefs.edit().putString(keyFor(userId), legacy).remove(LEGACY_KEY).apply()
        return legacy
    }

    private fun keyFor(userId: String) = "ledger_$userId"

    private fun save(userId: String, ledger: StepLedger) {
        val o = JSONObject()
            .put("boot", ledger.bootTimeMs)
            .put("days", JSONObject(ledger.days))
        ledger.lastValue?.let { o.put("last", it) }
        prefs.edit().putString(keyFor(userId), o.toString()).apply()
    }

    private companion object {
        const val LEGACY_KEY = "ledger"
        const val READ_TIMEOUT_MS = 3_000L
    }
}
