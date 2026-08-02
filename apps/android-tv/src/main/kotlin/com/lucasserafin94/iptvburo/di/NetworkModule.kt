package com.lucasserafin94.iptvburo.di

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            // Never let a HTTPS playback request cross to cleartext through a redirect.
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideMediaDataSourceFactory(
        okHttpClient: OkHttpClient,
    ): OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(USER_AGENT)

    @Provides
    @Singleton
    fun provideXtreamClient(): XtreamClient =
        XtreamClient(userAgent = XTREAM_USER_AGENT)

    private const val USER_AGENT = "IPTV BURO/0.2 Android"
    private const val XTREAM_USER_AGENT = "IPTV BURO/0.2 Android"
}
