package io.github.maturapoj.tokenflow.data.remote

/** An error payload inside the stream (`data: {"error": …}`). */
class GatewayException(message: String) : Exception(message)

/** A non-2xx response from the gateway. */
class GatewayHttpException(val code: Int, val body: String) : Exception("HTTP $code: $body")
