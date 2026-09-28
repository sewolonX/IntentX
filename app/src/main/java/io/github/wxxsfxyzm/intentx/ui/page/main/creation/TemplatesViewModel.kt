// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import androidx.lifecycle.ViewModel
import io.github.wxxsfxyzm.intentx.domain.intent.IntentTemplate
import io.github.wxxsfxyzm.intentx.framework.intent.TemplateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class TemplatesViewModel(private val availability: TemplateAvailability) : ViewModel() {
    private val available = MutableStateFlow<Set<IntentTemplate>>(emptySet())
    val uiState = available.asStateFlow()

    suspend fun refresh() {
        try {
            available.value = availability.available()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Unable to check system templates")
            available.value = emptySet()
        }
    }
}
