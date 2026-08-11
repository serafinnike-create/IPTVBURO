package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.BuildConfig
import com.lucasserafin94.iptvburo.data.discovery.NoShelfCache
import com.lucasserafin94.iptvburo.data.discovery.StreamingDiscoveryRepository
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How the TMDB key is resolved, which is what decides whether metadata works at all.
 *
 * This exists because pasting a key appeared to do nothing. Two separate causes: the profile's key
 * was the *only* one consulted, so a build shipping its own key behaved as unconfigured; and the
 * resolution was duplicated at four call sites, so fixing one left the others answering differently.
 * Both are properties of [StreamingDiscoveryRepository.effectiveKey], asserted here.
 */
class MetadataKeyResolutionTest {
    private val repository = StreamingDiscoveryRepository(OkHttpClient(), NoShelfCache, Dispatchers.Unconfined)

    @Test
    fun `a profile key wins over the bundled one`() {
        assertEquals(
            "profile-key",
            repository.effectiveKey("profile-key"),
        )
    }

    @Test
    fun `a blank profile key falls back rather than switching metadata off`() {
        // Clearing the field should restore the default behaviour, not disable trailers and cast
        // photos entirely — the same rule the desktop applies.
        assertEquals(
            BuildConfig.BUNDLED_TMDB_KEY.takeIf(String::isNotBlank),
            repository.effectiveKey("   "),
        )
    }

    @Test
    fun `no key anywhere resolves to null and hides the destination`() {
        assumeTrue(
            "This build bundles a key, so the unconfigured case cannot be exercised here.",
            BuildConfig.BUNDLED_TMDB_KEY.isBlank(),
        )

        assertNull(repository.effectiveKey(null))
        assertEquals(
            StreamingDiscoveryCapability.UNAVAILABLE,
            repository.capabilityFor(null),
        )
    }

    @Test
    fun `a bundled key makes the feature available with no profile key`() {
        assumeTrue(
            "This build bundles no key, so the out-of-the-box case cannot be exercised here.",
            BuildConfig.BUNDLED_TMDB_KEY.isNotBlank(),
        )

        assertEquals(
            StreamingDiscoveryCapability.AVAILABLE,
            repository.capabilityFor(null),
        )
    }

    @Test
    fun `capability agrees with key resolution in every case`() {
        // The invariant that ties the two together: the destination is visible exactly when a key
        // will be found at request time. Any disagreement means a screen that opens onto nothing,
        // or a working key behind a hidden entry.
        listOf(null, "", "   ", "profile-key").forEach { candidate ->
            val expected =
                if (repository.effectiveKey(candidate) != null) {
                    StreamingDiscoveryCapability.AVAILABLE
                } else {
                    StreamingDiscoveryCapability.UNAVAILABLE
                }
            assertEquals(
                "Capability and key resolution disagreed for input: ${candidate?.let { "'$it'" }}",
                expected,
                repository.capabilityFor(candidate),
            )
        }
    }
}
