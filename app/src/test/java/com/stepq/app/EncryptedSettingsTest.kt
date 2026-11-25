package com.stepq.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.SharedPreferencesSettings
import com.stepq.app.data.EncryptedSettings
import com.stepq.app.data.StringCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The stored Supabase session is encrypted, old plain sessions are upgraded, bad ones dropped. */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class EncryptedSettingsTest {
    /** Reversible stand-in for the Keystore cipher (Robolectric has no AndroidKeyStore). */
    private object Rot : StringCrypto {
        override fun encrypt(plain: String) = plain.reversed() + "#"
        override fun decrypt(sealed: String): String {
            require(sealed.endsWith("#")) { "tampered" }
            return sealed.dropLast(1).reversed()
        }
    }

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("enc_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    private val raw = SharedPreferencesSettings(prefs, commit = true)
    private val settings = EncryptedSettings(raw, Rot)

    @Test
    fun valuesAreNeverStoredInPlainText() {
        settings.putString("session", "{\"refresh_token\":\"abc\"}")
        val stored = raw.getStringOrNull("session")!!
        assertTrue(stored.startsWith(EncryptedSettings.PREFIX))
        assertFalse(stored.contains("refresh_token"))
        assertEquals("{\"refresh_token\":\"abc\"}", settings.getStringOrNull("session"))
    }

    @Test
    fun plainSessionsFromOlderVersionsAreKeptAndEncrypted() {
        raw.putString("session", "old-plain")
        assertEquals("old-plain", settings.getString("session", ""))
        assertTrue(raw.getStringOrNull("session")!!.startsWith(EncryptedSettings.PREFIX))
        assertEquals("old-plain", settings.getStringOrNull("session"))
    }

    @Test
    fun undecryptableValuesAreDropped() {
        raw.putString("session", EncryptedSettings.PREFIX + "garbage")
        assertNull(settings.getStringOrNull("session"))
        assertFalse(raw.hasKey("session"))
    }

    @Test
    fun withoutKeystoreValuesPassThrough() {
        val plain = EncryptedSettings(raw, crypto = null)
        plain.putString("k", "v")
        assertEquals("v", raw.getStringOrNull("k"))
    }
}
