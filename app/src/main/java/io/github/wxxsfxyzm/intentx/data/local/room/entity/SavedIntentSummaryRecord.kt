// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room.entity

/** Projection for list rendering; does not load executable payloads. */
data class SavedIntentSummaryRecord(
    val id: String,
    val name: String,
    val description: String,
    val operation: String,
)
