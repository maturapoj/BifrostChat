package io.github.maturapoj.tokenflow.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.maturapoj.tokenflow.R
import androidx.compose.ui.res.stringResource

/** Shown instead of an empty chat until an endpoint is set up. */
@Composable
internal fun SetupPrompt(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
    }
}
