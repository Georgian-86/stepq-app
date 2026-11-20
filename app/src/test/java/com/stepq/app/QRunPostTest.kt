package com.stepq.app

import com.stepq.app.data.Place
import com.stepq.app.data.QRunsData
import com.stepq.app.ui.qrun.plannerSplit
import com.stepq.app.ui.qrun.postErrorMessage
import com.stepq.app.ui.qrun.withPosted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class QRunPostTest {
    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val starts = Instant.parse("2026-11-21T01:30:00Z")

    @Test fun postedQRunShowsInPlannerEvenIfTheListDidNotRefresh() {
        val data = withPosted(QRunsData("me", emptyList()), "q1", " Morning walk ", Place("Carter Road", 19.06, 72.82), starts, 0, now)
        val (upcoming, _) = plannerSplit(data.runs, "me", now)
        assertEquals(listOf("q1"), upcoming.map { it.id })
        assertEquals("Morning walk", upcoming[0].title)
        assertTrue(upcoming[0].joined)
        // Before the first load finished there's no list yet; the new QRun still shows.
        assertEquals(1, withPosted(null, "q1", "Walk", Place("Juhu"), starts, 0, now).runs.size)
    }

    @Test fun serverCopyWins() {
        val first = withPosted(QRunsData("me", emptyList()), "q1", "Walk", Place("Juhu"), starts, 0, now)
        val server = first.runs[0].copy(hostName = "Riya Shah")
        val again = withPosted(QRunsData("me", listOf(server)), "q1", "Walk", Place("Juhu"), starts, 0, now)
        assertEquals("Riya Shah", again.runs.single().hostName)
    }

    @Test fun postErrors() {
        assertEquals("Only StepQ can post paid QRuns. Set the price to 0.", postErrorMessage("Only StepQ can post paid QRuns."))
        assertEquals("Pick a time at least 15 minutes from now, then try again.", postErrorMessage("new row violates row-level security policy for table \"qruns\""))
        assertEquals("QRuns aren't set up on the server yet. Try again later.", postErrorMessage("PGRST106: The schema must be one of the following"))
        assertEquals("Couldn't post your QRun. Check the time and try again.", postErrorMessage(null))
    }
}
