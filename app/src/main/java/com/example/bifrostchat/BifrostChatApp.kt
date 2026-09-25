package com.example.bifrostchat

import android.app.Application
import com.example.bifrostchat.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BifrostChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BifrostChatApp)
            modules(appModules)
        }
    }
}
