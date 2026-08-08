package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * A settings panel whose lower half cannot be reached.
 *
 * Reported twice, and both times it was the same mistake in a different place: a scrollable child
 * given unbounded height. An unweighted `Column` child is measured against infinity, so a
 * scrollable one lays its whole content out past the bottom of the window and never scrolls at
 * all — the settings below the fold exist, are composed, and are drawn where nothing can reach
 * them.
 *
 * The comments in `SettingsDialog` say "never fillMaxSize, always weight(1f)". This makes that a
 * fact rather than a note somebody has to read.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollableSettingsUiTest {
    /** The correct shape: a fixed header, then the scrolling area as the weighted child. */
    @Composable
    private fun WeightedPanel() {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Configurações")
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val state = rememberLazyListState()
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("settings-list")) {
                    items(SECTION_COUNT) { index ->
                        Text(text = "Secção $index", modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    /**
     * The last section has to be reachable.
     *
     * With enough sections to overflow any window, `performScrollToNode` fails outright if the list
     * is not actually scrollable — which is precisely the bug.
     */
    @Test
    fun `every section can be scrolled to`() =
        runComposeUiTest {
            setContent { WeightedPanel() }

            onNodeWithTag("settings-list").performScrollToNode(hasText("Secção ${SECTION_COUNT - 1}"))

            onNodeWithText("Secção ${SECTION_COUNT - 1}").assertIsDisplayed()
        }

    /** And the first is still there afterwards, so scrolling moved rather than replaced. */
    @Test
    fun `scrolling back reaches the first section`() =
        runComposeUiTest {
            setContent { WeightedPanel() }

            onNodeWithTag("settings-list").performScrollToNode(hasText("Secção ${SECTION_COUNT - 1}"))
            onNodeWithTag("settings-list").performScrollToNode(hasText("Secção 0"))

            onNodeWithText("Secção 0").assertIsDisplayed()
        }

    private companion object {
        /** Far more than fits any window, so the assertion cannot pass by the content being short. */
        const val SECTION_COUNT = 40
    }
}
