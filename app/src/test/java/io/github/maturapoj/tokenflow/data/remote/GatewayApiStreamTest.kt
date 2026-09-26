package io.github.maturapoj.tokenflow.data.remote

import io.github.maturapoj.tokenflow.data.repository.ChatRepositoryImpl
import io.github.maturapoj.tokenflow.domain.model.ChatError
import io.github.maturapoj.tokenflow.domain.model.ChatException
import io.github.maturapoj.tokenflow.domain.model.ChatMessage
import io.github.maturapoj.tokenflow.domain.model.Role
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.model.StreamEvent
import io.github.maturapoj.tokenflow.fakes.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/** Talks to a local MockWebServer over a real socket; real time, not virtual time. */
class GatewayApiStreamTest {

    private val server = MockWebServer()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var api: GatewayApi

    @Before fun setUp() {
        server.start()
        val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
        settings = FakeSettingsRepository(GatewaySettings(server.url("/").toString().trimEnd('/'), "test-key"))
        api = GatewayApi(settings, http)
    }

    @After fun tearDown() = server.shutdown()

    private fun chunk(text: String) = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$text\"}}]}\n\n"

    @Test fun `streams events until DONE`() = runBlocking {
        server.enqueue(MockResponse().setBody(chunk("Hel") + chunk("lo") + "data: [DONE]\n\n"))

        val events = api.streamChat("m", listOf(MessageDto("user", "hi"))).toList()

        assertEquals(listOf(StreamEvent.Content("Hel"), StreamEvent.Content("lo")), events)
        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"stream\":true"))
    }

    @Test fun `local servers get no Authorization header when the key is empty`() = runBlocking {
        settings.saveEndpoint(settings.settings.value.baseUrl, "")
        server.enqueue(MockResponse().setBody("data: [DONE]\n\n"))

        api.streamChat("m", emptyList()).toList()

        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `no endpoint fails with NotConfigured before any request`() = runBlocking {
        settings.saveEndpoint("", "")
        val error = runCatching { api.listModelIds() }.exceptionOrNull()
        assertEquals(ChatError.NotConfigured, (error as ChatException).error)
        assertEquals(0, server.requestCount)
    }

    @Test fun `rejected key surfaces as Unauthorized through the repository`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid key"}}"""))

        val error = runCatching {
            ChatRepositoryImpl(api).streamChat("m", listOf(ChatMessage(Role.User, "hi"))).toList()
        }.exceptionOrNull()

        assertEquals(ChatError.Unauthorized, (error as ChatException).error)
    }

    @Test fun `cancelling while the server is silent returns immediately`() = runBlocking {
        // First chunk goes out at once; the rest is held back for 3 s, so the reader blocks on the socket.
        // Before the fix, cancel waited for that read to return.
        val first = chunk("first")
        server.enqueue(
            MockResponse()
                .setBody(first + chunk("second"))
                .throttleBody(first.length.toLong(), 3, TimeUnit.SECONDS),
        )

        val gotFirst = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            api.streamChat("m", listOf(MessageDto("user", "hi"))).collect { gotFirst.complete(Unit) }
        }
        withTimeout(5_000) { gotFirst.await() }
        delay(300) // let the reader settle into the blocking read

        val elapsed = measureTimeMillis { job.cancelAndJoin() }
        assertTrue("cancel took ${elapsed}ms", elapsed < 1_000)
    }
}
