// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.domain.catalog.SystemAppProvider
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.executor.IntentExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class SavedIntentViewModel(
    private val repository: SavedIntentRepository,
    private val builder: IntentBuilder,
    private val executor: IntentExecutor,
    private val appProvider: SystemAppProvider,
) : ViewModel() {
    val profiles = repository.summaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val events = Channel<SavedIntentEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun launch(id: String) {
        viewModelScope.launch {
            try {
                val profile = requireNotNull(repository.get(id)) { "Profile no longer exists" }
                Timber.d("Executing saved profile: id=%s, operation=%s, authorizer=%s", profile.id, profile.operation, profile.authorizer)
                val spec = profile.intent
                val disabled = if (spec.packageName != null && spec.className != null) {
                    try {
                        when (profile.operation) {
                            IntentOperation.Activity -> appProvider.isActivityEnabled(spec.packageName, spec.className) == false
                            IntentOperation.Broadcast -> appProvider.isReceiverEnabled(spec.packageName, spec.className) == false
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.w(error, "Unable to check saved component enabled state")
                        false
                    }
                } else {
                    false
                }
                if (disabled) {
                    Timber.w("Saved profile execution blocked: component is disabled")
                    events.send(if (profile.operation == IntentOperation.Activity) SavedIntentEvent.ActivityDisabled else SavedIntentEvent.ReceiverDisabled)
                    return@launch
                }
                val intent = builder.build(profile.intent, profile.operation)
                val authorizer = Authorizer.entries.firstOrNull { it.name == profile.authorizer } ?: Authorizer.None
                val success = when (profile.operation) {
                    IntentOperation.Activity -> executor.startActivity(authorizer, intent)
                    IntentOperation.Broadcast -> executor.sendBroadcast(authorizer, intent)
                }
                if (success) Timber.d("Saved profile execution submitted") else Timber.w("Saved profile execution rejected by Android")
                events.send(
                    if (success) {
                        if (profile.operation == IntentOperation.Activity) SavedIntentEvent.LaunchSucceeded else SavedIntentEvent.BroadcastSent
                    } else {
                        SavedIntentEvent.LaunchFailed
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Saved profile execution failed: id=%s", id)
                events.send(SavedIntentEvent.LaunchFailed)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                Timber.d("Saved profile deleted: id=%s", id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Unable to delete saved profile: id=%s", id)
                events.send(SavedIntentEvent.DeleteFailed)
            }
        }
    }
}

enum class SavedIntentEvent { LaunchSucceeded, BroadcastSent, LaunchFailed, ActivityDisabled, ReceiverDisabled, DeleteFailed }
