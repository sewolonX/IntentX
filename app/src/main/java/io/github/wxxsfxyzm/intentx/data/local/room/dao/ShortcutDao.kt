// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.wxxsfxyzm.intentx.data.local.room.entity.ShortcutEntity

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM intent_shortcuts WHERE id = :id")
    suspend fun get(id: String): ShortcutEntity?

    @Upsert
    suspend fun upsert(shortcut: ShortcutEntity)
}
