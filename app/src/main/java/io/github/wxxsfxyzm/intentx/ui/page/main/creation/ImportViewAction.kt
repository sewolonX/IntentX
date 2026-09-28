// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

sealed interface ImportViewAction {
    data class Input(val value: String) : ImportViewAction
    data class ReadDocument(val uri: String) : ImportViewAction
    data object Preview : ImportViewAction
    data class Toggle(val index: Int) : ImportViewAction
    data class Review(val index: Int) : ImportViewAction
    data object SaveSelected : ImportViewAction
}
