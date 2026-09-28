// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.wxxsfxyzm.intentx.data.settings.ThemePreferences
import io.github.wxxsfxyzm.intentx.data.settings.local.datastore.AppDataStore
import io.github.wxxsfxyzm.intentx.data.settings.repository.AppSettingsRepositoryImpl
import io.github.wxxsfxyzm.intentx.domain.settings.repository.AppSettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val settingsModule = module {
    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("app_settings")
        }
    }
    single { AppDataStore(get()) }
    single<AppSettingsRepository> { AppSettingsRepositoryImpl(get()) }
    single { ThemePreferences(get()) }
}
