// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.data.intent.ProfileTransferCodec
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.framework.intent.DocumentAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

class ImportViewModel(
    private val codec: ProfileTransferCodec,
    private val repository: SavedIntentRepository,
    private val documents: DocumentAccess,
) : ViewModel() {
    private val state = MutableStateFlow(ImportViewState())
    val uiState = state.asStateFlow()
    private val events = Channel<ImportViewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()
    private var initialized = false

    fun initialize(text: String) {
        if (initialized) return
        initialized = true
        state.value = ImportViewState(input = text)
    }

    fun dispatch(action: ImportViewAction) {
        if (state.value.busy) return
        when (action) {
            is ImportViewAction.Input -> state.value = ImportViewState(input = action.value)

            is ImportViewAction.Toggle -> if (action.index in state.value.entries.indices) {
                state.update {
                    it.copy(selected = if (action.index in it.selected) it.selected - action.index else it.selected + action.index)
                }
            }

            else -> {
                val snapshot = state.value
                state.update { it.copy(busy = true, error = null) }
                viewModelScope.launch {
                    try {
                        when (action) {
                            is ImportViewAction.ReadDocument -> {
                                val text = documents.read(Uri.parse(action.uri))
                                val entries = withContext(Dispatchers.Default) { codec.parse(text) }
                                state.value = ImportViewState(input = text, entries = entries, selected = entries.indices.toSet(), busy = true)
                            }

                            ImportViewAction.Preview -> {
                                val entries = withContext(Dispatchers.Default) { codec.parse(snapshot.input) }
                                state.update { it.copy(entries = entries, selected = entries.indices.toSet()) }
                            }

                            is ImportViewAction.Review -> events.send(ImportViewEvent.Review(codec.createProfile(snapshot.entries[action.index])))

                            ImportViewAction.SaveSelected -> {
                                require(snapshot.selected.isNotEmpty()) { "Select at least one profile" }
                                val profiles = withContext(Dispatchers.Default) { snapshot.selected.sorted().map { codec.createProfile(snapshot.entries[it]) } }
                                repository.upsertAll(profiles)
                                Timber.d("Imported profiles: count=%d", profiles.size)
                                events.send(ImportViewEvent.Saved)
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.w("Profile import rejected: %s", error.javaClass.simpleName)
                        state.update { it.copy(error = if (error is SerializationException) "Invalid or unsupported profile JSON" else error.message?.take(300) ?: "Unable to import profiles") }
                    } finally {
                        state.update { it.copy(busy = false) }
                    }
                }
            }
        }
    }
}
