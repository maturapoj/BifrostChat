package com.example.bifrostchat.presentation.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bifrostchat.R

@Composable
internal fun InputBar(isStreaming: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.message_hint)) },
            maxLines = 5,
        )
        if (isStreaming) {
            OutlinedButton(onClick = onStop, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.stop)) }
        } else {
            Button(
                onClick = { onSend(text); text = "" },
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.send)) }
        }
    }
}
