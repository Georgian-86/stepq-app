package com.stepq.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Free/Pro rules both apps read from app.my_plan(). */
class PlanTest {
    @Test fun freeDefaults() {
        val free = MyPlan.FREE_DEFAULTS
        assertFalse(free.has(MyPlan.F.CHANGE_THEME))
        assertEquals(0, free.postsLeft)
        assertEquals(2, free.restoresLeft)
        assertEquals("leaf", free.copy(theme = "ocean").activeTheme)
        assertFalse(free.has("unknown_feature"))
    }

    @Test fun unlimitedIsNull() {
        val pro = MyPlan(
            plan = "pro", isPro = true, theme = "ocean",
            features = listOf(
                PlanFeature(MyPlan.F.COMMUNITY_POSTS, "Posts", "limit", free = 0, pro = null, mine = null),
                PlanFeature(MyPlan.F.CHANGE_THEME, "Theme", "switch", free = 0, pro = 1, mine = 1),
            ),
        )
        assertNull(pro.postsLeft)
        assertTrue(pro.has(MyPlan.F.COMMUNITY_POSTS))
        assertEquals("ocean", pro.activeTheme)
    }

    @Test fun limitKeyFromPostgresHint() {
        assertEquals("community_posts", planLimitKey(IllegalStateException("boom\nplan_limit:community_posts")))
        assertNull(planLimitKey(IllegalStateException("other")))
    }
}
