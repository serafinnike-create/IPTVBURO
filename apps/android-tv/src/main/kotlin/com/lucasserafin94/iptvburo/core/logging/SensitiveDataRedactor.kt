package com.lucasserafin94.iptvburo.core.logging

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes credentials and network identifiers before text reaches a logging backend.
 *
 * Redaction intentionally favours privacy over diagnostic detail. URL paths are not logged because
 * several IPTV APIs embed usernames, passwords, and signed identifiers in path segments.
 */
@Singleton
class SensitiveDataRedactor @Inject constructor() {
    fun redact(value: String): String {
        if (value.isEmpty()) return value

        return value
            .replace(URL_PATTERN) { match -> redactUrl(match.value) }
            .replace(AUTHORIZATION_PATTERN, "$1<redacted>")
            .replace(SENSITIVE_VALUE_PATTERN, "$1$2<redacted>")
            .replace(JWT_PATTERN, "<redacted-token>")
            .replace(IPV4_PATTERN, "<redacted-ip>")
            .replace(IPV6_PATTERN, "<redacted-ip>")
    }

    private fun redactUrl(rawUrl: String): String {
        val trailingPunctuation = rawUrl.takeLastWhile { it in URL_TRAILING_PUNCTUATION }
        val candidate = rawUrl.dropLast(trailingPunctuation.length)
        val safeUrl = runCatching {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching "<redacted-url>"
            val host = uri.host
                ?.takeUnless(::isIpAddress)
                ?.lowercase()
                ?: "<redacted-host>"
            val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
            val hasSensitiveRemainder =
                uri.rawUserInfo != null ||
                    !uri.rawPath.isNullOrBlank() ||
                    uri.rawQuery != null ||
                    uri.rawFragment != null

            buildString {
                append(scheme)
                append("://")
                append(host)
                append(port)
                if (hasSensitiveRemainder) append("/<redacted>")
            }
        }.getOrDefault("<redacted-url>")

        return safeUrl + trailingPunctuation
    }

    private fun isIpAddress(host: String): Boolean =
        IPV4_WHOLE_PATTERN.matches(host) || host.contains(':')

    private companion object {
        val URL_PATTERN =
            Regex("""(?i)\b[a-z][a-z0-9+.-]*://[^\s<>"']+""")
        val AUTHORIZATION_PATTERN =
            Regex(
                """(?i)(\bauthorization\b["']?\s*[:=]\s*)["']?(?:basic\s+|bearer\s+)?[^\s,;"'}]+["']?""",
            )
        val SENSITIVE_VALUE_PATTERN =
            Regex(
                """(?i)\b(username|user|password|passwd|pass|token|access[_-]?token|refresh[_-]?token|(?:x[_-]?)?api[_-]?key|apikey|client[_-]?secret|secret|signature|sig|credential)\b(["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s&,;]+)""",
            )
        val JWT_PATTERN =
            Regex("""\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\b""")
        val IPV4_PATTERN =
            Regex("""(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])""")
        val IPV4_WHOLE_PATTERN =
            Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
        val IPV6_PATTERN =
            Regex("""(?i)(?<![\w:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![\w:])""")
        val URL_TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
    }
}
