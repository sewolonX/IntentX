// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.shortcut

import io.github.wxxsfxyzm.intentx.data.intent.IntentPayloadCodec
import io.github.wxxsfxyzm.intentx.data.local.room.dao.ShortcutDao
import io.github.wxxsfxyzm.intentx.data.local.room.entity.ShortcutEntity
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.shortcut.IntentShortcut
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutRepository
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ShortcutRepositoryImpl(private val dao: ShortcutDao, private val codec: IntentPayloadCodec) : ShortcutRepository {

    override suspend fun get(id: String): IntentShortcut? = withContext(Dispatchers.Default) {
        dao.get(id)?.let {
            IntentShortcut(
                id = it.id,
                token = it.token,
                name = it.name,
                intent = codec.decode(it.payloadVersion, it.payloadJson),
                operation = IntentOperation.valueOf(it.operation),
                authorizer = Authorizer.valueOf(it.authorizer),
                version = it.version,
            )
        }
    }

    override suspend fun upsert(shortcut: IntentShortcut): Unit = withContext(Dispatchers.Default) {
        dao.upsert(
            ShortcutEntity(
                id = shortcut.id,
                token = shortcut.token,
                name = shortcut.name,
                operation = shortcut.operation.name,
                authorizer = shortcut.authorizer.name,
                version = shortcut.version,
                payloadVersion = codec.version,
                payloadJson = codec.encode(shortcut.intent),
            ),
        )
        Timber.d("Shortcut snapshot persisted: id=%s, version=%d", shortcut.id, shortcut.version)
    }
}
