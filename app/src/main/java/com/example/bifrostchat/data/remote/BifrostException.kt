package com.example.bifrostchat.data.remote

/** An error payload inside the stream (`data: {"error": …}`). */
class BifrostException(message: String) : Exception(message)

/** A non-2xx response from the gateway. */
class BifrostHttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")
