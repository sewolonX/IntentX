// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors
// Copyright (C) 2023-2026 iamr0s
package io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.menu.GroupedDropdownMenuPopup

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DropDownMenuWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    choice: Int,
    data: List<String>,
    onChoiceChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var clickOffset by remember { mutableStateOf(Offset.Zero) }

    BaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        enabled = enabled,
        isError = isError,
        onClick = { offset ->
            clickOffset = offset
            expanded = !expanded
        },
        foreContent = {
            Box(
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                GroupedDropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    clickOffset = clickOffset,
                    groupSizes = listOf(data.size),
                    itemContent = { _, index, shape, dismissItem ->
                        val isSelected = index == choice
                        SelectableDropdownMenuItem(
                            selected = isSelected,
                            onClick = {
                                onChoiceChange(index)
                                dismissItem()
                            },
                            text = { Text(text = data[index]) },
                            shapes = shape,
                        )
                    },
                    dropdownMenuAnchorPosition = MenuAnchorPosition.End,
                )
            }
        },
    )
}
