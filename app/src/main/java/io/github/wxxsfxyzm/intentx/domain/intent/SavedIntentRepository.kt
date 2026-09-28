// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

import kotlinx.coroutines.flow.Flow

interface SavedIntentRepository {
    val summaries: Flow<List<SavedIntentSummary>>
    suspend fun get(id: String): SavedIntentProfile?
    suspend fun upsert(profile: SavedIntentProfile)
    suspend fun delete(id: String)
}
