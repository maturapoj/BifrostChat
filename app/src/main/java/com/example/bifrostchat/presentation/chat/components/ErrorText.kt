package com.example.bifrostchat.presentation.chat.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.example.bifrostchat.R
import com.example.bifrostchat.domain.model.ChatError

/** User-facing text for an error; also used outside composition (the Snackbar). */
fun ChatError.message(resources: Resources): String = when (this) {
    ChatError.Network -> resources.getString(R.string.error_network)
    ChatError.Unauthorized -> resources.getString(R.string.error_unauthorized)
    ChatError.RateLimited -> resources.getString(R.string.error_rate_limited)
    is ChatError.Server -> resources.getString(R.string.error_server, code)
    is ChatError.Gateway -> resources.getString(R.string.error_gateway, detail)
    is ChatError.Unknown -> resources.getString(R.string.error_unknown, detail)
}

@Composable
fun ChatError.message(): String = message(LocalResources.current)
