package com.example.bifrostchat.presentation.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bifrostchat.R
import com.example.bifrostchat.domain.model.LlmModel
import com.example.bifrostchat.domain.model.ModelGroup

@Composable
internal fun ModelPicker(
    groups: List<ModelGroup>,
    selected: LlmModel?,
    enabled: Boolean,
    onSelect: (LlmModel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.clickable(enabled = enabled) { expanded = true }) {
            Text(
                text = (selected?.name ?: stringResource(R.string.loading_models)) + " ▾",
                style = MaterialTheme.typography.titleMedium,
            )
            selected?.let {
                Text(it.provider, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.7f))
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
