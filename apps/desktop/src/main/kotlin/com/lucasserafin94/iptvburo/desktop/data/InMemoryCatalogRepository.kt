package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.ImportedCatalog
import com.lucasserafin94.iptvburo.desktop.model.SafeImportWarning
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.playlist.M3uParser
import com.lucasserafin94.iptvburo.playlist.ParsedChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID

/**
 * Session-only catalog storage.
 *
 * Stream URLs, request headers and source paths can carry credentials, so this repository never
 * serializes them. Clearing the source or closing the process drops every reference held here.
 */
class InMemoryCatalogRepository(
    private val parserFactory: () -> M3uParser = ::M3uParser,
) {
    private val catalogs = linkedMapOf<String, ImportedCatalog>()

    fun importLocal(
        path: Path,
        sourceLabel: String,
    ): ImportedCatalog {
        require(Files.isRegularFile(path)) { "The selected source is not a regular file." }

        val sourceId = UUID.randomUUID().toString()
        val categoriesByName = linkedMapOf<String, Category>()
        val channels = mutableListOf<Channel>()

        val summary =
            Files.newInputStream(path).use { input ->
                parserFactory().parseStreaming(input) { parsed ->
                    val category =
                        parsed.groupTitle
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?.let { categoryName ->
                                categoriesByName.getOrPut(categoryName.normalizedKey()) {
                                    Category(
                                        id = UUID.randomUUID().toString(),
                                        sourceId = sourceId,
                                        name = categoryName.take(MAX_CATEGORY_NAME_LENGTH),
                                        sortOrder = categoriesByName.size,
                                    )
                                }
                            }
                    channels +=
                        parsed.toDomainChannel(
                            sourceId = sourceId,
                            categoryId = category?.id,
                        )
                }
            }

        val source =
            Source(
                id = sourceId,
                name = sourceLabel.take(MAX_SOURCE_NAME_LENGTH),
                type = SourceType.LOCAL_M3U,
                createdAtEpochMillis = System.currentTimeMillis(),
                channelCount = channels.size,
            )
        val catalog =
            ImportedCatalog(
                source = source,
                categories = categoriesByName.values.toList(),
                channels = channels.toList(),
                warnings =
                    summary.warnings.map { warning ->
                        SafeImportWarning(
                            code = warning.code,
                            lineNumber = warning.lineNumber,
                        )
                    },
            )
        synchronized(catalogs) {
            catalogs[sourceId] = catalog
        }
        return catalog
    }

    fun forget(sourceId: String) {
        synchronized(catalogs) {
            catalogs.remove(sourceId)
        }
    }

    fun clear() {
        synchronized(catalogs) {
            catalogs.clear()
        }
    }

    internal fun sourceCount(): Int =
        synchronized(catalogs) {
            catalogs.size
        }

    private fun ParsedChannel.toDomainChannel(
        sourceId: String,
        categoryId: String?,
    ): Channel =
        Channel(
            id = UUID.randomUUID().toString(),
            sourceId = sourceId,
            name = name.take(MAX_CHANNEL_NAME_LENGTH),
            streamUri = streamUri,
            categoryId = categoryId,
            tvgId = tvgId,
            tvgName = tvgName,
            logoUri = logoUri,
            requestHeaders = requestHeaders,
        )

    private fun String.normalizedKey(): String =
        lowercase(Locale.ROOT)
            .trim()
            .replace(WHITESPACE, " ")

    private companion object {
        const val MAX_SOURCE_NAME_LENGTH = 80
        const val MAX_CATEGORY_NAME_LENGTH = 120
        const val MAX_CHANNEL_NAME_LENGTH = 240
        val WHITESPACE = Regex("\\s+")
    }
}
