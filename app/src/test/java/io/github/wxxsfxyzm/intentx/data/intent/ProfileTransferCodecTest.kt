// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.IntentUriCodec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileTransferCodecTest {
    private val spec = IntentSpec(
        "example.app", "example.app.Main", "example.ACTION", null, null,
        listOf("example.CATEGORY"), Int.MIN_VALUE, listOf(ExtraSpec("bundle", ExtraType.Bundle, "{\"中文\":1}")), null,
    )
    private val uriCodec = object : IntentUriCodec {
        override fun parse(value: String) = spec.copy(flags = 0x10000000)
        override fun encode(spec: IntentSpec, operation: IntentOperation, format: Int) = error("Unused")
    }
    private val codec = ProfileTransferCodec(Json { encodeDefaults = true }, uriCodec)
    private val profile = SavedIntentProfile("source-id", "名字", "Description", spec, "Root", 1, 2, IntentOperation.Broadcast)

    @Test
    fun completeJsonPreservesMetadataAndTypedPayloadWhileAssigningFreshIds() {
        val encoded = codec.encode(listOf(profile))
        val entry = codec.parse(encoded).single()
        val imported = codec.createProfile(entry)
        assertEquals(profile.intent, imported.intent)
        assertEquals(profile.name, imported.name)
        assertEquals(profile.description, imported.description)
        assertEquals(profile.authorizer, imported.authorizer)
        assertEquals(profile.operation, imported.operation)
        assertNotEquals(profile.id, imported.id)
        assertNotEquals(imported.id, codec.createProfile(entry).id)
    }

    @Test
    fun linkAndFileOpeningPoliciesRoundTripWithoutLosingTheirKind() {
        val link = profile.copy(
            kind = ProfileKind.Link,
            operation = IntentOperation.Activity,
            authorizer = "None",
            intent = spec.copy(
                packageName = "example.browser", className = null, action = "android.intent.action.VIEW",
                dataUri = "https://example.com", categories = listOf("android.intent.category.BROWSABLE"), flags = 0x10000000,
                extras = emptyList(), matchActivity = true, openingPolicy = OpeningPolicy.Application,
            ),
        )
        val file = link.copy(
            kind = ProfileKind.File,
            intent = link.intent.copy(
                dataUri = "content://example/document/1",
                mimeType = "text/plain",
                categories = emptyList(),
                flags = 0x10000001,
                documentName = "Notes",
                requiresDocumentRead = true,
            ),
        )
        val entries = codec.parse(codec.encode(listOf(link, file)))
        assertEquals(listOf(ProfileKind.Link, ProfileKind.File), entries.map { it.kind })
        assertEquals(listOf(link.intent, file.intent), entries.map { it.intent })
    }

    @Test
    fun rejectsUnknownFieldsVersionsAndTransferredCapabilityTokens() {
        val encoded = codec.encode(listOf(profile))
        assertThrows(SerializationException::class.java) { codec.parse(encoded.replaceFirst("{", "{\"token\":\"secret\",")) }
        assertThrows(IllegalArgumentException::class.java) { codec.parse(encoded.replace("\"version\":1", "\"version\":2")) }
        assertThrows(SerializationException::class.java) { codec.parse(encoded.replace("\"format\":\"intentx\",", "")) }
        assertThrows(IllegalArgumentException::class.java) { codec.parse(codec.encode(listOf(profile.copy(authorizer = "unknown")))) }
    }

    @Test
    fun rejectsPartialBatchesAndOversizedInputBeforeSavingAnything() {
        val invalid = profile.copy(name = " ")
        assertThrows(IllegalArgumentException::class.java) { codec.parse(codec.encode(listOf(profile, invalid))) }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("a".repeat(ProfileTransferCodec.MAX_BYTES + 1)) }
    }

    @Test
    fun customMatchingIsExplicitAndDocumentProfilesCannotRequestRoot() {
        val matching = profile.copy(
            operation = IntentOperation.Activity,
            authorizer = "None",
            intent = spec.copy(packageName = null, className = null, action = "android.settings.WIFI_SETTINGS", matchActivity = true, flags = 0x10000000),
        )
        assertEquals(matching.intent, codec.parse(codec.encode(listOf(matching))).single().intent)
        assertThrows(IllegalArgumentException::class.java) { codec.parse(codec.encode(listOf(matching.copy(intent = matching.intent.copy(matchActivity = false))))) }
        val file = matching.copy(
            kind = ProfileKind.File,
            authorizer = "Root",
            intent = matching.intent.copy(
                action = "android.intent.action.VIEW",
                dataUri = "content://example/1",
                mimeType = "text/plain",
                extras = emptyList(),
                categories = emptyList(),
                flags = 0x10000001,
                requiresDocumentRead = true,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { codec.parse(codec.encode(listOf(file))) }
    }

    @Test
    fun uriImportProducesAnEditableCustomActivityWithAppPermissions() {
        val entry = codec.parse("intent://example#Intent;end").single()
        assertEquals(ProfileKind.CustomIntent, entry.kind)
        assertEquals(IntentOperation.Activity, entry.operation)
        assertEquals("None", entry.authorizer)
    }
}
