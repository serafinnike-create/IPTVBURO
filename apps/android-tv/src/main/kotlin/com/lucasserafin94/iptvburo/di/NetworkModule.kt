package com.lucasserafin94.iptvburo.di

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lucasserafin94.iptvburo.data.repository.DownloadRateReporter
import com.lucasserafin94.iptvburo.stalker.StalkerClient
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
    fun provideXtreamClient(downloadRateReporter: DownloadRateReporter): XtreamClient =
        XtreamClient(
            userAgent = XTREAM_USER_AGENT,
            // So a screen waiting on a long catalogue can say how fast it is arriving. Reported
            // from the thread reading the body; the reporter is what makes it safe to observe.
            onDownloadRate = downloadRateReporter::report,
        )

    /**
     * Stalker portals fingerprint the caller and reject anything that is not a set-top box, so the
     * client keeps its own MAG identity rather than reusing the app's user agent.
     */
    @Provides
    @Singleton
    fun provideStalkerClient(): StalkerClient = StalkerClient()

    private const val USER_AGENT = "IPTV BURO/0.2 Android"
    private const val XTREAM_USER_AGENT = "IPTV BURO/0.2 Android"
}
