package com.lucasserafin94.iptvburo.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How fast the catalogue is arriving, published for whichever screen is waiting on it.
 *
 * A shared holder rather than a callback passed down the call chain, because the client that reads
 * the bytes is a process-wide singleton while the screen that wants the figure comes and goes. The
 * client writes here as it reads; a screen collects while it is up and stops when it leaves.
 *
 * Null means "no honest figure", not "zero": between requests, or before enough has arrived to
 * divide by, the app does not know the rate, and a zero on screen would read as stopped.
 */
@Singleton
class DownloadRateReporter @Inject constructor() {
    private val current = MutableStateFlow<Long?>(null)

    /** Bytes per second, or null when nothing can be said. */
    val bytesPerSecond: Flow<Long?> = current.asStateFlow()

    /** Called from the thread reading the response body. */
    fun report(rate: Long?) {
        current.value = rate
    }

    /**
     * Forgets the last figure.
     *
     * Called when a download ends, so a finished import does not leave its final speed sitting on
     * a later screen as though something were still arriving.
     */
    fun clear() {
        current.value = null
    }
}
