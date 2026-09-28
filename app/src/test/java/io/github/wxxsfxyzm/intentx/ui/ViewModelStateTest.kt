// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.di.serializationModule
import io.github.wxxsfxyzm.intentx.domain.authorization.AuthorizationStatus
import io.github.wxxsfxyzm.intentx.domain.authorization.AuthorizationStatusProvider
import io.github.wxxsfxyzm.intentx.domain.authorization.RootMode
import io.github.wxxsfxyzm.intentx.domain.authorization.ShizukuMode
import io.github.wxxsfxyzm.intentx.domain.authorization.ShizukuState
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivitySortOrder
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivityStatusFilter
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivityTarget
import io.github.wxxsfxyzm.intentx.domain.catalog.CatalogSortOrder
import io.github.wxxsfxyzm.intentx.domain.catalog.InstalledAppTarget
import io.github.wxxsfxyzm.intentx.domain.catalog.SystemAppProvider
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentSummary
import io.github.wxxsfxyzm.intentx.domain.settings.repository.AppSettingsRepository
import io.github.wxxsfxyzm.intentx.domain.shortcut.IntentShortcut
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutCreator
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutResult
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.executor.IntentExecutor
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogViewEvent
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorChoice
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorField
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewEvent
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.SavedIntentEvent
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.SavedIntentViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewEvent
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.dsl.koinApplication

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {
    private val stores = ViewModelStore()
    private val serialization = koinApplication { modules(serializationModule) }
    private val json = serialization.koin.get<Json>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        stores.clear()
        Dispatchers.resetMain()
        serialization.close()
    }

    @Test
    fun editorRestoresFieldsRowsChoicesAndUnknownFlagBits() = runTest {
        val handle = SavedStateHandle()
        val model = EditorViewModel(handle, FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        model.dispatch(EditorViewAction.SetField(EditorField.Title, "Example"))
        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 2))
        model.dispatch(EditorViewAction.SetFlags("0x80000001"))
        model.dispatch(EditorViewAction.ToggleFlag(0x10000000, true))
        model.dispatch(EditorViewAction.AddExtra)
        model.dispatch(EditorViewAction.AddExtra)
        val extra = model.uiState.value.extras.last()
        model.dispatch(EditorViewAction.UpdateExtra(extra.copy(fields = mapOf(EditorField.ExtraValue to "hello"), type = 3)))
        model.dispatch(EditorViewAction.RemoveExtra(model.uiState.value.extras.first().id))
        model.dispatch(EditorViewAction.AddClip)
        val clip = model.uiState.value.clips.single()
        model.dispatch(EditorViewAction.UpdateClip(clip.copy(fields = mapOf(EditorField.DataUri to "content://example/item"))))
        val recreated = EditorViewModel(SavedStateHandle(mapOf("editor_draft" to handle.get<String>("editor_draft"))), FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        assertEquals(model.uiState.value, recreated.uiState.value)
        assertEquals("0x90000001", recreated.uiState.value.flags)
        assertEquals("hello", recreated.uiState.value.extras.single().fields[EditorField.ExtraValue])
        recreated.dispatch(EditorViewAction.AddExtra)
        assertTrue(recreated.uiState.value.extras.last().id > clip.id)
        recreated.dispatch(EditorViewAction.SetFlags("invalid"))
        recreated.dispatch(EditorViewAction.ToggleFlag(0x10000000, false))
        assertEquals("invalid", recreated.uiState.value.flags)
    }

    @Test
    fun documentDraftIgnoresPrivilegedDefaultAndSavesItsKindAndReadGrant() = runTest {
        val settings = FakeSettings().apply { authorizer.value = Authorizer.Root }
        val repository = FakeSavedIntentRepository()
        val model = EditorViewModel(SavedStateHandle(), FakeActivityLauncher(), settings, FakeIntentBuilder(), repository, EmptyAppProvider(), FakeShortcutCreator(), json)
        stores.put("documentEditor", model)
        model.initializeKind(ProfileKind.File)
        runCurrent()
        assertEquals(0, model.uiState.value.choices[EditorChoice.LaunchMode])
        model.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
        assertEquals(IntentOperation.Activity, model.uiState.value.operation)
        model.selectDocument("content://example/document/1", "Notes", "text/plain")
        model.dispatch(EditorViewAction.Save("Notes", ""))
        assertEquals(EditorViewEvent.SaveSucceeded, model.eventFlow.first())
        val saved = repository.profiles.value.single()
        assertEquals(ProfileKind.File, saved.kind)
        assertEquals("None", saved.authorizer)
        assertEquals(0x10000001, saved.intent.flags)
        assertEquals(true, saved.intent.requiresDocumentRead)
    }

    @Test
    fun editorSavesAndUpdatesTheSameProfile() = runTest {
        val repository = FakeSavedIntentRepository()
        val model = EditorViewModel(SavedStateHandle(), FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), repository, EmptyAppProvider(), FakeShortcutCreator(), json)
        stores.put("editor", model)
        runCurrent()
        model.dispatch(EditorViewAction.SetField(EditorField.PackageName, "example.app"))
        val before = model.uiState.value
        model.dispatch(EditorViewAction.Save("Example", "Description"))
        runCurrent()
        assertEquals(EditorViewEvent.SaveSucceeded, model.eventFlow.first())
        assertEquals(before.fields, model.uiState.value.fields)
        assertEquals(before.flags, model.uiState.value.flags)
        assertEquals("Example", repository.profiles.value.single().name)
        assertEquals("Description", repository.profiles.value.single().description)
        val savedId = repository.profiles.value.single().id
        assertEquals(savedId, model.uiState.value.profileId)
        model.dispatch(EditorViewAction.SetProfileName("Renamed"))
        model.dispatch(EditorViewAction.SetProfileDescription("Updated"))
        model.dispatch(EditorViewAction.Save(model.uiState.value.profileName, model.uiState.value.profileDescription))
        runCurrent()
        assertEquals(EditorViewEvent.SaveSucceeded, model.eventFlow.first())
        assertEquals(1, repository.profiles.value.size)
        assertEquals(savedId, repository.profiles.value.single().id)
        assertEquals("Renamed", repository.profiles.value.single().name)
        assertEquals("Updated", repository.profiles.value.single().description)
    }

    @Test
    fun editorUsesPreferredAuthorizerOnlyUntilUserChoosesLaunchMode() = runTest {
        val settings = FakeSettings().apply { authorizer.value = Authorizer.Shizuku }
        val model = EditorViewModel(SavedStateHandle(), FakeActivityLauncher(), settings, FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        runCurrent()
        assertEquals(2, model.uiState.value.choices[EditorChoice.LaunchMode])

        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 1))
        settings.authorizer.value = Authorizer.None
        runCurrent()
        assertEquals(1, model.uiState.value.choices[EditorChoice.LaunchMode])
    }

    @Test
    fun normalLaunchRestoresRequiredNewTaskFlagAfterPrivilegedEditing() = runTest {
        val model = EditorViewModel(SavedStateHandle(), FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        runCurrent()
        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 1))
        model.dispatch(EditorViewAction.SetFlags("0x00000000"))
        assertEquals("0x00000000", model.uiState.value.flags)

        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 0))
        assertEquals("0x10000000", model.uiState.value.flags)
    }

    @Test
    fun automaticModeUsesPreferenceAndRestoresFallbackFlag() = runTest {
        val settings = FakeSettings().apply { authorizer.value = Authorizer.Auto }
        val model = EditorViewModel(SavedStateHandle(), FakeActivityLauncher(), settings, FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        stores.put("automaticEditor", model)
        runCurrent()
        assertEquals(3, model.uiState.value.choices[EditorChoice.LaunchMode])
        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 1))
        model.dispatch(EditorViewAction.SetFlags("0x00000000"))
        model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, 3))
        assertEquals("0x10000000", model.uiState.value.flags)
        model.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
        assertEquals("0x00000000", model.uiState.value.flags)
    }

    @Test
    fun broadcastDraftUsesBroadcastExecutionAndSavedTypeCannotChange() = runTest {
        val executor = FakeActivityLauncher()
        val repository = FakeSavedIntentRepository()
        val model = EditorViewModel(SavedStateHandle(), executor, FakeSettings(), FakeIntentBuilder(), repository, EmptyAppProvider(), FakeShortcutCreator(), json)
        stores.put("broadcastEditor", model)
        runCurrent()
        model.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
        assertEquals("0x00000000", model.uiState.value.flags)
        model.dispatch(EditorViewAction.SetField(EditorField.Action, "example.ACTION"))
        model.dispatch(EditorViewAction.Launch)
        runCurrent()
        assertEquals(EditorViewEvent.BroadcastSent, model.eventFlow.first())
        assertEquals(0, executor.activityCalls)
        assertEquals(1, executor.broadcastCalls)

        model.dispatch(EditorViewAction.Save("Broadcast", ""))
        runCurrent()
        assertEquals(EditorViewEvent.SaveSucceeded, model.eventFlow.first())
        assertEquals(IntentOperation.Broadcast, repository.profiles.value.single().operation)
        model.dispatch(EditorViewAction.SetOperation(IntentOperation.Activity))
        assertEquals(IntentOperation.Broadcast, model.uiState.value.operation)
    }

    @Test
    fun switchingOperationKeepsSeparateComponentNamesAndFlagsAcrossDraftRestore() = runTest {
        val handle = SavedStateHandle()
        val model = EditorViewModel(handle, FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), FakeShortcutCreator(), json)
        model.dispatch(EditorViewAction.SetField(EditorField.ClassName, "example.Activity"))
        model.dispatch(EditorViewAction.SetFlags("0x10000001"))
        model.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
        assertEquals(null, model.uiState.value.fields[EditorField.ClassName])
        assertEquals("0x00000000", model.uiState.value.flags)
        model.dispatch(EditorViewAction.SetField(EditorField.ClassName, "example.Receiver"))
        model.dispatch(EditorViewAction.SetFlags("0x00000002"))

        val recreated = EditorViewModel(
            SavedStateHandle(mapOf("editor_draft" to handle.get<String>("editor_draft"))),
            FakeActivityLauncher(),
            FakeSettings(),
            FakeIntentBuilder(),
            FakeSavedIntentRepository(),
            EmptyAppProvider(),
            FakeShortcutCreator(),
            json,
        )
        recreated.dispatch(EditorViewAction.SetOperation(IntentOperation.Activity))
        assertEquals("example.Activity", recreated.uiState.value.fields[EditorField.ClassName])
        assertEquals("0x10000001", recreated.uiState.value.flags)
        recreated.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
        assertEquals("example.Receiver", recreated.uiState.value.fields[EditorField.ClassName])
        assertEquals("0x00000002", recreated.uiState.value.flags)
    }

    @Test
    fun editorDoesNotLaunchAComponentThatIsCurrentlyDisabled() = runTest {
        val launcher = FakeActivityLauncher()
        val model = EditorViewModel(SavedStateHandle(), launcher, FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(false), FakeShortcutCreator(), json)
        stores.put("disabledEditor", model)
        model.dispatch(EditorViewAction.SetField(EditorField.PackageName, "example.app"))
        model.dispatch(EditorViewAction.SetField(EditorField.ClassName, "example.app.Disabled"))
        model.dispatch(EditorViewAction.Launch)
        runCurrent()
        assertEquals(EditorViewEvent.ActivityDisabled, model.eventFlow.first())
        assertEquals(0, launcher.calls)
    }

    @Test
    fun savedIntentDoesNotLaunchAComponentThatIsCurrentlyDisabled() = runTest {
        val launcher = FakeActivityLauncher()
        val repository = FakeSavedIntentRepository()
        val model = SavedIntentViewModel(repository, FakeIntentBuilder(), launcher, EmptyAppProvider(false))
        stores.put("disabledSavedIntent", model)
        val profile = SavedIntentProfile(
            "id",
            "Disabled",
            "",
            IntentSpec("example.app", "example.app.Disabled", null, null, null, emptyList(), 0, emptyList(), null),
            Authorizer.Root.name,
            1,
            1,
        )
        repository.upsert(profile)
        model.launch(profile.id)
        runCurrent()
        assertEquals(SavedIntentEvent.ActivityDisabled, model.eventFlow.first())
        assertEquals(0, launcher.calls)
    }

    @Test
    fun catalogRestoresFiltersAndDeliversNavigationOnce() = runTest {
        val handle = SavedStateHandle()
        val provider = EmptyAppProvider()
        val model = CatalogViewModel(handle, provider, FakeSettings())
        stores.put("catalog", model)
        model.dispatch(CatalogViewAction.SetQuery("example"))
        model.dispatch(CatalogViewAction.SetShowSystem(true))
        val restored = CatalogViewModel(SavedStateHandle(mapOf("query" to handle.get<String>("query"), "show_system" to true)), provider, FakeSettings())
        assertEquals(model.uiState.value, restored.uiState.value)
        model.dispatch(CatalogViewAction.OpenApp("example.app"))
        runCurrent()
        assertEquals(CatalogViewEvent.NavigateToActivities("example.app", ""), model.eventFlow.first())
        model.viewModelScope.coroutineContext.job.children.toList().joinAll()
    }

    @Test
    fun authorizationObservesOnlyInForegroundAndDeduplicatesRootChecks() = runTest {
        val settings = FakeSettings()
        val provider = FakeStatusProvider()
        val model = AuthorizationViewModel(settings, provider)
        stores.put("auth", model)
        settings.authorizer.value = Authorizer.Root
        model.dispatch(AuthorizationViewAction.StartObserving)
        model.dispatch(AuthorizationViewAction.StartObserving)
        runCurrent()
        assertEquals(Authorizer.Root, model.uiState.value.selected)
        assertEquals(1, provider.checks)
        assertEquals(1, provider.shizukuState.subscriptionCount.value)
        model.dispatch(AuthorizationViewAction.Select(Authorizer.Root))
        runCurrent()
        assertEquals(1, provider.checks)
        provider.shizukuState.value = ShizukuState(AuthorizationStatus.Ready, ShizukuMode.Shell)
        runCurrent()
        assertEquals(ShizukuState(AuthorizationStatus.Ready, ShizukuMode.Shell), model.uiState.value.shizukuState)
        model.dispatch(AuthorizationViewAction.StopObserving)
        runCurrent()
        assertEquals(0, provider.shizukuState.subscriptionCount.value)
        model.dispatch(AuthorizationViewAction.StartObserving)
        runCurrent()
        assertEquals(2, provider.checks)
        provider.rootReady.complete(Unit)
        runCurrent()
        assertEquals(AuthorizationStatus.Ready, model.uiState.value.rootStatus)
        assertEquals(RootMode.KernelSU, model.uiState.value.rootMode)
    }

    @Test
    fun failedPreferenceWriteDoesNotSelectOrRequestPermission() = runTest {
        val settings = FakeSettings().apply { failWrites = true }
        val provider = FakeStatusProvider()
        val model = AuthorizationViewModel(settings, provider)
        stores.put("auth", model)
        model.dispatch(AuthorizationViewAction.Select(Authorizer.Shizuku))
        assertEquals(AuthorizationViewEvent.ShowMessage(R.string.settings_save_failed), model.eventFlow.first())
        assertEquals(Authorizer.None, model.uiState.value.selected)
        assertEquals(0, provider.permissionRequests)
    }

    private class FakeSettings : AppSettingsRepository {
        override val authorizer = MutableStateFlow(Authorizer.None)
        override val showSystemApps = MutableStateFlow(false)
        override val catalogSortOrder = MutableStateFlow(CatalogSortOrder.Label)
        override val catalogReverseOrder = MutableStateFlow(false)
        override val catalogShowPackageName = MutableStateFlow(true)
        override val activitySortOrder = MutableStateFlow(ActivitySortOrder.Label)
        override val activityExportedFilter = MutableStateFlow(ActivityStatusFilter.All)
        override val activityEnabledFilter = MutableStateFlow(ActivityStatusFilter.All)
        var failWrites = false
        override suspend fun setAuthorizer(authorizer: Authorizer) {
            if (failWrites) throw IOException("test")
            this.authorizer.value = authorizer
        }
        override suspend fun setShowSystemApps(show: Boolean) {
            showSystemApps.value = show
        }
        override suspend fun setCatalogSortOrder(order: CatalogSortOrder) {
            catalogSortOrder.value = order
        }
        override suspend fun setCatalogReverseOrder(reverse: Boolean) {
            catalogReverseOrder.value = reverse
        }
        override suspend fun setCatalogShowPackageName(show: Boolean) {
            catalogShowPackageName.value = show
        }
        override suspend fun setActivitySortOrder(order: ActivitySortOrder) {
            activitySortOrder.value = order
        }
        override suspend fun setActivityExportedFilter(filter: ActivityStatusFilter) {
            activityExportedFilter.value = filter
        }
        override suspend fun setActivityEnabledFilter(filter: ActivityStatusFilter) {
            activityEnabledFilter.value = filter
        }
    }

    @Test
    fun shortcutCapturesSelectedModeAndOperationAndReusesDraftId() = runTest {
        for (mode in 0..3) {
            val shortcuts = FakeShortcutCreator()
            val handle = SavedStateHandle()
            val model = EditorViewModel(handle, FakeActivityLauncher(), FakeSettings(), FakeIntentBuilder(), FakeSavedIntentRepository(), EmptyAppProvider(), shortcuts, json)
            stores.put("shortcut$mode", model)
            runCurrent()
            model.dispatch(EditorViewAction.SetOperation(IntentOperation.Broadcast))
            model.dispatch(EditorViewAction.SetChoice(EditorChoice.LaunchMode, mode))
            model.dispatch(EditorViewAction.SetField(EditorField.Action, "example.ACTION"))
            model.dispatch(EditorViewAction.SetField(EditorField.ShortcutName, "  My shortcut  "))
            model.dispatch(EditorViewAction.PinShortcut)
            model.dispatch(EditorViewAction.PinShortcut) // Prevent duplicate requests before the first finishes.
            runCurrent()
            assertEquals(EditorViewEvent.ShortcutFinished(ShortcutResult.Requested), model.eventFlow.first())
            val first = shortcuts.requests.single()
            assertEquals(listOf(Authorizer.None, Authorizer.Root, Authorizer.Shizuku, Authorizer.Auto)[mode], first.authorizer)
            assertEquals(IntentOperation.Broadcast, first.operation)
            assertEquals("example.ACTION", first.intent.action)
            assertEquals("My shortcut", first.name)
            model.dispatch(EditorViewAction.SetField(EditorField.Action, "example.CHANGED"))
            model.dispatch(EditorViewAction.PinShortcut)
            runCurrent()
            assertEquals(EditorViewEvent.ShortcutFinished(ShortcutResult.Requested), model.eventFlow.first())
            assertEquals(first.id, shortcuts.requests.last().id)
            assertEquals("example.CHANGED", shortcuts.requests.last().intent.action)
            assertEquals(false, model.uiState.value.creatingShortcut)
        }
    }

    private class FakeShortcutCreator : ShortcutCreator {
        val requests = mutableListOf<IntentShortcut>()
        override suspend fun pin(shortcut: IntentShortcut, iconUri: String?): ShortcutResult {
            requests += shortcut
            return ShortcutResult.Requested
        }
    }

    private class FakeActivityLauncher : IntentExecutor {
        var calls = 0
        var activityCalls = 0
        var broadcastCalls = 0
        override suspend fun startActivity(authorizer: Authorizer, intent: Intent): Boolean {
            calls++
            activityCalls++
            return true
        }
        override suspend fun sendBroadcast(authorizer: Authorizer, intent: Intent): Boolean {
            calls++
            broadcastCalls++
            return true
        }
    }

    private class FakeIntentBuilder : IntentBuilder {
        override fun build(spec: IntentSpec) = Intent()
        override fun build(spec: IntentSpec, operation: IntentOperation) = Intent()
    }

    private class FakeSavedIntentRepository : SavedIntentRepository {
        val profiles = MutableStateFlow<List<SavedIntentProfile>>(emptyList())
        override val summaries = profiles.map { entries ->
            entries.map { SavedIntentSummary(it.id, it.name, it.description, it.operation) }
        }
        override suspend fun get(id: String) = profiles.value.firstOrNull { it.id == id }
        override suspend fun upsert(profile: SavedIntentProfile) {
            profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
        }
        override suspend fun upsertAll(profiles: List<SavedIntentProfile>) {
            this.profiles.value = this.profiles.value.filterNot { existing -> profiles.any { it.id == existing.id } } + profiles
        }
        override suspend fun delete(id: String) {
            profiles.value = profiles.value.filterNot { it.id == id }
        }
    }

    private class EmptyAppProvider(private val enabled: Boolean? = null) : SystemAppProvider {
        override val packageChanges = emptyFlow<Unit>()
        override suspend fun getInstalledApps() = emptyList<InstalledAppTarget>()
        override suspend fun getActivities(packageName: String) = emptyList<ActivityTarget>()
        override suspend fun isActivityEnabled(packageName: String, className: String) = enabled
    }

    private class FakeStatusProvider : AuthorizationStatusProvider {
        override val shizukuState = MutableStateFlow(ShizukuState(AuthorizationStatus.PermissionRequired))
        val rootReady = CompletableDeferred<Unit>()
        var checks = 0
        var permissionRequests = 0
        override suspend fun checkRoot(): RootMode {
            checks++
            rootReady.await()
            return RootMode.KernelSU
        }
        override fun requestShizukuPermission() {
            permissionRequests++
        }
    }
}
