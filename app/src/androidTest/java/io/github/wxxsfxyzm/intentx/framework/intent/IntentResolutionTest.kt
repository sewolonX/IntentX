// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.intent

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wxxsfxyzm.intentx.data.intent.AndroidIntentBuilder
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentResolutionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val builder = AndroidIntentBuilder(context, Json { ignoreUnknownKeys = true })
    private val codec = AndroidIntentUriCodec(builder)
    private val link = IntentSpec(
        null, null, Intent.ACTION_VIEW, "https://example.com", null,
        listOf(Intent.CATEGORY_BROWSABLE), Intent.FLAG_ACTIVITY_NEW_TASK, emptyList(), null, matchActivity = true,
    )

    @Test
    fun matchingDoesNotBecomeAnApplicationLauncherAndChooserPreservesItsTarget() {
        val matching = builder.build(link.copy(packageName = "example.browser", openingPolicy = OpeningPolicy.Application))
        assertNull(matching.component)
        assertEquals("example.browser", matching.`package`)
        assertEquals(Intent.ACTION_VIEW, matching.action)
        val chooser = builder.build(link.copy(openingPolicy = OpeningPolicy.AlwaysAsk))
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(link.dataUri, target.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThrows(IllegalArgumentException::class.java) { builder.build(link.copy(matchActivity = false)) }
    }

    @Test
    fun uriRoundTripPreservesScalarTypesComponentsAndAllFlags() {
        val original = link.copy(
            packageName = "example.app",
            className = "example.app.Main",
            matchActivity = false,
            flags = Int.MIN_VALUE or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            extras = listOf(ExtraSpec("message", ExtraType.String, "a=b;中文"), ExtraSpec("count", ExtraType.Int, "42"), ExtraSpec("enabled", ExtraType.Boolean, "true")),
        )
        for (format in 0..2) {
            val restored = codec.parse(codec.encode(original, IntentOperation.Activity, format))
            assertEquals(original, restored)
        }
    }

    @Test
    fun rejectsUnsupportedUriFieldsAndUnrepresentableExportInsteadOfLosingThem() {
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;action=example.ACTION;unknown=value;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;action=example.ACTION;SEL;action=other.ACTION;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;component=broken;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;component=example.app/;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;B.enabled=wrong;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.parse("intent:#Intent;c.letter=too-long;end") }
        assertThrows(IllegalArgumentException::class.java) { codec.encode(link, IntentOperation.Broadcast, 0) }
        assertThrows(IllegalArgumentException::class.java) { codec.encode(link.copy(extras = listOf(ExtraSpec("ids", ExtraType.IntArray, "1\n2"))), IntentOperation.Activity, 0) }
        assertThrows(IllegalArgumentException::class.java) { codec.encode(link.copy(openingPolicy = OpeningPolicy.AlwaysAsk), IntentOperation.Activity, 0) }
    }
}
