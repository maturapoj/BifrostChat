package io.github.maturapoj.tokenflow.presentation.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.maturapoj.tokenflow.R
import io.github.maturapoj.tokenflow.domain.model.ChatSession
import io.github.maturapoj.tokenflow.presentation.chat.state.ChatIntent

@Composable
internal fun SessionDrawer(
    sessions: List<ChatSession>,
    currentId: Long?,
    onIntent: (ChatIntent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }

    ModalDrawerSheet {
        Text(
            stringResource(R.string.chats),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("+  " + stringResource(R.string.new_chat)) },
            selected = currentId == null,
            onClick = { onIntent(ChatIntent.NewChat) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_chats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(sessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(session.title.ifEmpty { stringResource(R.string.new_chat) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        val description = stringResource(R.string.delete_chat)
                        Box(
                            Modifier
                                .clickable { pendingDelete = session }
                                .semantics { contentDescription = description; role = Role.Button }
                                .padding(8.dp),
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.outline, modifier = Modifier.clearAndSetSemantics {})
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("⚙  " + stringResource(R.string.settings)) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_message, session.title.ifEmpty { stringResource(R.string.new_chat) })) },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(ChatIntent.DeleteSession(session.id))
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
