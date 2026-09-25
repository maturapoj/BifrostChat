package com.example.bifrostchat.presentation.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bifrostchat.domain.model.ChatSession
import com.example.bifrostchat.presentation.chat.state.ChatIntent

@Composable
internal fun SessionDrawer(sessions: List<ChatSession>, currentId: Long?, onIntent: (ChatIntent) -> Unit) {
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }

    ModalDrawerSheet {
        Text(
            "Chats",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("+  New chat") },
            selected = currentId == null,
            onClick = { onIntent(ChatIntent.NewChat) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (sessions.isEmpty()) {
            Text(
                "No saved chats yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        LazyColumn {
            items(sessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(session.title.ifEmpty { "New chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                session.modelId.substringAfter('/'),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                        }
                    },
                    selected = session.id == currentId,
                    onClick = { onIntent(ChatIntent.OpenSession(session.id)) },
                    badge = {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .clickable { pendingDelete = session }
                                .padding(8.dp),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${session.title.ifEmpty { "New chat" }}\" and its messages will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(ChatIntent.DeleteSession(session.id))
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}
