package com.lucasserafin94.iptvburo.data.security

import com.lucasserafin94.iptvburo.stalker.StalkerCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials

interface SourceConnectionStore {
    fun saveXtream(
        sourceId: String,
        credentials: XtreamCredentials,
    )

    fun readXtream(sourceId: String): XtreamCredentials?

    /**
     * Stores Stalker portal credentials under the same encrypted vault.
     *
     * The MAC address is the credential on a Stalker portal, so it is protected exactly like an
     * Xtream password rather than treated as a device identifier.
     */
    fun saveStalker(
        sourceId: String,
        credentials: StalkerCredentials,
    )

    fun readStalker(sourceId: String): StalkerCredentials?

    fun remove(sourceId: String)
}

class SourceConnectionStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
