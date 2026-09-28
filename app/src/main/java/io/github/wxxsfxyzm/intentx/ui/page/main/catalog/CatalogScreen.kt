// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.catalog

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.data.catalog.AppIconLoader
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivitySortOrder
import io.github.wxxsfxyzm.intentx.domain.catalog.ActivityStatusFilter
import io.github.wxxsfxyzm.intentx.domain.catalog.CatalogSortOrder
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.menu.GroupedDropdownMenuPopup
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.TextFieldWidget
import io.github.wxxsfxyzm.intentx.ui.util.CollectUiEvents
import io.github.wxxsfxyzm.intentx.ui.util.ImeDismissalFocusScope
import io.github.wxxsfxyzm.intentx.ui.util.clearFocusOnImeDismiss
import kotlinx.coroutines.awaitCancellation
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun CatalogScreen(
    modifier: Modifier = Modifier,
    packageName: String? = null,
    contentPadding: PaddingValues = PaddingValues(),
    showSearch: Boolean = true,
) {
    val viewModel: CatalogViewModel = koinViewModel(key = packageName ?: "apps")
    val icons: AppIconLoader = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var permissionMissing by remember(context) { mutableStateOf(packageName == null && InstalledAppsPermission.isMissing(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Timber.d("Installed-apps permission result: granted=%s", granted)
        permissionMissing = !granted && InstalledAppsPermission.isMissing(context)
        if (granted) viewModel.dispatch(CatalogViewAction.Reload)
    }
    val requestPermission = {
        Timber.d("Requesting OEM installed-apps permission")
        permissionRequested = true
        permissionLauncher.launch(InstalledAppsPermission.NAME)
    }
    val configuration = LocalConfiguration.current
    LaunchedEffect(viewModel, owner, packageName, configuration.locales.toLanguageTags()) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (packageName == null) {
                permissionMissing = InstalledAppsPermission.isMissing(context)
                Timber.d("Installed-apps permission missing=%s", permissionMissing)
                if (permissionMissing && !permissionRequested) requestPermission()
            }
            viewModel.dispatch(CatalogViewAction.StartObserving(packageName))
            try {
                awaitCancellation()
            } finally {
                viewModel.dispatch(CatalogViewAction.StopObserving)
            }
        }
    }
    val navigator = LocalNavigator.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CollectUiEvents(viewModel.eventFlow) { event ->
        when (event) {
            is CatalogViewEvent.ShowMessage -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()

            is CatalogViewEvent.NavigateToActivities -> {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                navigator.push(Route.Activities(event.packageName, event.appLabel))
            }

            is CatalogViewEvent.NavigateToEditor -> {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                navigator.push(Route.Editor(event.packageName, event.className, event.activityLabel, operation = event.operation))
            }
        }
    }
    CatalogContent(
        state,
        viewModel::dispatch,
        modifier,
        contentPadding,
        requestInstalledAppsPermission = if (permissionMissing) requestPermission else null,
    ) { item ->
        key(item.packageName, item.lastUpdateTime, configuration.densityDpi, configuration.uiMode) {
            val bitmap by produceState<Bitmap?>(null, icons, item.packageName, item.lastUpdateTime) {
                value = icons.load(item.packageName, item.lastUpdateTime)
            }
            val icon = bitmap
            if (icon == null) {
                Box(Modifier.size(40.dp))
            } else {
                Image(icon.asImageBitmap(), null, Modifier.size(40.dp))
            }
        }
    }
}

@Composable
fun CatalogSearchField(packageName: String? = null, active: Boolean = true) {
    val viewModel: CatalogViewModel = koinViewModel(key = packageName ?: "apps")
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val route = LocalNavigator.current.current()
    val isCurrentPage = if (packageName == null) {
        route == Route.Main && active
    } else {
        route is Route.Activities && route.packageName == packageName
    }
    ImeDismissalFocusScope(enabled = isCurrentPage) {
        TextFieldWidget(
            value = state.query,
            onValueChange = { viewModel.dispatch(CatalogViewAction.SetQuery(it)) },
            title = stringResource(
                when {
                    packageName != null && state.componentTab == IntentOperation.Broadcast -> R.string.search_receivers
                    packageName != null -> R.string.search_activities
                    state.searchActivities -> R.string.search_apps_and_activities
                    else -> R.string.search_apps
                },
            ),
            leadingContent = { Icon(Icons.Outlined.Search, null) },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            textFieldModifier = Modifier.clearFocusOnImeDismiss(),
            useLabelAsPlaceholder = true,
        )
    }
}

@Composable
fun ComponentTabs(packageName: String) {
    val viewModel: CatalogViewModel = koinViewModel(key = packageName)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PrimaryTabRow(
        selectedTabIndex = if (state.componentTab == IntentOperation.Activity) 0 else 1,
        containerColor = Color.Transparent,
    ) {
        Tab(
            selected = state.componentTab == IntentOperation.Activity,
            onClick = { viewModel.dispatch(CatalogViewAction.SetComponentTab(IntentOperation.Activity)) },
            text = { Text(stringResource(R.string.operation_activity)) },
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Tab(
            selected = state.componentTab == IntentOperation.Broadcast,
            onClick = { viewModel.dispatch(CatalogViewAction.SetComponentTab(IntentOperation.Broadcast)) },
            text = { Text(stringResource(R.string.operation_broadcast)) },
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CatalogActionMenu() {
    val viewModel: CatalogViewModel = koinViewModel(key = "apps")
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.catalog_options))
        }
        GroupedDropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groupSizes = listOf(3, 4, 1),
            keepOpenOnItemClick = true,
            popupProperties = PopupProperties(focusable = false),
            itemContent = { group, index, shapes, _ ->
                val order = CatalogSortOrder.entries.getOrNull(index)
                val checked = when (group) {
                    0 -> {
                        state.sortOrder == order
                    }

                    1 -> {
                        when (index) {
                            0 -> state.reverseOrder
                            1 -> state.showSystem
                            2 -> !state.showPackageName
                            else -> state.hideOverlays
                        }
                    }

                    else -> {
                        state.searchActivities
                    }
                }
                val label = when (group) {
                    0 -> {
                        when (order) {
                            CatalogSortOrder.Label -> R.string.sort_by_label
                            CatalogSortOrder.PackageName -> R.string.sort_by_package_name
                            CatalogSortOrder.FirstInstallTime -> R.string.sort_by_install_time
                            null -> error("Invalid catalog sort option")
                        }
                    }

                    1 -> {
                        when (index) {
                            0 -> if (state.sortOrder == CatalogSortOrder.FirstInstallTime) {
                                R.string.sort_oldest_first
                            } else {
                                R.string.sort_z_to_a
                            }

                            1 -> R.string.system_apps

                            2 -> R.string.catalog_hide_package_name

                            else -> R.string.catalog_hide_overlays
                        }
                    }

                    else -> {
                        R.string.catalog_search_activities
                    }
                }
                CheckableDropdownMenuItem(
                    shapes = shapes,
                    checked = checked,
                    onCheckedChange = { enabled ->
                        when (group) {
                            0 -> {
                                viewModel.dispatch(CatalogViewAction.SetSortOrder(checkNotNull(order)))
                                expanded = false
                            }

                            1 -> {
                                when (index) {
                                    0 -> viewModel.dispatch(CatalogViewAction.SetReverseOrder(enabled))
                                    1 -> viewModel.dispatch(CatalogViewAction.SetShowSystem(enabled))
                                    2 -> viewModel.dispatch(CatalogViewAction.SetShowPackageName(!enabled))
                                    else -> viewModel.dispatch(CatalogViewAction.SetHideOverlays(enabled))
                                }
                            }

                            else -> {
                                viewModel.dispatch(CatalogViewAction.SetSearchActivities(enabled))
                            }
                        }
                    },
                    text = { Text(stringResource(label)) },
                    trailingContent = if (checked) ({ Icon(Icons.Filled.Check, null) }) else null,
                )
            },
        )
    }
}

@Composable
fun ActivityActionMenu(packageName: String) {
    val viewModel: CatalogViewModel = koinViewModel(key = packageName)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.catalog_options))
        }
        GroupedDropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groupSizes = listOf(2, 3, 3),
            keepOpenOnItemClick = true,
            popupProperties = PopupProperties(focusable = false),
            itemContent = { group, index, shapes, _ ->
                val statusFilter = ActivityStatusFilter.entries.getOrNull(index)
                val checked = when (group) {
                    0 -> state.activitySortOrder == ActivitySortOrder.entries[index]
                    1 -> state.activityExportedFilter == statusFilter
                    else -> state.activityEnabledFilter == statusFilter
                }
                val label = when (group) {
                    0 -> if (index == 0) R.string.activity_sort_by_label else R.string.activity_sort_by_name

                    1 -> when (index) {
                        0 -> R.string.activity_filter_all_exported
                        1 -> R.string.activity_filter_exported
                        else -> R.string.activity_filter_not_exported
                    }

                    else -> when (index) {
                        0 -> R.string.activity_filter_all_enabled
                        1 -> R.string.activity_filter_enabled
                        else -> R.string.activity_filter_disabled
                    }
                }
                CheckableDropdownMenuItem(
                    shapes = shapes,
                    checked = checked,
                    onCheckedChange = {
                        when (group) {
                            0 -> {
                                viewModel.dispatch(CatalogViewAction.SetActivitySortOrder(ActivitySortOrder.entries[index]))
                                expanded = false
                            }

                            1 -> viewModel.dispatch(CatalogViewAction.SetActivityExportedFilter(checkNotNull(statusFilter)))

                            else -> viewModel.dispatch(CatalogViewAction.SetActivityEnabledFilter(checkNotNull(statusFilter)))
                        }
                    },
                    text = { Text(stringResource(label)) },
                    trailingContent = if (checked) ({ Icon(Icons.Filled.Check, null) }) else null,
                )
            },
        )
    }
}
