package io.github.maturapoj.tokenflow.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.maturapoj.tokenflow.BuildConfig
import io.github.maturapoj.tokenflow.R
import io.github.maturapoj.tokenflow.presentation.chat.components.message
import io.github.maturapoj.tokenflow.presentation.theme.navyTopAppBarColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsContent(state, vm::onIntent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    val back = stringResource(R.string.back)
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                        modifier = Modifier.semantics { contentDescription = back },
                    ) { Text("←", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {}) }
                },
                colors = navyTopAppBarColors(),
            )
        },
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.forEach { preset ->
                    AssistChip(onClick = { onIntent(SettingsIntent.UsePreset(preset)) }, label = { Text(preset.label) })
                }
            }

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { onIntent(SettingsIntent.EditBaseUrl(it)) },
                label = { Text(stringResource(R.string.base_url)) },
                placeholder = { Text("https://api.openai.com") },
                isError = state.invalidUrl,
                supportingText = {
                    Text(stringResource(if (state.invalidUrl) R.string.base_url_invalid else R.string.base_url_help))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { onIntent(SettingsIntent.EditApiKey(it)) },
                label = { Text(stringResource(R.string.api_key)) },
                supportingText = { Text(stringResource(R.string.api_key_help)) },
                singleLine = true,
                visualTransformation = if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { onIntent(SettingsIntent.ToggleKeyVisibility) }) {
                        Text(stringResource(if (state.showKey) R.string.hide else R.string.show))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onIntent(SettingsIntent.Save) },
                enabled = state.status != SaveStatus.Testing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_and_test)) }

            when (val status = state.status) {
                SaveStatus.Idle -> Unit
                SaveStatus.Testing -> Text(stringResource(R.string.testing_connection))
                is SaveStatus.Connected -> Text(
                    "✓ " + stringResource(R.string.connected_models, status.models),
                    color = MaterialTheme.colorScheme.primary,
                )
                is SaveStatus.Failed -> Text("⚠ " + status.error.message(), color = MaterialTheme.colorScheme.error)
            }

            // Handy for bug reports.
            Text(
                stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
