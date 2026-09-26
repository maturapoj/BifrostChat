package com.example.bifrostchat.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bifrostchat.R
import com.example.bifrostchat.domain.model.Role
import com.example.bifrostchat.domain.model.StreamStats
import com.example.bifrostchat.presentation.chat.markdown.MarkdownText
import com.example.bifrostchat.presentation.chat.state.UiMessage
import com.example.bifrostchat.presentation.chat.streaming.rememberSmoothReveal
import kotlin.math.max

@Composable
internal fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == Role.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            // Assistant replies take the full width so code blocks have room.
            (if (isUser) Modifier.widthIn(max = 340.dp) else Modifier.fillMaxWidth())
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp),
        ) {
            if (isUser) {
                Text(msg.content)
            } else {
                AssistantContent(msg)
            }
            msg.error?.let { Text("⚠ " + it.message(), color = MaterialTheme.colorScheme.error) }
            msg.stats?.let { StatsLine(it) }
        }
    }
}

@Composable
private fun AssistantContent(msg: UiMessage) {
    val reasoning = rememberSmoothReveal(msg.reasoning)
    val content = rememberSmoothReveal(msg.content)
    // Keep the cursor while revealed text is still catching up after the stream ends.
    val typing = msg.isStreaming || content.length < msg.content.length

    if (reasoning.isNotEmpty()) ReasoningBlock(reasoning, stillThinking = msg.isStreaming && msg.content.isEmpty())
    if (content.isNotEmpty() || (typing && msg.reasoning.isEmpty())) {
        SelectionContainer {
            MarkdownText(content + if (typing) " ▍" else "")
        }
    }
}

@Composable
private fun ReasoningBlock(text: String, stillThinking: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            stringResource(if (stillThinking) R.string.thinking else R.string.thought) + if (expanded) " ▴" else " ▾",
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
        s.timeToFirstToken?.let { add("TTFT ${it.inWholeMilliseconds}ms") }
        add("${s.chunks} chunks")
        s.completionTokens?.let { add("$it tok" + (s.reasoningTokens?.takeIf { r -> r > 0 }?.let { r -> " ($r think)" } ?: "")) }
        s.total?.let { add("total ${it.inWholeMilliseconds}ms") }
        s.tokensPerSecond?.let { add("%.1f tok/s".format(it)) }
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 6.dp),
    )
}
