// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

import kotlinx.serialization.Serializable

/** Only executable Intent fields and their launch mode are persisted. */
@Serializable
data class SavedIntentProfile(
    val id: String,
    val name: String,
    val description: String,
    val intent: IntentSpec,
    val authorizer: String,
    val createdAt: Long,
    val updatedAt: Long,
    val operation: IntentOperation = IntentOperation.Activity,
    val kind: ProfileKind = ProfileKind.CustomIntent,
)
