package com.stepq.app.data

import com.stepq.app.BuildConfig
import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * The app's one Supabase client. It uses only the public anon key: every read and write is
 * checked by row-level security on the server, so nothing in the APK grants extra access.
 *
 * Returns null when no anon key was configured at build time (see README), so the app still
 * opens and the log-in screen can say so instead of crashing.
 */
object Supabase {
    private lateinit var appContext: Context

    /** Called once from StepqApplication, before anything touches [client]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: SupabaseClient? by lazy {
        if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@lazy null
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth) {
                // Keep the session in our own SharedPreferences instead of relying on the
                // library finding a Context by itself, which fails on some setups and then
                // breaks every sign-in.
                // commit = true writes each refreshed session to disk before going on. With the
                // default (apply) a process killed right after a refresh could start next time
                // with the old refresh token, which Supabase treats as reuse and revokes.
                // The tokens are encrypted with a Keystore key before they reach the disk
                // (EncryptedSettings); sessions saved by older versions are upgraded on read.
                val settings = EncryptedSettings(
                    SharedPreferencesSettings(
                        appContext.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE),
                        commit = true,
                    ),
                )
                sessionManager = SettingsSessionManager(settings)
                codeVerifierCache = SettingsCodeVerifierCache(settings)
                // PKCE: an emailed link (confirm, reset password, change email) carries a one-time
                // code that only works together with a secret kept on this phone, never the
                // session itself. With the implicit flow the tokens were in the stepq:// link,
                // which any other app claiming the same scheme could read.
                flowType = FlowType.PKCE
                // Email confirmation links come back into the app (see AndroidManifest).
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
            }
            install(Postgrest) {
                // App tables live in their own schema, separate from the website's private
                // `stepq` schema. See docs/PLAN.md.
                defaultSchema = APP_SCHEMA
            }
            // Profile photos (StepQ Pro), in the private `avatars` bucket.
            install(io.github.jan.supabase.storage.Storage)
        }
    }

    const val APP_SCHEMA = "app"
    const val DEEPLINK_SCHEME = "stepq"
    const val DEEPLINK_HOST = "auth-callback"

    /** Must be listed under Authentication → URL Configuration → Redirect URLs in Supabase. */
    const val AUTH_REDIRECT_URL = "$DEEPLINK_SCHEME://$DEEPLINK_HOST"
}
