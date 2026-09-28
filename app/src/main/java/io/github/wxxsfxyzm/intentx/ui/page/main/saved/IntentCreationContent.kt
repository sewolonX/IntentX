// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.ui.page.main.saved

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.ui.icons.AppIcons
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.NavigationItemWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn

@Composable
fun IntentCreationContent(
    onCreateIntent: () -> Unit,
    onUnavailableOption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.add_quick_action),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        item(key = "creation_options") {
            SegmentedColumn {
                item(key = "intent") {
                    NavigationItemWidget(
                        icon = AppIcons.Edit,
                        title = stringResource(R.string.creation_custom_intent),
                        description = stringResource(R.string.creation_custom_intent_desc),
                        onClick = onCreateIntent,
                    )
                }
                item(key = "link") {
                    NavigationItemWidget(
                        icon = AppIcons.OpenLink,
                        title = stringResource(R.string.creation_open_link),
                        description = stringResource(R.string.creation_open_link_desc),
                        onClick = onUnavailableOption,
                    )
                }
                item(key = "file") {
                    NavigationItemWidget(
                        icon = AppIcons.OpenFile,
                        title = stringResource(R.string.creation_open_file),
                        description = stringResource(R.string.creation_open_file_desc),
                        onClick = onUnavailableOption,
                    )
                }
                item(key = "template") {
                    NavigationItemWidget(
                        icon = AppIcons.Templates,
                        title = stringResource(R.string.creation_from_template),
                        description = stringResource(R.string.creation_from_template_desc),
                        onClick = onUnavailableOption,
                    )
                }
                item(key = "import") {
                    NavigationItemWidget(
                        icon = AppIcons.Import,
                        title = stringResource(R.string.creation_import_profile),
                        description = stringResource(R.string.creation_import_profile_desc),
                        onClick = onUnavailableOption,
                    )
                }
            }
        }
    }
}
