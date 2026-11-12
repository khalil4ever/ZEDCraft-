package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.local.ZdChatMessageEntity
import com.example.ui.screens.ZdAssistantContent
import com.example.ui.theme.ZEDCraftTheme
import com.example.ui.viewmodel.ZedCraftUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZdAssistantUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `composer submits prompt and disables send until text entered`() {
        var sentPrompt: String? = null
        composeTestRule.setContent {
            ZEDCraftTheme {
                ZdAssistantContent(
                    uiState = ZedCraftUiState(isAppReadyForContent = true),
                    chatMessages = listOf(
                        ZdChatMessageEntity(
                            id = 1,
                            role = "assistant",
                            content = "Welcome to **ZD**! Ask me anything.",
                            createdAtMs = 1000L
                        )
                    ),
                    onSendPrompt = { sentPrompt = it },
                    onStopGeneration = {},
                    onRetryLastPrompt = {},
                    onDismissError = {},
                    onClearChat = {},
                    onSaveToVault = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zd_send_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("zd_prompt_input").performTextInput("How do I improve my CTR?")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zd_send_button").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals("How do I improve my CTR?", sentPrompt)
    }

    @Test
    fun `thinking state shows indicator and stop button that cancels generation`() {
        var stopClicked = false
        composeTestRule.setContent {
            ZEDCraftTheme {
                ZdAssistantContent(
                    uiState = ZedCraftUiState(
                        isAppReadyForContent = true,
                        isZdThinking = true,
                        streamingZdMessage = null
                    ),
                    chatMessages = emptyList(),
                    onSendPrompt = {},
                    onStopGeneration = { stopClicked = true },
                    onRetryLastPrompt = {},
                    onDismissError = {},
                    onClearChat = {},
                    onSaveToVault = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zd_thinking_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zd_stop_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(stopClicked)
    }

    @Test
    fun `error card renders real error message with working retry and dismiss buttons`() {
        var retryClicked = false
        var dismissClicked = false
        val realError = "Gemini API key was rejected (HTTP 403). Verify GEMINI_API_KEY in AI Studio Secrets."

        composeTestRule.setContent {
            ZEDCraftTheme {
                ZdAssistantContent(
                    uiState = ZedCraftUiState(
                        isAppReadyForContent = true,
                        isZdThinking = false,
                        zdError = realError
                    ),
                    chatMessages = emptyList(),
                    onSendPrompt = {},
                    onStopGeneration = {},
                    onRetryLastPrompt = { retryClicked = true },
                    onDismissError = { dismissClicked = true },
                    onClearChat = {},
                    onSaveToVault = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("zd_friendly_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(realError).assertIsDisplayed()
        composeTestRule.onNodeWithTag("zd_retry_button").performClick()
        composeTestRule.onNodeWithTag("zd_dismiss_error_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(retryClicked)
        assertTrue(dismissClicked)
    }
}
