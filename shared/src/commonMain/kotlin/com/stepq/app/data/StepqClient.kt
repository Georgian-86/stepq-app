package com.stepq.app.data

import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * The one Supabase client each app makes, configured the same on Android and iOS. It uses only
 * the public anon key: every read and write is checked by row-level security on the server, so
 * nothing in the app grants extra access. Never pass a service-role key here.
 */
object StepqClient {
    const val APP_SCHEMA = "app"
    const val DEEPLINK_SCHEME = "stepq"
    const val DEEPLINK_HOST = "auth-callback"

    /** Must be listed under Authentication → URL Configuration → Redirect URLs in Supabase. */
    const val AUTH_REDIRECT_URL = "$DEEPLINK_SCHEME://$DEEPLINK_HOST"

    const val SITE_URL = "https://stepq.in"

    /**
     * [sessionStore] keeps the session and the PKCE verifier. Each platform passes encrypted,
     * on-device storage: Android a Keystore-encrypted SharedPreferences, iOS the Keychain.
     */
    fun create(url: String, anonKey: String, sessionStore: Settings): SupabaseClient? {
        if (anonKey.isBlank()) return null
        return createSupabaseClient(url, anonKey) {
            install(Auth) {
                sessionManager = SettingsSessionManager(sessionStore)
                codeVerifierCache = SettingsCodeVerifierCache(sessionStore)
                // PKCE: an emailed link carries a one-time code that only works together with a
                // secret kept on this phone, never the session itself.
                flowType = FlowType.PKCE
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
            }
            install(Postgrest) {
                // App tables live in their own schema, separate from the website's private
                // `stepq` schema.
                defaultSchema = APP_SCHEMA
            }
            // Profile photos (StepQ Pro), in the private `avatars` bucket.
            install(Storage)
        }
    }
}
