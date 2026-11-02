package com.stepq.app.data

import com.stepq.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
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
    val client: SupabaseClient? by lazy {
        if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) return@lazy null
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest) {
                // App tables live in their own schema, separate from the website's private
                // `stepq` schema. See docs/PLAN.md.
                defaultSchema = APP_SCHEMA
            }
        }
    }

    const val APP_SCHEMA = "app"
}
