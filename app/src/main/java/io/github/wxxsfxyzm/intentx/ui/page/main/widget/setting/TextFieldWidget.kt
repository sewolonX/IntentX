// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2023-2026 iamr0s, IntentX contributors
package io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.wxxsfxyzm.intentx.R

@Composable
fun TextFieldWidget(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    title: String = "",
    error: String = "",
    isError: Boolean = error.isNotBlank(),
    supportingText: String = "",
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    clickableInWidget: Boolean = true,
) {
    val state = rememberTextFieldState(value)
    val initialized = remember { mutableStateOf(false) }
    val lastExternalValue = remember { mutableStateOf(value) }
    val lastNotifiedText = remember { mutableStateOf(value) }
    val text = state.text.toString()

    SideEffect {
        if (!initialized.value || value != lastExternalValue.value) {
            initialized.value = true
            lastExternalValue.value = value
            lastNotifiedText.value = value
            if (text != value) {
                state.edit {
                    replace(0, length, value)
                }
            }
        } else if (text != value && text != lastNotifiedText.value) {
            lastNotifiedText.value = text
            onValueChange(text)
        }
    }

    TextFieldWidget(
        modifier = modifier,
        textFieldModifier = textFieldModifier,
        state = state,
        title = title,
        error = error,
        isError = isError,
        supportingText = supportingText,
        labelColor = labelColor,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
        lineLimits = lineLimits,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        clickableInWidget = clickableInWidget,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextFieldWidget(
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    state: TextFieldState,
    onClick: (() -> Unit)? = null,
    title: String = "",
    error: String = "",
    isError: Boolean = error.isNotBlank(),
    supportingText: String = "",
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
    ),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    outputTransformation: OutputTransformation? = null,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    scrollState: ScrollState = rememberScrollState(),
    clickableInWidget: Boolean = true,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isClickableMode = onClick != null

    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    fun onClickInternal() {
        if (onClick != null) {
            onClick()
            return
        }

        if (!readOnly && enabled) {
            focusRequester.requestFocus()
        }
    }

    BaseWidget(
        modifier = modifier,
        title = if (useLabelAsPlaceholder) null else title,
        icon = null,
        iconPlaceholder = false,
        leadingContent = leadingContent,
        onClick = if (isClickableMode) {
            { onClickInternal() }
        } else {
            null
        },
        onTrailingClick = if (clickableInWidget && trailingContent != null && !isClickableMode) {
            { onClickInternal() }
        } else null,
        descriptionColumnContent = {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(textFieldModifier)
                    .focusRequester(focusRequester)
                    .focusProperties { canFocus = !isClickableMode },
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = if (isError) SolidColor(MaterialTheme.colorScheme.error) else cursorBrush,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits,
                onTextLayout = currentOnTextLayout,
                interactionSource = interactionSource,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                scrollState = scrollState,
                decorator = { innerTextField ->
                    Column {
                        Box(
                            modifier = if (isClickableMode) {
                                Modifier.clickable {
                                    onClickInternal()
                                }
                            } else {
                                Modifier
                            }
                        ) {
                            if (state.text.isEmpty()) {
                                Text(
                                    text = if (useLabelAsPlaceholder) title else stringResource(R.string.click_to_input),
                                    style = textStyle,
                                    color = labelColor.copy(alpha = 0.6f),
                                )
                            }

                            if (error.isNotBlank() && !focused && state.text.isBlank()) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            innerTextField()
                        }

                        AnimatedVisibility(
                            visible = focused,
                            enter = expandHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Start // Unroll downwards like a blind
                            ) + expandVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Top // Unroll downwards like a blind
                            ),
                            exit = shrinkHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Start // Roll up upwards
                            ) + shrinkVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Top // Unroll downwards like a blind
                            )
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            HorizontalDivider(
                                thickness = 2.dp,
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        if (supportingText.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(top = 2.dp),
                                text = supportingText,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = error.isNotBlank() && (focused || state.text.isNotBlank()),
                enter = expandHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Start // Unroll downwards like a blind
                ) + expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top // Unroll downwards like a blind
                ),
                exit = shrinkHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Start // Roll up upwards
                ) + shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top // Unroll downwards like a blind
                )
            ) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = if (trailingContent != null) {
            {
                trailingContent()
            }
        } else null
    )
}
