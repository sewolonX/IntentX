// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors
package io.github.wxxsfxyzm.intentx.ui.page.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.intent.ProfileKind
import io.github.wxxsfxyzm.intentx.ui.ShortcutSettingsSheet
import io.github.wxxsfxyzm.intentx.ui.icons.AppIcons
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.ActivityActionMenu
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.TextFieldWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogActionMenu
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogScreen
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.CatalogSearchField
import io.github.wxxsfxyzm.intentx.ui.page.main.catalog.ComponentTabs
import io.github.wxxsfxyzm.intentx.ui.page.main.creation.ImportPage
import io.github.wxxsfxyzm.intentx.ui.page.main.creation.TemplatesPage
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorPage
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.editor.EditorViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.IntentCreationContent
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.SavedIntentExportAction
import io.github.wxxsfxyzm.intentx.ui.page.main.saved.SavedIntentScreen
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.PreferredPage
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.ExpressiveBackButton
import io.github.wxxsfxyzm.intentx.ui.theme.getMaterial3AppBarColor
import io.github.wxxsfxyzm.intentx.ui.theme.installerMaterial3BlurEffect
import io.github.wxxsfxyzm.intentx.ui.theme.rememberMaterial3BlurBackdrop
import io.github.wxxsfxyzm.intentx.ui.util.ImeDismissalFocusScope
import io.github.wxxsfxyzm.intentx.ui.util.clearFocusOnImeDismiss
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.nav.gesture.WindowNavigationEventBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentXMainTab(page: Int, title: String, useBlur: Boolean, outerPadding: PaddingValues, active: Boolean) {
    val navigator = LocalNavigator.current
    var showCreationSheet by rememberSaveable { mutableStateOf(false) }
    IntentXPageScaffold(
        title = title,
        useBlur = useBlur,
        outerPadding = outerPadding,
        topBarContent = if (page == 0) ({ CatalogSearchField(active = active) }) else null,
        onCreate = when (page) {
            0 -> ({ navigator.push(Route.Editor()) })
            1 -> ({ showCreationSheet = true })
            else -> null
        },
        createContentDescription = if (page == 1) R.string.add_quick_action else R.string.new_intent,
        actions = when (page) {
            0 -> ({ CatalogActionMenu() })
            1 -> ({ SavedIntentExportAction() })
            else -> null
        },
    ) { modifier, contentPadding, _ ->
        when (page) {
            0 -> CatalogScreen(modifier = modifier, contentPadding = contentPadding, showSearch = false)
            1 -> SavedIntentScreen(modifier = modifier, contentPadding = contentPadding)
            2 -> PreferredPage(modifier = modifier, contentPadding = contentPadding)
        }
    }
    if (showCreationSheet && active) {
        ModalBottomSheet(
            onDismissRequest = { showCreationSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            WindowNavigationEventBridge()
            IntentCreationContent(
                onCreateIntent = {
                    showCreationSheet = false
                    navigator.push(Route.Editor(returnToSaved = true))
                },
                onCreateLink = {
                    showCreationSheet = false
                    navigator.push(Route.Editor(kind = ProfileKind.Link, returnToSaved = true))
                },
                onCreateFile = {
                    showCreationSheet = false
                    navigator.push(Route.Editor(kind = ProfileKind.File, returnToSaved = true))
                },
                onCreateTemplate = {
                    showCreationSheet = false
                    navigator.push(Route.Templates)
                },
                onImport = {
                    showCreationSheet = false
                    navigator.push(Route.Import())
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentXDestination(route: Route, useBlur: Boolean) {
    val navigator = LocalNavigator.current
    val editorViewModel = if (route is Route.Editor) koinViewModel<EditorViewModel>() else null
    var showShortcutSheet by rememberSaveable(route) { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable(route) { mutableStateOf(false) }
    val editorActions: (@Composable () -> Unit)? = editorViewModel?.let { model ->
        {
            val state by model.uiState.collectAsStateWithLifecycle()
            IconButton(onClick = { showShortcutSheet = true }) {
                Icon(AppIcons.AddToHomeScreen, stringResource(R.string.pin))
            }
            IconButton(
                enabled = ((route as? Route.Editor)?.profileId == null || state.profileId != null) &&
                    (state.profileId == null || state.profileName.isNotBlank()),
                onClick = {
                    if (state.profileId == null) {
                        showSaveDialog = true
                    } else {
                        model.dispatch(EditorViewAction.Save(state.profileName, state.profileDescription))
                    }
                },
            ) {
                Icon(AppIcons.Save, stringResource(R.string.save))
            }
        }
    }
    val editorFab: (@Composable () -> Unit)? = editorViewModel?.let { model ->
        {
            val state by model.uiState.collectAsStateWithLifecycle()
            val profileId = (route as? Route.Editor)?.profileId
            if (profileId == null || state.profileId == profileId) {
                ExtendedFloatingActionButton(
                    onClick = { model.dispatch(EditorViewAction.Launch) },
                    icon = { Icon(AppIcons.Launcher, null) },
                    text = {
                        Text(
                            stringResource(
                                if (state.kind != ProfileKind.CustomIntent) {
                                    R.string.quick_open
                                } else if (state.operation == IntentOperation.Activity) {
                                    R.string.launch
                                } else {
                                    R.string.send_broadcast
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
    val pageContent: @Composable (Modifier, PaddingValues) -> Unit = { modifier, contentPadding ->
        if (route is Route.Editor) {
            ImeDismissalFocusScope(enabled = navigator.current() == route && !showShortcutSheet && !showSaveDialog) {
                EditorPage(
                    modifier = modifier,
                    contentPadding = contentPadding,
                    route = route,
                    viewModel = checkNotNull(editorViewModel),
                )
            }
        } else if (route is Route.Activities) {
            CatalogScreen(modifier, route.packageName, contentPadding, showSearch = false)
        } else if (route is Route.Templates) {
            TemplatesPage(modifier, contentPadding)
        } else if (route is Route.Import) {
            ImeDismissalFocusScope(enabled = navigator.current() == route) {
                ImportPage(route, modifier, contentPadding)
            }
        }
    }
    if (route is Route.Editor) {
        EditorDestinationScaffold(
            title = route.activityLabel?.ifBlank { null } ?: stringResource(
                when (route.kind) {
                    ProfileKind.CustomIntent -> R.string.new_intent
                    ProfileKind.Link -> R.string.creation_open_link
                    ProfileKind.File -> R.string.creation_open_file
                },
            ),
            editorActions = editorActions,
            floatingActionButton = editorFab,
            useBlur = useBlur,
            content = pageContent,
        )
    } else if (route is Route.Import) {
        EditorDestinationScaffold(
            title = stringResource(R.string.creation_import_profile),
            editorActions = null,
            floatingActionButton = null,
            useBlur = useBlur,
            content = pageContent,
        )
    } else {
        IntentXPageScaffold(
            title = when (route) {
                is Route.Activities -> route.appLabel.ifBlank { route.packageName }
                is Route.Templates -> stringResource(R.string.creation_from_template)
                else -> stringResource(R.string.new_intent)
            },
            useBlur = useBlur,
            back = true,
            topBarContent = if (route is Route.Activities) {
                (
                    {
                        CatalogSearchField(route.packageName)
                        ComponentTabs(route.packageName)
                    }
                    )
            } else {
                null
            },
            actions = if (route is Route.Activities) ({ ActivityActionMenu(route.packageName) }) else null,
        ) { modifier, contentPadding, _ -> pageContent(modifier, contentPadding) }
    }
    if (showShortcutSheet && editorViewModel != null) {
        val state by editorViewModel.uiState.collectAsStateWithLifecycle()
        ModalBottomSheet(onDismissRequest = { showShortcutSheet = false }) {
            WindowNavigationEventBridge()
            ImeDismissalFocusScope(enabled = navigator.current() == route) {
                ShortcutSettingsSheet(state, editorViewModel::dispatch)
            }
        }
    }
    if (showSaveDialog && editorViewModel != null) {
        val suggestion = editorViewModel.suggestedProfileName()
        var name by rememberSaveable(route, suggestion) { mutableStateOf(suggestion) }
        var description by rememberSaveable(route) { mutableStateOf(editorViewModel.uiState.value.profileDescription) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.profile_save_title)) },
            text = {
                ImeDismissalFocusScope(enabled = navigator.current() == route) {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        TextFieldWidget(
                            value = name,
                            onValueChange = { name = it },
                            title = stringResource(R.string.profile_name),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            isError = name.isBlank(),
                            textFieldModifier = Modifier.clearFocusOnImeDismiss(),
                        )
                        TextFieldWidget(
                            value = description,
                            onValueChange = { description = it },
                            title = stringResource(R.string.profile_description_optional),
                            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4),
                            textFieldModifier = Modifier.clearFocusOnImeDismiss(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        editorViewModel.dispatch(EditorViewAction.Save(name, description))
                        showSaveDialog = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorDestinationScaffold(
    title: String,
    editorActions: (@Composable () -> Unit)?,
    floatingActionButton: (@Composable () -> Unit)?,
    useBlur: Boolean,
    content: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            Column(
                modifier = Modifier
                    .installerMaterial3BlurEffect(backdrop)
                    .background(backdrop.getMaterial3AppBarColor()),
            ) {
                LargeFlexibleTopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                    title = { Text(title) },
                    navigationIcon = {
                        Row {
                            ExpressiveBackButton { navigator.pop() }
                            Spacer(Modifier.size(16.dp))
                        }
                    },
                    actions = { editorActions?.invoke() },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = backdrop.getMaterial3AppBarColor(),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        scrolledContainerColor = backdrop.getMaterial3AppBarColor(),
                    ),
                )
            }
        },
        floatingActionButton = { floatingActionButton?.invoke() },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
            padding,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IntentXPageScaffold(
    modifier: Modifier = Modifier,
    title: String,
    useBlur: Boolean,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    back: Boolean = false,
    onCreate: (() -> Unit)? = null,
    createContentDescription: Int = R.string.new_intent,
    topBarContent: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier, PaddingValues, LayerBackdrop?) -> Unit,
) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val backdrop = rememberMaterial3BlurBackdrop(useBlur)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            Column(
                modifier = Modifier
                    .installerMaterial3BlurEffect(backdrop)
                    .background(backdrop.getMaterial3AppBarColor()),
            ) {
                LargeFlexibleTopAppBar(
                    windowInsets = if (back) {
                        TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp))
                    } else {
                        TopAppBarDefaults.windowInsets
                    },
                    title = {
                        Text(
                            text = title,
                            modifier = if (back) Modifier else Modifier.padding(start = 12.dp),
                        )
                    },
                    navigationIcon = {
                        if (back) {
                            Row {
                                ExpressiveBackButton { navigator.pop() }
                                Spacer(Modifier.size(16.dp))
                            }
                        }
                    },
                    actions = { actions?.invoke() },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = backdrop.getMaterial3AppBarColor(),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        scrolledContainerColor = backdrop.getMaterial3AppBarColor(),
                    ),
                )
                topBarContent?.invoke()
            }
        },
        floatingActionButton = {
            if (floatingActionButton != null) {
                floatingActionButton()
            } else if (onCreate != null) {
                FloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.padding(outerPadding),
                ) { Icon(Icons.Outlined.Add, stringResource(createContentDescription)) }
            }
        },
    ) { padding ->
        // Apply the pager inset while drawing the blur backdrop behind the app bar.
        content(
            Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .consumeWindowInsets(padding + outerPadding),
            padding + outerPadding,
            backdrop,
        )
    }
}
