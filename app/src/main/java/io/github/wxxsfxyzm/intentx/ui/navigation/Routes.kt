// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.navigation
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Main : Route

    @Serializable data object Theme : Route

    @Serializable data object About : Route

    @Serializable data object Templates : Route

    @Serializable data class Import(val initialText: String = "") : Route

    @Serializable data object OpenSourceLicense : Route

    @Serializable data class Activities(val packageName: String, val appLabel: String) : Route

    @Serializable data class Editor(
        val packageName: String? = null,
        val className: String? = null,
        val activityLabel: String? = null,
        val profileId: String? = null,
        val operation: IntentOperation = IntentOperation.Activity,
        val kind: ProfileKind = ProfileKind.CustomIntent,
        val draft: SavedIntentProfile? = null,
        val returnToSaved: Boolean = false,
    ) : Route
}
