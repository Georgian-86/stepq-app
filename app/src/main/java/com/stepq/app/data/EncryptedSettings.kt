package com.stepq.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.russhwolf.settings.Settings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Settings whose string values are encrypted with AES-256-GCM under a key kept in the Android
 * Keystore, which never leaves the phone's secure hardware. Supabase keeps the signed-in
 * session (access and refresh tokens) here, so a copy of the app's files is not enough to take
 * over someone's account (DPDP Rules 2025, rule 6: encryption as a reasonable safeguard).
 *
 * - Values written before this existed are plain text: they are read once and rewritten
 *   encrypted, so nobody is signed out by the update.
 * - A value that can't be decrypted (the key was lost, say after a factory reset restore) is
 *   dropped, which just means signing in again.
 * - If the Keystore can't be used at all (some test environments), values stay as they are.
 */
class EncryptedSettings(
    private val inner: Settings,
    private val crypto: StringCrypto? = KeystoreCrypto.orNull(),
) : Settings by inner {

    override fun putString(key: String, value: String) {
        inner.putString(key, crypto?.let { PREFIX + it.encrypt(value) } ?: value)
    }

    override fun getStringOrNull(key: String): String? {
        val stored = inner.getStringOrNull(key) ?: return null
        val c = crypto ?: return stored
        if (!stored.startsWith(PREFIX)) {
            // Written by an older version in plain text: encrypt it now.
            putString(key, stored)
            return stored
        }
        return runCatching { c.decrypt(stored.removePrefix(PREFIX)) }.getOrElse {
            inner.remove(key)
            null
        }
    }

    override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue

    companion object {
        const val PREFIX = "enc1:"
    }
}

/** Turns strings into an opaque encrypted form and back. */
interface StringCrypto {
    fun encrypt(plain: String): String
    fun decrypt(sealed: String): String
}

/** AES-256-GCM with a non-exportable Android Keystore key. */
class KeystoreCrypto private constructor(private val key: SecretKey) : StringCrypto {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    companion object {
        private const val ALIAS = "stepq_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        /** The crypto, or null when the Android Keystore isn't available. */
        fun orNull(): StringCrypto? = runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                    generateKey()
                }
            KeystoreCrypto(key)
        }.getOrNull()
    }
}
