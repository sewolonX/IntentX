// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "saved_intents", indices = [Index(value = ["updatedAt", "id"])])
data class SavedIntentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val operation: String,
    val authorizer: String,
    val createdAt: Long,
    val updatedAt: Long,
    val payloadVersion: Int,
    val payloadJson: String,
)
