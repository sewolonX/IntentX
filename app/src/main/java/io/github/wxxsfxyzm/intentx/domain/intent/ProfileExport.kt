// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

import kotlinx.serialization.Serializable

/** Portable configuration deliberately excludes local IDs, timestamps, and capability tokens. */
@Serializable
data class ProfileExport(
    val name: String,
    val description: String = "",
    val kind: ProfileKind = ProfileKind.CustomIntent,
    val operation: IntentOperation = IntentOperation.Activity,
    val authorizer: String = "None",
    val intent: IntentSpec,
)
