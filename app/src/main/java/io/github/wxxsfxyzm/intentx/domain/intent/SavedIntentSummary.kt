// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

data class SavedIntentSummary(
    val id: String,
    val name: String,
    val description: String,
    val operation: IntentOperation,
    val kind: ProfileKind = ProfileKind.CustomIntent,
)
