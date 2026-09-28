// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.saved

sealed interface ProfileExportEvent {
    data object Ready : ProfileExportEvent
    data object Saved : ProfileExportEvent
    data object Failed : ProfileExportEvent
}
