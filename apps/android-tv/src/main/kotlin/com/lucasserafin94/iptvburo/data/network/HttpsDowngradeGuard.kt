package com.lucasserafin94.iptvburo.data.network

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Refuses a redirect that would take an HTTPS request to cleartext, and lets every other through.
 *
 * The client used to set `followSslRedirects(false)` for this, which refuses scheme changes in
 * *both* directions: a provider redirecting `http://` streams to `https://` — common, and exactly
 * what should be encouraged — failed to play. This keeps the one direction that matters closed.
 *
 * A network interceptor, so it sees each hop of the chain rather than only the final answer.
 */
class HttpsDowngradeGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isRedirect) return response
        val location = response.header("Location")?.let(request.url::resolve) ?: return response
        if (isDowngrade(request.url, location)) {
            response.close()
            // No URL in the message: stream addresses carry the account's credentials.
            throw IOException("Refused a redirect from HTTPS to cleartext HTTP")
        }
        return response
    }

    companion object {
        internal fun isDowngrade(from: HttpUrl, to: HttpUrl): Boolean = from.isHttps && !to.isHttps
    }
}
