package com.stepq.app.data

import android.content.Context
import android.provider.ContactsContract
import java.security.MessageDigest

/**
 * Email addresses in the phone's contacts, as SHA-256 hashes of the trimmed, lower-cased
 * address. Only the hashes leave the phone (to app.match_contacts). Needs READ_CONTACTS.
 */
object Contacts {
    fun emailHashes(context: Context, limit: Int = 2000): List<String> {
        val emails = mutableSetOf<String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext() && emails.size < limit) {
                    c.getString(0)?.let { emails += normalizeEmail(it) }
                }
            }
        }
        return emails.filter { it.contains('@') }.map(::sha256Hex)
    }

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
