package com.example.bifrostchat.data.remote

/** An error reported by the gateway: a non-2xx response or an `error` payload in the stream. */
class BifrostException(message: String) : Exception(message)
