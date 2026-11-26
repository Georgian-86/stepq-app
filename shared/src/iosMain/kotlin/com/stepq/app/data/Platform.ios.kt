package com.stepq.app.data

import platform.Foundation.NSLog

actual fun logWarn(tag: String, message: String, error: Throwable?) {
    NSLog("%s", "[$tag] $message${error?.let { ": ${it::class.simpleName}: ${it.message}" } ?: ""}")
}
