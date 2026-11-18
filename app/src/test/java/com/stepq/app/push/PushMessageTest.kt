package com.stepq.app.push

import com.stepq.app.ui.main.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageTest {
    @Test fun readsTheServersDataMessage() {
        val m = PushMessage.from(
            mapOf(
                "title" to "Sooo close, Riya! 🔥",
                "body" to "Only 1,760 steps left.",
                "channel" to "nudges",
                "kind" to "evening_close",
                "route" to "challenges",
                "image" to "https://stepq.in/banner.png",
            ),
        )!!
        assertEquals("Sooo close, Riya! 🔥", m.title)
        assertEquals(PushChannels.NUDGES, m.channel)
        assertEquals("evening_close", m.kind)
        assertEquals("challenges", m.route)
        assertEquals("https://stepq.in/banner.png", m.image)
    }

    @Test fun fallsBackSafely() {
        assertNull(PushMessage.from(emptyMap()))
        val m = PushMessage.from(mapOf("channel" to "spam", "image" to "http://x/y.png"), "Hi", "There")!!
        assertEquals("Hi", m.title)
        assertEquals("There", m.body)
        assertEquals(PushChannels.NEWS, m.channel)
        assertNull(m.image)
        assertEquals("home", m.route)
    }

    @Test fun routesOpenTheRightTab() {
        assertEquals(Tab.Friends, PushRoute.tabFor("groups"))
        assertEquals(Tab.Challenges, PushRoute.tabFor("challenges"))
        assertEquals(Tab.Home, PushRoute.tabFor("qrun"))
        assertNull(PushRoute.tabFor(null))
    }
}
