package io.github.maturapoj.tokenflow.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import io.github.maturapoj.tokenflow.BuildConfig
import io.github.maturapoj.tokenflow.data.local.ChatDatabase
import io.github.maturapoj.tokenflow.data.remote.GatewayApi
import io.github.maturapoj.tokenflow.data.repository.ChatRepositoryImpl
import io.github.maturapoj.tokenflow.data.repository.SessionRepositoryImpl
import io.github.maturapoj.tokenflow.data.settings.KeystoreCipher
import io.github.maturapoj.tokenflow.data.settings.SettingsRepositoryImpl
import io.github.maturapoj.tokenflow.domain.model.GatewaySettings
import io.github.maturapoj.tokenflow.domain.repository.ChatRepository
import io.github.maturapoj.tokenflow.domain.repository.SessionRepository
import io.github.maturapoj.tokenflow.domain.repository.SettingsRepository
import io.github.maturapoj.tokenflow.domain.usecase.ChatUseCase
import io.github.maturapoj.tokenflow.domain.usecase.SessionUseCase
import io.github.maturapoj.tokenflow.presentation.chat.ChatViewModel
import io.github.maturapoj.tokenflow.presentation.settings.SettingsViewModel
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Reasoning models can pause a long time between chunks.
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
    }
    single { KeystoreCipher() }
    single<SettingsRepository> {
        SettingsRepositoryImpl(get(), get(), GatewaySettings(BuildConfig.DEFAULT_BASE_URL, BuildConfig.DEFAULT_API_KEY))
    }
    single { GatewayApi(get(), get()) }
    singleOf(::ChatRepositoryImpl) bind ChatRepository::class

    single { Room.databaseBuilder(androidContext(), ChatDatabase::class.java, "chat.db").build() }
    single { get<ChatDatabase>().chatDao() }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
}

val domainModule = module {
    // Lambda: ChatUseCase's timing and save-interval parameters keep their defaults.
    factory { ChatUseCase(get(), get(), get()) }
    factoryOf(::SessionUseCase)
}

val presentationModule = module {
    viewModelOf(::ChatViewModel)
    viewModelOf(::SettingsViewModel)
}

val appModules = listOf(dataModule, domainModule, presentationModule)
