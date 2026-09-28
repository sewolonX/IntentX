// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.intent

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.IntentUriCodec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy

class AndroidIntentUriCodec(private val builder: IntentBuilder) : IntentUriCodec {
    override fun parse(value: String): IntentSpec {
        val text = value.trim()
        require(text.isNotEmpty() && Uri.parse(text).scheme != null) { "Enter a complete URI with a scheme" }
        if (text.contains("#Intent;")) {
            require(text.endsWith(";end")) { "Incomplete Intent URI" }
            require(text.lastIndexOf('#') == text.lastIndexOf("#Intent;")) { "Escape fragment characters inside Intent URI fields" }
            val supported = setOf("scheme", "action", "category", "type", "launchFlags", "package", "component")
            text.substringAfterLast("#Intent;").removeSuffix("end").split(';').filter(String::isNotBlank).forEach {
                val key = it.substringBefore('=')
                require(key in supported || Regex("[SBbcsilfd]\\..+").matches(key)) { "Unsupported Intent URI field: $key" }
                require(it.contains('=')) { "Missing Intent URI field value" }
                val value = Uri.decode(it.substringAfter('='))
                when {
                    key == "component" -> {
                        val component = ComponentName.unflattenFromString(value)
                        require(component != null && component.packageName.isNotBlank() && component.className.isNotBlank()) { "Invalid Intent URI component" }
                    }

                    key in setOf("action", "category", "package", "type") -> require(value.isNotBlank() && value == value.trim()) { "Invalid Intent URI field: $key" }

                    key.startsWith("B.") -> require(value.toBooleanStrictOrNull() != null) { "Invalid Boolean Extra" }

                    key.startsWith("c.") -> require(value.length == 1) { "Invalid Char Extra" }

                    key.startsWith("f.") -> require(value.toFloatOrNull()?.isFinite() == true) { "Invalid Float Extra" }

                    key.startsWith("d.") -> require(value.toDoubleOrNull()?.isFinite() == true) { "Invalid Double Extra" }
                }
            }
        }
        val parsingFlags = Intent.URI_ALLOW_UNSAFE or if (text.contains("#Intent;")) 0 else Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME
        val intent = Intent.parseUri(text, parsingFlags)
        require(intent.component == null || intent.`package` == null || intent.`package` == intent.component?.packageName) { "A conflicting package restriction with an explicit component is not supported" }
        require(intent.selector == null && intent.identifier == null && intent.sourceBounds == null && intent.clipData == null) {
            "Selector, identifier, bounds, and ClipData are not supported in URI import"
        }
        val extras = intent.extras?.let { bundle ->
            bundle.keySet().sorted().map { key ->
                @Suppress("DEPRECATION")
                val value = bundle.get(key)
                val type = when (value) {
                    is String -> ExtraType.String
                    is Boolean -> ExtraType.Boolean
                    is Byte -> ExtraType.Byte
                    is Short -> ExtraType.Short
                    is Int -> ExtraType.Int
                    is Long -> ExtraType.Long
                    is Float -> ExtraType.Float
                    is Double -> ExtraType.Double
                    is Char -> ExtraType.Char
                    else -> error("Unsupported URI Extra: $key")
                }
                ExtraSpec(key, type, value.toString())
            }
        }.orEmpty()
        require(extras.all { it.key.isNotBlank() && it.key == it.key.trim() }) { "Extra keys must be nonempty and have no surrounding whitespace" }
        return IntentSpec(
            intent.component?.packageName ?: intent.`package`, intent.component?.className,
            intent.action, intent.dataString, intent.type,
            intent.categories.orEmpty().sorted(), intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK, extras, null,
            matchActivity = intent.component == null,
        )
    }

    override fun encode(spec: IntentSpec, operation: IntentOperation, format: Int): String {
        require(operation == IntentOperation.Activity) { "Use profile JSON to export a broadcast operation" }
        require(spec.clipData == null && !spec.requiresDocumentRead && spec.openingPolicy != OpeningPolicy.AlwaysAsk) {
            "Use profile JSON to export ClipData, document access, or an opening policy"
        }
        val supported = setOf(
            ExtraType.String, ExtraType.Boolean, ExtraType.Byte, ExtraType.Short, ExtraType.Int,
            ExtraType.Long, ExtraType.Float, ExtraType.Double, ExtraType.Char,
        )
        require(spec.extras.all { (if (it.type == ExtraType.Auto) ExtraType.infer(it.value) else it.type) in supported }) {
            "Intent URI cannot preserve these Extra types; use profile JSON"
        }
        val intent = builder.build(spec, operation)
        val uri = intent.toUri(
            when (format) {
                0 -> Intent.URI_INTENT_SCHEME
                1 -> Intent.URI_ANDROID_APP_SCHEME
                2 -> 0
                else -> error("Unknown URI format")
            },
        )
        val restored = parse(uri)
        val extras = intent.extras

        @Suppress("DEPRECATION")
        val extrasPreserved = restored.extras.map { it.key }.toSet() == extras?.keySet().orEmpty() &&
            restored.extras.all { it.value == extras?.get(it.key).toString() }
        require(
            restored.action == intent.action && restored.dataUri == intent.dataString && restored.mimeType == intent.type &&
                restored.packageName == (intent.component?.packageName ?: intent.`package`) && restored.className == intent.component?.className &&
                restored.categories.toSet() == intent.categories.orEmpty() && restored.flags == intent.flags && extrasPreserved,
        ) {
            "This URI format cannot preserve the Intent; use profile JSON"
        }
        return uri
    }
}
