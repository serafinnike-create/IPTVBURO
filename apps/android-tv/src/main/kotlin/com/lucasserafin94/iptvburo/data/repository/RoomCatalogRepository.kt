package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.local.IptvBuroDatabase
import com.lucasserafin94.iptvburo.data.local.dao.CategoryDao
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.local.dao.SourceDao
import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity
import com.lucasserafin94.iptvburo.data.mapper.PlaylistEntityMapper
import com.lucasserafin94.iptvburo.data.mapper.toDomain
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.playlist.M3uParser
import com.lucasserafin94.iptvburo.playlist.M3uParseSummary
import com.lucasserafin94.iptvburo.di.IoDispatcher
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomCatalogRepository @Inject constructor(
    private val database: IptvBuroDatabase,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val parser: M3uParser,
    private val playlistEntityMapper: PlaylistEntityMapper,
    private val logger: AppLogger,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {
    override fun observeSources(): Flow<List<Source>> =
        sourceDao.observeAll().map { entities -> entities.map(SourceEntity::toDomain) }

    override fun observeCategories(sourceId: String): Flow<List<Category>> =
        categoryDao.observeForSource(sourceId)
            .map { entities -> entities.map(CategoryEntity::toDomain) }

    override fun observeChannels(
        sourceId: String,
        categoryId: String?,
    ): Flow<List<Channel>> =
        channelDao.observeForSource(sourceId, categoryId)
            .map { entities -> entities.map(ChannelEntity::toDomain) }

    override suspend fun getChannel(id: String): Channel? =
        channelDao.getById(id)?.toDomain()

    override suspend fun importPlaylist(
        displayName: String,
        inputStream: InputStream,
    ): PlaylistImportResult = withContext(ioDispatcher) {
        val sanitizedDisplayName = displayName.trim()
        require(sanitizedDisplayName.isNotEmpty()) { "The playlist display name cannot be blank." }
        require(sanitizedDisplayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "The playlist display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters."
        }

        logger.info(TAG, "Playlist import started")
        val sourceId = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val mappingSession = playlistEntityMapper.newSession(sourceId)
        val channelBatch = ArrayList<ChannelEntity>(INSERT_BATCH_SIZE)
        val importContext = currentCoroutineContext()
        var importedChannelCount = 0

        val parserSummary =
            try {
                database.runInTransaction(
                    Callable<M3uParseSummary> {
                        sourceDao.upsertBlocking(
                            SourceEntity(
                                id = sourceId,
                                displayName = sanitizedDisplayName,
                                type = SourceType.LOCAL_M3U.name,
                                createdAtEpochMillis = importedAt,
                                updatedAtEpochMillis = importedAt,
                                channelCount = 0,
                            ),
                        )

                        val summary =
                            parser.parseStreaming(inputStream) { parsedChannel ->
                                importContext.ensureActive()
                                mappingSession.mapChannel(parsedChannel)?.let { mapped ->
                                    mapped.newCategory?.let(categoryDao::upsertBlocking)
                                    channelBatch += mapped.channel
                                    importedChannelCount += 1
                                    if (channelBatch.size == INSERT_BATCH_SIZE) {
                                        channelDao.insertAllBlocking(channelBatch)
                                        channelBatch.clear()
                                    }
                                }
                            }
                        require(importedChannelCount > 0) {
                            "The playlist does not contain any supported HTTP or HTTPS streams."
                        }
                        if (channelBatch.isNotEmpty()) {
                            channelDao.insertAllBlocking(channelBatch)
                            channelBatch.clear()
                        }
                        sourceDao.upsertBlocking(
                            SourceEntity(
                                id = sourceId,
                                displayName = sanitizedDisplayName,
                                type = SourceType.LOCAL_M3U.name,
                                createdAtEpochMillis = importedAt,
                                updatedAtEpochMillis = importedAt,
                                channelCount = importedChannelCount,
                            ),
                        )
                        summary
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error(TAG, "Playlist import failed", error)
                throw error
            }

        logger.info(
            TAG,
            "Playlist import completed: $importedChannelCount channels, " +
                "${mappingSession.categoryCount} categories, " +
                "${parserSummary.warnings.size} parser warnings, " +
                "${mappingSession.skippedChannelCount} skipped",
        )

        PlaylistImportResult(
            sourceId = sourceId,
            importedChannelCount = importedChannelCount,
            importedCategoryCount = mappingSession.categoryCount,
            parserWarningCount = parserSummary.warnings.size,
            skippedChannelCount = mappingSession.skippedChannelCount,
        )
    }

    private companion object {
        const val TAG = "CatalogRepository"
        const val MAX_DISPLAY_NAME_LENGTH = 120
        const val INSERT_BATCH_SIZE = 500
    }
}
