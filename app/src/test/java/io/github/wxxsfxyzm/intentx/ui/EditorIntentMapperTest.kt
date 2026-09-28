// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui

import android.content.Intent
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.DraftRowState
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorChoice
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorField
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorIntentMapper
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EditorIntentMapperTest {
    @Test
    fun compactLinkAndFileFormsHaveActualFlagsAndOpeningPolicy() {
        val link = EditorViewState(
            kind = ProfileKind.Link,
            fields = mapOf(EditorField.DataUri to "https://example.com"),
            choices = mapOf(EditorChoice.OpeningPolicy to OpeningPolicy.AlwaysAsk.ordinal),
        )
        val spec = EditorIntentMapper.parse(link)
        assertEquals("android.intent.action.VIEW", spec.action)
        assertEquals(0x10000000, spec.flags)
        assertEquals(true, spec.matchActivity)
        assertEquals(OpeningPolicy.AlwaysAsk, spec.openingPolicy)
        assertNull(spec.packageName)
        assertEquals("weixin:", EditorIntentMapper.parse(link.copy(fields = mapOf(EditorField.DataUri to "weixin:"))).dataUri)
        val file = link.copy(
            kind = ProfileKind.File,
            fields = mapOf(
                EditorField.DataUri to "content://example/document/1",
                EditorField.MimeType to "text/plain",
                EditorField.DocumentName to "Notes",
            ),
        )
        val document = EditorIntentMapper.parse(file)
        assertEquals(0x10000001, document.flags)
        assertEquals(true, document.requiresDocumentRead)
        assertEquals("Notes", document.documentName)
        assertEquals(Authorizer.None, EditorIntentMapper.authorizer(file.copy(choices = file.choices + (EditorChoice.LaunchMode to 1))))
        val restored = EditorIntentMapper.restore(SavedIntentProfile("file", "Notes", "", document, "None", 1, 1, kind = ProfileKind.File))
        assertEquals(document, EditorIntentMapper.parse(restored))
    }

    @Test
    fun compactFormsRejectRelativeUrisMissingApplicationsAndTypedIntentUris() {
        val state = EditorViewState(kind = ProfileKind.Link, fields = mapOf(EditorField.DataUri to "example.com"))
        assertThrows(IllegalArgumentException::class.java) { EditorIntentMapper.parse(state) }
        assertThrows(IllegalArgumentException::class.java) {
            EditorIntentMapper.parse(state.copy(fields = mapOf(EditorField.DataUri to "intent://example#Intent;end")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorIntentMapper.parse(state.copy(fields = mapOf(EditorField.DataUri to "https://example.com"), choices = mapOf(EditorChoice.OpeningPolicy to OpeningPolicy.Application.ordinal)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorIntentMapper.parse(state.copy(kind = ProfileKind.File, fields = mapOf(EditorField.DataUri to "file:///tmp/document")))
        }
    }

    @Test
    fun automaticAuthorizerSurvivesProfileRestore() {
        val profile = SavedIntentProfile(
            id = "auto",
            name = "Automatic",
            description = "",
            intent = EditorIntentMapper.parse(EditorViewState()),
            authorizer = Authorizer.Auto.name,
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertEquals(Authorizer.Auto, EditorIntentMapper.authorizer(EditorIntentMapper.restore(profile)))
    }

    @Test
    fun newDraftShowsLaunchFlagWhileAnExplicitlyClearedFlagStaysCleared() {
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, EditorIntentMapper.parse(EditorViewState()).flags)
        assertEquals(0, EditorIntentMapper.parse(EditorViewState(flags = "0x00000000")).flags)
    }

    @Test
    fun mapsExecutableFieldsExtrasAndClipItemsWithoutDroppingUnknownFlags() {
        val state = EditorViewState(
            fields = mapOf(
                EditorField.PackageName to " example.app ",
                EditorField.ClassName to "example.app.Main",
                EditorField.Action to "example.ACTION",
                EditorField.Categories to "one, two\none",
                EditorField.ClipLabel to "Selection",
            ),
            flags = "0x80000001",
            extras = listOf(
                DraftRowState(1, mapOf(EditorField.ExtraKey to "count", EditorField.ExtraValue to "42"), ExtraType.Int.ordinal),
                DraftRowState(2),
            ),
            clips = listOf(
                DraftRowState(3, mapOf(EditorField.ClipText to "text", EditorField.DataUri to "content://example/item")),
                DraftRowState(4),
            ),
        )

        val spec = EditorIntentMapper.parse(state)
        assertEquals("example.app", spec.packageName)
        assertEquals(listOf("one", "two"), spec.categories)
        assertEquals(0x80000001u.toInt(), spec.flags)
        assertEquals(1, spec.extras.size)
        assertEquals(ExtraType.Int, spec.extras.single().type)
        assertEquals("42", spec.extras.single().value)
        assertEquals("Selection", spec.clipData?.label)
        assertEquals("content://example/item", spec.clipData?.items?.single()?.uri)
    }

    @Test
    fun rejectsIncompleteOrUnsupportedDraftsInsteadOfSilentlyDroppingThem() {
        val incompleteExtra = EditorViewState(extras = listOf(DraftRowState(1, mapOf(EditorField.ExtraValue to "value"))))
        assertThrows(IllegalArgumentException::class.java) { EditorIntentMapper.parse(incompleteExtra) }

        val incompleteClip = EditorViewState(fields = mapOf(EditorField.ClipLabel to "label"))
        assertThrows(IllegalArgumentException::class.java) { EditorIntentMapper.parse(incompleteClip) }

        val advanced = EditorViewState(choices = mapOf(EditorChoice.WindowMode to 1))
        assertThrows(IllegalArgumentException::class.java) { EditorIntentMapper.parse(advanced) }
        assertNull(EditorIntentMapper.parse(EditorViewState()).clipData)
    }

    @Test
    fun savedProfileRestoresExecutableFieldsAndLaunchMode() {
        val original = EditorViewState(
            fields = mapOf(
                EditorField.PackageName to "example.app",
                EditorField.ClassName to "example.app.Main",
                EditorField.Action to "example.ACTION",
                EditorField.Categories to "one\ntwo",
                EditorField.ClipLabel to "selection",
            ),
            choices = mapOf(EditorChoice.LaunchMode to 2),
            flags = "0x80000001",
            extras = listOf(
                DraftRowState(
                    1,
                    mapOf(EditorField.ExtraKey to "count", EditorField.ExtraValue to "42"),
                    ExtraType.Int.ordinal,
                ),
            ),
            clips = listOf(DraftRowState(2, mapOf(EditorField.ClipText to "hello"))),
        )
        val profile = SavedIntentProfile("id", "My Intent", "Description", EditorIntentMapper.parse(original), "Shizuku", 1, 2)
        val persisted = Json.decodeFromString<SavedIntentProfile>(Json.encodeToString(profile))
        val restored = EditorIntentMapper.restore(persisted)

        assertEquals(profile, persisted)
        assertEquals(profile.intent, EditorIntentMapper.parse(restored))
        assertEquals(2, restored.choices[EditorChoice.LaunchMode])
        assertEquals("My Intent", restored.fields[EditorField.Title])
        assertEquals("id", restored.profileId)
        assertEquals("My Intent", restored.profileName)
        assertEquals("Description", restored.profileDescription)
        assertEquals(5, restored.nextId)
    }

    @Test
    fun oldProfileDefaultsToActivityWhileBroadcastRestoresItsOperation() {
        val legacy = SavedIntentProfile("id", "Old", "", EditorIntentMapper.parse(EditorViewState()), "None", 1, 2)
        val oldJson = Json.encodeToString(legacy)
        assertEquals(IntentOperation.Activity, Json.decodeFromString<SavedIntentProfile>(oldJson).operation)

        val broadcast = legacy.copy(operation = IntentOperation.Broadcast)
        val restored = EditorIntentMapper.restore(Json.decodeFromString<SavedIntentProfile>(Json.encodeToString(broadcast)))
        assertEquals(IntentOperation.Broadcast, restored.operation)
        assertEquals(true, restored.operationLocked)
    }
}
