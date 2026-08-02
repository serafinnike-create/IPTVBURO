package com.lucasserafin94.iptvburo.data.security

import com.lucasserafin94.iptvburo.xtream.XtreamCredentials

interface SourceConnectionStore {
    fun saveXtream(
        sourceId: String,
        credentials: XtreamCredentials,
    )

    fun readXtream(sourceId: String): XtreamCredentials?

    fun remove(sourceId: String)
}

class SourceConnectionStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
