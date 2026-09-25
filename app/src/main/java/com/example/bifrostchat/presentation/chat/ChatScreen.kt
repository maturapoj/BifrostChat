package com.example.bifrostchat.presentation.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamStats
import com.example.bifrostchat.presentation.chat.components.InputBar
import com.example.bifrostchat.presentation.chat.components.MessageBubble
import com.example.bifrostchat.presentation.chat.components.ModelPicker
import com.example.bifrostchat.presentation.chat.components.SessionDrawer
import com.example.bifrostchat.presentation.chat.state.ChatEffect
import com.example.bifrostchat.presentation.chat.state.ChatIntent
import com.example.bifrostchat.presentation.chat.state.ChatState
import com.example.bifrostchat.presentation.chat.state.UiMessage
import com.example.bifrostchat.presentation.theme.BifrostChatTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatScreen(vm: ChatViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is ChatEffect.ModelsFailed -> {
                    val result = snackbar.showSnackbar(
                        message = "โหลด models ไม่ได้: ${effect.message}",
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.onIntent(ChatIntent.LoadModels)
                }
            }
        }
    }

    ChatContent(state = state, snackbar = snackbar, onIntent = vm::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
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
            )
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ModelPicker(state.modelGroups, state.selectedModel, enabled = !state.isStreaming) {
                        onIntent(ChatIntent.SelectModel(it.id))
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = { scope.launch { drawerState.open() } },
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    ) { Text("☰", fontSize = 20.sp) }
                },
                actions = {
                    TextButton(
                        onClick = { onIntent(ChatIntent.NewChat) },
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    ) { Text("New") }
                },
                // Navy bar: primary in light mode; in dark mode primary is light blue, so use the navy container.
                colors = if (isSystemInDarkTheme()) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                },
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
    BifrostChatTheme {
        ChatContent(
            state = ChatState(
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
