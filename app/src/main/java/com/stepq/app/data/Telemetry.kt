package com.stepq.app.data

import com.stepq.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Firebase starts switched off (see the meta-data in AndroidManifest.xml), so nothing is sent
 * to Google before the person has agreed to the Terms and Privacy policy at sign-up. Crash
 * reports and the push token are turned on while someone is signed in, and crash reports are
 * turned off again on sign-out.
 */
object Telemetry {
    fun watch(scope: CoroutineScope) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        val client = Supabase.client ?: return
        scope.launch {
            client.auth.sessionStatus
                .map { it is SessionStatus.Authenticated }
                .distinctUntilChanged()
                .collect { signedIn -> setEnabled(signedIn) }
        }
    }

    private fun setEnabled(on: Boolean) {
        runCatching {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on
        }
        if (on) runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = true }
    }
}
