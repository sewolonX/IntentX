// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.saved

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.data.intent.ProfileTransferCodec
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.framework.intent.DocumentAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ProfileExportViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SavedIntentRepository,
    private val codec: ProfileTransferCodec,
    private val documents: DocumentAccess,
) : ViewModel() {
    private val events = Channel<ProfileExportEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()
    private var busy = false

    fun export(id: String? = null) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (savedStateHandle.get<String>("pending_export") != null) {
                    events.send(ProfileExportEvent.Ready)
                    return@launch
                }
                val ids = if (id == null) repository.summaries.first().map { it.id } else listOf(id)
                require(ids.isNotEmpty()) { "There are no profiles to export" }
                val profiles = ids.map { requireNotNull(repository.get(it)) { "Profile no longer exists" } }
                val encoded = withContext(Dispatchers.Default) { codec.encode(profiles) }
                savedStateHandle["pending_export"] = documents.stageExport(encoded)
                events.send(ProfileExportEvent.Ready)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Profile export failed")
                events.send(ProfileExportEvent.Failed)
            } finally {
                busy = false
            }
        }
    }

    fun write(uri: String?) {
        val id = savedStateHandle.remove<String>("pending_export") ?: return
        viewModelScope.launch {
            try {
                documents.completeExport(uri?.let(Uri::parse), id)
                if (uri != null) events.send(ProfileExportEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Unable to write profile archive")
                events.send(ProfileExportEvent.Failed)
            }
        }
    }
}
