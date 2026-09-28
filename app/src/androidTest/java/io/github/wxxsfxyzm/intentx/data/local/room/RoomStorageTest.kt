// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.local.room

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wxxsfxyzm.intentx.data.intent.IntentPayloadCodec
import io.github.wxxsfxyzm.intentx.data.intent.SavedIntentRepositoryImpl
import io.github.wxxsfxyzm.intentx.data.shortcut.ShortcutRepositoryImpl
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.domain.shortcut.IntentShortcut
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class RoomStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "storage-test-${UUID.randomUUID()}.db"
    private val codec = IntentPayloadCodec(GlobalContext.get().get())
    private lateinit var database: IntentXDatabase
    private val intent = IntentSpec(
        "example.app", "example.app.Main", "example.ACTION", null, null,
        listOf("example.CATEGORY"), Int.MIN_VALUE or 0x10000000,
        listOf(ExtraSpec("message", ExtraType.String, "中文\na=b")), null,
    )

    @Before
    fun setUp() {
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun profilesPersistIndependentlyAndListUpdatesInDatabaseOrder() = runTest {
        var repository = SavedIntentRepositoryImpl(database.savedIntentDao, codec)
        val first = profile("first", 1)
        val second = profile("second", 2).copy(operation = IntentOperation.Broadcast, authorizer = Authorizer.Root.name)
        repository.upsert(first)
        repository.upsert(second)
        assertEquals(listOf(second.id, first.id), repository.summaries.first().map { it.id })

        val updated = first.copy(name = "Updated", updatedAt = 3)
        val change = async(start = CoroutineStart.UNDISPATCHED) {
            repository.summaries.first { it.firstOrNull()?.name == updated.name }
        }
        repository.upsert(updated)
        assertEquals(listOf(first.id, second.id), change.await().map { it.id })

        database.close()
        database = openDatabase()
        repository = SavedIntentRepositoryImpl(database.savedIntentDao, codec)
        assertEquals(updated, repository.get(first.id))
        assertEquals(second, repository.get(second.id))
        repository.delete(first.id)
        assertNull(repository.get(first.id))
        assertEquals(listOf(second.id), repository.summaries.first().map { it.id })
        assertEquals(second, repository.get(second.id))
    }

    @Test
    fun batchUpsertRollsBackEarlierWritesWhenAnyRowFails() = runTest {
        val repository = SavedIntentRepositoryImpl(database.savedIntentDao, codec)
        val original = profile("existing", 1)
        repository.upsert(original)
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("CREATE TRIGGER reject_profile BEFORE INSERT ON saved_intents WHEN NEW.id = 'reject' BEGIN SELECT RAISE(ABORT, 'rejected by test'); END")
        }
        try {
            repository.upsertAll(listOf(original.copy(name = "Changed"), profile("new", 2), profile("reject", 3)))
            fail("The rejected row must abort the whole batch")
        } catch (_: Exception) {
            // Check both the existing-row update and the preceding insert were rolled back.
        }
        assertEquals(original, repository.get(original.id))
        assertNull(repository.get("new"))
        assertNull(repository.get("reject"))
        assertEquals(listOf(original.id), repository.summaries.first().map { it.id })
    }

    @Test
    fun malformedPayloadDoesNotBlockListOrOtherProfiles() = runTest {
        val repository = SavedIntentRepositoryImpl(database.savedIntentDao, codec)
        val valid = profile("valid", 1)
        val malformed = profile("malformed", 2)
        repository.upsert(valid)
        repository.upsert(malformed)
        val record = requireNotNull(database.savedIntentDao.get(malformed.id))
        database.savedIntentDao.upsert(record.copy(payloadJson = "invalid"))
        assertEquals(2, repository.summaries.first().size)
        assertEquals(valid, repository.get(valid.id))
        try {
            repository.get(malformed.id)
            fail("Malformed payload must not become an executable Intent")
        } catch (_: SerializationException) {
            // Only this record fails; it remains visible and can be deleted.
        }
        repository.delete(malformed.id)
        assertEquals(listOf(valid.id), repository.summaries.first().map { it.id })
    }

    @Test
    fun snapshotsPersistAndRemainIndependentWhileCapabilitiesAreValidated() = runTest {
        val profiles = SavedIntentRepositoryImpl(database.savedIntentDao, codec)
        val profile = profile("profile", 1)
        profiles.upsert(profile)
        var repository = ShortcutRepositoryImpl(database.shortcutDao, codec)
        val snapshots = Authorizer.entries.map {
            IntentShortcut(it.name, "token-${it.name}", "Example", intent, IntentOperation.Activity, it)
        }
        snapshots.forEach { repository.upsert(it) }
        profiles.upsert(profile.copy(intent = intent.copy(action = "changed.ACTION")))
        profiles.delete(profile.id)

        database.close()
        database = openDatabase()
        repository = ShortcutRepositoryImpl(database.shortcutDao, codec)
        for (snapshot in snapshots) {
            assertEquals(snapshot, repository.resolve(snapshot.id, snapshot.token))
            assertNull(repository.resolve(snapshot.id, "wrong-token"))
            assertNull(repository.resolve(snapshot.id, ""))
        }
        assertNull(repository.resolve("missing", snapshots.first().token))
        val previous = snapshots.first()
        val updated = previous.copy(token = "new-token", operation = IntentOperation.Broadcast)
        repository.upsert(updated)
        assertNull(repository.resolve(previous.id, previous.token))
        assertEquals(updated, repository.resolve(updated.id, updated.token))
        assertEquals(snapshots.last(), repository.resolve(snapshots.last().id, snapshots.last().token))
        repository.upsert(updated.copy(version = 2))
        assertNull(repository.resolve(updated.id, updated.token))
    }

    private fun profile(id: String, updatedAt: Long) = SavedIntentProfile(
        id,
        id,
        "Description",
        intent,
        Authorizer.Auto.name,
        1,
        updatedAt,
    )

    private fun openDatabase(): IntentXDatabase = Room.databaseBuilder(context, IntentXDatabase::class.java, name)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
