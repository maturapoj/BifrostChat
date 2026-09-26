package io.github.maturapoj.tokenflow.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maturapoj.tokenflow.R
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.presentation.chat.ChatContent
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatIntent
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatState
import io.github.maturapoj.tokenflow.presentation.chat.state.UiMessage
import io.github.maturapoj.tokenflow.presentation.settings.Preset
import io.github.maturapoj.tokenflow.presentation.settings.SettingsContent
import io.github.maturapoj.tokenflow.presentation.settings.SettingsIntent
import io.github.maturapoj.tokenflow.presentation.settings.SettingsState
import io.github.maturapoj.tokenflow.presentation.theme.TokenFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for the stateless screens: render a state, act, check the intents sent back. */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule val compose = createComposeRule()

    private fun text(@StringRes id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun unconfiguredChatPromptsForSettings() {
        var opened = false
        compose.setContent {
            TokenFlowTheme { ChatContent(ChatState(isConfigured = false), onIntent = {}, onOpenSettings = { opened = true }) }
        }

        compose.onNodeWithText(text(R.string.setup_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.open_settings)).performClick()
        assertTrue(opened)
    }

    @Test fun typingAndSendingSendsTheText() {
        val intents = mutableListOf<ChatIntent>()
        compose.setContent { TokenFlowTheme { ChatContent(ChatState(), onIntent = { intents += it }) } }

        compose.onNodeWithText(text(R.string.message_hint)).performTextInput("hello")
        compose.onNodeWithText(text(R.string.send)).performClick()

        assertEquals(listOf<ChatIntent>(ChatIntent.Send("hello")), intents)
    }

    @Test fun streamingShowsStopAndTheReply() {
        val intents = mutableListOf<ChatIntent>()
        val state = ChatState(
            isStreaming = true,
            messages = listOf(
                UiMessage(0, Role.User, "q"),
                UiMessage(1, Role.Assistant, "Partial **answer**", isStreaming = true),
            ),
        )
        compose.setContent { TokenFlowTheme { ChatContent(state, onIntent = { intents += it }) } }

        compose.onNodeWithText("Partial answer", substring = true).assertIsDisplayed() // Markdown rendered, not raw
        compose.onNodeWithText(text(R.string.stop)).performClick()
        assertEquals(listOf<ChatIntent>(ChatIntent.Stop), intents)
    }

    @Test fun settingsShowsInvalidUrlAndSaves() {
        val intents = mutableListOf<SettingsIntent>()
        compose.setContent {
            TokenFlowTheme {
                SettingsContent(SettingsState(baseUrl = "api.openai.com", invalidUrl = true), onIntent = { intents += it }, onBack = {})
            }
        }

        compose.onNodeWithText(text(R.string.base_url_invalid)).assertIsDisplayed()
        compose.onNodeWithText("OpenRouter").performClick()
        compose.onNodeWithText(text(R.string.save_and_test)).performClick()

        assertEquals(
            listOf(SettingsIntent.UsePreset(Preset.OpenRouter), SettingsIntent.Save),
            intents,
        )
    }
}
