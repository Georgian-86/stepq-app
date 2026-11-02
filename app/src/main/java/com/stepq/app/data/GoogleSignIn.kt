package com.stepq.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.stepq.app.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Google's ID token plus the raw nonce whose SHA-256 was sent to Google. */
data class GoogleIdToken(val idToken: String, val rawNonce: String, val displayName: String?)

class GoogleSignInCancelled : Exception("Cancelled")
class GoogleSignInNotConfigured : Exception("Google sign-in isn't set up in this build yet.")

/**
 * Shows the Android account picker (Credential Manager) and returns a Google ID token for
 * StepQ's web client ID. Supabase then verifies the token and the nonce.
 */
object GoogleSignIn {
    suspend fun getIdToken(context: Context): GoogleIdToken {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) throw GoogleSignInNotConfigured()
        val rawNonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()
        val response = try {
            CredentialManager.create(context).getCredential(
                context,
                GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (e: NoCredentialException) {
            throw IllegalStateException(
                "Google found no account it can use for StepQ. Add a Google account in Settings, " +
                    "or this build's signing key isn't registered with Google yet." +
                    if (BuildConfig.DEBUG) "\n(${e.type}: ${e.errorMessage})" else "",
                e,
            )
        }
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unexpected credential type")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleIdToken(google.idToken, rawNonce, google.displayName)
    }
}
