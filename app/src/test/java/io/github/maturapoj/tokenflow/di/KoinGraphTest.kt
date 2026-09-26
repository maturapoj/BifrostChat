package io.github.maturapoj.tokenflow.di

import io.github.maturapoj.tokenflow.domain.repository.ChatRepository
import io.github.maturapoj.tokenflow.domain.repository.SessionRepository
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import io.github.maturapoj.tokenflow.fakes.FakeSettingsRepository
import io.github.maturapoj.tokenflow.fakes.FakeSessionRepository
import io.github.maturapoj.tokenflow.presentation.chat.ChatViewModel
import io.github.maturapoj.tokenflow.presentation.settings.SettingsViewModel
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
 * Room and DataStore need an Android Context, so the session and settings repositories are in-memory fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinGraphTest : KoinTest {

    @get:Rule val koin = KoinTestRule.create {
        allowOverride(true)
        modules(
            appModules + module {
                single<SessionRepository> { FakeSessionRepository() }
                single<SettingsRepository> { FakeSettingsRepository() }
            },
        )
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `every definition resolves`() {
        assertNotNull(get<ChatViewModel>())
        assertNotNull(get<SettingsViewModel>())
    }

    @Test fun `repository is a singleton`() {
        assertSame(get<ChatRepository>(), get<ChatRepository>())
    }
}
