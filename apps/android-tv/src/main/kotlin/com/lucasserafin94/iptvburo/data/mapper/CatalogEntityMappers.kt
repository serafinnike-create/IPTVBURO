package com.lucasserafin94.iptvburo.data.mapper

import com.lucasserafin94.iptvburo.data.local.entity.CategoryEntity
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.SourceEntity
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType

fun SourceEntity.toDomain(): Source =
    Source(
        id = id,
        name = displayName,
        type = runCatching { SourceType.valueOf(type) }.getOrDefault(SourceType.LOCAL_M3U),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        channelCount = channelCount,
        subscriptionExpiresAtEpochSeconds = subscriptionExpiresAtEpochSeconds,
    )

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        sourceId = sourceId,
        name = name,
        sortOrder = sortOrder,
        contentType =
            runCatching { CatalogContentType.valueOf(contentType) }
                .getOrDefault(CatalogContentType.UNKNOWN),
        providerCategoryId = providerCategoryId,
    )

fun ChannelEntity.toDomain(): Channel =
    Channel(
        id = id,
        sourceId = sourceId,
        categoryId = categoryId,
        tvgId = tvgId,
        tvgName = tvgName,
        name = name,
        logoUri = logoUrl,
        streamUri = streamUrl,
        requestHeaders = buildMap {
            userAgent?.let { put("User-Agent", it) }
            referer?.let { put("Referer", it) }
            origin?.let { put("Origin", it) }
        },
        contentType =
            runCatching { CatalogContentType.valueOf(contentType) }
                .getOrDefault(CatalogContentType.UNKNOWN),
        providerItemId = providerItemId,
        containerExtension = containerExtension,
        year = year,
        rating = rating,
    )
