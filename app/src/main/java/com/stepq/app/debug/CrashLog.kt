package com.stepq.app.debug

import android.content.Context
import android.os.Build
import com.stepq.app.BuildConfig
import java.io.File
import java.time.Instant

/**
 * Keeps the last crash on the phone so the next launch can show it (only in debug builds,
 * until Crashlytics is set up). Nothing is sent anywhere.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        if (!BuildConfig.DEBUG) return
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    buildString {
                        appendLine("StepQ ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Time: ${Instant.now()}  Thread: ${thread.name}")
                        appendLine()
                        append(error.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
