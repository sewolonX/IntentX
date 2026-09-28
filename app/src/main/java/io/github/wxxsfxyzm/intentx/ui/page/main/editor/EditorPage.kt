// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningApp
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutResult
import io.github.wxxsfxyzm.intentx.framework.intent.DocumentAccess
import io.github.wxxsfxyzm.intentx.framework.intent.OpeningAppResolver
import io.github.wxxsfxyzm.intentx.ui.IntentEditor
import io.github.wxxsfxyzm.intentx.ui.LocalAuthorizationUi
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.util.CollectUiEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun EditorPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    route: Route.Editor? = null,
    viewModel: EditorViewModel = koinViewModel(),
) {
    LaunchedEffect(route) {
        route?.let {
            if (it.profileId != null) {
                viewModel.loadProfile(it.profileId)
            } else if (it.draft != null) {
                viewModel.applyDraft(it.draft)
            } else {
                viewModel.initializeKind(it.kind)
                viewModel.prefillComponent(it.packageName, it.className, it.activityLabel, it.operation)
            }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayedState = if (route != null && (
            (route.profileId != null && state.profileId != route.profileId) ||
                (route.className != null && !state.operationLocked)
            )
    ) {
        state.copy(operation = route.operation, operationLocked = true)
    } else {
        state
    }
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val uriViewModel = koinViewModel<EditorUriViewModel>()
    CollectUiEvents(uriViewModel.eventFlow) { event ->
        when (event) {
            is EditorUriEvent.Imported -> viewModel.applyImportedIntent(event.intent)

            is EditorUriEvent.Exported -> {
                viewModel.dispatch(EditorViewAction.SetField(EditorField.IntentUri, event.uri))
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Intent URI", event.uri))
                Toast.makeText(context, R.string.intent_uri_copied, Toast.LENGTH_SHORT).show()
            }

            is EditorUriEvent.Failed -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        }
    }
    val scope = rememberCoroutineScope()
    val documents = koinInject<DocumentAccess>()
    val appResolver = koinInject<OpeningAppResolver>()
    var showApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<OpeningApp>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (name, mime) = documents.retain(uri)
                    viewModel.selectDocument(uri.toString(), name, mime)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.w(error, "Unable to retain selected document")
                    Toast.makeText(context, R.string.document_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    LaunchedEffect(showApps, state.fields[EditorField.DataUri], state.fields[EditorField.MimeType]) {
        if (showApps) {
            apps = null
            try {
                apps = appResolver.find(state.fields[EditorField.DataUri].orEmpty(), if (state.kind == ProfileKind.File) state.fields[EditorField.MimeType] else null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Unable to resolve matching applications")
                apps = emptyList()
            }
        }
    }
    CollectUiEvents(viewModel.eventFlow) { event ->
        when (event) {
            is EditorViewEvent.UriRequested -> uriViewModel.process(if (event.export) EditorViewAction.ExportUri else EditorViewAction.ImportUri, event.state, route?.className != null)

            EditorViewEvent.LaunchSucceeded -> Toast.makeText(context, R.string.editor_launch_success, Toast.LENGTH_SHORT).show()

            EditorViewEvent.BroadcastSent -> Toast.makeText(context, R.string.editor_broadcast_sent, Toast.LENGTH_SHORT).show()

            EditorViewEvent.SaveSucceeded -> {
                Toast.makeText(context, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                if (route?.returnToSaved == true) navigator.popUntil { it == Route.Main } else navigator.pop()
            }

            EditorViewEvent.SaveFailed -> Toast.makeText(context, R.string.profile_save_failed, Toast.LENGTH_SHORT).show()

            EditorViewEvent.LaunchFailed -> Toast.makeText(context, R.string.editor_launch_failed, Toast.LENGTH_SHORT).show()

            EditorViewEvent.ActivityDisabled -> Toast.makeText(context, R.string.editor_activity_disabled, Toast.LENGTH_SHORT).show()

            EditorViewEvent.ReceiverDisabled -> Toast.makeText(context, R.string.editor_receiver_disabled, Toast.LENGTH_SHORT).show()

            is EditorViewEvent.InvalidIntent -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()

            is EditorViewEvent.ShortcutFinished -> {
                val message = when (event.result) {
                    ShortcutResult.Requested -> R.string.shortcut_requested
                    ShortcutResult.Updated -> R.string.shortcut_updated
                    ShortcutResult.Unsupported -> R.string.shortcut_unsupported
                    ShortcutResult.XiaomiPermissionRequired -> R.string.shortcut_permission_xiaomi
                    ShortcutResult.ColorOsPermissionRequired -> R.string.shortcut_permission_coloros
                    ShortcutResult.PermissionRequired -> R.string.shortcut_permission_required
                    ShortcutResult.Failed -> R.string.shortcut_failed
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (event.result in setOf(ShortcutResult.XiaomiPermissionRequired, ShortcutResult.ColorOsPermissionRequired, ShortcutResult.PermissionRequired)) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    } catch (error: Exception) {
                        Timber.w(error, "Unable to open shortcut permission settings")
                        // Some OEMs have no handler; the permission hint remains useful.
                    }
                }
            }
        }
    }
    if (displayedState.kind != ProfileKind.CustomIntent) {
        QuickActionEditor(
            displayedState,
            viewModel::dispatch,
            onSelectFile = { filePicker.launch(arrayOf("*/*")) },
            onSelectApp = { showApps = true },
            onImportUri = { navigator.push(Route.Import(state.fields[EditorField.DataUri].orEmpty())) },
            modifier = modifier,
            contentPadding = contentPadding,
        )
    } else {
        IntentEditor(
            displayedState,
            LocalAuthorizationUi.current.state,
            viewModel::dispatch,
            modifier,
            contentPadding,
            allowOperationSelection = route?.profileId == null && route?.className == null,
        )
    }
    if (showApps) {
        OpeningAppDialog(apps, onSelect = {
            viewModel.dispatch(EditorViewAction.SetField(EditorField.PackageName, it.packageName))
            showApps = false
        }, onDismiss = { showApps = false })
    }
}
