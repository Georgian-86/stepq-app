package com.stepq.app.data

import com.stepq.app.BuildConfig
import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
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
                val settings = SharedPreferencesSettings(
                    appContext.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE),
                    commit = true,
                )
                sessionManager = SettingsSessionManager(settings)
                codeVerifierCache = SettingsCodeVerifierCache(settings)
                // Email confirmation links come back into the app (see AndroidManifest).
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
            }
            install(Postgrest) {
                // App tables live in their own schema, separate from the website's private
                // `stepq` schema. See docs/PLAN.md.
                defaultSchema = APP_SCHEMA
            }
        }
    }

    const val APP_SCHEMA = "app"
    const val DEEPLINK_SCHEME = "stepq"
    const val DEEPLINK_HOST = "auth-callback"

    /** Must be listed under Authentication → URL Configuration → Redirect URLs in Supabase. */
    const val AUTH_REDIRECT_URL = "$DEEPLINK_SCHEME://$DEEPLINK_HOST"
}
