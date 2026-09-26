package io.github.maturapoj.tokenflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.maturapoj.tokenflow.presentation.chat.ChatScreen
import io.github.maturapoj.tokenflow.presentation.settings.SettingsScreen
import io.github.maturapoj.tokenflow.presentation.theme.TokenFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokenFlowTheme {
                // Two screens don't need a navigation library. The chat ViewModel is scoped to the
                // activity, so it keeps streaming while Settings is open.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onBack = { showSettings = false })
                } else {
                    ChatScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
