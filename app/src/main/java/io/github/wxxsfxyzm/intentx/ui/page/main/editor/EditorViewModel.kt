// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.domain.catalog.SystemAppProvider
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.domain.settings.repository.AppSettingsRepository
import io.github.wxxsfxyzm.intentx.domain.shortcut.IntentShortcut
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutCreator
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutResult
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.executor.IntentExecutor
import io.github.wxxsfxyzm.intentx.ui.parseFlags
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val intentExecutor: IntentExecutor,
    private val settings: AppSettingsRepository,
    private val intentBuilder: IntentBuilder,
    private val profiles: SavedIntentRepository,
    private val appProvider: SystemAppProvider,
    private val shortcuts: ShortcutCreator,
    private val json: Json,
) : ViewModel() {
    private val operationFields = setOf(EditorField.ClassName, EditorField.User, EditorField.Display, EditorField.LaunchBounds)
    private val _uiState = MutableStateFlow(
        savedStateHandle.get<String>("editor_draft")?.let { json.decodeFromString<EditorViewState>(it) } ?: EditorViewState(),
    )
    val uiState = _uiState.asStateFlow()
    private val events = Channel<EditorViewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()
    private var editingProfile: SavedIntentProfile? = null
    private var componentSearchJob: Job? = null

    init {
        savedStateHandle.get<String>("editor_profile_id")?.let(::loadProfile)
        viewModelScope.launch {
            val preferredAuthorizer = try {
                settings.authorizer.first()
            } catch (error: IOException) {
                Timber.e(error, "Unable to read preferred editor authorizer")
                return@launch
            }
            val launchMode = when (preferredAuthorizer) {
                Authorizer.None -> 0
                Authorizer.Root -> 1
                Authorizer.Shizuku -> 2
                Authorizer.Auto -> 3
            }
            Timber.d("Editor preferred authorizer loaded: %s", preferredAuthorizer)
            _uiState.update { state ->
                val updated = if (EditorChoice.LaunchMode in state.choices) {
                    state
                } else {
                    state.copy(choices = state.choices + (EditorChoice.LaunchMode to launchMode))
                }
                if (updated.operation == IntentOperation.Activity && EditorIntentMapper.authorizer(updated).requiresNewTask) {
                    updated.withRequiredNormalLaunchFlag()
                } else {
                    updated
                }
            }
            savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
        }
        viewModelScope.launch {
            try {
                val packages = appProvider.getInstalledApps()
                    .asSequence()
                    .map { it.packageName }
                    .distinct()
                    .sorted()
                    .toList()
                _uiState.update { it.copy(packageSuggestions = packages) }
                savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
                refreshComponentSuggestions(_uiState.value.fields[EditorField.PackageName].orEmpty())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Unable to load package suggestions")
                // Suggestions are optional; free-form component editing remains available.
            }
        }
    }

    fun prefillComponent(packageName: String?, className: String?, componentLabel: String?, operation: IntentOperation) {
        if (packageName == null || className == null || savedStateHandle.get<Boolean>("component_prefilled") == true) return
        Timber.d("Prefilling %s editor: %s/%s", operation, packageName, className)
        _uiState.update {
            it.copy(
                operation = operation,
                operationLocked = true,
                flags = if (operation == IntentOperation.Broadcast) "0x00000000" else it.flags,
                fields = it.fields + mapOf(
                    EditorField.Title to (componentLabel.orEmpty()),
                    EditorField.PackageName to packageName,
                    EditorField.ClassName to className,
                ),
            )
        }
        savedStateHandle["component_prefilled"] = true
        savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
        refreshComponentSuggestions(packageName)
    }

    fun loadProfile(id: String) {
        if (editingProfile?.id == id) return
        viewModelScope.launch {
            try {
                val profile = profiles.get(id)
                if (profile == null) {
                    Timber.w("Requested profile no longer exists: id=%s", id)
                    events.send(EditorViewEvent.SaveFailed)
                    return@launch
                }
                editingProfile = profile
                Timber.d("Editor profile loaded: id=%s, operation=%s, authorizer=%s", id, profile.operation, profile.authorizer)
                if (savedStateHandle.get<String>("editor_profile_id") != id) {
                    _uiState.value = EditorIntentMapper.restore(profile)
                    savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
                    savedStateHandle["editor_profile_id"] = id
                    refreshComponentSuggestions(profile.intent.packageName.orEmpty())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Unable to load editor profile: id=%s", id)
                events.send(EditorViewEvent.SaveFailed)
            }
        }
    }

    fun suggestedProfileName(): String = _uiState.value.fields[EditorField.Title].orEmpty()

    fun dispatch(action: EditorViewAction) {
        if (action is EditorViewAction.Launch) {
            launchIntent(_uiState.value)
            return
        }
        if (action is EditorViewAction.Save) {
            saveProfile(action)
            return
        }
        if (action is EditorViewAction.PinShortcut) {
            pinShortcut()
            return
        }
        if (action is EditorViewAction.ImportUri || action is EditorViewAction.ExportUri) {
            viewModelScope.launch { events.send(EditorViewEvent.FeatureNotConnected) }
            return
        }
        _uiState.update { state ->
            when (action) {
                is EditorViewAction.SetOperation -> {
                    if (state.operationLocked || state.profileId != null || state.operation == action.operation) {
                        state
                    } else {
                        val drafts = state.operationDrafts + (
                            state.operation to OperationDraftState(
                                fields = state.fields.filterKeys(operationFields::contains),
                                flags = state.flags,
                                windowMode = state.choices[EditorChoice.WindowMode],
                            )
                            )
                        val incoming = drafts[action.operation]
                        state.copy(
                            operation = action.operation,
                            operationDrafts = drafts,
                            fields = (state.fields - operationFields) + incoming?.fields.orEmpty(),
                            flags = incoming?.flags ?: if (action.operation == IntentOperation.Activity) {
                                IntentFlagCatalog.defaultActivityFlagsText
                            } else {
                                "0x00000000"
                            },
                            choices = (state.choices - EditorChoice.WindowMode) +
                                (incoming?.windowMode?.let { mapOf(EditorChoice.WindowMode to it) }.orEmpty()),
                        )
                    }
                }

                is EditorViewAction.SetProfileName -> state.copy(profileName = action.value)

                is EditorViewAction.SetProfileDescription -> state.copy(profileDescription = action.value)

                is EditorViewAction.SetField -> state.copy(fields = state.fields + (action.field to action.value))

                is EditorViewAction.SetChoice -> {
                    val updated = state.copy(choices = state.choices + (action.choice to action.value))
                    if (state.operation == IntentOperation.Activity && action.choice == EditorChoice.LaunchMode && EditorIntentMapper.authorizer(
                            updated,
                        ).requiresNewTask
                    ) {
                        updated.withRequiredNormalLaunchFlag()
                    } else {
                        updated
                    }
                }

                is EditorViewAction.SetFlags -> state.copy(flags = action.value)

                is EditorViewAction.ToggleFlag -> {
                    val mask = parseFlags(state.flags)
                    if (mask == null) {
                        state
                    } else {
                        val bit = action.flag.toUInt()
                        val value = if (action.checked) mask or bit else mask and bit.inv()
                        state.copy(flags = "0x" + value.toString(16).padStart(8, '0'))
                    }
                }

                EditorViewAction.AddExtra -> state.copy(
                    extras = state.extras + DraftRowState(state.nextId, type = ExtraType.Auto.ordinal),
                    nextId = state.nextId + 1,
                )

                is EditorViewAction.UpdateExtra -> state.copy(extras = state.extras.map { if (it.id == action.row.id) action.row else it })

                is EditorViewAction.RemoveExtra -> state.copy(extras = state.extras.filterNot { it.id == action.id })

                EditorViewAction.AddCategory -> state.copy(
                    categories = state.categories + DraftRowState(state.nextId),
                    nextId = state.nextId + 1,
                )

                is EditorViewAction.UpdateCategory -> state.copy(categories = state.categories.map { if (it.id == action.row.id) action.row else it })

                is EditorViewAction.RemoveCategory -> state.copy(categories = state.categories.filterNot { it.id == action.id })

                EditorViewAction.AddClip -> state.copy(
                    clips = state.clips + DraftRowState(state.nextId),
                    nextId = state.nextId + 1,
                )

                is EditorViewAction.UpdateClip -> state.copy(clips = state.clips.map { if (it.id == action.row.id) action.row else it })

                is EditorViewAction.RemoveClip -> state.copy(clips = state.clips.filterNot { it.id == action.id })
            }
        }
        if (action is EditorViewAction.SetOperation) {
            refreshComponentSuggestions(_uiState.value.fields[EditorField.PackageName].orEmpty())
        }
        if (action is EditorViewAction.SetField && action.field == EditorField.PackageName) {
            refreshComponentSuggestions(action.value)
        }
        savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
    }

    private fun refreshComponentSuggestions(packageName: String) {
        componentSearchJob?.cancel()
        val packageValue = packageName.trim()
        if (packageValue.isEmpty()) {
            _uiState.update { it.copy(componentSuggestions = emptyList()) }
            return
        }
        componentSearchJob = viewModelScope.launch {
            delay(120.milliseconds)
            try {
                val operation = _uiState.value.operation
                val components = when (operation) {
                    IntentOperation.Activity -> appProvider.getActivities(packageValue).map {
                        ComponentSuggestion(it.packageName, it.className, it.label, it.exported, it.enabled)
                    }

                    IntentOperation.Broadcast -> appProvider.getReceivers(packageValue).map {
                        ComponentSuggestion(it.packageName, it.className, it.label, it.exported, it.enabled)
                    }
                }
                _uiState.update { it.copy(componentSuggestions = components.sortedBy { component -> component.className }) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is PackageManager.NameNotFoundException) {
                    Timber.w(error, "Component suggestions unavailable: package=%s", packageValue)
                }
                _uiState.update { it.copy(componentSuggestions = emptyList()) }
            }
        }
    }

    private fun launchIntent(state: EditorViewState) {
        viewModelScope.launch {
            Timber.d(
                "Executing editor intent: operation=%s, authorizer=%s",
                state.operation,
                EditorIntentMapper.authorizer(state),
            )
            try {
                val spec = EditorIntentMapper.parse(state)
                val disabled = if (spec.packageName != null && spec.className != null) {
                    try {
                        when (state.operation) {
                            IntentOperation.Activity -> appProvider.isActivityEnabled(spec.packageName, spec.className) == false
                            IntentOperation.Broadcast -> appProvider.isReceiverEnabled(spec.packageName, spec.className) == false
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.w(error, "Unable to check component enabled state")
                        false
                    }
                } else {
                    false
                }
                if (disabled) {
                    Timber.w("Execution blocked: component is disabled")
                    events.send(if (state.operation == IntentOperation.Activity) EditorViewEvent.ActivityDisabled else EditorViewEvent.ReceiverDisabled)
                    return@launch
                }
                val intent = intentBuilder.build(spec, state.operation)
                val authorizer = EditorIntentMapper.authorizer(state)
                val success = when (state.operation) {
                    IntentOperation.Activity -> intentExecutor.startActivity(authorizer, intent)
                    IntentOperation.Broadcast -> intentExecutor.sendBroadcast(authorizer, intent)
                }
                if (success) Timber.d("Editor execution submitted successfully") else Timber.w("Editor execution rejected by Android")
                events.send(
                    if (success) {
                        if (state.operation == IntentOperation.Activity) EditorViewEvent.LaunchSucceeded else EditorViewEvent.BroadcastSent
                    } else {
                        EditorViewEvent.LaunchFailed
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                Timber.w(error, "Editor intent validation failed")
                events.send(EditorViewEvent.InvalidIntent(error.message ?: "Invalid Intent"))
            } catch (error: Exception) {
                Timber.e(error, "Editor intent execution failed")
                events.send(EditorViewEvent.LaunchFailed)
            }
        }
    }

    private fun saveProfile(action: EditorViewAction.Save) {
        viewModelScope.launch {
            try {
                val name = action.name.trim()
                require(name.isNotEmpty()) { "Enter a profile name" }
                val spec = EditorIntentMapper.parse(_uiState.value)
                intentBuilder.build(spec, _uiState.value.operation) // Validate the same fields used during execution.
                val now = System.currentTimeMillis()
                val existingId = _uiState.value.profileId
                val previous = editingProfile ?: existingId?.let { id ->
                    profiles.get(id)
                }
                require(existingId == null || previous != null) { "Profile no longer exists" }
                val profile = SavedIntentProfile(
                    id = previous?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    description = action.description.trim(),
                    intent = spec,
                    authorizer = EditorIntentMapper.authorizer(_uiState.value).name,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    operation = _uiState.value.operation,
                )
                profiles.upsert(profile)
                Timber.d("Profile saved: id=%s, operation=%s, authorizer=%s", profile.id, profile.operation, profile.authorizer)
                editingProfile = profile
                _uiState.update { it.copy(profileId = profile.id, profileName = name, profileDescription = profile.description) }
                savedStateHandle["editor_draft"] = json.encodeToString(_uiState.value)
                savedStateHandle["editor_profile_id"] = profile.id
                events.send(EditorViewEvent.SaveSucceeded)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                Timber.w(error, "Profile validation failed")
                events.send(EditorViewEvent.InvalidIntent(error.message ?: "Invalid Intent"))
            } catch (error: Exception) {
                Timber.e(error, "Unable to save Intent profile")
                events.send(EditorViewEvent.SaveFailed)
            }
        }
    }

    private fun pinShortcut() {
        if (_uiState.value.creatingShortcut) return
        val state = _uiState.value
        _uiState.update { it.copy(creatingShortcut = true) }
        viewModelScope.launch {
            Timber.d(
                "Creating editor shortcut: operation=%s, authorizer=%s",
                state.operation,
                EditorIntentMapper.authorizer(state),
            )
            try {
                val spec = EditorIntentMapper.parse(state)
                intentBuilder.build(spec, state.operation)
                val name = state.fields[EditorField.ShortcutName].orEmpty().trim().ifEmpty {
                    state.profileName.ifBlank {
                        state.fields[EditorField.Title].orEmpty().ifBlank {
                            spec.className?.substringAfterLast('.') ?: spec.packageName ?: spec.action.orEmpty()
                        }
                    }
                }
                require(name.isNotBlank()) { "Enter a shortcut name" }
                val id = state.profileId?.let { "profile_$it" }
                    ?: savedStateHandle.get<String>("editor_shortcut_id")
                    ?: UUID.randomUUID().toString().also {
                        savedStateHandle["editor_shortcut_id"] = it
                    }
                val shortcut = IntentShortcut(
                    id,
                    UUID.randomUUID().toString(),
                    name,
                    spec,
                    state.operation,
                    EditorIntentMapper.authorizer(state),
                )
                val result = shortcuts.pin(shortcut, state.fields[EditorField.ShortcutIcon]?.trim()?.ifEmpty { null })
                Timber.d("Shortcut creation result: %s", result)
                events.send(EditorViewEvent.ShortcutFinished(result))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                Timber.w(error, "Shortcut validation failed")
                events.send(EditorViewEvent.InvalidIntent(error.message ?: "Invalid shortcut"))
            } catch (error: Exception) {
                Timber.e(error, "Unable to create shortcut")
                events.send(EditorViewEvent.ShortcutFinished(ShortcutResult.Failed))
            } finally {
                _uiState.update { it.copy(creatingShortcut = false) }
            }
        }
    }

    private fun EditorViewState.withRequiredNormalLaunchFlag(): EditorViewState {
        val mask = parseFlags(flags) ?: return this
        val updated = mask or IntentFlagCatalog.defaultActivityFlags.toUInt()
        return copy(flags = "0x" + updated.toString(16).padStart(8, '0'))
    }
}
