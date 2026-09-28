// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.di

import androidx.room3.Room
import io.github.wxxsfxyzm.intentx.data.intent.IntentPayloadCodec
import io.github.wxxsfxyzm.intentx.data.intent.ProfileTransferCodec
import io.github.wxxsfxyzm.intentx.data.intent.SavedIntentRepositoryImpl
import io.github.wxxsfxyzm.intentx.data.local.room.IntentXDatabase
import io.github.wxxsfxyzm.intentx.data.shortcut.ShortcutRepositoryImpl
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutRepository
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val storageModule = module {
    single {
        Room.databaseBuilder(androidContext(), IntentXDatabase::class.java, "intentx.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    } onClose { it?.close() }
    single { get<IntentXDatabase>().savedIntentDao }
    single { get<IntentXDatabase>().shortcutDao }
    single { IntentPayloadCodec(get()) }
    single(named("profileTransferJson")) {
        Json(get<Json>()) {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }
    }
    single { ProfileTransferCodec(get(named("profileTransferJson")), get()) }
    single<SavedIntentRepository> { SavedIntentRepositoryImpl(get(), get()) }
    single<ShortcutRepository> { ShortcutRepositoryImpl(get(), get()) }
}
