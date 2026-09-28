// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import io.github.wxxsfxyzm.intentx.domain.intent.ProfileExport

data class ImportViewState(
    val input: String = "",
    val entries: List<ProfileExport> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
)
