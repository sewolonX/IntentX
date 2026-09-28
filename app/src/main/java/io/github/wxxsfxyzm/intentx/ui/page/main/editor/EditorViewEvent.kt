// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutResult

sealed interface EditorViewEvent {
    data class UriRequested(val export: Boolean, val state: EditorViewState) : EditorViewEvent
    data object LaunchSucceeded : EditorViewEvent
    data object BroadcastSent : EditorViewEvent
    data object SaveSucceeded : EditorViewEvent
    data object SaveFailed : EditorViewEvent
    data object LaunchFailed : EditorViewEvent
    data object ActivityDisabled : EditorViewEvent
    data object ReceiverDisabled : EditorViewEvent
    data class InvalidIntent(val message: String) : EditorViewEvent
    data class ShortcutFinished(val result: ShortcutResult) : EditorViewEvent
}
