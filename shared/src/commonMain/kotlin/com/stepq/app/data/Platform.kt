package com.stepq.app.data

/** A warning in the platform log (Logcat on Android, the unified log on iOS). */
expect fun logWarn(tag: String, message: String, error: Throwable? = null)

/** Milliseconds since 1970, for the few places that store a plain timestamp. */
fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
