package com.stepq.app.ui.pro

import androidx.test.core.app.ApplicationProvider
import com.stepq.app.data.MyPlan
import com.stepq.app.data.PlanFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class UpgradePromptTest {
    private val day = 24L * 60 * 60 * 1000
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun plan(days: Int?, pro: Boolean = false) = MyPlan.FREE_DEFAULTS.copy(
        isPro = pro,
        features = MyPlan.FREE_DEFAULTS.features + PlanFeature(MyPlan.F.UPGRADE_POPUP_DAYS, "Pop-up", "limit", "days", days, 0, if (pro) 0 else days),
    )

    @Test fun popsUpEveryFewDaysButNotOnDayOne() {
        val t0 = 1_000_000_000_000L
        assertFalse("first open", UpgradePrompt.dueNow(ctx, plan(3), false, t0))
        assertFalse("same day", UpgradePrompt.dueNow(ctx, plan(3), false, t0 + day / 2))
        assertTrue("next day", UpgradePrompt.dueNow(ctx, plan(3), false, t0 + day))
        assertFalse("two days later", UpgradePrompt.dueNow(ctx, plan(3), false, t0 + 3 * day))
        assertTrue("three days later", UpgradePrompt.dueNow(ctx, plan(3), false, t0 + 4 * day))
    }

    @Test fun neverForProAskedOrSwitchedOff() {
        val t0 = 1_000_000_000_000L
        UpgradePrompt.dueNow(ctx, plan(3), false, t0)
        assertFalse(UpgradePrompt.dueNow(ctx, plan(3, pro = true), false, t0 + 10 * day))
        assertFalse(UpgradePrompt.dueNow(ctx, plan(3), true, t0 + 10 * day))
        assertFalse(UpgradePrompt.dueNow(ctx, plan(0), false, t0 + 10 * day))
        assertFalse(UpgradePrompt.dueNow(ctx, MyPlan.FREE_DEFAULTS, false, t0 + 10 * day))
    }

    @Test fun perksFollowTheAdminValues() {
        val titles = upgradePerks(MyPlan.FREE_DEFAULTS).map { it.title }
        assertTrue("Streak saves" in titles)
        assertEquals("8 a month", upgradePerks(MyPlan.FREE_DEFAULTS).first { it.title == "Streak saves" }.pro)
        val same = MyPlan.FREE_DEFAULTS.copy(features = MyPlan.FREE_DEFAULTS.features.map { if (it.key == MyPlan.F.STREAK_RESTORES) it.copy(pro = it.free) else it })
        assertFalse("Streak saves" in upgradePerks(same).map { it.title })
    }
}
