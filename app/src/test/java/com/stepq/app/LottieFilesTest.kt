package com.stepq.app

import org.robolectric.RuntimeEnvironment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The hand-made Lottie files in res/raw load, with every layer and a real duration. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LottieFilesTest {
    private fun check(res: Int, layers: Int) {
        val result = LottieCompositionFactory.fromRawResSync(RuntimeEnvironment.getApplication(), res)
        assertNull(result.exception)
        val composition = assertNotNullAndGet(result.value)
        assertTrue(composition.layers.size == layers)
        assertTrue(composition.duration > 500f)
    }

    private fun <T> assertNotNullAndGet(v: T?): T { assertNotNull(v); return v!! }

    @Test fun confetti() = check(R.raw.lottie_confetti, 44)
    @Test fun success() = check(R.raw.lottie_success, 3)
}
