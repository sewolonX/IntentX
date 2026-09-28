// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentUriCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class EditorUriViewModel(private val codec: IntentUriCodec) : ViewModel() {
    private val events = Channel<EditorUriEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun process(action: EditorViewAction, state: EditorViewState, componentLocked: Boolean) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    when (action) {
                        EditorViewAction.ImportUri -> {
                            require(!componentLocked && state.operation == IntentOperation.Activity) { "Intent URI import requires an editable Activity target" }
                            EditorUriEvent.Imported(codec.parse(state.fields[EditorField.IntentUri].orEmpty()))
                        }

                        EditorViewAction.ExportUri -> EditorUriEvent.Exported(codec.encode(EditorIntentMapper.parse(state), state.operation, state.choices[EditorChoice.UriFormat] ?: 0))

                        else -> error("Unsupported URI action")
                    }
                }
                events.send(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w("Intent URI conversion rejected: %s", error.javaClass.simpleName)
                events.send(EditorUriEvent.Failed(if (error is java.net.URISyntaxException) "Invalid Intent URI" else error.message?.take(300) ?: "Cannot convert this Intent"))
            }
        }
    }
}
