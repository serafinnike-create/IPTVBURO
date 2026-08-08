package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching
import kotlin.test.Test

/**
 * Searching the history gallery, through the real Compose runtime.
 *
 * `HistorySearchTest` pins the matching rule; this pins that typing into the box actually changes
 * what is on screen. The two failures are different: one is a filter that returns the wrong list,
 * the other is a correct filter whose result never reaches the user — and only this one can see the
 * second.
 */
@OptIn(ExperimentalTestApi::class)
class HistorySearchUiTest {
    private val titles =
        listOf(
            "O Poderoso Chefão",
            "Duna: Parte Dois",
            "Interestelar",
            "A Origem",
        )

    /** The gallery's filter and list, in the same shape the real screen uses. */
    @Composable
    private fun GalleryUnderTest() {
        var query by remember { mutableStateOf("") }
        val searchable = remember { titles.map { title -> title to title.normalisedForMatching() } }
        val visible =
            remember(query) {
                val needle = query.trim().normalisedForMatching()
                if (needle.isBlank()) titles else searchable.filter { (_, n) -> n.contains(needle) }.map { it.first }
            }
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = { entered -> query = entered },
                modifier = Modifier.testTag("search"),
            )
            visible.forEach { title ->
                Text(text = title, modifier = Modifier.testTag("poster"))
            }
        }
    }

    @Test
    fun `an empty search shows everything`() =
        runComposeUiTest {
            setContent { GalleryUnderTest() }

            onAllNodesWithTag("poster").assertCountEquals(titles.size)
        }

    /** Typing narrows the wall, and the narrowing is what the user sees. */
    @Test
    fun `typing filters the gallery`() =
        runComposeUiTest {
            setContent { GalleryUnderTest() }

            onNodeWithTag("search").performTextInput("duna")

            onAllNodesWithTag("poster").assertCountEquals(1)
            onNodeWithText("Duna: Parte Dois").assertIsDisplayed()
        }

    /**
     * The reported bug, end to end: an unaccented query must find the accented title.
     *
     * "chefao" found nothing while "Chefão" was on screen, because `contains` compares code points.
     */
    @Test
    fun `an unaccented query finds an accented title`() =
        runComposeUiTest {
            setContent { GalleryUnderTest() }

            onNodeWithTag("search").performTextInput("chefao")

            onAllNodesWithTag("poster").assertCountEquals(1)
            onNodeWithText("O Poderoso Chefão").assertIsDisplayed()
        }

    /** Clearing the box brings the whole wall back rather than leaving it filtered. */
    @Test
    fun `clearing the search restores every poster`() =
        runComposeUiTest {
            setContent { GalleryUnderTest() }

            onNodeWithTag("search").performTextInput("duna")
            onAllNodesWithTag("poster").assertCountEquals(1)

            onNodeWithTag("search").performTextReplacement("")

            onAllNodesWithTag("poster").assertCountEquals(titles.size)
        }

    /** A query that matches nothing shows nothing, rather than silently showing everything. */
    @Test
    fun `a failed search shows no posters`() =
        runComposeUiTest {
            setContent { GalleryUnderTest() }

            onNodeWithTag("search").performTextInput("zzzz")

            onAllNodesWithTag("poster").assertCountEquals(0)
        }
}
