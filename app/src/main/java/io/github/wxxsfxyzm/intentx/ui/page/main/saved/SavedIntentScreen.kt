// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.saved

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentSummary
import io.github.wxxsfxyzm.intentx.ui.EmptyPanel
import io.github.wxxsfxyzm.intentx.ui.icons.AppIcons
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.util.CollectUiEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun SavedIntentScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SavedIntentViewModel = koinViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val exportModel = koinViewModel<ProfileExportViewModel>()
    val fileCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        exportModel.write(uri?.toString())
    }
    CollectUiEvents(exportModel.eventFlow) { event ->
        when (event) {
            ProfileExportEvent.Ready -> fileCreator.launch("intentx-profiles.json")
            ProfileExportEvent.Saved -> Toast.makeText(context, R.string.export_saved, Toast.LENGTH_SHORT).show()
            ProfileExportEvent.Failed -> Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }
    var deleting by remember { mutableStateOf<SavedIntentSummary?>(null) }
    CollectUiEvents(viewModel.eventFlow) { event ->
        val message = when (event) {
            SavedIntentEvent.LaunchSucceeded -> R.string.editor_launch_success
            SavedIntentEvent.BroadcastSent -> R.string.editor_broadcast_sent
            SavedIntentEvent.LaunchFailed -> R.string.editor_launch_failed
            SavedIntentEvent.ActivityDisabled -> R.string.editor_activity_disabled
            SavedIntentEvent.ReceiverDisabled -> R.string.editor_receiver_disabled
            SavedIntentEvent.DeleteFailed -> R.string.profile_delete_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    if (profiles.isEmpty()) {
        EmptyPanel(stringResource(R.string.empty_saved), stringResource(R.string.saved_note), modifier.padding(contentPadding))
    } else {
        LazyVerticalGrid(
            modifier = modifier.fillMaxSize(),
            columns = GridCells.Adaptive(320.dp),
            contentPadding = PaddingValues(16.dp) + contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(profiles, key = SavedIntentSummary::id) { profile ->
                SavedIntentCard(
                    profile = profile,
                    onLaunch = { viewModel.launch(profile.id) },
                    onEdit = {
                        navigator.push(Route.Editor(activityLabel = profile.name, profileId = profile.id, operation = profile.operation, kind = profile.kind))
                    },
                    onDelete = { deleting = profile },
                    onExport = { exportModel.export(profile.id) },
                )
            }
        }
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    deleting = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedIntentCard(
    profile: SavedIntentSummary,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(profile.name, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(
                        when (profile.kind) {
                            ProfileKind.Link -> R.string.creation_open_link
                            ProfileKind.File -> R.string.creation_open_file
                            ProfileKind.CustomIntent -> if (profile.operation == IntentOperation.Activity) R.string.operation_activity else R.string.operation_broadcast
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.description.isNotEmpty()) {
                    Text(profile.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onLaunch) {
                Icon(Icons.Outlined.PlayArrow, stringResource(if (profile.operation == IntentOperation.Activity) R.string.launch else R.string.send_broadcast))
            }
            IconButton(onClick = onEdit) { Icon(AppIcons.Edit, stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) { Icon(AppIcons.Delete, stringResource(R.string.delete)) }
            IconButton(onClick = onExport) { Icon(AppIcons.Export, stringResource(R.string.export_profiles)) }
        }
    }
}

@Composable
fun SavedIntentExportAction(viewModel: ProfileExportViewModel = koinViewModel()) {
    IconButton(onClick = { viewModel.export() }) { Icon(AppIcons.Export, stringResource(R.string.export_profiles)) }
}
