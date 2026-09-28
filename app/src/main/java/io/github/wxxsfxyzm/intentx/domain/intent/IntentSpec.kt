// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

import kotlinx.serialization.Serializable

/** Platform-independent description of an Activity launch. */
@Serializable
data class IntentSpec(
    val packageName: String?,
    val className: String?,
    val action: String?,
    val dataUri: String?,
    val mimeType: String?,
    val categories: List<String>,
    val flags: Int,
    val extras: List<ExtraSpec>,
    val clipData: ClipDataSpec?,
    val matchActivity: Boolean = false,
    val openingPolicy: OpeningPolicy = OpeningPolicy.SystemDefault,
    val documentName: String? = null,
    val requiresDocumentRead: Boolean = false,
)

@Serializable
data class ExtraSpec(val key: String, val type: ExtraType, val value: String)

@Serializable
data class ClipDataSpec(
    val label: String?,
    val mimeTypes: List<String>,
    val items: List<ClipItemSpec>,
)

@Serializable
data class ClipItemSpec(
    val text: String?,
    val html: String?,
    val uri: String?,
    val intentUri: String?,
)
