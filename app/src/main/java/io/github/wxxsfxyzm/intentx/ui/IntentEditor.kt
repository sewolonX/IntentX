// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.authorization.AuthorizationStatus
import io.github.wxxsfxyzm.intentx.domain.authorization.ShizukuMode
import io.github.wxxsfxyzm.intentx.domain.intent.ExtraType
import io.github.wxxsfxyzm.intentx.domain.intent.IntentActionCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.executor.Authorizer
import io.github.wxxsfxyzm.intentx.framework.shortcut.ShortcutIconLoader
import io.github.wxxsfxyzm.intentx.ui.icons.AppIcons
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.DraftRowState
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorChoice
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorField
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorIntentMapper
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewState
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewState
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseItemContainer
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.DropDownMenuWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SwitchWidget
import io.github.wxxsfxyzm.intentx.ui.util.clearFocusOnImeDismiss
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.TextFieldWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
internal fun IntentEditor(
    state: EditorViewState,
    authorization: AuthorizationViewState,
    onAction: (EditorViewAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    allowOperationSelection: Boolean = true,
) {
    val isBroadcast = state.operation == IntentOperation.Broadcast
    val availableFlags = if (isBroadcast) IntentFlagCatalog.broadcastEntries else IntentFlagCatalog.entries
    val componentReadOnly = state.operationLocked && state.profileId == null
    val activityLabel = stringResource(R.string.operation_activity)
    val broadcastLabel = stringResource(R.string.operation_broadcast)
    val flags = state.flags
    var showFlags by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val mask = parseFlags(flags)
    val capabilityNote = if (isBroadcast) {
        stringResource(R.string.editor_broadcast_capability)
    } else {
        when (state.choices[EditorChoice.LaunchMode] ?: 0) {
            3 -> stringResource(R.string.auth_auto_note)

            1 -> if (authorization.rootStatus == AuthorizationStatus.Ready) {
                stringResource(R.string.editor_capability_root, stringResource(R.string.auth_mode_root))
            } else {
                stringResource(R.string.editor_capability_unavailable)
            }

            2 -> when (authorization.shizukuState.status) {
                AuthorizationStatus.PermissionRequired -> stringResource(R.string.editor_capability_permission)

                AuthorizationStatus.Ready -> when (authorization.shizukuState.mode) {
                    ShizukuMode.Root -> stringResource(
                        R.string.editor_capability_shizuku_root,
                        stringResource(R.string.auth_mode_shizuku),
                        stringResource(R.string.auth_mode_root),
                    )

                    ShizukuMode.Shell -> stringResource(
                        R.string.editor_capability_shizuku_shell,
                        stringResource(R.string.auth_mode_shizuku),
                        stringResource(R.string.auth_mode_shell),
                    )

                    ShizukuMode.Unknown -> stringResource(R.string.editor_capability_unknown)
                }

                else -> stringResource(R.string.editor_capability_unavailable)
            }

            else -> stringResource(R.string.editor_capability_normal)
        }
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        if (allowOperationSelection && !state.operationLocked && state.profileId == null) {
            val activityInteraction = remember { MutableInteractionSource() }
            val broadcastInteraction = remember { MutableInteractionSource() }
            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(menuState = it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(activityInteraction),
                            checked = !isBroadcast,
                            onCheckedChange = { onAction(EditorViewAction.SetOperation(IntentOperation.Activity)) },
                            interactionSource = activityInteraction,
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text(activityLabel) }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        ToggleButton(
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(broadcastInteraction),
                            checked = isBroadcast,
                            onCheckedChange = { onAction(EditorViewAction.SetOperation(IntentOperation.Broadcast)) },
                            interactionSource = broadcastInteraction,
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text(broadcastLabel) }
                    },
                    menuContent = {},
                )
            }
        }
        if (state.profileId != null) {
            SegmentedColumn(title = stringResource(R.string.profile_details)) {
                item {
                    DraftField(
                        R.string.profile_name,
                        state.profileName,
                        { onAction(EditorViewAction.SetProfileName(it)) },
                    )
                }
                item {
                    DraftField(
                        R.string.profile_description_optional,
                        state.profileDescription,
                        { onAction(EditorViewAction.SetProfileDescription(it)) },
                        multiline = true,
                    )
                }
            }
        }
        SegmentedColumn(title = stringResource(R.string.component)) {
            item {
                if (componentReadOnly) {
                    ReadOnlyComponentField(R.string.title, state.fields[EditorField.Title].orEmpty())
                } else {
                    DraftField(
                        R.string.title,
                        state.fields[EditorField.Title].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Title, it)) },
                    )
                }
            }
            item {
                if (componentReadOnly) {
                    ReadOnlyComponentField(R.string.package_name, state.fields[EditorField.PackageName].orEmpty())
                } else {
                    DraftSuggestionField(
                        R.string.package_name,
                        state.fields[EditorField.PackageName].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.PackageName, it)) },
                        suggestions = state.packageSuggestions.map { DraftSuggestion(it) },
                    )
                }
            }
            item {
                if (componentReadOnly) {
                    ReadOnlyComponentField(
                        if (isBroadcast) R.string.receiver_class_name else R.string.class_name,
                        state.fields[EditorField.ClassName].orEmpty(),
                    )
                } else {
                    val packageName = state.fields[EditorField.PackageName].orEmpty().trim()
                    val componentSuggestions = if (packageName.isBlank()) {
                        emptyList<DraftSuggestion>()
                    } else {
                        state.componentSuggestions.map { suggestion ->
                            DraftSuggestion(
                                value = suggestion.className,
                                title = suggestion.label.ifBlank { suggestion.className },
                                secondaryText = suggestion.className,
                                onSelect = {
                                    onAction(EditorViewAction.SetField(EditorField.PackageName, suggestion.packageName))
                                    onAction(EditorViewAction.SetField(EditorField.ClassName, suggestion.className))
                                    if (suggestion.label.isNotBlank()) {
                                        onAction(EditorViewAction.SetField(EditorField.Title, suggestion.label))
                                    }
                                },
                            )
                        }
                    }
                    DraftSuggestionField(
                        if (isBroadcast) R.string.receiver_class_name else R.string.class_name,
                        state.fields[EditorField.ClassName].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.ClassName, it)) },
                        suggestions = componentSuggestions,
                        emptyMessage = stringResource(R.string.editor_enter_package_for_search)
                            .takeIf { packageName.isBlank() },
                    )
                }
            }
        }
        SegmentedColumn(title = stringResource(R.string.parameters)) {
            item {
                if (isBroadcast) {
                    DraftField(
                        R.string.action,
                        state.fields[EditorField.Action].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Action, it)) },
                    )
                } else {
                    DraftSuggestionField(
                        R.string.action,
                        state.fields[EditorField.Action].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Action, it)) },
                        suggestions = IntentActionCatalog.suggestions.map { DraftSuggestion(it) },
                        emptyMessage = stringResource(R.string.editor_no_matching_actions),
                    )
                }
            }
            item {
                DraftField(
                    R.string.data_uri,
                    state.fields[EditorField.DataUri].orEmpty(),
                    { onAction(EditorViewAction.SetField(EditorField.DataUri, it)) },
                )
            }
            item {
                DraftField(
                    R.string.mime_type,
                    state.fields[EditorField.MimeType].orEmpty(),
                    { onAction(EditorViewAction.SetField(EditorField.MimeType, it)) },
                )
            }
        }
        DraftRows(
            R.string.categories,
            R.string.editor_add_category,
            state.categories,
            { onAction(EditorViewAction.AddCategory) },
            { onAction(EditorViewAction.RemoveCategory(it)) },
        ) { row, remove ->
            CategoryDraft(
                row,
                state.categories.indexOfFirst { it.id == row.id } + 1,
                { onAction(EditorViewAction.UpdateCategory(it)) },
                remove,
            )
        }
        DraftRows(
            R.string.extras,
            R.string.editor_add_extra,
            state.extras,
            { onAction(EditorViewAction.AddExtra) },
            { onAction(EditorViewAction.RemoveExtra(it)) },
        ) { row, remove ->
            ExtraDraft(
                row,
                state.extras.indexOfFirst { it.id == row.id } + 1,
                { onAction(EditorViewAction.UpdateExtra(it)) },
                remove,
            )
        }
        SegmentedColumn(title = stringResource(R.string.flags)) {
            item {
                TextFieldWidget(
                    value = flags,
                    onValueChange = { onAction(EditorViewAction.SetFlags(it)) },
                    title = stringResource(R.string.flags),
                    supportingText = stringResource(if (mask == null) R.string.flags_error else R.string.flags_hint),
                    isError = mask == null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth(),
                    textFieldModifier = Modifier
                        .clearFocusOnImeDismiss()
                        .fillMaxWidth(),
                )
            }
            item {
                val arrowRotation = animateFloatAsState(
                    targetValue = if (showFlags) 180f else 0f,
                    label = "namedFlagsArrow",
                )
                val expansionState =
                    stringResource(if (showFlags) R.string.editor_flags_expanded else R.string.editor_flags_collapsed)
                BaseWidget(
                    modifier = Modifier.semantics { stateDescription = expansionState },
                    title = stringResource(R.string.editor_flag_catalog),
                    iconPlaceholder = false,
                    onClick = { showFlags = !showFlags },
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation.value },
                    )
                }
            }
            // Keep mounted while collapsed so an unfinished filter survives toggling.
            item(animatedVisibility = showFlags) {
                TextFieldWidget(
                    value = query,
                    onValueChange = { query = it },
                    title = stringResource(R.string.editor_filter_flags),
                    modifier = Modifier
                        .fillMaxWidth(),
                    textFieldModifier = Modifier
                        .clearFocusOnImeDismiss()
                        .fillMaxWidth(),
                )
            }
            availableFlags.filter { it.name.contains(query.trim(), ignoreCase = true) }.forEach { flag ->
                item(key = flag.name, animatedVisibility = showFlags) {
                    val requiredByNormalLaunch = !isBroadcast && EditorIntentMapper.authorizer(state).requiresNewTask &&
                        flag.mask == IntentFlagCatalog.defaultActivityFlags
                    SwitchWidget(
                        title = flag.name,
                        description = if (requiredByNormalLaunch) {
                            stringResource(
                                if (EditorIntentMapper.authorizer(state) == Authorizer.Auto) {
                                    R.string.editor_new_task_required_auto
                                } else {
                                    R.string.editor_new_task_required_normal
                                },
                            )
                        } else {
                            "0x" + flag.mask.toUInt().toString(16).padStart(8, '0')
                        },
                        iconPlaceholder = false,
                        enabled = mask != null && !requiredByNormalLaunch,
                        checked = mask != null && (mask and flag.mask.toUInt()) != 0u,
                        onCheckedChange = { checked ->
                            onAction(EditorViewAction.ToggleFlag(flag.mask, checked))
                        },
                    )
                }
            }
            if (showFlags && availableFlags.none { it.name.contains(query.trim(), ignoreCase = true) }) {
                item { SectionNote(R.string.editor_no_matches) }
            }
            item { SectionNote(if (isBroadcast) R.string.editor_broadcast_flag_note else R.string.editor_flag_note) }
        }
        SegmentedColumn(title = stringResource(R.string.launch_mode)) {
            item {
                DraftChoice(
                    R.string.launch_mode,
                    listOf(
                        stringResource(R.string.normal),
                        stringResource(R.string.auth_mode_root),
                        stringResource(R.string.auth_mode_shizuku),
                        stringResource(R.string.auth_mode_auto),
                    ),
                    state.choices[EditorChoice.LaunchMode] ?: 0,
                    iconPlaceholder = false,
                ) { onAction(EditorViewAction.SetChoice(EditorChoice.LaunchMode, it)) }
            }
            item { SectionNote(capabilityNote) }
        }
        SegmentedColumn {
            item {
                val rotation = animateFloatAsState(if (showAdvanced) 180f else 0f, label = "advancedArrow")
                val expansionState =
                    stringResource(if (showAdvanced) R.string.editor_flags_expanded else R.string.editor_flags_collapsed)
                BaseWidget(
                    modifier = Modifier.semantics { stateDescription = expansionState },
                    title = stringResource(R.string.editor_more_options),
                    iconPlaceholder = false,
                    onClick = { showAdvanced = !showAdvanced },
                ) {
                    Icon(AppIcons.ArrowDropDown, null, Modifier.graphicsLayer { rotationZ = rotation.value })
                }
            }
        }
        if (showAdvanced) {
            Text(
                stringResource(R.string.editor_ui_only),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedColumn(title = "ClipData") {
                item {
                    DraftField(
                        R.string.editor_clip_label,
                        state.fields[EditorField.ClipLabel].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.ClipLabel, it)) },
                    )
                }
                item {
                    DraftField(
                        R.string.editor_clip_mimes,
                        state.fields[EditorField.ClipMimes].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.ClipMimes, it)) },
                        multiline = true,
                    )
                }
                item { SectionNote(R.string.editor_clip_note) }
            }
            DraftRows(
                R.string.editor_clip_items,
                R.string.editor_add_clip,
                state.clips,
                { onAction(EditorViewAction.AddClip) },
                { onAction(EditorViewAction.RemoveClip(it)) },
            ) { row, remove ->
                ClipDraft(row, { onAction(EditorViewAction.UpdateClip(it)) }, remove)
            }
            SegmentedColumn(title = stringResource(R.string.editor_advanced)) {
                item {
                    DraftField(
                        R.string.editor_identifier,
                        state.fields[EditorField.Identifier].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Identifier, it)) },
                    )
                }
                item {
                    DraftField(
                        R.string.editor_bounds,
                        state.fields[EditorField.Bounds].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Bounds, it)) },
                        hint = R.string.editor_bounds_hint,
                    )
                }
                item { SectionNote(R.string.editor_selector_note) }
                item {
                    DraftField(
                        R.string.editor_selector,
                        state.fields[EditorField.Selector].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.Selector, it)) },
                        multiline = true,
                    )
                }
            }
            if (!isBroadcast) {
                SegmentedColumn(title = stringResource(R.string.editor_launch_options)) {
                    item {
                        DraftField(
                            R.string.editor_user,
                            state.fields[EditorField.User].orEmpty(),
                            { onAction(EditorViewAction.SetField(EditorField.User, it)) },
                            hint = R.string.editor_current_default,
                        )
                    }
                    item {
                        DraftField(
                            R.string.editor_display,
                            state.fields[EditorField.Display].orEmpty(),
                            { onAction(EditorViewAction.SetField(EditorField.Display, it)) },
                            hint = R.string.editor_current_default,
                        )
                    }
                    item {
                        DraftChoice(
                            R.string.editor_window,
                            listOf(stringResource(R.string.editor_system_default), "Fullscreen", "Freeform", "Split-screen"),
                            state.choices[EditorChoice.WindowMode] ?: 0,
                        ) { onAction(EditorViewAction.SetChoice(EditorChoice.WindowMode, it)) }
                    }
                    item {
                        DraftField(
                            R.string.editor_launch_bounds,
                            state.fields[EditorField.LaunchBounds].orEmpty(),
                            { onAction(EditorViewAction.SetField(EditorField.LaunchBounds, it)) },
                            hint = R.string.editor_bounds_hint,
                        )
                    }
                    item { SectionNote(R.string.editor_options_note) }
                }
            }
            SegmentedColumn(title = stringResource(R.string.editor_uri_tools)) {
                item {
                    DraftField(
                        R.string.editor_intent_uri,
                        state.fields[EditorField.IntentUri].orEmpty(),
                        { onAction(EditorViewAction.SetField(EditorField.IntentUri, it)) },
                        multiline = true,
                    )
                }
                item {
                    DraftChoice(
                        R.string.editor_uri_format,
                        listOf("URI_INTENT_SCHEME", "URI_ANDROID_APP_SCHEME", stringResource(R.string.editor_uri_plain)),
                        state.choices[EditorChoice.UriFormat] ?: 0,
                    ) { onAction(EditorViewAction.SetChoice(EditorChoice.UriFormat, it)) }
                }
                item {
                    BaseItemContainer {
                        OutlinedButton(
                            onClick = { onAction(EditorViewAction.ImportUri) },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(stringResource(R.string.editor_import))
                        }
                        OutlinedButton(
                            onClick = { onAction(EditorViewAction.ExportUri) },
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(stringResource(R.string.editor_export))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
internal fun ShortcutSettingsSheet(state: EditorViewState, onAction: (EditorViewAction) -> Unit) {
    val context = LocalContext.current
    val iconUri = state.fields[EditorField.ShortcutIcon].orEmpty()
    val preview by produceState<ShortcutIconPreview?>(null, context, iconUri) {
        if (iconUri.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                ShortcutIconPreview(iconUri, ShortcutIconLoader(context).loadBitmap(iconUri.toUri()).asImageBitmap())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Unable to load shortcut image preview")
                ShortcutIconPreview(iconUri, null)
            }
        }
    }
    val currentPreview = preview?.takeIf { it.uri == iconUri }
    val iconDescription = when {
        iconUri.isBlank() -> R.string.editor_icon_hint
        currentPreview == null -> R.string.shortcut_icon_loading
        currentPreview.bitmap == null -> R.string.shortcut_icon_failed
        else -> R.string.shortcut_icon_selected
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (error: SecurityException) {
                Timber.w(error, "Unable to retain shortcut image permission; using temporary grant")
                // The temporary grant still allows using this image for the current shortcut.
            }
            onAction(EditorViewAction.SetField(EditorField.ShortcutIcon, uri.toString()))
            Timber.d("Custom shortcut image selected: scheme=%s, provider=%s", uri.scheme, uri.authority)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.pin),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        SegmentedColumn {
            item {
                DraftField(
                    R.string.editor_shortcut_name,
                    state.fields[EditorField.ShortcutName].orEmpty(),
                    { onAction(EditorViewAction.SetField(EditorField.ShortcutName, it)) },
                )
            }
            item {
                BaseWidget(
                    title = stringResource(R.string.shortcut_choose_icon),
                    description = stringResource(iconDescription),
                    isError = iconUri.isNotBlank() && currentPreview != null && currentPreview.bitmap == null,
                    iconPlaceholder = false,
                    onClick = { iconPicker.launch(arrayOf("image/*")) },
                    enabled = !state.creatingShortcut,
                    trailingContent = {
                        currentPreview?.bitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = stringResource(R.string.shortcut_icon_preview),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            )
                        }
                    },
                )
            }
        }
        Text(
            stringResource(R.string.editor_shortcut_snapshot_hint),
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onAction(EditorViewAction.PinShortcut) },
            enabled = !state.creatingShortcut,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.pin))
        }
    }
}

private data class ShortcutIconPreview(val uri: String, val bitmap: ImageBitmap?)

@Composable
private fun DraftRows(
    title: Int,
    addLabel: Int,
    rows: List<DraftRowState>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    content: @Composable (DraftRowState, () -> Unit) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val currentContent by rememberUpdatedState(content)
    val rowContents = rows.associate { row ->
        row.id to key(row.id) {
            remember {
                movableContentOf<DraftRowState, () -> Unit> { currentRow, remove ->
                    currentContent(currentRow, remove)
                }
            }
        }
    }
    SegmentedColumn(modifier = modifier, title = if (collapsible) "" else stringResource(title)) {
        if (collapsible) {
            expandableItem(
                expanded = expanded,
                topContent = {
                    BaseWidget(
                        title = stringResource(title),
                        iconPlaceholder = false,
                        onClick = { expanded = !expanded },
                    ) {
                        Icon(
                            AppIcons.ArrowDropDown,
                            null,
                            Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                        )
                    }
                },
                bottomContent = {
                    Column {
                        rows.forEach { row ->
                            rowContents.getValue(row.id)(row) { onRemove(row.id) }
                        }
                        BaseItemContainer {
                            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(addLabel))
                            }
                        }
                    }
                },
            )
        } else {
            rows.forEach { row ->
                item(key = row.id) { rowContents.getValue(row.id)(row) { onRemove(row.id) } }
            }
            item(key = "add") {
                BaseItemContainer {
                    TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(addLabel))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDraft(
    row: DraftRowState,
    number: Int,
    onChange: (DraftRowState) -> Unit,
    remove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val value = row.fields[EditorField.Categories].orEmpty()
    BaseItemContainer(modifier) {
        BaseWidget(
            title = value.ifBlank { stringResource(R.string.editor_category_number, number) },
            iconPlaceholder = false,
            onClick = { expanded = !expanded },
        ) {
            Icon(
                AppIcons.ArrowDropDown,
                null,
                Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        if (expanded) {
            DraftField(
                R.string.category,
                value,
                { onChange(row.copy(fields = row.fields + (EditorField.Categories to it))) },
            )
            TextButton(onClick = remove, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.editor_remove_category))
            }
        }
    }
}

@Composable
private fun ExtraDraft(
    row: DraftRowState,
    number: Int,
    onChange: (DraftRowState) -> Unit,
    remove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    var showTypeDialog by rememberSaveable(row.id) { mutableStateOf(false) }
    val type = ExtraType.entries.getOrNull(row.type)
    val typeDescription = if (type == ExtraType.Auto) {
        stringResource(R.string.editor_auto_type_result, ExtraType.infer(row.fields[EditorField.ExtraValue].orEmpty()).notation)
    } else {
        type?.notation
    }
    if (showTypeDialog) {
        ExtraTypeDialog(
            currentType = type,
            onDismiss = { showTypeDialog = false },
            onSelect = {
                onChange(row.copy(type = it.ordinal))
                showTypeDialog = false
            },
        )
    }
    BaseItemContainer(modifier) {
        BaseWidget(
            title = row.fields[EditorField.ExtraKey].orEmpty().ifBlank { stringResource(R.string.editor_extra_number, number) },
            description = typeDescription,
            iconPlaceholder = false,
            onClick = { expanded = !expanded },
        ) {
            Icon(
                AppIcons.ArrowDropDown,
                null,
                Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        if (expanded) {
            DraftField(
                R.string.editor_key,
                row.fields[EditorField.ExtraKey].orEmpty(),
                { onChange(row.copy(fields = row.fields + (EditorField.ExtraKey to it))) },
            )
            BaseWidget(
                title = stringResource(R.string.editor_type),
                description = typeDescription,
                iconPlaceholder = false,
                onClick = { showTypeDialog = true },
            )
            DraftField(
                R.string.editor_value,
                row.fields[EditorField.ExtraValue].orEmpty(),
                { onChange(row.copy(fields = row.fields + (EditorField.ExtraValue to it))) },
                multiline = true,
            )
            Text(
                stringResource(
                    when {
                        type == ExtraType.Boolean -> R.string.editor_boolean_hint
                        type == ExtraType.Bundle -> R.string.editor_bundle_hint
                        type?.isCollection == true -> R.string.editor_array_hint
                        else -> R.string.editor_value_hint
                    },
                ),
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = remove, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.editor_remove_extra))
            }
        }
    }
}

@Composable
private fun ExtraTypeDialog(currentType: ExtraType?, onDismiss: () -> Unit, onSelect: (ExtraType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_type)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                (listOf(ExtraType.Auto) + ExtraType.entries.filter { it != ExtraType.Auto }).forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == currentType, onClick = { onSelect(type) })
                        Spacer(Modifier.width(8.dp))
                        Text(if (type == ExtraType.Auto) stringResource(R.string.editor_auto_type) else type.notation)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun ClipDraft(row: DraftRowState, onChange: (DraftRowState) -> Unit, remove: () -> Unit, modifier: Modifier = Modifier) {
    BaseItemContainer(modifier) {
        DraftField(
            R.string.editor_clip_text,
            row.fields[EditorField.ClipText].orEmpty(),
            { onChange(row.copy(fields = row.fields + (EditorField.ClipText to it))) },
            multiline = true,
        )
        DraftField(
            R.string.editor_clip_html,
            row.fields[EditorField.ClipHtml].orEmpty(),
            { onChange(row.copy(fields = row.fields + (EditorField.ClipHtml to it))) },
            multiline = true,
        )
        DraftField(
            R.string.data_uri,
            row.fields[EditorField.DataUri].orEmpty(),
            { onChange(row.copy(fields = row.fields + (EditorField.DataUri to it))) },
        )
        DraftField(
            R.string.editor_clip_intent,
            row.fields[EditorField.ClipIntent].orEmpty(),
            { onChange(row.copy(fields = row.fields + (EditorField.ClipIntent to it))) },
            multiline = true,
        )
        TextButton(onClick = remove, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.editor_remove_clip))
        }
    }
}

@Composable
private fun DraftChoice(
    title: Int,
    options: List<String>,
    selection: Int,
    iconPlaceholder: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    DropDownMenuWidget(
        title = stringResource(title),
        description = options[selection],
        iconPlaceholder = iconPlaceholder,
        choice = selection,
        data = options,
        onChoiceChange = onSelect,
    )
}

private data class DraftSuggestion(
    val value: String,
    val title: String = value,
    val secondaryText: String? = null,
    val onSelect: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DraftSuggestionField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<DraftSuggestion>,
    emptyMessage: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        val query = value.trim()
        suggestions
            .filter { suggestion ->
                query.isEmpty() || listOf(
                    suggestion.value,
                    suggestion.title,
                    suggestion.secondaryText,
                ).any { it?.contains(query, ignoreCase = true) == true }
            }
            .sortedWith(
                compareByDescending<DraftSuggestion> { suggestion ->
                    val queryMatches = query.isNotEmpty()
                    queryMatches && listOf(
                        suggestion.value,
                        suggestion.title,
                        suggestion.secondaryText,
                    ).any { it?.startsWith(query, ignoreCase = true) == true }
                }.thenBy { it.value.length },
            )
            .take(3)
    }
    val menuExpanded = expanded && (matches.isNotEmpty() || emptyMessage != null)
    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextFieldWidget(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            title = stringResource(label),
            lineLimits = TextFieldLineLimits.SingleLine,
            trailingContent = {
                Box(
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                ) {
                    ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded)
                }
            },
            clickableInWidget = false,
            modifier = Modifier.fillMaxWidth(),
            textFieldModifier = Modifier
                .clearFocusOnImeDismiss { expanded = false }
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                if (matches.isEmpty()) {
                    emptyMessage?.let { message ->
                        DropdownMenuItem(
                            text = { Text(message) },
                            onClick = {},
                            enabled = false,
                            shape = MenuDefaults.standaloneItemShape,
                        )
                    }
                } else {
                    matches.forEachIndexed { index, suggestion ->
                        if (index > 0) Spacer(modifier = Modifier.height(2.dp))
                        val selected = value == suggestion.value
                        SelectableDropdownMenuItem(
                            selected = selected,
                            onClick = {
                                suggestion.onSelect?.invoke() ?: onValueChange(suggestion.value)
                                expanded = false
                            },
                            text = {
                                if (suggestion.secondaryText == null) {
                                    if (selected) {
                                        Text(suggestion.title)
                                    } else {
                                        HighlightedActionText(suggestion.title, value)
                                    }
                                } else {
                                    val secondaryColor = LocalContentColor.current.copy(alpha = 0.7f)
                                    Column {
                                        Text(suggestion.title)
                                        Text(
                                            suggestion.secondaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryColor,
                                        )
                                    }
                                }
                            },
                            shapes = MenuDefaults.itemShape(0, 1),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun HighlightedActionText(action: String, query: String) {
    val term = query.trim()
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        background = MaterialTheme.colorScheme.primaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        buildAnnotatedString {
            append(action)
            if (term.isNotEmpty()) {
                var start = action.indexOf(term, ignoreCase = true)
                while (start >= 0) {
                    addStyle(highlight, start, start + term.length)
                    start = action.indexOf(term, start + term.length, ignoreCase = true)
                }
            }
        },
    )
}

@Composable
private fun ReadOnlyComponentField(label: Int, value: String) {
    BaseWidget(
        title = stringResource(label),
        description = value.ifBlank { null },
        iconPlaceholder = false,
    )
}

@Composable
private fun DraftField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    hint: Int? = null,
    remove: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        TextFieldWidget(
            value = value,
            onValueChange = onValueChange,
            title = stringResource(label),
            supportingText = hint?.let { stringResource(it) }.orEmpty(),
            lineLimits = if (multiline) {
                TextFieldLineLimits.MultiLine()
            } else {
                TextFieldLineLimits.SingleLine
            },
            modifier = Modifier
                .fillMaxWidth(),
            textFieldModifier = Modifier
                .clearFocusOnImeDismiss()
                .fillMaxWidth(),
        )
        remove?.let {
            TextButton(onClick = it, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.editor_remove_category))
            }
        }
    }
}

@Composable
private fun SectionNote(text: Int, modifier: Modifier = Modifier) = SectionNote(stringResource(text), modifier)

@Composable
private fun SectionNote(text: String, modifier: Modifier = Modifier) {
    BaseItemContainer(modifier) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}
