// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentUriCodec
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileArchive
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileExport
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import java.util.UUID
import kotlinx.serialization.json.Json

class ProfileTransferCodec(private val json: Json, private val uriCodec: IntentUriCodec) {
    fun parse(text: String): List<ProfileExport> {
        require(text.toByteArray().size <= MAX_BYTES) { "Import exceeds the 1 MiB limit" }
        val input = text.trim()
        val entries = if (input.startsWith("{")) {
            val archive = json.decodeFromString<ProfileArchive>(input)
            require(archive.format == "intentx" && archive.version == 1) { "Unsupported profile archive format or version" }
            archive.profiles
        } else {
            listOf(ProfileExport(name = input.take(120), intent = uriCodec.parse(input)))
        }
        require(entries.size in 1..1000) { "Select between 1 and 1000 profiles" }
        entries.forEach(::validate)
        return entries
    }

    fun encode(profiles: List<SavedIntentProfile>): String {
        require(profiles.size in 1..1000) { "Select between 1 and 1000 profiles" }
        return json.encodeToString(
            ProfileArchive(
                format = "intentx",
                version = 1,
                profiles = profiles.map {
                    ProfileExport(it.name, it.description, it.kind, it.operation, it.authorizer, it.intent)
                },
            ),
        )
    }

    fun createProfile(entry: ProfileExport): SavedIntentProfile {
        validate(entry)
        val now = System.currentTimeMillis()
        return SavedIntentProfile(
            UUID.randomUUID().toString(), entry.name.trim(), entry.description,
            entry.intent, entry.authorizer, now, now, entry.operation, entry.kind,
        )
    }

    private fun validate(entry: ProfileExport) {
        require(entry.name.isNotBlank()) { "Every profile needs a name" }
        val authorizer = Authorizer.entries.firstOrNull { it.name == entry.authorizer }
        requireNotNull(authorizer) { "Unknown authorizer" }
        val spec = entry.intent
        require(spec.className == null || spec.className.isNotBlank()) { "A class must be nonempty" }
        require(spec.dataUri == null || Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*").matches(spec.dataUri)) { "Enter an absolute URI with a scheme" }
        require(spec.extras.map { it.key }.distinct().size == spec.extras.size && spec.extras.all { it.key.isNotBlank() }) { "Extra keys must be nonempty and unique" }
        if (entry.kind != ProfileKind.CustomIntent) {
            require(entry.operation == IntentOperation.Activity && authorizer == Authorizer.None) { "Links and files use app permissions" }
            require(spec.matchActivity && spec.className == null && spec.action == "android.intent.action.VIEW" && !spec.dataUri.isNullOrBlank()) { "Invalid link or file configuration" }
            require(spec.extras.isEmpty() && spec.clipData == null) { "Use a custom Intent for additional fields" }
            require(spec.requiresDocumentRead == (entry.kind == ProfileKind.File)) { "Invalid document access configuration" }
            require(spec.openingPolicy != OpeningPolicy.Application || !spec.packageName.isNullOrBlank()) { "Select an application" }
            require(spec.openingPolicy == OpeningPolicy.Application || spec.packageName == null) { "Only a specified application may restrict the package" }
            if (entry.kind == ProfileKind.File) {
                require(spec.dataUri.startsWith("content://") && !spec.mimeType.isNullOrBlank() && spec.flags == (IntentFlagCatalog.defaultActivityFlags or 1) && spec.categories.isEmpty()) { "Files require a document URI, MIME type, and read grant" }
            } else {
                require(spec.mimeType == null && spec.documentName == null && spec.flags == IntentFlagCatalog.defaultActivityFlags && spec.categories == listOf("android.intent.category.BROWSABLE")) { "Use a custom Intent for additional link parameters" }
                require(!spec.dataUri.contains("#Intent;")) { "Import an Intent URI as a custom Intent" }
            }
        } else {
            require(!spec.requiresDocumentRead && spec.documentName == null && spec.openingPolicy == OpeningPolicy.SystemDefault) { "Use a file or link profile for opening policies" }
        }
        if (entry.operation == IntentOperation.Activity && authorizer.requiresNewTask) {
            require(spec.flags and 0x10000000 != 0) { "This launch mode requires ACTIVITY_NEW_TASK" }
        }
        if (entry.operation == IntentOperation.Activity && !spec.matchActivity) require(!spec.packageName.isNullOrBlank()) { "Activity needs a package" }
        if (entry.operation == IntentOperation.Activity && spec.matchActivity) require(!spec.action.isNullOrBlank() || spec.dataUri != null || spec.className != null) { "Intent matching needs an action or URI" }
        if (spec.className != null) require(!spec.packageName.isNullOrBlank()) { "A class needs a package" }
        if (entry.operation == IntentOperation.Broadcast) require(spec.action != null || spec.className != null) { "Broadcast needs an action or receiver" }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
