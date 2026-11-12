package com.stepq.app

import com.stepq.app.data.FriendToday
import com.stepq.app.data.FriendsData
import com.stepq.app.data.PersonSummary
import com.stepq.app.data.Profile
import com.stepq.app.ui.friends.Relation
import com.stepq.app.ui.friends.relationTo
import com.stepq.app.ui.friends.reminderSentMessage
import com.stepq.app.ui.friends.remindersBanner
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendsLogicTest {
    private val me = Profile(id = "me", displayName = "Riya", username = "riya.walks")
    private fun person(id: String) = PersonSummary(id, id, id)
    private val data = FriendsData(
        me = me,
        board = listOf(FriendToday(person("me"), 100, 0, true), FriendToday(person("friend"), 50, 0, false)),
        requests = listOf(person("asker")),
        sentRequestIds = setOf("asked"),
        remindedToday = emptySet(),
        remindersLeft = 5,
    )

    @Test fun relations() {
        assertEquals(Relation.Me, relationTo("me", data))
        assertEquals(Relation.Friend, relationTo("friend", data))
        assertEquals(Relation.Requested, relationTo("asked", data))
        assertEquals(Relation.AsksYou, relationTo("asker", data))
        assertEquals(Relation.None, relationTo("stranger", data))
    }

    @Test fun reminderMessages() {
        assertEquals("Water reminder sent to Meera. 4 left today.", reminderSentMessage("Meera Rao", 4))
        assertEquals("Water reminder sent to Meera. That's all 5 for today.", reminderSentMessage("Meera", 0))
        assertEquals("Water reminder sent to Meera.", reminderSentMessage("Meera", null))
        assertEquals("Water reminders left today: 5 of 5 · Free plan", remindersBanner(5))
    }

    @Test fun groupCopy() {
        assertEquals("Give your group a name.", com.stepq.app.ui.friends.groupNameProblem("  "))
        assertEquals(null, com.stepq.app.ui.friends.groupNameProblem("Morning walkers"))
        assertEquals("5 members · 32,410 steps together today", com.stepq.app.ui.friends.groupSummary(5, 32410))
        assertEquals("1 member · 0 steps together today", com.stepq.app.ui.friends.groupSummary(1, 0))
    }
}
