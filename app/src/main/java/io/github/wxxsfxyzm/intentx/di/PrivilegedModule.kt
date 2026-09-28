// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.di

import io.github.wxxsfxyzm.intentx.data.intent.AndroidIntentBuilder
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.domain.authorization.AuthorizationStatusProvider
import io.github.wxxsfxyzm.intentx.domain.intent.IntentUriCodec
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutCreator
import io.github.wxxsfxyzm.intentx.executor.DirectPrivilegedExecutor
import io.github.wxxsfxyzm.intentx.executor.IntentExecutor
import io.github.wxxsfxyzm.intentx.framework.authorization.DirectAuthorizationStatusProvider
import io.github.wxxsfxyzm.intentx.framework.intent.AndroidIntentUriCodec
import io.github.wxxsfxyzm.intentx.framework.intent.DocumentAccess
import io.github.wxxsfxyzm.intentx.framework.intent.OpeningAppResolver
import io.github.wxxsfxyzm.intentx.framework.intent.TemplateAvailability
import io.github.wxxsfxyzm.intentx.framework.shortcut.AndroidShortcutCreator
import org.koin.dsl.module

val privilegedModule = module {
    single<IntentBuilder> { AndroidIntentBuilder(get(), get()) }
    single<IntentUriCodec> { AndroidIntentUriCodec(get()) }
    single { DocumentAccess(get()) }
    single { OpeningAppResolver(get()) }
    single { TemplateAvailability(get()) }
    single { DirectPrivilegedExecutor(get()) }
    single<IntentExecutor> { get<DirectPrivilegedExecutor>() }
    single<AuthorizationStatusProvider> { DirectAuthorizationStatusProvider(get()) }
    single<ShortcutCreator> { AndroidShortcutCreator(get(), get(), get()) }
}
