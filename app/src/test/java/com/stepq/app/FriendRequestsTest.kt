package com.stepq.app

import com.stepq.app.data.FriendsRepository
import com.stepq.app.data.PersonSummary
import com.stepq.app.push.InboxRequest
import com.stepq.app.push.requestMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendRequestsTest {
    private val meera = PersonSummary("m", "Meera Iyer", "meera")

    @Test fun requestsShowEvenWithoutNames() {
        // Names loaded: use them.
        assertEquals(listOf(meera), FriendsRepository.requestsWithFallback(listOf(meera), listOf("m")))
        // The names function is missing on the server: the request still shows, so it can be accepted.
        val fallback = FriendsRepository.requestsWithFallback(null, listOf("m", "k"))
        assertEquals(listOf("m", "k"), fallback.map { it.id })
        assertEquals(FriendsRepository.UNKNOWN_SENDER, fallback[0].displayName)
        // A pending row the named list missed is added too.
        assertEquals(listOf("m", "k"), FriendsRepository.requestsWithFallback(listOf(meera), listOf("m", "k")).map { it.id })
    }

    @Test fun requestNotification() {
        assertNull(requestMessage(emptyList()))
        val one = listOf(InboxRequest("m", "Meera Iyer", "2026-11-21T08:50:00+00:00"))
        assertEquals("Meera wants to be your friend", requestMessage(one)!!.first)
        val two = one + InboxRequest("k", "Kabir Rao", "2026-11-21T08:40:00+00:00")
        assertEquals("Meera and 1 more want to be your friends", requestMessage(two)!!.first)
    }
}
