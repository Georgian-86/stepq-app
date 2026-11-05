package com.stepq.app.health

import java.time.LocalDate

/**
 * Turns readings of the phone's step counter into steps per day.
 *
 * Android's step counter is a running total since the phone last booted, kept by the
 * phone's motion chip even while StepQ is closed. Each time StepQ reads it, the steps since
 * the previous reading are added to the day of the new reading.
 *
 * @property lastValue the counter at the previous reading, or null before the first one.
 * @property bootTimeMs when the phone booted, at the previous reading.
 * @property days steps per ISO day.
 */
data class StepLedger(
    val lastValue: Long? = null,
    val bootTimeMs: Long = 0,
    val days: Map<String, Int> = emptyMap(),
) {
    /**
     * Adds a reading. [counter] is the sensor's total since boot, [bootTimeMs] is when this
     * boot started (wall clock minus uptime). The first reading only sets the baseline, since
     * the total may include steps from earlier days, unless [bootedToday].
     */
    fun record(counter: Long, bootTimeMs: Long, today: LocalDate, bootedToday: Boolean = false): StepLedger {
        val previous = lastValue
        val rebooted = previous != null &&
            (counter < previous || kotlin.math.abs(bootTimeMs - this.bootTimeMs) > BOOT_TOLERANCE_MS)
        val delta = when {
            // First reading: the total is all today's if the phone was switched on today.
            previous == null -> if (bootedToday) counter else 0L
            // After a reboot the counter starts again at 0, so everything it shows is new.
            rebooted -> counter
            else -> counter - previous
        }.coerceIn(0L, MAX_STEPS_PER_READING)
        val key = today.toString()
        val updated = (days + (key to ((days[key] ?: 0) + delta.toInt())))
            .filterKeys { it >= today.minusDays(KEEP_DAYS - 1).toString() }
        return StepLedger(lastValue = counter, bootTimeMs = bootTimeMs, days = updated)
    }

    companion object {
        /** Wall clock minus uptime jitters a little; a real reboot moves it by far more. */
        const val BOOT_TOLERANCE_MS = 60_000L
        const val MAX_STEPS_PER_READING = 200_000L
        const val KEEP_DAYS = 14L
    }
}
