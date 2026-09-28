// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec

sealed interface EditorUriEvent {
    data class Imported(val intent: IntentSpec) : EditorUriEvent
    data class Exported(val uri: String) : EditorUriEvent
    data class Failed(val message: String) : EditorUriEvent
}
