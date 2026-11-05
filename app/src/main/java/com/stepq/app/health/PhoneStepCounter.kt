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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate
import kotlin.coroutines.resume

/**
 * Reads the phone's own step counter (no extra app needed). Needs the "Physical activity"
 * permission on Android 10 and later. See [StepLedger] for how readings become days.
 */
class PhoneStepCounter(context: Context) {
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

    /** Takes a reading and returns steps per day for the days kept so far. */
    suspend fun sync(today: LocalDate): Map<String, Int> {
        var ledger = load()
        if (isReady) {
            val counter = readCounter()
            if (counter != null) {
                val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
                ledger = ledger.record(counter, bootTime, today)
                save(ledger)
            }
        }
        return ledger.days
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

    private fun load(): StepLedger {
        val json = prefs.getString(KEY, null) ?: return StepLedger()
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

    private fun save(ledger: StepLedger) {
        val o = JSONObject()
            .put("boot", ledger.bootTimeMs)
            .put("days", JSONObject(ledger.days))
        ledger.lastValue?.let { o.put("last", it) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "ledger"
        const val READ_TIMEOUT_MS = 3_000L
    }
}
