package com.example.bifrostchat.presentation.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup
import com.example.bifrostchat.domain.model.Role
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

    // reverseLayout anchors index 0 (newest) to the bottom, so a growing
    // message and the keyboard opening keep the stream in view.
    LaunchedEffect(state.messages.size) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ModelPicker(state.modelGroups, state.selectedModel, enabled = !state.isStreaming) {
                        onIntent(ChatIntent.SelectModel(it.id))
                    }
                },
                actions = { TextButton(onClick = { onIntent(ChatIntent.Clear) }) { Text("Clear") } },
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

@Preview
@Composable
private fun ChatContentPreview() {
    MaterialTheme {
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
                        content = "Air scatters short blue wavelengths more",
                        isStreaming = true,
                        stats = StreamStats(timeToFirstTokenMs = 1200, chunks = 3),
                    ),
                ),
                isStreaming = true,
            ),
            onIntent = {},
        )
    }
}

@Composable
private fun ModelPicker(
    groups: List<ModelGroup>,
    selected: LlmModel?,
    enabled: Boolean,
    onSelect: (LlmModel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.clickable(enabled = enabled) { expanded = true }) {
            Text(
                text = (selected?.name ?: "Loading models…") + " ▾",
                style = MaterialTheme.typography.titleMedium,
            )
            selected?.let {
                Text(it.provider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider()
                Text(
                    group.provider,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                group.models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.name) },
                        trailingIcon = { if (model.id == selected?.id) Text("✓") },
                        onClick = { onSelect(model); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == Role.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp)
                .animateContentSize(),
        ) {
            if (msg.reasoning.isNotEmpty()) ReasoningBlock(msg.reasoning, stillThinking = msg.isStreaming && msg.content.isEmpty())

            val cursor = if (msg.isStreaming) " ▍" else ""
            if (msg.content.isNotEmpty() || (msg.isStreaming && msg.reasoning.isEmpty())) {
                Text(msg.content + cursor)
            }
            msg.error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
            msg.stats?.let { StatsLine(it) }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String, stillThinking: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            (if (stillThinking) "Thinking…" else "Thought") + if (expanded) " ▴" else " ▾",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Text(
                text,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StatsLine(s: StreamStats) {
    val parts = buildList {
        s.timeToFirstTokenMs?.let { add("TTFT ${it}ms") }
        add("${s.chunks} chunks")
        s.completionTokens?.let { add("$it tok" + (s.reasoningTokens?.takeIf { r -> r > 0 }?.let { r -> " ($r think)" } ?: "")) }
        s.totalMs?.let { add("total ${it}ms") }
        s.tokensPerSecond?.let { add("%.1f tok/s".format(it)) }
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun InputBar(isStreaming: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
        )
        if (isStreaming) {
            OutlinedButton(onClick = onStop, modifier = Modifier.padding(start = 8.dp)) { Text("Stop") }
        } else {
            Button(
                onClick = { onSend(text); text = "" },
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Send") }
        }
    }
}
