// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.wxxsfxyzm.intentx.data.local.room.dao.SavedIntentDao
import io.github.wxxsfxyzm.intentx.data.local.room.dao.ShortcutDao
import io.github.wxxsfxyzm.intentx.data.local.room.entity.SavedIntentEntity
import io.github.wxxsfxyzm.intentx.data.local.room.entity.ShortcutEntity

@Database(
    entities = [SavedIntentEntity::class, ShortcutEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class IntentXDatabase : RoomDatabase() {
    abstract val savedIntentDao: SavedIntentDao
    abstract val shortcutDao: ShortcutDao
}
