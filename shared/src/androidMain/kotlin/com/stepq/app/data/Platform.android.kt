package com.stepq.app.data

actual fun logWarn(tag: String, message: String, error: Throwable?) {
    android.util.Log.w(tag, message, error)
}
