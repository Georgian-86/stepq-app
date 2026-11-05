package com.stepq.app

import com.stepq.app.health.StepLedger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StepLedgerTest {
    private val boot = 1_000_000L
    private val mon = LocalDate.of(2026, 11, 2)
    private val tue = mon.plusDays(1)

    @Test fun firstReadingOnlySetsTheBaseline() {
        val l = StepLedger().record(5_000, boot, mon)
        assertEquals(0, l.days[mon.toString()])
        assertEquals(5_000L, l.lastValue)
    }

    @Test fun addsStepsSinceLastReading() {
        val l = StepLedger().record(5_000, boot, mon).record(6_200, boot + 500, mon).record(7_000, boot, mon)
        assertEquals(2_000, l.days[mon.toString()])
    }

    @Test fun stepsGoToTheDayOfTheReading() {
        val l = StepLedger().record(100, boot, mon).record(1_100, boot, mon).record(4_100, boot, tue)
        assertEquals(1_000, l.days[mon.toString()])
        assertEquals(3_000, l.days[tue.toString()])
    }

    @Test fun rebootRestartsTheCounter() {
        val l = StepLedger().record(100, boot, mon).record(9_000, boot, mon)
            .record(300, boot + 3_600_000, mon)
        assertEquals(9_200, l.days[mon.toString()])
    }

    @Test fun counterGoingBackwardsCountsAsReboot() {
        val l = StepLedger().record(8_000, boot, mon).record(8_500, boot, mon).record(200, boot, mon)
        assertEquals(700, l.days[mon.toString()])
    }

    @Test fun dropsOldDays() {
        var l = StepLedger().record(0, boot, mon)
        for (i in 1..20) l = l.record(i * 100L, boot, mon.plusDays(i.toLong()))
        assertEquals(14, l.days.size)
    }
}
