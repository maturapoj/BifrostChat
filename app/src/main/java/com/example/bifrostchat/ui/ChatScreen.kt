package com.example.bifrostchat.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // reverseLayout anchors index 0 (newest) to the bottom, so a growing
    // message and the keyboard opening keep the stream in view.
    LaunchedEffect(state.messages.size) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ModelPicker(state.models, state.selectedModel, enabled = !state.isStreaming, onSelect = vm::selectModel) },
                actions = { TextButton(onClick = vm::clear) { Text("Clear") } },
            )
        },
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp).clickable { vm.loadModels() },
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages.asReversed(), key = { it.id }) { MessageBubble(it) }
            }
            InputBar(isStreaming = state.isStreaming, onSend = vm::send, onStop = vm::stop)
        }
    }
}

@Composable
private fun ModelPicker(models: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = (selected.ifEmpty { "Loading models…" }) + " ▾",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = enabled) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(text = { Text(model) }, onClick = { onSelect(model); expanded = false })
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
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
