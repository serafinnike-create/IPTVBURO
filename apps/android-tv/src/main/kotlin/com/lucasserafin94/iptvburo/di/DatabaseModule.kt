package com.lucasserafin94.iptvburo.di

import android.content.Context
import androidx.room.Room
import com.lucasserafin94.iptvburo.data.local.IptvBuroDatabase
import com.lucasserafin94.iptvburo.data.local.dao.CategoryDao
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.local.dao.SourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): IptvBuroDatabase =
        Room.databaseBuilder(
            context,
            IptvBuroDatabase::class.java,
            DATABASE_NAME,
        ).build()

    @Provides
    fun provideSourceDao(database: IptvBuroDatabase): SourceDao = database.sourceDao()

    @Provides
    fun provideCategoryDao(database: IptvBuroDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideChannelDao(database: IptvBuroDatabase): ChannelDao = database.channelDao()

    private const val DATABASE_NAME = "iptv-buro.db"
}
