// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseItemContainer
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn
import io.github.wxxsfxyzm.intentx.ui.util.CollectUiEvents
import io.github.wxxsfxyzm.intentx.ui.util.clearFocusOnImeDismiss
import org.koin.androidx.compose.koinViewModel

@Composable
fun ImportPage(route: Route.Import, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(), viewModel: ImportViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    LaunchedEffect(viewModel, route) { viewModel.initialize(route.initialText) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.dispatch(ImportViewAction.ReadDocument(uri.toString()))
    }
    CollectUiEvents(viewModel.eventFlow) {
        when (it) {
            is ImportViewEvent.Review -> navigator.replace(Route.Editor(kind = it.draft.kind, draft = it.draft, returnToSaved = true))

            ImportViewEvent.Saved -> {
                Toast.makeText(context, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                navigator.popUntil { key -> key == Route.Main }
            }
        }
    }
    ImportContent(state, viewModel::dispatch, onSelectFile = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier, contentPadding)
}

@Composable
private fun ImportContent(state: ImportViewState, onAction: (ImportViewAction) -> Unit, onSelectFile: () -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item(key = "input") {
            SegmentedColumn {
                item(key = "file") {
                    BaseWidget(title = stringResource(R.string.import_select_file), iconPlaceholder = false, enabled = !state.busy, onClick = onSelectFile)
                }
                item(key = "text") {
                    BaseItemContainer {
                        OutlinedTextField(
                            state.input,
                            { onAction(ImportViewAction.Input(it)) },
                            label = { Text(stringResource(R.string.import_text)) },
                            minLines = 3,
                            maxLines = 8,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().clearFocusOnImeDismiss().padding(12.dp),
                        )
                    }
                }
            }
            TextButton(onClick = { onAction(ImportViewAction.Preview) }, enabled = !state.busy && state.input.isNotBlank(), modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.import_preview))
            }
            state.error?.let { Text(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
        }
        if (state.entries.isNotEmpty()) {
            item(key = "notice") {
                Text(stringResource(R.string.import_access_notice), Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(state.entries, key = { index, _ -> "entry_$index" }) { index, entry ->
                val kind = stringResource(
                    when (entry.kind) {
                        ProfileKind.CustomIntent -> R.string.creation_custom_intent
                        ProfileKind.Link -> R.string.creation_open_link
                        ProfileKind.File -> R.string.creation_open_file
                    },
                )
                val operation = stringResource(if (entry.operation == IntentOperation.Activity) R.string.operation_activity else R.string.operation_broadcast)
                val authorizer = stringResource(
                    when (entry.authorizer) {
                        "Root" -> R.string.auth_mode_root
                        "Shizuku" -> R.string.auth_mode_shizuku
                        "Auto" -> R.string.auth_mode_auto
                        else -> R.string.normal
                    },
                )
                SegmentedColumn {
                    item(key = "entry") {
                        BaseWidget(
                            title = entry.name,
                            description = listOf(entry.description, kind, operation, authorizer).filter(String::isNotBlank).joinToString(" · "),
                            iconPlaceholder = false,
                            enabled = !state.busy,
                            onClick = { onAction(ImportViewAction.Review(index)) },
                            trailingContent = { Checkbox(index in state.selected, { onAction(ImportViewAction.Toggle(index)) }, enabled = !state.busy) },
                        )
                    }
                }
            }
            item(key = "save") {
                Button(onClick = { onAction(ImportViewAction.SaveSelected) }, enabled = !state.busy && state.selected.isNotEmpty(), modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.import_selected))
                }
            }
        }
    }
}
