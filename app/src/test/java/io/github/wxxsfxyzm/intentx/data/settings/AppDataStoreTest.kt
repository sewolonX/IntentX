// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.wxxsfxyzm.intentx.data.settings.local.datastore.AppDataStore
import io.github.wxxsfxyzm.intentx.data.settings.repository.AppSettingsRepositoryImpl
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivitySortOrder
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivityStatusFilter
import io.github.wxxsfxyzm.intentx.domain.catalog.CatalogSortOrder
import io.github.wxxsfxyzm.intentx.domain.settings.model.preferences.theme.ThemeMode
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.theme.ThemeSettingsAction
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun authorizationSurvivesReopeningWithoutChangingTheme() = runTest {
        val file = File(temporaryFolder.root, "authorization.preferences_pb")
        withStore(file) { store ->
            val settings = AppSettingsRepositoryImpl(store)
            assertEquals(Authorizer.None, settings.authorizer.first())
            store.putBoolean(AppDataStore.THEME_USE_DYNAMIC_COLOR, false)
        }
        for (authorizer in Authorizer.entries) {
            withStore(file) { AppSettingsRepositoryImpl(it).setAuthorizer(authorizer) }
            withStore(file) { store ->
                assertEquals(authorizer, AppSettingsRepositoryImpl(store).authorizer.first())
                assertFalse(store.getBoolean(AppDataStore.THEME_USE_DYNAMIC_COLOR, true).first())
            }
        }
    }

    @Test
    fun pureBlackPreferenceDefaultsOffAndSurvivesReopeningAndThemeChanges() = runTest {
        val file = File(temporaryFolder.root, "theme.preferences_pb")
        withStore(file) { store ->
            val preferences = ThemePreferences(store)
            assertFalse(preferences.settings.first().usePureBlack)
            assertFalse(preferences.themeStateFlow.first().usePureBlack)
            preferences.update(ThemeSettingsAction.SetUsePureBlack(true))
            assertTrue(preferences.settings.first().usePureBlack)
            assertTrue(preferences.themeStateFlow.first().usePureBlack)
            preferences.update(ThemeSettingsAction.SetThemeMode(ThemeMode.LIGHT))
        }
        withStore(file) { store ->
            val preferences = ThemePreferences(store)
            assertEquals(ThemeMode.LIGHT, preferences.settings.first().themeMode)
            assertTrue(preferences.settings.first().usePureBlack)
            assertTrue(preferences.themeStateFlow.first().usePureBlack)
            for (mode in listOf(ThemeMode.DARK, ThemeMode.SYSTEM)) {
                preferences.update(ThemeSettingsAction.SetThemeMode(mode))
                assertEquals(mode, preferences.themeStateFlow.first().themeMode)
                assertTrue(preferences.settings.first().usePureBlack)
                assertTrue(preferences.themeStateFlow.first().usePureBlack)
            }
            preferences.update(ThemeSettingsAction.SetUsePureBlack(false))
        }
        withStore(file) { store ->
            val preferences = ThemePreferences(store)
            assertFalse(preferences.settings.first().usePureBlack)
            assertFalse(preferences.themeStateFlow.first().usePureBlack)
        }
    }

    @Test
    fun catalogPreferencesSurviveReopening() = runTest {
        val file = File(temporaryFolder.root, "catalog.preferences_pb")
        withStore(file) { store ->
            val settings = AppSettingsRepositoryImpl(store)
            assertFalse(settings.showSystemApps.first())
            assertEquals(CatalogSortOrder.Label, settings.catalogSortOrder.first())
            assertFalse(settings.catalogReverseOrder.first())
            assertEquals(true, settings.catalogShowPackageName.first())
            settings.setShowSystemApps(true)
            settings.setCatalogSortOrder(CatalogSortOrder.FirstInstallTime)
            settings.setCatalogReverseOrder(true)
            settings.setCatalogShowPackageName(false)
        }
        withStore(file) { store ->
            assertEquals(true, AppSettingsRepositoryImpl(store).showSystemApps.first())
            val settings = AppSettingsRepositoryImpl(store)
            assertEquals(CatalogSortOrder.FirstInstallTime, settings.catalogSortOrder.first())
            assertEquals(true, settings.catalogReverseOrder.first())
            assertEquals(false, settings.catalogShowPackageName.first())
        }
    }

    @Test
    fun activityPreferencesSurviveReopening() = runTest {
        val file = File(temporaryFolder.root, "activities.preferences_pb")
        withStore(file) { store ->
            val settings = AppSettingsRepositoryImpl(store)
            assertEquals(ActivitySortOrder.Label, settings.activitySortOrder.first())
            assertEquals(ActivityStatusFilter.All, settings.activityExportedFilter.first())
            assertEquals(ActivityStatusFilter.All, settings.activityEnabledFilter.first())
            settings.setActivitySortOrder(ActivitySortOrder.ClassName)
            settings.setActivityExportedFilter(ActivityStatusFilter.Yes)
            settings.setActivityEnabledFilter(ActivityStatusFilter.No)
        }
        withStore(file) { store ->
            val settings = AppSettingsRepositoryImpl(store)
            assertEquals(ActivitySortOrder.ClassName, settings.activitySortOrder.first())
            assertEquals(ActivityStatusFilter.Yes, settings.activityExportedFilter.first())
            assertEquals(ActivityStatusFilter.No, settings.activityEnabledFilter.first())
        }
    }

    @Test
    fun unknownAuthorizationFallsBackWithoutOverwritingStoredValue() = runTest {
        withStore(File(temporaryFolder.root, "unknown.preferences_pb")) { store ->
            store.putString(AppDataStore.AUTHORIZER, "FutureBackend")
            assertEquals(Authorizer.None, AppSettingsRepositoryImpl(store).authorizer.first())
            assertEquals("FutureBackend", store.getString(AppDataStore.AUTHORIZER).first())
        }
    }

    private suspend fun withStore(file: File, block: suspend (AppDataStore) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        try {
            block(AppDataStore(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
