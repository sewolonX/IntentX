// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile

sealed interface ImportViewEvent {
    data class Review(val draft: SavedIntentProfile) : ImportViewEvent
    data object Saved : ImportViewEvent
}
