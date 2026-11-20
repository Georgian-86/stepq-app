package com.stepq.app

import com.stepq.app.ui.components.footPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkCycleTest {
    @Test fun footStepsForwardThenSlidesBack() {
        // Swing: starts at the back, lifts highest halfway, lands in front.
        assertEquals(-1f, footPose(0f).depth, 0.001f)
        assertEquals(1f, footPose(0.25f).lift, 0.001f)
        assertEquals(1f, footPose(0.5f).depth, 0.001f)
        // Stance: on the ground, sliding back under Piko.
        assertEquals(0f, footPose(0.75f).lift, 0.001f)
        assertTrue(footPose(0.6f).depth > footPose(0.9f).depth)
        // The cycle loops without a jump.
        assertEquals(footPose(0.999f).depth, footPose(0f).depth, 0.01f)
    }
}
