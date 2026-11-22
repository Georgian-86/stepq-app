package com.stepq.app

import com.stepq.app.data.Post
import com.stepq.app.ui.community.compactCount
import com.stepq.app.ui.community.mergePage
import com.stepq.app.ui.community.postErrorMessage
import com.stepq.app.ui.community.postProblem
import com.stepq.app.ui.community.postShareText
import com.stepq.app.ui.community.timeAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CommunityLogicTest {
    private val now = Instant.parse("2026-11-22T12:00:00Z")
    private fun post(id: String, by: String? = null, liked: Boolean = false, likes: Int = 0) =
        Post(id = id, authorId = "a", authorName = "Meera", authorUsername = "meera", body = "Hi", createdAt = now.toString(), repostedBy = by, liked = liked, likeCount = likes)

    @Test fun ages() {
        assertEquals("now", timeAgo("2026-11-22T11:59:30Z", now, ZoneOffset.UTC))
        assertEquals("5m", timeAgo("2026-11-22T11:55:00Z", now, ZoneOffset.UTC))
        assertEquals("3h", timeAgo("2026-11-22T09:00:00+00:00", now, ZoneOffset.UTC))
        assertEquals("2d", timeAgo("2026-11-20T12:00:00Z", now, ZoneOffset.UTC))
        assertEquals("1 Nov", timeAgo("2026-11-01T12:00:00Z", now, ZoneOffset.UTC))
        assertEquals("1 Nov 2025", timeAgo("2025-11-01T12:00:00Z", now, ZoneOffset.UTC))
    }

    @Test fun counts() {
        assertEquals("", compactCount(0))
        assertEquals("999", compactCount(999))
        assertEquals("1.2K", compactCount(1_240))
        assertEquals("12K", compactCount(12_000))
        assertEquals("3.4M", compactCount(3_400_000))
    }

    @Test fun likeTogglesCount() {
        val liked = post("p", likes = 3).toggledLike()
        assertTrue(liked.liked)
        assertEquals(4, liked.likeCount)
        assertEquals(3, liked.toggledLike().likeCount)
    }

    @Test fun nextPageSkipsEntriesAlreadyShown() {
        val merged = mergePage(listOf(post("a"), post("b")), listOf(post("b"), post("b", by = "Sana"), post("c")))
        assertEquals(listOf("a", "b", "b", "c"), merged.map { it.id })
    }

    @Test fun postNeedsTextUnlessSharingSteps() {
        assertEquals("Write something first.", postProblem("  ", shareSteps = false))
        assertNull(postProblem("", shareSteps = true))
        assertTrue(postProblem("x".repeat(501), false)!!.startsWith("Keep it under"))
    }

    @Test fun serverReasonsAreShown() {
        assertEquals(
            "You're posting a lot. Take a breather and try again in a bit.",
            postErrorMessage(Exception("""{"message":"You're posting a lot. Take a breather and try again in a bit."}""")),
        )
        assertTrue(postErrorMessage(Exception("timeout")).startsWith("Couldn't post"))
    }

    @Test fun shareTextForAnAchievement() {
        val win = post("w").copy(kind = "achievement", body = "", steps = 11_240, goal = 10_000)
        assertTrue(postShareText(win).startsWith("Meera (@meera) walked 11,240 steps and hit their goal on StepQ"))
    }
}
