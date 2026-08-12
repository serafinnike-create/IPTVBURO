package com.lucasserafin94.iptvburo.di

import com.lucasserafin94.iptvburo.core.logging.AndroidAppLogger
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.discovery.DataStoreShelfCache
import com.lucasserafin94.iptvburo.data.discovery.ShelfCache
import com.lucasserafin94.iptvburo.data.preferences.CatalogueGuard
import com.lucasserafin94.iptvburo.data.preferences.CatalogueGuardPreferences
import com.lucasserafin94.iptvburo.data.preferences.DataStoreOnboardingPreferences
import com.lucasserafin94.iptvburo.data.preferences.SubtitlePreferences
import com.lucasserafin94.iptvburo.data.preferences.SubtitleSettings
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseClient
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.RoomCatalogRepository
import com.lucasserafin94.iptvburo.data.security.AndroidKeystoreSourceConnectionStore
import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
import com.lucasserafin94.iptvburo.data.security.AndroidMetadataKeyStore
import com.lucasserafin94.iptvburo.data.security.MetadataKeyStore
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressRepository
import com.lucasserafin94.iptvburo.playback.RoomPlaybackProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun bindAppLogger(implementation: AndroidAppLogger): AppLogger

    @Binds
    abstract fun bindOnboardingPreferences(
        implementation: DataStoreOnboardingPreferences,
    ): OnboardingPreferences

    @Binds
    abstract fun bindShelfCache(
        implementation: DataStoreShelfCache,
    ): ShelfCache

    @Binds
    abstract fun bindCatalogueGuard(
        implementation: CatalogueGuardPreferences,
    ): CatalogueGuard

    @Binds
    abstract fun bindSubtitleSettings(
        implementation: SubtitlePreferences,
    ): SubtitleSettings

    @Binds
    abstract fun bindCatalogRepository(
        implementation: RoomCatalogRepository,
    ): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindSourceConnectionStore(
        implementation: AndroidKeystoreSourceConnectionStore,
    ): SourceConnectionStore

    @Binds
    @Singleton
    abstract fun bindLicenseService(implementation: AndroidLicenseClient): AndroidLicenseService

    @Binds
    @Singleton
    abstract fun bindMetadataKeyStore(implementation: AndroidMetadataKeyStore): MetadataKeyStore

    @Binds
    @Singleton
    abstract fun bindPlaybackProgressRepository(
        implementation: RoomPlaybackProgressRepository,
    ): PlaybackProgressRepository
}
