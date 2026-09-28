// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Independent snapshots survive profile edits and deletion; no profile foreign key. */
@Entity(tableName = "intent_shortcuts")
data class ShortcutEntity(
    @PrimaryKey val id: String,
    val token: String,
    val name: String,
    val operation: String,
    val authorizer: String,
    val version: Int,
    val payloadVersion: Int,
    val payloadJson: String,
)
