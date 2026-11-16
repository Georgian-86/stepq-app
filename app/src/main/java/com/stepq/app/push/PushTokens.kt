package com.stepq.app.push

import com.google.firebase.messaging.FirebaseMessaging
import com.stepq.app.BuildConfig
import com.stepq.app.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Ties this phone's FCM token to the signed-in user (app.register_push_token), so the server
 * can send them pushes, and unties it on log-out. Does nothing in builds without Firebase.
 */
object PushTokens {
    /** The current FCM token, or null without Firebase / Play services. */
    private suspend fun currentToken(): String? {
        if (!BuildConfig.FIREBASE_ENABLED) return null
        return runCatching {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    cont.resume(if (task.isSuccessful) task.result else null)
                }
            }
        }.getOrNull()
    }

    /** Stores `token` (or the current one) for the signed-in user. Safe to call often. */
    suspend fun register(token: String? = null) {
        val client = Supabase.client ?: return
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) return
        val t = token ?: currentToken() ?: return
        runCatching {
            client.postgrest.rpc(
                "register_push_token",
                buildJsonObject {
                    put("p_token", t)
                    put("p_platform", "android")
                },
            )
        }
    }

    /** Before log-out: removes this phone's token, so the next person on it gets no pushes. */
    suspend fun unregister() {
        val client = Supabase.client ?: return
        val t = currentToken() ?: return
        runCatching {
            client.postgrest.from("push_tokens").delete { filter { eq("token", t) } }
        }
    }
}
