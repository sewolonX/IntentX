// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import io.github.wxxsfxyzm.intentx.di.serializationModule
import io.github.wxxsfxyzm.intentx.domain.intent.ClipDataSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ClipItemSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.koin.dsl.koinApplication

class IntentPayloadCodecTest {
    private val serialization = koinApplication { modules(serializationModule) }
    private val codec = IntentPayloadCodec(serialization.koin.get())
    private val intent = IntentSpec(
        packageName = "example.app",
        className = "example.app.Main",
        action = "example.ACTION",
        dataUri = "content://example/document/1",
        mimeType = "text/plain",
        categories = listOf("example.CATEGORY"),
        flags = Int.MIN_VALUE or 0x10000000,
        extras = listOf(
            ExtraSpec("message", ExtraType.String, "a=b\n中文"),
            ExtraSpec("ids", ExtraType.LongArray, "1\n9223372036854775807"),
            ExtraSpec("bundle", ExtraType.Bundle, "{\"nested\":{\"enabled\":true}}"),
        ),
        clipData = ClipDataSpec("Label", listOf("text/plain"), listOf(ClipItemSpec(null, null, "content://example/1", null))),
    )

    @After
    fun tearDown() {
        serialization.close()
    }

    @Test
    fun preservesTypedValuesClipsAndAllFlagBits() {
        assertEquals(intent, codec.decode(codec.version, codec.encode(intent)))
    }

    @Test
    fun usesInjectedUnknownFieldPolicyAndRejectsUnsupportedVersions() {
        val payload = codec.encode(intent).replaceFirst("{", "{\"futureField\":true,")
        assertEquals(intent, codec.decode(codec.version, payload))
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.version + 1, payload) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.version, "invalid") }
    }
}
