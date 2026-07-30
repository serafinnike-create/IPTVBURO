package com.lucasserafin94.iptvburo.core.logging

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppLogger @Inject constructor(
    private val redactor: SensitiveDataRedactor,
) : AppLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, redactor.redact(message))
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, redactor.redact(message))
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, redactor.redact(message))
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        // Throwable messages are not logged: third-party exceptions may contain raw URLs,
        // response bodies, or opaque credentials that no general-purpose redactor can identify.
        val throwableSummary = throwable?.let { " (${it::class.java.simpleName})" }.orEmpty()
        Log.e(tag, redactor.redact(message + throwableSummary))
    }
}
