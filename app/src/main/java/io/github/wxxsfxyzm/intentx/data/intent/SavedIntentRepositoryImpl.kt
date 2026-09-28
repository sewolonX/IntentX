// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import io.github.wxxsfxyzm.intentx.data.local.room.dao.SavedIntentDao
import io.github.wxxsfxyzm.intentx.data.local.room.entity.SavedIntentEntity
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentRepository
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

class SavedIntentRepositoryImpl(
    private val dao: SavedIntentDao,
    private val codec: IntentPayloadCodec,
) : SavedIntentRepository {
    override val summaries: Flow<List<SavedIntentSummary>> = dao.observeSummaries().map { records ->
        records.map {
            SavedIntentSummary(it.id, it.name, it.description, IntentOperation.valueOf(it.operation), ProfileKind.valueOf(it.kind))
        }
    }

    override suspend fun get(id: String): SavedIntentProfile? = withContext(Dispatchers.Default) {
        dao.get(id)?.let {
            SavedIntentProfile(
                id = it.id,
                name = it.name,
                description = it.description,
                intent = codec.decode(it.payloadVersion, it.payloadJson),
                authorizer = it.authorizer,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                operation = IntentOperation.valueOf(it.operation),
                kind = ProfileKind.valueOf(it.kind),
            )
        }
    }

    override suspend fun upsert(profile: SavedIntentProfile) = upsertAll(listOf(profile))

    override suspend fun upsertAll(profiles: List<SavedIntentProfile>): Unit = withContext(Dispatchers.Default) {
        val entities = profiles.map { profile ->
            SavedIntentEntity(
                id = profile.id,
                name = profile.name,
                description = profile.description,
                operation = profile.operation.name,
                authorizer = profile.authorizer,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
                payloadVersion = codec.version,
                payloadJson = codec.encode(profile.intent),
                kind = profile.kind.name,
            )
        }
        dao.upsertAll(entities)
        Timber.d("Intent profiles persisted: count=%d", profiles.size)
    }

    override suspend fun delete(id: String) = dao.delete(id)
}
