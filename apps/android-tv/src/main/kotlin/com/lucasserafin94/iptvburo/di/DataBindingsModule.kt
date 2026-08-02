package com.lucasserafin94.iptvburo.di

import com.lucasserafin94.iptvburo.core.logging.AndroidAppLogger
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.preferences.DataStoreOnboardingPreferences
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.RoomCatalogRepository
import com.lucasserafin94.iptvburo.data.security.AndroidKeystoreSourceConnectionStore
import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
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
    abstract fun bindCatalogRepository(
        implementation: RoomCatalogRepository,
    ): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindSourceConnectionStore(
        implementation: AndroidKeystoreSourceConnectionStore,
    ): SourceConnectionStore
}
