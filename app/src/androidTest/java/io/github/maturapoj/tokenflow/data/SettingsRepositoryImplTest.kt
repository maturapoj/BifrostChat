package io.github.maturapoj.tokenflow.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.maturapoj.tokenflow.data.settings.KeystoreCipher
import io.github.maturapoj.tokenflow.data.settings.SettingsRepositoryImpl
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on a device: the Android Keystore isn't available on the JVM. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file: File = context.preferencesDataStoreFile("settings-test")
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val defaults = GatewaySettings("https://default.test", "default-key")
    private val repo = SettingsRepositoryImpl(store, KeystoreCipher("tokenflow_test_key"), defaults)

    @After fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test fun cipherRoundTrips() {
        val cipher = KeystoreCipher("tokenflow_test_key")
        val encrypted = cipher.encrypt("sk-secret")
        assertFalse(encrypted.contains("sk-secret"))
        assertEquals("sk-secret", cipher.decrypt(encrypted))
    }

    @Test fun defaultsApplyUntilAnEndpointIsSaved() = runTest {
        assertEquals(defaults, repo.settings.first())

        repo.saveEndpoint("http://10.0.2.2:11434", "")
        assertEquals(GatewaySettings("http://10.0.2.2:11434", ""), repo.settings.first())
    }

    @Test fun apiKeyIsStoredEncrypted() = runTest {
        repo.saveEndpoint("https://api.example", "sk-very-secret")
        repo.saveLastModel("p/m")

        assertEquals(GatewaySettings("https://api.example", "sk-very-secret", "p/m"), repo.settings.first())
        val raw = store.data.first().asMap().values.joinToString()
        assertFalse("key must not be in the file in plain text: $raw", raw.contains("sk-very-secret"))
    }
}
