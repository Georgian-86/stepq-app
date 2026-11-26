package com.stepq.app.data

import com.stepq.app.BuildConfig
import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient

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
        // Keep the session in our own SharedPreferences instead of relying on the library
        // finding a Context by itself, which fails on some setups and then breaks every sign-in.
        // commit = true writes each refreshed session to disk before going on. With the default
        // (apply) a process killed right after a refresh could start next time with the old
        // refresh token, which Supabase treats as reuse and revokes. The tokens are encrypted with
        // a Keystore key before they reach the disk (EncryptedSettings); sessions saved by older
        // versions are upgraded on read. The rest of the setup is shared with iOS (StepqClient).
        StepqClient.create(
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY,
            EncryptedSettings(
                SharedPreferencesSettings(
                    appContext.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE),
                    commit = true,
                ),
            ),
        )
    }

    const val APP_SCHEMA = StepqClient.APP_SCHEMA
    const val DEEPLINK_SCHEME = StepqClient.DEEPLINK_SCHEME
    const val DEEPLINK_HOST = StepqClient.DEEPLINK_HOST

    /** Must be listed under Authentication → URL Configuration → Redirect URLs in Supabase. */
    const val AUTH_REDIRECT_URL = StepqClient.AUTH_REDIRECT_URL
}
