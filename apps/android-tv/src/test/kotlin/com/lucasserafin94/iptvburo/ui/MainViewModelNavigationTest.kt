package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.PlaylistImportResult
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import java.io.InputStream
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelNavigationTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `live without a source keeps live selected and shows a treated state`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.selectSection(AppSection.LIVE)

        assertEquals(AppSection.LIVE, viewModel.state.value.section)
        assertEquals(
            AppContent.SectionPlaceholder(AppSection.LIVE),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `live with a source opens its categories`() = runTest {
        val source =
            Source(
                id = "source-1",
                name = "Local playlist",
                type = SourceType.LOCAL_M3U,
                channelCount = 12,
            )
        val viewModel = createViewModel(sources = listOf(source))
        runCurrent()

        viewModel.selectSection(AppSection.LIVE)

        assertEquals(AppSection.LIVE, viewModel.state.value.section)
        assertEquals(
            AppContent.Categories(
                sourceId = source.id,
                sourceName = source.name,
            ),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `observed source hides playlist file extension`() = runTest {
        val source =
            Source(
                id = "source-1",
                name = "My authorized playlist.m3u8",
                type = SourceType.LOCAL_M3U,
                channelCount = 2,
            )
        val viewModel = createViewModel(sources = listOf(source))
        runCurrent()

        assertEquals(
            "My authorized playlist",
            viewModel.state.value.sources.single().name,
        )
    }

    @Test
    fun `unfinished ribbon sections always expose an explicit placeholder`() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        val unfinishedSections =
            listOf(
                AppSection.MOVIES,
                AppSection.SERIES,
                AppSection.DISCOVER,
                AppSection.MY_BURO,
                AppSection.SEARCH,
                AppSection.PROFILE,
            )

        unfinishedSections.forEach { section ->
            viewModel.selectSection(section)

            assertEquals(section, viewModel.state.value.section)
            assertEquals(
                AppContent.SectionPlaceholder(section),
                viewModel.state.value.content,
            )
        }
    }

    @Test
    fun `story remembers its item and back restores home`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.openStory("editorial-feature")

        assertEquals(
            AppContent.Story(itemId = "editorial-feature"),
            viewModel.state.value.content,
        )
        assertEquals("editorial-feature", viewModel.state.value.lastFocusedHomeItemId)

        assertTrue(viewModel.goBack())
        assertEquals(AppSection.HOME, viewModel.state.value.section)
        assertEquals(AppContent.Home, viewModel.state.value.content)
        assertEquals("editorial-feature", viewModel.state.value.lastFocusedHomeItemId)
    }

    @Test
    fun `source category channel and player back stack remains intact`() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        val source = SourceUi(id = "source-1", name = "Local playlist", channelCount = 1)
        val category = CategoryUi(id = "news", name = "News", channelCount = 1)
        val channel =
            ChannelUi(
                id = "channel-1",
                name = "Channel 1",
                categoryName = "News",
                streamUrl = "https://example.invalid/channel.m3u8",
                logoUrl = null,
            )

        viewModel.openSources()
        viewModel.openSource(source)
        viewModel.openCategory(category)
        viewModel.openChannel(channel)

        assertEquals(AppContent.Player(channel), viewModel.state.value.content)
        assertTrue(viewModel.goBack())
        assertTrue(viewModel.state.value.content is AppContent.Channels)
        assertTrue(viewModel.goBack())
        assertTrue(viewModel.state.value.content is AppContent.Categories)
        assertTrue(viewModel.goBack())
        assertEquals(AppContent.Sources, viewModel.state.value.content)
    }

    private fun TestScope.createViewModel(sources: List<Source> = emptyList()): MainViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return MainViewModel(
            contextProvider = Provider {
                error("Android context is not used by these navigation assertions")
            },
            catalogRepository = FakeCatalogRepository(sources),
            onboardingPreferences = FakeOnboardingPreferences,
            logger = NoOpLogger,
            ioDispatcher = dispatcher,
        )
    }
}

private class FakeCatalogRepository(
    private val sources: List<Source>,
) : CatalogRepository {
    override fun observeSources(): Flow<List<Source>> = flowOf(sources)

    override fun observeCategories(sourceId: String): Flow<List<Category>> = emptyFlow()

    override fun observeChannels(
        sourceId: String,
        categoryId: String?,
    ): Flow<List<Channel>> = emptyFlow()

    override suspend fun getChannel(id: String): Channel? = null

    override suspend fun importPlaylist(
        displayName: String,
        inputStream: InputStream,
    ): PlaylistImportResult = error("Not used by navigation tests")
}

private data object FakeOnboardingPreferences : OnboardingPreferences {
    override val accepted: Flow<Boolean> = flowOf(false)

    override suspend fun acceptLegalNotice() = Unit
}

private data object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String) = Unit

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}
