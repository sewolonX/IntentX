// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import io.github.wxxsfxyzm.intentx.domain.intent.ClipDataSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ClipItemSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraSpec
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.ui.parseFlags

/** Converts the editor draft into the executable subset without losing populated fields. */
object EditorIntentMapper {
    fun authorizer(state: EditorViewState): Authorizer {
        if (state.kind != ProfileKind.CustomIntent) return Authorizer.None
        return when (state.choices[EditorChoice.LaunchMode] ?: 0) {
            1 -> Authorizer.Root
            2 -> Authorizer.Shizuku
            3 -> Authorizer.Auto
            else -> Authorizer.None
        }
    }

    fun restore(profile: SavedIntentProfile): EditorViewState {
        val spec = profile.intent
        val fields = buildMap {
            put(EditorField.Title, profile.name)
            spec.packageName?.let { put(EditorField.PackageName, it) }
            spec.className?.let { put(EditorField.ClassName, it) }
            spec.action?.let { put(EditorField.Action, it) }
            spec.dataUri?.let { put(EditorField.DataUri, it) }
            spec.mimeType?.let { put(EditorField.MimeType, it) }
            spec.documentName?.let { put(EditorField.DocumentName, it) }
            if (spec.categories.isNotEmpty()) put(EditorField.Categories, spec.categories.joinToString("\n"))
            spec.clipData?.label?.let { put(EditorField.ClipLabel, it) }
            spec.clipData?.mimeTypes?.let { put(EditorField.ClipMimes, it.joinToString("\n")) }
        }
        val extras = spec.extras.mapIndexed { index, extra ->
            DraftRowState(index + 1, mapOf(EditorField.ExtraKey to extra.key, EditorField.ExtraValue to extra.value), extra.type.ordinal)
        }
        val categories = spec.categories.mapIndexed { index, category ->
            DraftRowState(extras.size + index + 1, mapOf(EditorField.Categories to category))
        }
        val clips = spec.clipData?.items.orEmpty().mapIndexed { index, item ->
            DraftRowState(
                extras.size + categories.size + index + 1,
                buildMap {
                    item.text?.let { put(EditorField.ClipText, it) }
                    item.html?.let { put(EditorField.ClipHtml, it) }
                    item.uri?.let { put(EditorField.DataUri, it) }
                    item.intentUri?.let { put(EditorField.ClipIntent, it) }
                },
            )
        }
        val mode = when (profile.authorizer) {
            Authorizer.Root.name -> 1
            Authorizer.Shizuku.name -> 2
            Authorizer.Auto.name -> 3
            else -> 0
        }
        return EditorViewState(
            operation = profile.operation,
            operationLocked = true,
            profileId = profile.id,
            profileName = profile.name,
            profileDescription = profile.description,
            fields = fields,
            choices = mapOf(EditorChoice.LaunchMode to mode, EditorChoice.OpeningPolicy to spec.openingPolicy.ordinal),
            flags = "0x" + spec.flags.toUInt().toString(16).padStart(8, '0'),
            extras = extras,
            categories = categories,
            clips = clips,
            nextId = extras.size + categories.size + clips.size + 1,
            kind = profile.kind,
            matchActivity = spec.matchActivity,
        )
    }

    fun parse(state: EditorViewState): IntentSpec {
        fun field(key: EditorField) = state.fields[key].orEmpty().trim().ifEmpty { null }

        if (state.kind != ProfileKind.CustomIntent) {
            val file = state.kind == ProfileKind.File
            val uri = requireNotNull(field(EditorField.DataUri)) { "Enter a link or select a file" }
            require(!uri.contains("#Intent;")) { "Use Import profile to parse an Intent URI" }
            require(Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*").matches(uri)) { "Enter an absolute URI with a scheme" }
            require(!file || uri.startsWith("content://")) { "Select a document using the file picker" }
            val policy = OpeningPolicy.entries.getOrNull(state.choices[EditorChoice.OpeningPolicy] ?: 0)
            requireNotNull(policy) { "Invalid opening policy" }
            val packageName = if (policy == OpeningPolicy.Application) {
                requireNotNull(field(EditorField.PackageName)) { "Select an application" }
            } else {
                null
            }
            return IntentSpec(
                packageName, null, "android.intent.action.VIEW", uri,
                if (file) field(EditorField.MimeType) ?: "application/octet-stream" else null,
                if (file) emptyList() else listOf("android.intent.category.BROWSABLE"),
                IntentFlagCatalog.defaultActivityFlags or if (file) 1 else 0,
                emptyList(), null,
                matchActivity = true,
                openingPolicy = policy,
                documentName = field(EditorField.DocumentName),
                requiresDocumentRead = file,
            )
        }

        val flags = requireNotNull(parseFlags(state.flags)) { "Invalid Intent flags" }
        val extras = state.extras.mapIndexedNotNull { index, row ->
            val key = row.fields[EditorField.ExtraKey].orEmpty().trim()
            val value = row.fields[EditorField.ExtraValue].orEmpty()
            if (key.isEmpty() && value.isBlank()) return@mapIndexedNotNull null
            require(key.isNotEmpty()) { "Extra ${index + 1} needs a key" }
            val type = ExtraType.entries.getOrNull(row.type)
            requireNotNull(type) { "Extra ${index + 1} has an unknown type" }
            ExtraSpec(key, type, value)
        }
        require(extras.map { it.key }.distinct().size == extras.size) { "Extra keys must be unique" }

        val categories = state.categories.mapIndexedNotNull { _, row ->
            val value = row.fields[EditorField.Categories].orEmpty().trim()
            if (value.isEmpty()) return@mapIndexedNotNull null
            value
        }.ifEmpty {
            // Drafts created before categories became editable rows stored one value per line.
            state.fields[EditorField.Categories].orEmpty()
                .split(',', '\n')
                .map(String::trim)
                .filter(String::isNotEmpty)
        }
        val normalizedCategories = categories.distinct()

        val clipItems = state.clips.mapIndexedNotNull { index, row ->
            fun clipField(key: EditorField) = row.fields[key].orEmpty().trim().ifEmpty { null }
            val item = ClipItemSpec(
                row.fields[EditorField.ClipText]?.takeIf(String::isNotEmpty),
                row.fields[EditorField.ClipHtml]?.takeIf(String::isNotEmpty),
                clipField(EditorField.DataUri),
                clipField(EditorField.ClipIntent),
            )
            if (item.text == null && item.html == null && item.uri == null && item.intentUri == null) return@mapIndexedNotNull null
            require(item.html == null || item.text != null) { "Clip item ${index + 1}: HTML needs plain text" }
            item
        }
        val clipLabel = field(EditorField.ClipLabel)
        val clipMimes = state.fields[EditorField.ClipMimes].orEmpty().lines().map(String::trim).filter(String::isNotEmpty).distinct()
        val clip = if (clipItems.isEmpty()) {
            require(clipLabel == null && clipMimes.isEmpty()) { "ClipData needs at least one item" }
            null
        } else {
            ClipDataSpec(clipLabel, clipMimes, clipItems)
        }

        val advanced = listOf(
            EditorField.Identifier,
            EditorField.Bounds,
            EditorField.Selector,
            EditorField.User,
            EditorField.Display,
            EditorField.LaunchBounds,
        ).firstOrNull { field(it) != null }
        require(advanced == null && (state.choices[EditorChoice.WindowMode] ?: 0) == 0) {
            "Advanced launch fields are not connected yet"
        }
        return IntentSpec(
            packageName = field(EditorField.PackageName),
            className = field(EditorField.ClassName),
            action = field(EditorField.Action),
            dataUri = field(EditorField.DataUri),
            mimeType = field(EditorField.MimeType),
            categories = normalizedCategories,
            flags = flags.toInt(),
            extras = extras,
            clipData = clip,
            matchActivity = state.matchActivity,
        )
    }
}
