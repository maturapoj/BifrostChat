package io.github.maturapoj.tokenflow.presentation.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maturapoj.tokenflow.R
import io.github.maturapoj.tokenflow.domain.model.LlmModel
import io.github.maturapoj.tokenflow.domain.model.ModelGroup
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.StreamStats
import io.github.maturapoj.tokenflow.presentation.chat.components.InputBar
import io.github.maturapoj.tokenflow.presentation.chat.components.MessageBubble
import io.github.maturapoj.tokenflow.presentation.chat.components.ModelPicker
import io.github.maturapoj.tokenflow.presentation.chat.components.SessionDrawer
import io.github.maturapoj.tokenflow.presentation.chat.components.SetupPrompt
import io.github.maturapoj.tokenflow.presentation.chat.components.message
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatEffect
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatIntent
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatState
import io.github.maturapoj.tokenflow.presentation.chat.state.UiMessage
import io.github.maturapoj.tokenflow.presentation.theme.TokenFlowTheme
import io.github.maturapoj.tokenflow.presentation.theme.navyTopAppBarColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatScreen(onOpenSettings: () -> Unit, vm: ChatViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is ChatEffect.ModelsFailed -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.models_load_failed, effect.error.message(resources)),
                        actionLabel = resources.getString(R.string.retry),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.onIntent(ChatIntent.LoadModels)
                }
            }
        }
    }

    ChatContent(state = state, snackbar = snackbar, onIntent = vm::onIntent, onOpenSettings = onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    onOpenSettings: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // reverseLayout anchors index 0 (newest) to the bottom, so a growing
    // message and the keyboard opening keep the stream in view.
    LaunchedEffect(state.messages.size) { listState.scrollToItem(0) }

    // Back closes the drawer first instead of leaving the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = state.sessions,
                currentId = state.currentSessionId,
                onIntent = { intent ->
                    onIntent(intent)
                    if (intent !is ChatIntent.DeleteSession) scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ModelPicker(
                        state.modelGroups,
                        state.selectedModel,
                        enabled = !state.isStreaming,
                        placeholder = stringResource(if (state.isConfigured) R.string.loading_models else R.string.app_name),
                    ) {
                        onIntent(ChatIntent.SelectModel(it.id))
                    }
                },
                navigationIcon = {
                    val openChats = stringResource(R.string.open_chats)
                    TextButton(
                        onClick = { scope.launch { drawerState.open() } },
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                        modifier = Modifier.semantics { contentDescription = openChats },
                    ) { Text("☰", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {}) }
                },
                actions = {
                    TextButton(
                        onClick = { onIntent(ChatIntent.NewChat) },
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    ) { Text(stringResource(R.string.new_chat_action)) }
                },
                colors = navyTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages.asReversed(), key = { it.id }) { MessageBubble(it) }
                if (!state.isConfigured) item(key = "setup") { SetupPrompt(onOpenSettings) }
            }
            InputBar(
                isStreaming = state.isStreaming,
                onSend = { onIntent(ChatIntent.Send(it)) },
                onStop = { onIntent(ChatIntent.Stop) },
            )
        }
    }
    }
}

@Preview
@Composable
private fun ChatContentPreview() {
    TokenFlowTheme {
        ChatContent(
            state = ChatState(
                selectedModelId = "dashscope/deepseek-v4-flash-0731",
                modelGroups = listOf(
                    ModelGroup("dashscope", listOf(LlmModel("dashscope/deepseek-v4-flash-0731", "dashscope", "deepseek-v4-flash-0731"))),
                ),
                messages = listOf(
                    UiMessage(id = 0, role = Role.User, content = "Why is the sky blue?"),
                    UiMessage(
                        id = 1,
                        role = Role.Assistant,
                        reasoning = "Rayleigh scattering.",
                        content = "Air scatters **short** wavelengths more.\n\n```kotlin\nval sky = Color.Blue\n```",
                        isStreaming = true,
                        stats = StreamStats(timeToFirstToken = 1_200.milliseconds, chunks = 3),
                    ),
                ),
                isStreaming = true,
            ),
            onIntent = {},
        )
    }
}
