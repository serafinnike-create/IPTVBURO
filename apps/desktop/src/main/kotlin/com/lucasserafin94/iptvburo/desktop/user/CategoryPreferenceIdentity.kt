package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Stable identity for category preferences.
 *
 * Xtream providers commonly reuse numeric category ids across live, film and series catalogues.
 * Persisting a bare id therefore made a choice in one section affect the other two. New writes are
 * scoped by content type. Bare ids from older builds still match every section until the next edit,
 * when [migrateLegacy] expands them into explicit scoped entries without losing protection.
 */
internal object CategoryPreferenceIdentity {
    private const val PREFIX = "iptvburo-category-v2:"

    fun scoped(
        contentType: XtreamContentType,
        providerId: String,
    ): String =
        "$PREFIX${contentType.name}:" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                providerId.toByteArray(StandardCharsets.UTF_8),
            )

    fun matches(
        storedIds: Set<String>,
        contentType: XtreamContentType,
        providerId: String,
    ): Boolean = providerId in storedIds || scoped(contentType, providerId) in storedIds

    /** Preserves the broad behaviour of every old bare id while making future edits independent. */
    fun migrateLegacy(storedIds: Set<String>): Set<String> =
        storedIds.flatMapTo(linkedSetOf()) { stored ->
            if (isScoped(stored)) {
                listOf(stored)
            } else {
                XtreamContentType.entries.map { type -> scoped(type, stored) }
            }
        }

    private fun isScoped(value: String): Boolean {
        if (!value.startsWith(PREFIX)) return false
        val rest = value.removePrefix(PREFIX)
        val separator = rest.indexOf(':')
        if (separator <= 0 || separator == rest.lastIndex) return false
        if (XtreamContentType.entries.none { type -> type.name == rest.take(separator) }) return false
        return runCatching {
            Base64.getUrlDecoder().decode(rest.drop(separator + 1))
        }.isSuccess
    }
}
