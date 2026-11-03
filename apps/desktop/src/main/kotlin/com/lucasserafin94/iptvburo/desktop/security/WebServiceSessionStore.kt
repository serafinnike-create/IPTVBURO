package com.lucasserafin94.iptvburo.desktop.security

import com.lucasserafin94.iptvburo.domain.model.WebNavigation
import com.lucasserafin94.iptvburo.domain.model.WebServiceBrand
import com.lucasserafin94.iptvburo.domain.model.WebServicePolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * One cookie the service's page set, as kept between sessions.
 *
 * [toString] carries the name and domain only: the value is the user's session with the service and
 * is as much a secret as a password.
 */
data class StoredWebCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    /** Null for a cookie that ends with the browser session. */
    val expiresEpochMillis: Long?,
) {
    override fun toString(): String = "StoredWebCookie($name @ $domain, value=<redacted>)"
}

/**
 * Keeps a profile's sign-in to a web service (GoDorama) between launches of the app.
 *
 * The embedded browser's own cache is a scratch directory deleted on exit — chosen for the trailers,
 * so nothing about what was watched survives — which would mean signing in to the service again
 * every time the app opened. Instead the service's own cookies are saved here when its window
 * closes and put back when it opens, sealed with [LocalSecretProtector] (DPAPI on Windows, the
 * Keychain key on macOS) exactly like a saved list's password.
 *
 * Per profile, which is also what keeps two adults on one computer out of each other's accounts:
 * opening the service clears whatever the engine holds for it before this profile's cookies go in.
 *
 * Only cookies for the service's own hosts are ever kept, and nothing is written at all on a machine
 * where the protector is unavailable — the user signs in again, rather than having a session on
 * disk in the clear.
 */
class WebServiceSessionStore(
    private val directory: Path = defaultDirectory(),
    private val protector: SecretSealer = LocalProtectorSealer,
) {
    fun load(
        profileId: String,
        brand: WebServiceBrand,
    ): List<StoredWebCookie> {
        if (!protector.isAvailable) return emptyList()
        val file = fileFor(profileId, brand) ?: return emptyList()
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching { decode(protector.unseal(Files.readAllBytes(file))) }
            .getOrDefault(emptyList())
            .filter { cookie -> belongsTo(cookie, brand) }
    }

    fun save(
        profileId: String,
        brand: WebServiceBrand,
        cookies: List<StoredWebCookie>,
    ) {
        if (!protector.isAvailable) return
        val file = fileFor(profileId, brand) ?: return
        val kept = cookies.filter { cookie -> belongsTo(cookie, brand) }
        if (kept.isEmpty()) {
            clear(profileId, brand)
            return
        }
        runCatching {
            Files.createDirectories(file.parent)
            Files.write(file, protector.seal(encode(kept)))
        }
    }

    /** Forgets the sign-in: the user signed out, or the service was turned off. */
    fun clear(
        profileId: String,
        brand: WebServiceBrand,
    ) {
        val file = fileFor(profileId, brand) ?: return
        runCatching { Files.deleteIfExists(file) }
    }

    private fun fileFor(
        profileId: String,
        brand: WebServiceBrand,
    ): Path? {
        // Both are ours — a UUID and a constant — but they become a file name, so anything that is
        // not plainly safe is refused rather than escaped.
        if (!SAFE_NAME.matches(profileId) || !SAFE_NAME.matches(brand.id)) return null
        return directory.resolve("$profileId-${brand.id}.session")
    }

    /** Abstracted so the format can be tested without DPAPI or a Keychain. */
    interface SecretSealer {
        val isAvailable: Boolean

        fun seal(plaintext: ByteArray): ByteArray

        fun unseal(sealed: ByteArray): ByteArray
    }

    private object LocalProtectorSealer : SecretSealer {
        override val isAvailable: Boolean get() = LocalSecretProtector.isAvailable

        override fun seal(plaintext: ByteArray): ByteArray = LocalSecretProtector.protect(plaintext)

        override fun unseal(sealed: ByteArray): ByteArray = LocalSecretProtector.unprotect(sealed)
    }

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9-]{1,64}")
        private const val FORMAT = "v1"

        fun defaultDirectory(): Path =
            com.lucasserafin94.iptvburo.desktop.DesktopStorage.localData().resolve("web-sessions")

        /**
         * The saved cookies as they go back into the engine: one per name, host and path, each
         * scoped to its own host only.
         *
         * GoDorama signs in with a host-only cookie. Put back as a domain cookie (which is what
         * giving the engine a domain does), it sat beside the fresh one the sign-in set under the
         * same name, the browser sent both, and the server read the stale one first, so the
         * viewer signed in and was shown the page as if nobody had. Saves made before this fix
         * may hold both copies; the host-only one is the one the site itself set, and it wins.
         */
        fun forRestore(saved: List<StoredWebCookie>): List<StoredWebCookie> =
            saved
                .groupBy { cookie ->
                    Triple(cookie.name, cookie.domain.trim().trimStart('.').lowercase(), cookie.path.ifBlank { "/" })
                }.values
                .map { copies -> copies.firstOrNull { !it.domain.trim().startsWith('.') } ?: copies.first() }
                .map { cookie -> cookie.copy(domain = cookie.domain.trim().trimStart('.'), path = cookie.path.ifBlank { "/" }) }
                .filter { cookie -> cookie.domain.isNotBlank() }

        /** Whether [cookie] was set for one of the service's own hosts. */
        fun belongsTo(
            cookie: StoredWebCookie,
            brand: WebServiceBrand,
        ): Boolean {
            val host = cookie.domain.trim().trimStart('.')
            if (host.isBlank()) return false
            return WebServicePolicy.navigation(brand, "https://$host/") == WebNavigation.StayInside
        }

        /** One line per cookie, every field Base64 so no value can break the format. */
        fun encode(cookies: List<StoredWebCookie>): ByteArray {
            val encoder = Base64.getEncoder()
            fun field(value: String) = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
            val lines =
                cookies.map { cookie ->
                    listOf(
                        field(cookie.name),
                        field(cookie.value),
                        field(cookie.domain),
                        field(cookie.path),
                        if (cookie.secure) "1" else "0",
                        if (cookie.httpOnly) "1" else "0",
                        cookie.expiresEpochMillis?.toString() ?: "-",
                    ).joinToString(" ")
                }
            return (listOf(FORMAT) + lines).joinToString("\n").toByteArray(Charsets.UTF_8)
        }

        /** The reverse of [encode]. A line that does not parse is dropped, not fatal. */
        fun decode(bytes: ByteArray): List<StoredWebCookie> {
            val lines = bytes.toString(Charsets.UTF_8).split('\n')
            if (lines.firstOrNull() != FORMAT) return emptyList()
            val decoder = Base64.getDecoder()
            fun field(value: String) = decoder.decode(value).toString(Charsets.UTF_8)
            return lines.drop(1).mapNotNull { line ->
                runCatching {
                    val parts = line.split(' ')
                    require(parts.size == 7)
                    StoredWebCookie(
                        name = field(parts[0]),
                        value = field(parts[1]),
                        domain = field(parts[2]),
                        path = field(parts[3]),
                        secure = parts[4] == "1",
                        httpOnly = parts[5] == "1",
                        expiresEpochMillis = parts[6].takeIf { it != "-" }?.toLong(),
                    )
                }.getOrNull()
            }
        }
    }
}
