package com.stepq.app.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarTest {
    @Test fun oldSavedAvatarsStillDecode() {
        val old = Json.decodeFromString(AvatarConfig.serializer(), """{"skin":1,"hair":2,"outfit":3,"headband":4,"style":1}""")
        assertEquals(AvatarConfig(skin = 1, hair = 2, outfit = 3, headband = 4, style = 1), old)
        assertEquals(-1, old.body)
        assertEquals(0, old.top)
    }

    @Test fun figureFollowsTheChoiceThenTheOldHairDefault() {
        assertTrue(AvatarConfig(style = 1).isFemale)
        assertTrue(AvatarConfig(style = 2).isFemale)
        assertFalse(AvatarConfig(style = 0).isFemale)
        assertFalse(AvatarConfig(style = 1, body = 0).isFemale)
        assertTrue(AvatarConfig(style = 5, body = 1).isFemale)
    }

    @Test fun genderPicksFigureAndHair() {
        assertEquals(AvatarConfig(style = 1, body = 1), defaultAvatar("female"))
        assertEquals(AvatarConfig(style = 0, body = 0), defaultAvatar("male"))
        assertEquals(AvatarConfig(style = 0, body = -1), defaultAvatar("prefer_not"))
        val dressed = AvatarConfig(top = 3, eyewear = 1, extra = 2)
        assertEquals(dressed.copy(style = 1, body = 1), dressed.forGender("female"))
    }

    @Test fun newChoicesAreSavedWithTheirJsonNames() {
        val json = Json.encodeToString(AvatarConfig.serializer(), AvatarConfig(body = 1, facialHair = 2))
        assertEquals("""{"body":1,"facial_hair":2}""", json)
    }

    @Test fun everyOptionDraws() {
        for (body in -1..1) for (style in AvatarArt.hairStyles.indices) for (top in AvatarArt.tops.indices)
            for (hw in AvatarArt.headwear.indices) for (ew in AvatarArt.eyewear.indices)
                for (fh in AvatarArt.facialHair.indices) for (ex in AvatarArt.extras.indices) {
                    val shapes = AvatarArt.shapes(AvatarConfig(body = body, style = style, top = top, headwear = hw, eyewear = ew, facialHair = fh, extra = ex))
                    assertTrue(shapes.size > 6)
                }
        // Out-of-range indexes from a newer app fall back instead of crashing.
        assertTrue(AvatarArt.shapes(AvatarConfig(skin = 99, hair = 99, outfit = 99, headband = 99, style = 99)).isNotEmpty())
    }
}
