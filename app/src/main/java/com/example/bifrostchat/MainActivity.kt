package com.example.bifrostchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bifrostchat.presentation.chat.ChatScreen
import com.example.bifrostchat.presentation.theme.BifrostChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BifrostChatTheme {
                ChatScreen()
            }
        }
    }
}
