package io.github.maturapoj.tokenflow

import android.app.Application
import io.github.maturapoj.tokenflow.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TokenFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TokenFlowApp)
            modules(appModules)
        }
    }
}
