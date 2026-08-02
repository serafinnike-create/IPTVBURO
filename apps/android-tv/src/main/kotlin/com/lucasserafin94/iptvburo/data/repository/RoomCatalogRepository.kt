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
import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.playlist.M3uParser
import com.lucasserafin94.iptvburo.playlist.M3uParseSummary
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamEpisode
import java.io.InputStream
import java.nio.charset.StandardCharsets
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
    private val xtreamClient: XtreamClient,
    private val sourceConnectionStore: SourceConnectionStore,
    private val logger: AppLogger,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CatalogRepository {
    override fun observeSources(): Flow<List<Source>> =
        sourceDao.observeAll().map { entities -> entities.map(SourceEntity::toDomain) }

    override fun observeCategories(
        sourceId: String,
        contentType: CatalogContentType?,
    ): Flow<List<Category>> =
        categoryDao.observeForSource(sourceId, contentType?.name)
            .map { entities -> entities.map(CategoryEntity::toDomain) }

    override fun observeCategoryItemCounts(
        sourceId: String,
        contentType: CatalogContentType?,
    ): Flow<Map<String?, Int>> =
        categoryDao.observeItemCounts(sourceId, contentType?.name)
            .map { counts -> counts.associate { it.categoryId to it.itemCount } }

    override fun observeChannels(
        sourceId: String,
        categoryId: String?,
        contentType: CatalogContentType?,
    ): Flow<List<Channel>> =
        channelDao.observeForSource(sourceId, categoryId, contentType?.name)
            .map { entities -> entities.map(ChannelEntity::toDomain) }

    override suspend fun loadChannelsPage(
        sourceId: String,
        categoryId: String?,
        contentType: CatalogContentType?,
        offset: Int,
        limit: Int,
    ): CatalogPage = withContext(ioDispatcher) {
        require(offset >= 0) { "offset cannot be negative" }
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be between 1 and $MAX_PAGE_SIZE" }
        val type = contentType?.name
        val total = channelDao.countForSource(sourceId, categoryId, type)
        val items =
            channelDao.loadPage(sourceId, categoryId, type, limit, offset)
                .map(ChannelEntity::toDomain)
        CatalogPage(
            items = items,
            offset = offset,
            totalCount = total,
        )
    }

    override suspend fun loadChannelsPageAfter(
        sourceId: String,
        categoryId: String?,
        contentType: CatalogContentType?,
        cursor: CatalogCursor?,
        limit: Int,
    ): CatalogPage = withContext(ioDispatcher) {
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be between 1 and $MAX_PAGE_SIZE" }
        val type = contentType?.name
        val total = channelDao.countForSource(sourceId, categoryId, type)
        val entities =
            if (cursor?.sortOrder != null && cursor.itemId != null) {
                channelDao.loadPageAfter(
                    sourceId = sourceId,
                    categoryId = categoryId,
                    contentType = type,
                    afterSortOrder = cursor.sortOrder,
                    afterId = cursor.itemId,
                    limit = limit,
                )
            } else {
                channelDao.loadFirstPage(sourceId, categoryId, type, limit)
            }
        val loadedBefore = cursor?.loadedItemCount ?: 0
        val loadedAfter = loadedBefore + entities.size
        val last = entities.lastOrNull()
        CatalogPage(
            items = entities.map(ChannelEntity::toDomain),
            offset = loadedBefore,
            totalCount = total,
            nextCursor =
                if (last != null && loadedAfter < total) {
                    CatalogCursor(
                        sortOrder = last.sortOrder,
                        itemId = last.id,
                        loadedItemCount = loadedAfter,
                    )
                } else {
                    null
                },
        )
    }

    override suspend fun getChannel(id: String): Channel? = withContext(ioDispatcher) {
        val entity = channelDao.getById(id) ?: return@withContext null
        val channel = entity.toDomain()
        if (channel.contentType !in PLAYABLE_XTREAM_TYPES) {
            return@withContext channel
        }
        val providerItemId = channel.providerItemId ?: return@withContext channel
        val source = sourceDao.getById(channel.sourceId)
        if (source?.type != SourceType.XTREAM.name) {
            return@withContext channel
        }
        val credentials =
            sourceConnectionStore.readXtream(channel.sourceId)
                ?: throw IllegalStateException("The encrypted source connection is unavailable.")
        val url =
            xtreamClient.buildPlaybackUrl(
                credentials = credentials,
                contentType = channel.contentType.toXtreamContentType(),
                providerId = providerItemId,
                containerExtension =
                    channel.containerExtension
                        ?: source.preferredLiveExtension
                            ?.takeIf { channel.contentType == CatalogContentType.LIVE },
            )
        channel.copy(streamUri = url.toString())
    }

    override suspend fun findCompatibleMovieAlternative(
        sourceId: String,
        titlePrefix: String,
        excludeChannelId: String,
    ): Channel? = withContext(ioDispatcher) {
        channelDao.findCompatibleMovieAlternative(
            sourceId = sourceId,
            titlePrefix = titlePrefix.trim(),
            excludeChannelId = excludeChannelId,
    )?.toDomain()
    }

    override suspend fun loadForReleaseYear(
        sourceId: String,
        releaseYear: Int,
        limit: Int,
    ): List<Channel> = withContext(ioDispatcher) {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        require(releaseYear in 1888..currentYear + 3) { "Release year is outside the supported range." }
        require(limit in 1..MAX_PAGE_SIZE) { "Invalid release page size." }
        channelDao.loadForReleaseYear(sourceId, releaseYear, limit).map(ChannelEntity::toDomain)
    }

    override suspend fun loadRecentlyAdded(sourceId: String, limit: Int): List<Channel> =
        withContext(ioDispatcher) {
            require(limit in 1..MAX_PAGE_SIZE) { "Invalid recent page size." }
            channelDao.loadRecentlyAdded(sourceId, limit).map(ChannelEntity::toDomain)
        }

    override suspend fun loadShortEpg(sourceId: String, providerStreamId: String): LiveEpg =
        withContext(ioDispatcher) {
            require(providerStreamId.isNotBlank()) { "Provider stream ID is required." }
            val source = sourceDao.getById(sourceId) ?: return@withContext LiveEpg()
            if (source.type != SourceType.XTREAM.name) return@withContext LiveEpg()
            val credentials = sourceConnectionStore.readXtream(sourceId) ?: return@withContext LiveEpg()
            val (now, next) =
                xtreamClient.shortEpg(credentials, providerStreamId)
                    .nowAndNext(System.currentTimeMillis() / 1_000L)
            LiveEpg(
                now = now?.let { LiveProgram(it.title, it.description, it.startEpochSeconds, it.endEpochSeconds) },
                next = next?.let { LiveProgram(it.title, it.description, it.startEpochSeconds, it.endEpochSeconds) },
            )
        }

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

        val parserWarningCount =
            (parserSummary.warnings.size.toLong() + parserSummary.suppressedWarningCount)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        logger.info(
            TAG,
            "Playlist import completed: $importedChannelCount channels, " +
                "${mappingSession.categoryCount} categories, " +
                "$parserWarningCount parser warnings, " +
                "${mappingSession.skippedChannelCount} skipped",
        )

        PlaylistImportResult(
            sourceId = sourceId,
            importedChannelCount = importedChannelCount,
            importedCategoryCount = mappingSession.categoryCount,
            parserWarningCount = parserWarningCount,
            skippedChannelCount = mappingSession.skippedChannelCount,
        )
    }

    override suspend fun importXtream(
        request: XtreamImportRequest,
        onProgress: (XtreamImportStage) -> Unit,
    ): XtreamImportResult = withContext(ioDispatcher) {
        val displayName = request.displayName.trim()
        require(displayName.isNotEmpty()) { "The source display name cannot be blank." }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "The source display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters."
        }
        require(request.username.isNotBlank() && request.password.isNotBlank()) {
            "The Xtream username and password are required."
        }

        onProgress(XtreamImportStage.AUTHENTICATING)
        logger.info(TAG, "Xtream import started")
        val credentials =
            XtreamCredentials(
                serverUrl = request.serverUrl,
                username = request.username,
                password = request.password,
            )
        val account = xtreamClient.authenticate(credentials)
        currentCoroutineContext().ensureActive()
        require(account.authenticated) { "The Xtream account is not authenticated." }
        val preferredLiveExtension =
            account.allowedOutputFormats.preferredLiveExtension()

        val sourceId = UUID.randomUUID().toString()
        val importedAt = System.currentTimeMillis()
        val categoriesByProviderKey = linkedMapOf<String, CategoryEntity>()
        var categorySortOrder = 0
        var skippedItemCount = 0
        onProgress(XtreamImportStage.CATEGORIES)
        for (type in XtreamContentType.entries) {
            currentCoroutineContext().ensureActive()
            val collection = xtreamClient.categories(credentials, type)
            currentCoroutineContext().ensureActive()
            skippedItemCount += collection.skippedItemCount
            for (category in collection.items) {
                val contentType = type.toCatalogContentType()
                val key = categoryKey(contentType, category.providerId)
                categoriesByProviderKey[key] =
                    CategoryEntity(
                        id = stableId("$sourceId:xtream:category:$key"),
                        sourceId = sourceId,
                        name = category.name,
                        sortOrder = categorySortOrder++,
                        contentType = contentType.name,
                        providerCategoryId = category.providerId,
                    )
            }
        }

        val counts = mutableMapOf<XtreamContentType, Int>()
        val importContext = currentCoroutineContext()
        onProgress(XtreamImportStage.SAVING)
        try {
            importContext.ensureActive()
            sourceConnectionStore.saveXtream(sourceId, credentials)
            importContext.ensureActive()
            database.runInTransaction(
                Callable {
                    sourceDao.upsertBlocking(
                        SourceEntity(
                            id = sourceId,
                            displayName = displayName,
                            type = SourceType.XTREAM.name,
                            createdAtEpochMillis = importedAt,
                            updatedAtEpochMillis = importedAt,
                            channelCount = 0,
                            preferredLiveExtension = preferredLiveExtension,
                        ),
                    )
                    categoriesByProviderKey.values
                        .chunked(INSERT_BATCH_SIZE)
                        .forEach(categoryDao::upsertAllBlocking)

                    val channelBatch = ArrayList<ChannelEntity>(INSERT_BATCH_SIZE)
                    var totalItemCount = 0
                    for (type in XtreamContentType.entries) {
                        importContext.ensureActive()
                        onProgress(
                            when (type) {
                                XtreamContentType.LIVE -> XtreamImportStage.LIVE
                                XtreamContentType.MOVIE -> XtreamImportStage.MOVIES
                                XtreamContentType.SERIES -> XtreamImportStage.SERIES
                            },
                        )
                        var sortOrder = 0
                        val summary =
                            xtreamClient.streamCatalog(credentials, type) { item ->
                                importContext.ensureActive()
                                channelBatch +=
                                    item.toEntity(
                                        sourceId = sourceId,
                                        sortOrder = sortOrder++,
                                        categoryId =
                                            item.categoryIds.firstNotNullOfOrNull { providerCategoryId ->
                                                categoriesByProviderKey[
                                                    categoryKey(
                                                        type.toCatalogContentType(),
                                                        providerCategoryId,
                                                    ),
                                                ]?.id
                                            },
                                    )
                                if (channelBatch.size == INSERT_BATCH_SIZE) {
                                    channelDao.insertAllBlocking(channelBatch)
                                    channelBatch.clear()
                                }
                            }
                        if (channelBatch.isNotEmpty()) {
                            channelDao.insertAllBlocking(channelBatch)
                            channelBatch.clear()
                        }
                        counts[type] = summary.itemCount
                        skippedItemCount += summary.skippedItemCount
                        totalItemCount += summary.itemCount
                    }
                    require(totalItemCount > 0) {
                        "The Xtream account returned an empty catalog."
                    }
                    sourceDao.upsertBlocking(
                        SourceEntity(
                            id = sourceId,
                            displayName = displayName,
                            type = SourceType.XTREAM.name,
                            createdAtEpochMillis = importedAt,
                            updatedAtEpochMillis = importedAt,
                            channelCount = totalItemCount,
                            preferredLiveExtension = preferredLiveExtension,
                        ),
                    )
                },
            )
        } catch (error: Exception) {
            runCatching { sourceConnectionStore.remove(sourceId) }
            if (error !is CancellationException) {
                logger.error(TAG, "Xtream import failed during secure persistence", error)
            }
            throw error
        }

        val result =
            XtreamImportResult(
                sourceId = sourceId,
                liveCount = counts[XtreamContentType.LIVE] ?: 0,
                movieCount = counts[XtreamContentType.MOVIE] ?: 0,
                seriesCount = counts[XtreamContentType.SERIES] ?: 0,
                categoryCount = categoriesByProviderKey.size,
                skippedItemCount = skippedItemCount,
            )
        logger.info(
            TAG,
            "Xtream import completed: ${result.totalItemCount} catalog items, " +
                "${result.categoryCount} categories, ${result.skippedItemCount} skipped",
        )
        result
    }

    override suspend fun loadSeriesDetails(
        sourceId: String,
        providerSeriesId: String,
    ): SeriesDetails = withContext(ioDispatcher) {
        val source = sourceDao.getById(sourceId)
        require(source?.type == SourceType.XTREAM.name) {
            "Series details require an Xtream source."
        }
        val credentials =
            sourceConnectionStore.readXtream(sourceId)
                ?: throw IllegalStateException("The encrypted source connection is unavailable.")
        val details = xtreamClient.seriesDetails(credentials, providerSeriesId)
        SeriesDetails(
            sourceId = sourceId,
            providerSeriesId = providerSeriesId,
            title = details.title,
            plot = details.plot,
            artworkUri = details.artworkUrl,
            backdropUris = details.backdropUrls,
            cast = details.cast,
            director = details.director,
            genre = details.genre,
            releaseDate = details.releaseDate,
            rating = details.rating,
            youtubeTrailerId = details.youtubeTrailerId,
            episodes =
                details.episodes.map { episode ->
                    Episode(
                        id = stableId("$sourceId:xtream:episode:${episode.providerId}"),
                        sourceId = sourceId,
                        providerEpisodeId = episode.providerId,
                        title = episode.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        artworkUri = episode.artworkUrl,
                        containerExtension = episode.containerExtension,
                    )
                },
        )
    }

    override suspend fun loadMovieDetails(
        sourceId: String,
        providerMovieId: String,
    ): MovieDetails = withContext(ioDispatcher) {
        val source = sourceDao.getById(sourceId)
        require(source?.type == SourceType.XTREAM.name) {
            "Movie details require an Xtream source."
        }
        val credentials =
            sourceConnectionStore.readXtream(sourceId)
                ?: throw IllegalStateException("The encrypted source connection is unavailable.")
        val details = xtreamClient.movieDetails(credentials, providerMovieId)
        MovieDetails(
            sourceId = sourceId,
            providerMovieId = providerMovieId,
            title = details.title,
            plot = details.plot,
            cast = details.cast,
            director = details.director,
            genre = details.genre,
            duration = details.duration,
            releaseDate = details.releaseDate,
            country = details.country,
            rating = details.rating,
            artworkUri = details.artworkUrl,
            backdropUris = details.backdropUrls,
            youtubeTrailerId = details.youtubeTrailerId,
        )
    }

    override suspend fun resolveEpisode(episode: Episode): Channel = withContext(ioDispatcher) {
        require(episode.providerEpisodeId.isNotBlank()) {
            "The selected episode is unavailable."
        }
        val source = sourceDao.getById(episode.sourceId)
        require(source?.type == SourceType.XTREAM.name) {
            "Episode playback requires an Xtream source."
        }
        val credentials =
            sourceConnectionStore.readXtream(episode.sourceId)
                ?: throw IllegalStateException("The source connection is unavailable.")
        val playbackUrl =
            xtreamClient.buildEpisodePlaybackUrl(
                credentials = credentials,
                episode =
                    XtreamEpisode(
                        providerId = episode.providerEpisodeId,
                        title = episode.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        containerExtension = episode.containerExtension,
                        artworkUrl = episode.artworkUri,
                    ),
            )
        Channel(
            id = episode.id,
            sourceId = episode.sourceId,
            name = episode.title,
            streamUri = playbackUrl.toString(),
            logoUri = episode.artworkUri,
            contentType = CatalogContentType.EPISODE,
            providerItemId = episode.providerEpisodeId,
            containerExtension = episode.containerExtension,
        )
    }

    private fun XtreamCatalogItem.toEntity(
        sourceId: String,
        sortOrder: Int,
        categoryId: String?,
    ): ChannelEntity {
        val catalogType = contentType.toCatalogContentType()
        return ChannelEntity(
            id = stableId("$sourceId:xtream:item:${catalogType.name}:$providerId"),
            sourceId = sourceId,
            categoryId = categoryId,
            tvgId = null,
            tvgName = null,
            name = name,
            logoUrl = artworkUrl,
            streamUrl =
                "xtream://catalog/$sourceId/${catalogType.name.lowercase()}/$providerId",
            userAgent = null,
            referer = null,
            origin = null,
            sortOrder = sortOrder,
            contentType = catalogType.name,
            providerItemId = providerId,
            containerExtension = containerExtension,
            year = year,
            rating = rating,
            addedAtEpochSeconds = addedAtEpochSeconds,
        )
    }

    private fun XtreamContentType.toCatalogContentType(): CatalogContentType =
        when (this) {
            XtreamContentType.LIVE -> CatalogContentType.LIVE
            XtreamContentType.MOVIE -> CatalogContentType.MOVIE
            XtreamContentType.SERIES -> CatalogContentType.SERIES
        }

    private fun CatalogContentType.toXtreamContentType(): XtreamContentType =
        when (this) {
            CatalogContentType.LIVE -> XtreamContentType.LIVE
            CatalogContentType.MOVIE -> XtreamContentType.MOVIE
            CatalogContentType.SERIES -> XtreamContentType.SERIES
            CatalogContentType.EPISODE,
            CatalogContentType.UNKNOWN,
            -> throw IllegalArgumentException("The catalog item is not an Xtream root item.")
        }

    private fun categoryKey(
        contentType: CatalogContentType,
        providerCategoryId: String,
    ): String = "${contentType.name}:$providerCategoryId"

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun Set<String>.preferredLiveExtension(): String? =
        when {
            "ts" in this -> "ts"
            "m3u8" in this -> "m3u8"
            else -> null
        }

    private companion object {
        const val TAG = "CatalogRepository"
        const val MAX_DISPLAY_NAME_LENGTH = 120
        const val INSERT_BATCH_SIZE = 500
        const val MAX_PAGE_SIZE = 500
        val PLAYABLE_XTREAM_TYPES =
            setOf(CatalogContentType.LIVE, CatalogContentType.MOVIE)
    }
}
