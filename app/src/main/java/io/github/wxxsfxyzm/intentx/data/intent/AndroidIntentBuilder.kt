// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import io.github.wxxsfxyzm.intentx.domain.intent.ClipDataSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/** The only place where an executable spec becomes a framework Intent. */
class AndroidIntentBuilder(
    private val context: Context,
    private val json: Json,
) : IntentBuilder {
    override fun build(spec: IntentSpec): Intent = build(spec, IntentOperation.Activity)

    override fun build(spec: IntentSpec, operation: IntentOperation): Intent {
        Timber.d(
            "Building %s Intent: package=%s, class=%s, flags=0x%s, extras=%d",
            operation,
            spec.packageName,
            spec.className,
            spec.flags.toUInt().toString(16),
            spec.extras.size,
        )
        val intent = Intent(spec.action)
        val packageName = spec.packageName
        val className = spec.className
        when (operation) {
            IntentOperation.Activity -> {
                if (className != null) {
                    require(!packageName.isNullOrBlank()) { "A component needs a package name" }
                    intent.component = ComponentName(packageName, className)
                } else if (spec.matchActivity) {
                    require(spec.action != null || spec.dataUri != null) { "Intent matching needs an action or a URI" }
                    if (spec.openingPolicy == OpeningPolicy.Application) {
                        require(!packageName.isNullOrBlank()) { "Select an application" }
                    }
                    packageName?.let(intent::setPackage)
                } else {
                    require(!packageName.isNullOrBlank()) { "Enter a package name to launch an Activity" }
                    val launcher = context.packageManager.getLaunchIntentForPackage(packageName)
                    requireNotNull(launcher?.component) { "No launcher Activity found for $packageName" }
                    Timber.d("Resolved launcher Activity: %s", launcher.component?.className)
                    intent.component = launcher.component
                }
            }

            IntentOperation.Broadcast -> {
                require(spec.action != null || className != null) { "Enter an action or a receiver class" }
                require(className == null || !packageName.isNullOrBlank()) { "A receiver class needs a package name" }
                if (className != null) {
                    intent.component = ComponentName(packageName!!, className)
                } else if (packageName != null) {
                    intent.setPackage(packageName)
                }
            }
        }
        val data = spec.dataUri?.let(::parseUri)
        when {
            data != null && spec.mimeType != null -> intent.setDataAndType(data, spec.mimeType)
            data != null -> intent.data = data
            spec.mimeType != null -> intent.type = spec.mimeType
        }
        spec.categories.forEach(intent::addCategory)
        intent.flags = spec.flags
        spec.extras.forEach { addExtra(intent, it) }
        spec.clipData?.let { intent.clipData = buildClipData(it) }
        if (spec.requiresDocumentRead) {
            require(data?.scheme == "content") { "Select a document using the file picker" }
            requireNotNull(context.contentResolver.openAssetFileDescriptor(data, "r")) { "File access is unavailable; select the file again" }.close()
        }
        if (operation == IntentOperation.Activity && spec.openingPolicy == OpeningPolicy.AlwaysAsk) {
            return Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    private fun addExtra(intent: Intent, extra: ExtraSpec) {
        val key = extra.key
        val value = extra.value
        fun <T : Any> parsed(name: String, parse: (String) -> T?): T = parse(value.trim()) ?: throw IllegalArgumentException("$key: invalid $name")

        fun lines() = value.lines().map(String::trim).filter(String::isNotEmpty)
        fun <T : Any> array(name: String, parse: (String) -> T?): List<T> = lines().mapIndexed { index, item ->
            parse(item) ?: throw IllegalArgumentException("$key: invalid $name on line ${index + 1}")
        }
        when (extra.type) {
            ExtraType.Auto -> addExtra(intent, extra.copy(type = ExtraType.infer(value)))

            ExtraType.String -> intent.putExtra(key, value)

            ExtraType.Boolean -> intent.putExtra(key, parsed("Boolean", String::toBooleanStrictOrNull))

            ExtraType.Byte -> intent.putExtra(key, parsed("Byte", String::toByteOrNull))

            ExtraType.Short -> intent.putExtra(key, parsed("Short", String::toShortOrNull))

            ExtraType.Int -> intent.putExtra(key, parsed("Int", String::toIntOrNull))

            ExtraType.Long -> intent.putExtra(key, parsed("Long", String::toLongOrNull))

            ExtraType.Float -> intent.putExtra(key, parsed("Float") { it.toFloatOrNull()?.takeIf(Float::isFinite) })

            ExtraType.Double -> intent.putExtra(key, parsed("Double") { it.toDoubleOrNull()?.takeIf(Double::isFinite) })

            ExtraType.Char -> intent.putExtra(key, singleChar(value, key))

            ExtraType.CharSequence -> intent.putExtra(key, value as CharSequence)

            ExtraType.Uri -> intent.putExtra(key, parseUri(value))

            ExtraType.ComponentName -> intent.putExtra(
                key,
                requireNotNull(ComponentName.unflattenFromString(value.trim())) { "$key: use package/class" },
            )

            ExtraType.Bundle -> intent.putExtra(key, parseBundle(value, key))

            ExtraType.StringArray -> intent.putExtra(key, lines().toTypedArray())

            ExtraType.BooleanArray -> intent.putExtra(key, array("Boolean", String::toBooleanStrictOrNull).toBooleanArray())

            ExtraType.ByteArray -> intent.putExtra(key, array("Byte", String::toByteOrNull).toByteArray())

            ExtraType.ShortArray -> intent.putExtra(key, array("Short", String::toShortOrNull).toShortArray())

            ExtraType.IntArray -> intent.putExtra(key, array("Int", String::toIntOrNull).toIntArray())

            ExtraType.LongArray -> intent.putExtra(key, array("Long", String::toLongOrNull).toLongArray())

            ExtraType.FloatArray -> intent.putExtra(
                key,
                array("Float") { it.toFloatOrNull()?.takeIf(Float::isFinite) }.toFloatArray(),
            )

            ExtraType.DoubleArray -> intent.putExtra(
                key,
                array("Double") { it.toDoubleOrNull()?.takeIf(Double::isFinite) }.toDoubleArray(),
            )

            ExtraType.CharArray -> intent.putExtra(key, lines().map { singleChar(it, key) }.toCharArray())

            ExtraType.StringList -> intent.putStringArrayListExtra(key, ArrayList(lines()))

            ExtraType.IntList -> intent.putIntegerArrayListExtra(key, ArrayList(array("Int", String::toIntOrNull)))
        }
    }

    private fun buildClipData(spec: ClipDataSpec): ClipData {
        require(spec.items.isNotEmpty()) { "ClipData needs at least one item" }
        val mimeTypes = (
            spec.mimeTypes + spec.items.flatMap { item ->
                buildList {
                    if (item.text != null) add("text/plain")
                    if (item.html != null) add("text/html")
                    if (item.uri != null) add("text/uri-list")
                    if (item.intentUri != null) add("text/vnd.android.intent")
                }
            }
            ).distinct()
        require(mimeTypes.isNotEmpty()) { "ClipData needs a MIME type" }
        fun item(index: Int): ClipData.Item {
            val source = spec.items[index]
            val nested = source.intentUri?.let { value ->
                try {
                    Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
                } catch (error: Exception) {
                    throw IllegalArgumentException("Clip item ${index + 1}: invalid Intent URI", error)
                }
            }
            return ClipData.Item(source.text, source.html, nested, source.uri?.let(::parseUri))
        }
        return ClipData(spec.label, mimeTypes.toTypedArray(), item(0)).also { clip ->
            (1 until spec.items.size).forEach { clip.addItem(item(it)) }
        }
    }

    private fun parseBundle(value: String, key: String): Bundle {
        val objectValue = try {
            json.parseToJsonElement(value) as? JsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("$key: invalid Bundle JSON", error)
        }
        requireNotNull(objectValue) { "$key: Bundle must be a JSON object" }
        return Bundle().apply {
            objectValue.forEach { (childKey, element) ->
                when (element) {
                    JsonNull -> putString(childKey, null)

                    is JsonObject -> putBundle(childKey, parseBundle(element.toString(), "$key.$childKey"))

                    is JsonArray -> putStringArray(
                        childKey,
                        element.map { item ->
                            require(item is JsonPrimitive && item.isString) { "$key.$childKey: use a string array" }
                            item.content
                        }.toTypedArray(),
                    )

                    is JsonPrimitive -> when {
                        element.isString -> putString(childKey, element.content)
                        element.booleanOrNull != null -> putBoolean(childKey, element.booleanOrNull!!)
                        element.longOrNull != null -> putLong(childKey, element.longOrNull!!)
                        element.doubleOrNull != null -> putDouble(childKey, element.doubleOrNull!!)
                        else -> throw IllegalArgumentException("$key.$childKey: unsupported JSON value")
                    }
                }
            }
        }
    }

    private fun parseUri(value: String): Uri {
        require(value.isNotBlank()) { "URI cannot be empty" }
        return value.trim().toUri()
    }

    private fun singleChar(value: String, key: String): Char {
        require(value.length == 1) { "$key: expected one character" }
        return value.single()
    }
}
