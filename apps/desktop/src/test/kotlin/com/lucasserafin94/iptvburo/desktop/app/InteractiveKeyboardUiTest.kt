package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InteractiveKeyboardUiTest {
    @Test
    fun `focused navigation row activates once on Enter`() = runComposeUiTest {
        var activations = 0
        setContent {
            BuroInteractiveRow(
                onClick = { activations++ },
                selected = false,
                modifier = Modifier.testTag("row"),
            ) { Text("Library") }
        }
        onNodeWithTag("row").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("row").performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(1, activations) }
    }

    @Test
    fun `focused content card activates once on Space`() = runComposeUiTest {
        var activations = 0
        setContent {
            BuroInteractiveSurface(
                onClick = { activations++ },
                modifier = Modifier.testTag("card"),
            ) { Text("Resume") }
        }
        onNodeWithTag("card").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("card").performKeyInput { pressKey(Key.Spacebar) }
        runOnIdle { assertEquals(1, activations) }
    }
}
