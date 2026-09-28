// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.creation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.domain.intent.IntentFlagCatalog
import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import io.github.wxxsfxyzm.intentx.domain.intent.IntentTemplate
import io.github.wxxsfxyzm.intentx.domain.intent.SavedIntentProfile
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.BaseWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn
import org.koin.androidx.compose.koinViewModel

@Composable
fun TemplatesPage(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(), viewModel: TemplatesViewModel = koinViewModel()) {
    val available by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }
    val navigator = LocalNavigator.current
    TemplatesContent(available, onSelect = { template, name ->
        val now = System.currentTimeMillis()
        val draft = SavedIntentProfile("", name, "", IntentSpec(null, null, template.action, null, null, emptyList(), IntentFlagCatalog.defaultActivityFlags, emptyList(), null, matchActivity = true), "None", now, now)
        navigator.replace(Route.Editor(draft = draft, returnToSaved = true))
    }, modifier, contentPadding)
}

@Composable
private fun TemplatesContent(available: Set<IntentTemplate>, onSelect: (IntentTemplate, String) -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item(key = "templates") {
            SegmentedColumn(title = stringResource(R.string.system_templates)) {
                IntentTemplate.entries.forEach { template ->
                    item(key = template.name) {
                        val title = stringResource(
                            when (template) {
                                IntentTemplate.Wifi -> R.string.template_wifi
                                IntentTemplate.Bluetooth -> R.string.template_bluetooth
                                IntentTemplate.Applications -> R.string.template_applications
                                IntentTemplate.DateTime -> R.string.template_date_time
                            },
                        )
                        BaseWidget(
                            title = title,
                            description = if (template in available) template.action else stringResource(R.string.template_unavailable),
                            iconPlaceholder = false,
                            enabled = template in available,
                            onClick = { onSelect(template, title) },
                        )
                    }
                }
            }
        }
    }
}
