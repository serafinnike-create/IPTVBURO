package com.lucasserafin94.iptvburo.di

import com.lucasserafin94.iptvburo.playlist.M3uParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {
    @Provides
    @Singleton
    fun provideM3uParser(): M3uParser = M3uParser()
}
