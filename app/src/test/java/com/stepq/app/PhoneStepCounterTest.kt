package com.stepq.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stepq.app.health.PhoneStepCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.time.LocalDate

/** Simulates a phone with a step counter: the sensor sends totals, Home gets steps per day. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PhoneStepCounterTest {
    private lateinit var app: Application
    private lateinit var sensors: ShadowSensorManager
    private val today = LocalDate.now()

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        sensors = shadowOf(app.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }

    private lateinit var sensor: Sensor

    private fun addStepSensor() {
        sensor = ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER)
        sensors.addSensor(sensor)
    }

    private fun send(total: Float) {
        sensors.sendSensorEventToListeners(SensorEventBuilder.newBuilder(sensor, floatArrayOf(total)).build())
    }

    @Test fun notReadyWithoutPermission() {
        addStepSensor()
        val counter = PhoneStepCounter(app)
        assertTrue(counter.isAvailable)
        assertFalse(counter.isReady)
    }

    @Test fun notAvailableWithoutSensor() {
        assertFalse(PhoneStepCounter(app).isAvailable)
    }

    @Test fun walkingAddsStepsLive() = runBlocking {
        addStepSensor()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val counter = PhoneStepCounter(app)
        assertTrue(counter.isReady)

        val values = mutableListOf<Long>()
        val job = launch(Dispatchers.Unconfined) { counter.counterUpdates().take(4).toList(values) }
        yield()
        // Robolectric's clock boots "today", so the first total counts as today's steps.
        listOf(1_000f, 1_010f, 1_050f, 1_200f).forEach { send(it) }
        job.join()
        assertEquals(listOf(1_000L, 1_010L, 1_050L, 1_200L), values)

        var days = emptyMap<String, Int>()
        values.forEach { days = counter.record(ME, it, today) }
        assertEquals(1_200, days[today.toString()])
        assertTrue(sensors.listeners.isEmpty())

        // A second phone session (same ledger) keeps adding on top.
        days = PhoneStepCounter(app).record(ME, 1_500, today)
        assertEquals(1_500, days[today.toString()])
    }

    @Test fun anotherAccountOnThePhoneStartsAtZero() {
        addStepSensor()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val counter = PhoneStepCounter(app)
        counter.record(ME, 1_000, today)
        assertEquals(4_000, counter.record(ME, 4_000, today)[today.toString()])

        // Log out, a friend logs in on the same phone: their day starts at 0.
        assertEquals(0, counter.record(FRIEND, 4_100, today)[today.toString()])
        assertEquals(500, counter.record(FRIEND, 4_600, today)[today.toString()])

        // Back to the first account: only what was walked while it was signed in counts.
        assertEquals(4_000, PhoneStepCounter(app).record(ME, 4_000, today)[today.toString()])
    }

    @Test fun theOldSharedLedgerGoesToTheFirstAccountOnly() {
        addStepSensor()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val boot = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        app.getSharedPreferences("phone_steps", Context.MODE_PRIVATE).edit()
            .putString("ledger", """{"last":2000,"boot":$boot,"days":{"$today":2000}}""").commit()
        val counter = PhoneStepCounter(app)
        assertEquals(2_000, counter.record(ME, 2_000, today)[today.toString()])
        assertEquals(0, counter.record(FRIEND, 2_000, today)[today.toString()])
    }

    private companion object {
        const val ME = "user-a"
        const val FRIEND = "user-b"
    }
}
