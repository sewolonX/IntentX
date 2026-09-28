// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningApp
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningPolicy
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.ui.DraftField
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.DropDownMenuWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn

@Composable
fun QuickActionEditor(
    state: EditorViewState,
    onAction: (EditorViewAction) -> Unit,
    onSelectFile: () -> Unit,
    onSelectApp: () -> Unit,
    onImportUri: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val file = state.kind == ProfileKind.File
    val policies = listOf(stringResource(R.string.opening_default), stringResource(R.string.opening_ask), stringResource(R.string.opening_application))
    val policy = state.choices[EditorChoice.OpeningPolicy] ?: 0
    LazyColumn(modifier = modifier, contentPadding = contentPadding + PaddingValues(bottom = 96.dp)) {
        if (state.profileId != null) {
            item(key = "profile") {
                SegmentedColumn(title = stringResource(R.string.profile_details)) {
                    item(key = "name") {
                        DraftField(R.string.profile_name, state.profileName, { onAction(EditorViewAction.SetProfileName(it)) })
                    }
                    item(key = "description") {
                        DraftField(R.string.profile_description_optional, state.profileDescription, { onAction(EditorViewAction.SetProfileDescription(it)) }, multiline = true)
                    }
                }
            }
        }
        item(key = "target") {
            SegmentedColumn {
                item(key = "data") {
                    if (file) {
                        BaseWidget(
                            title = stringResource(R.string.select_document),
                            description = state.fields[EditorField.DocumentName]?.ifBlank { null } ?: stringResource(R.string.document_none),
                            iconPlaceholder = false,
                            onClick = { onSelectFile() },
                        )
                    } else {
                        DraftField(R.string.link_uri, state.fields[EditorField.DataUri].orEmpty(), { onAction(EditorViewAction.SetField(EditorField.DataUri, it)) })
                    }
                }
                if (file && !state.fields[EditorField.DataUri].isNullOrBlank()) {
                    item(key = "mime") {
                        BaseWidget(title = state.fields[EditorField.MimeType].orEmpty(), description = state.fields[EditorField.DataUri], iconPlaceholder = false)
                    }
                }
                item(key = "policy") {
                    DropDownMenuWidget(
                        title = stringResource(R.string.opening_policy),
                        description = policies.getOrNull(policy),
                        iconPlaceholder = false,
                        choice = policy,
                        data = policies,
                        onChoiceChange = { onAction(EditorViewAction.SetChoice(EditorChoice.OpeningPolicy, it)) },
                    )
                }
                if (policy == OpeningPolicy.Application.ordinal) {
                    item(key = "application") {
                        BaseWidget(
                            title = stringResource(R.string.opening_application),
                            description = state.fields[EditorField.PackageName]?.ifBlank { null } ?: stringResource(R.string.select_application),
                            iconPlaceholder = false,
                            onClick = { onSelectApp() },
                        )
                    }
                }
                if (!file) {
                    item(key = "intent_uri") {
                        BaseWidget(title = stringResource(R.string.import_intent_uri), description = stringResource(R.string.import_intent_uri_desc), iconPlaceholder = false, onClick = { onImportUri() })
                    }
                }
            }
        }
    }
}

@Composable
fun OpeningAppDialog(apps: List<OpeningApp>?, onSelect: (OpeningApp) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_application)) },
        text = {
            if (apps == null || apps.isEmpty()) {
                Text(stringResource(if (apps == null) R.string.quick_loading else R.string.no_matching_application))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = OpeningApp::packageName) { app ->
                        TextButton(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth()) {
                            Text(app.name, Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
