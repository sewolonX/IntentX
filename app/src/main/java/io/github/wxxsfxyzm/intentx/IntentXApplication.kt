// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx

import android.app.Application
import io.github.wxxsfxyzm.intentx.di.catalogModule
import io.github.wxxsfxyzm.intentx.di.privilegedModule
import io.github.wxxsfxyzm.intentx.di.serializationModule
import io.github.wxxsfxyzm.intentx.di.settingsModule
import io.github.wxxsfxyzm.intentx.di.storageModule
import io.github.wxxsfxyzm.intentx.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class IntentXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(
                object : Timber.DebugTree() {
                    override fun createStackElementTag(element: StackTraceElement): String? = super.createStackElementTag(element)?.substringBefore('$')?.take(23)
                },
            )
        }
        Timber.d("Starting IntentX %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        startKoin {
            androidContext(this@IntentXApplication)
            modules(serializationModule, settingsModule, storageModule, privilegedModule, catalogModule, viewModelModule)
        }
        Timber.d("Application dependencies initialized")
    }
}
