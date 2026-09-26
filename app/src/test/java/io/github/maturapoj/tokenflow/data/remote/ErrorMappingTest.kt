package io.github.maturapoj.tokenflow.data.remote

import io.github.maturapoj.tokenflow.domain.model.ChatError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorMappingTest {

    @Test fun `http status codes`() {
        assertEquals(ChatError.Unauthorized, GatewayHttpException(401, "").toChatError())
        assertEquals(ChatError.Unauthorized, GatewayHttpException(403, "").toChatError())
        assertEquals(ChatError.RateLimited, GatewayHttpException(429, "").toChatError())
        assertEquals(ChatError.Server(502, "bad gateway"), GatewayHttpException(502, "bad gateway").toChatError())
    }

    @Test fun `stream payload, network and parse failures`() {
        assertEquals(ChatError.Gateway("model overloaded"), GatewayException("model overloaded").toChatError())
        assertEquals(ChatError.Network, IOException("reset").toChatError())
        assertEquals(ChatError.Network, SocketTimeoutException().toChatError())
        assertEquals(ChatError.Unknown("Unexpected response from the gateway"), SerializationException("x").toChatError())
    }

    @Test fun `cancellation is not turned into an error`() {
        val cancel = CancellationException("stop")
        assertSame(cancel, cancel.toChatException())
    }
}
