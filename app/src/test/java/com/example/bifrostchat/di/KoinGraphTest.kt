package com.example.bifrostchat.di

import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.domain.repository.SessionRepository
import com.example.bifrostchat.fakes.FakeSessionRepository
import com.example.bifrostchat.presentation.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get

/**
 * Resolves the real graph; the ViewModel's init load is queued on a test dispatcher that never runs, so no network.
 * The Room-backed SessionRepository needs an Android Context, so it is swapped for an in-memory fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinGraphTest : KoinTest {

    @get:Rule val koin = KoinTestRule.create {
        allowOverride(true)
        modules(appModules + module { single<SessionRepository> { FakeSessionRepository() } })
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `every definition resolves`() {
        assertNotNull(get<ChatViewModel>())
    }

    @Test fun `repository is a singleton`() {
        assertSame(get<ChatRepository>(), get<ChatRepository>())
    }
}
