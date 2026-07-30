package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeSources(): Flow<List<Source>>

    fun observeCategories(sourceId: String): Flow<List<Category>>

    fun observeChannels(
        sourceId: String,
        categoryId: String? = null,
    ): Flow<List<Channel>>

    suspend fun getChannel(id: String): Channel?

    suspend fun importPlaylist(
        displayName: String,
        inputStream: InputStream,
    ): PlaylistImportResult
}

data class PlaylistImportResult(
    val sourceId: String,
    val importedChannelCount: Int,
    val importedCategoryCount: Int,
    val parserWarningCount: Int,
    val skippedChannelCount: Int,
)
