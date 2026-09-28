// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class EditorField {
    Title,
    PackageName,
    ClassName,
    Action,
    DataUri,
    MimeType,
    Categories,
    ClipLabel,
    ClipMimes,
    Identifier,
    Bounds,
    Selector,
    User,
    Display,
    LaunchBounds,
    IntentUri,
    ShortcutName,
    ShortcutIcon,
    ExtraKey,
    ExtraValue,
    ClipText,
    ClipHtml,
    ClipIntent,
    DocumentName,
}

@Serializable
enum class EditorChoice { LaunchMode, WindowMode, UriFormat, OpeningPolicy }

@Serializable
data class DraftRowState(
    val id: Int,
    val fields: Map<EditorField, String> = emptyMap(),
    val type: Int = 0,
)

@Serializable
data class ComponentSuggestion(
    val packageName: String,
    val className: String,
    val label: String,
    val exported: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
data class OperationDraftState(
    val fields: Map<EditorField, String> = emptyMap(),
    val flags: String,
    val windowMode: Int? = null,
)

@Serializable
data class EditorViewState(
    val operation: IntentOperation = IntentOperation.Activity,
    val operationLocked: Boolean = false,
    val profileId: String? = null,
    val profileName: String = "",
    val profileDescription: String = "",
    val fields: Map<EditorField, String> = emptyMap(),
    val choices: Map<EditorChoice, Int> = emptyMap(),
    val flags: String = IntentFlagCatalog.defaultActivityFlagsText,
    val operationDrafts: Map<IntentOperation, OperationDraftState> = emptyMap(),
    val extras: List<DraftRowState> = emptyList(),
    val categories: List<DraftRowState> = emptyList(),
    val clips: List<DraftRowState> = emptyList(),
    val nextId: Int = 1,
    @Transient val packageSuggestions: List<String> = emptyList(),
    @Transient val componentSuggestions: List<ComponentSuggestion> = emptyList(),
    @Transient val creatingShortcut: Boolean = false,
    val kind: ProfileKind = ProfileKind.CustomIntent,
    val matchActivity: Boolean = false,
)
