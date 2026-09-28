// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.di

import io.github.wxxsfxyzm.intentx.ui.AppViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.creation.ImportViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.creation.TemplatesViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorUriViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.ProfileExportViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.SavedIntentViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.SettingsSharedViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.about.AboutViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.theme.ThemeSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::AuthorizationViewModel)
    viewModelOf(::CatalogViewModel)
    viewModelOf(::EditorViewModel)
    viewModelOf(::EditorUriViewModel)
    viewModelOf(::ImportViewModel)
    viewModelOf(::TemplatesViewModel)
    viewModelOf(::SavedIntentViewModel)
    viewModelOf(::ProfileExportViewModel)
    viewModelOf(::SettingsSharedViewModel)
    viewModelOf(::ThemeSettingsViewModel)
}
