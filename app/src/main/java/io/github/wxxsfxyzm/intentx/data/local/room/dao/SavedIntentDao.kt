// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.wxxsfxyzm.intentx.data.local.room.entity.SavedIntentEntity
import io.github.wxxsfxyzm.intentx.data.local.room.entity.SavedIntentSummaryRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedIntentDao {
    @Query("SELECT id, name, description, operation, kind FROM saved_intents ORDER BY updatedAt DESC, id DESC")
    fun observeSummaries(): Flow<List<SavedIntentSummaryRecord>>

    @Query("SELECT * FROM saved_intents WHERE id = :id")
    suspend fun get(id: String): SavedIntentEntity?

    @Upsert
    suspend fun upsert(profile: SavedIntentEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<SavedIntentEntity>)

    @Query("DELETE FROM saved_intents WHERE id = :id")
    suspend fun delete(id: String)
}
