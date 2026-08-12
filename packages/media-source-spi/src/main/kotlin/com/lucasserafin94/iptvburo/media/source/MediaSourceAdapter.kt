package com.lucasserafin94.iptvburo.media.source

import com.lucasserafin94.iptvburo.domain.model.MediaCapabilities
import com.lucasserafin94.iptvburo.domain.model.MediaIdentity
import com.lucasserafin94.iptvburo.domain.model.MediaKind
import com.lucasserafin94.iptvburo.domain.model.SourceCapabilities
import kotlinx.coroutines.flow.Flow

enum class MediaSourceType {
    M3U,
    XTREAM,
    STALKER,
    LOCAL_FILES,
    PODCAST_RSS,
    RADIO_URL,
    UNKNOWN,
}

/**
 * Source configuration boundary. Values may contain credentials and are never printed.
 * Platform vaults remain responsible for encryption and lifecycle.
 */
class SourceConfig(
    val sourceId: String,
    val sourceType: MediaSourceType,
    val locator: String,
    val options: Map<String, String> = emptyMap(),
) {
    init {
        require(sourceId.isNotBlank())
        require(locator.isNotBlank())
    }

    override fun toString(): String =
        "SourceConfig(sourceId=<redacted>, sourceType=$sourceType, locator=<redacted>, " +
            "optionCount=${options.size})"
}

/** An opaque identity resolved only when playback is explicitly requested. */
class PlaybackLocator(
    val sourceId: String,
    val reference: String,
) {
    init {
        require(sourceId.isNotBlank())
        require(reference.isNotBlank())
        // Authenticated URLs exist only in SourceConfig/the vault and in ResolvedMedia in memory.
        // Persisting one here would bypass late resolution and copy credentials into queues/history.
        require("://" !in reference) { "PlaybackLocator must be an opaque source reference." }
    }

    override fun toString(): String =
        "PlaybackLocator(sourceId=<redacted>, reference=<redacted>)"
}

data class MediaDescriptor(
    val stableIdentity: MediaIdentity,
    val kind: MediaKind,
    val title: String,
    val locator: PlaybackLocator? = null,
)

data class SourceValidation(
    val valid: Boolean,
    val capabilities: SourceCapabilities = SourceCapabilities(),
    val warningCodes: List<String> = emptyList(),
) {
    init {
        warningCodes.forEach(::requireSafeDiagnosticCode)
    }
}

class ResolvedMedia(
    val kind: MediaKind,
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val capabilities: MediaCapabilities = MediaCapabilities(),
) {
    init {
        require(uri.isNotBlank())
    }

    override fun toString(): String =
        "ResolvedMedia(kind=$kind, uri=<redacted>, headerCount=${headers.size}, " +
            "capabilities=$capabilities)"
}

sealed interface MediaImportEvent {
    data object Started : MediaImportEvent

    data class CollectionDiscovered(val id: String, val title: String) : MediaImportEvent

    data class ItemDiscovered(val item: MediaDescriptor) : MediaImportEvent

    data class Progress(val discovered: Long, val total: Long? = null) : MediaImportEvent {
        init {
            require(discovered >= 0)
            require(total == null || total >= discovered)
        }
    }

    /** Explicit terminal signal when an adapter stops by user request rather than by failure. */
    data object Cancelled : MediaImportEvent

    /** Stable safe code only; raw provider responses never cross this contract. */
    data class Warning(val code: String) : MediaImportEvent {
        init {
            requireSafeDiagnosticCode(code)
        }
    }

    data class Completed(val discovered: Long) : MediaImportEvent {
        init {
            require(discovered >= 0)
        }
    }

    data class Failed(val code: String, val retryable: Boolean) : MediaImportEvent {
        init {
            requireSafeDiagnosticCode(code)
        }
    }
}

private val SAFE_DIAGNOSTIC_CODE = Regex("[a-z0-9][a-z0-9_.-]{0,63}")

private fun requireSafeDiagnosticCode(code: String) {
    require(SAFE_DIAGNOSTIC_CODE.matches(code)) {
        "Diagnostic codes must be stable lowercase identifiers, never provider responses."
    }
}

interface MediaSourceAdapter {
    val sourceType: MediaSourceType

    suspend fun validate(config: SourceConfig): SourceValidation

    fun scan(config: SourceConfig): Flow<MediaImportEvent>

    suspend fun resolve(locator: PlaybackLocator): ResolvedMedia

    suspend fun capabilities(config: SourceConfig): SourceCapabilities
}
