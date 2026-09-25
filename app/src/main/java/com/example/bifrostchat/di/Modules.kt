package com.example.bifrostchat.di

import com.example.bifrostchat.BuildConfig
import com.example.bifrostchat.data.remote.BifrostApi
import com.example.bifrostchat.data.repository.ChatRepositoryImpl
import com.example.bifrostchat.domain.repository.ChatRepository
import com.example.bifrostchat.domain.usecase.GetModelGroupsUseCase
import com.example.bifrostchat.domain.usecase.StreamChatUseCase
import com.example.bifrostchat.presentation.chat.ChatViewModel
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val dataModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Reasoning models can pause a long time between chunks.
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
    single { BifrostApi(BuildConfig.BIFROST_BASE_URL, BuildConfig.BIFROST_API_KEY, get()) }
    singleOf(::ChatRepositoryImpl) bind ChatRepository::class
}

val domainModule = module {
    factoryOf(::GetModelGroupsUseCase)
    factoryOf(::StreamChatUseCase)
}

val presentationModule = module {
    // Lambda instead of viewModelOf so the `clock` default is used.
    viewModel { ChatViewModel(get(), get()) }
}

val appModules = listOf(dataModule, domainModule, presentationModule)
