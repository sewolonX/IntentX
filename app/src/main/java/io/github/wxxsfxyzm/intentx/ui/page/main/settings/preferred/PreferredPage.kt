// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors
package io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wxxsfxyzm.intentx.BuildConfig
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.ui.LocalAuthorizationUi
import io.github.wxxsfxyzm.intentx.ui.icons.AppIcons
import io.github.wxxsfxyzm.intentx.ui.navigation.LocalNavigator
import io.github.wxxsfxyzm.intentx.ui.navigation.Route
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationContent
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewAction
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.authorization.AuthorizationViewState
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.NavigationItemWidget
import io.github.wxxsfxyzm.intentx.ui.page.main.widget.setting.SegmentedColumn

@Composable
fun PreferredPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val authorization = LocalAuthorizationUi.current
    val navigator = LocalNavigator.current
    PreferredContent(
        authorizationState = authorization.state,
        onAuthorizationAction = authorization.dispatch,
        onOpenTheme = { navigator.push(Route.Theme) },
        onOpenAbout = { navigator.push(Route.About) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun PreferredContent(
    authorizationState: AuthorizationViewState,
    onAuthorizationAction: (AuthorizationViewAction) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item(key = "authorization") {
            AuthorizationContent(authorizationState, onAuthorizationAction)
        }
        item(key = "appearance") {
            SegmentedColumn(title = stringResource(R.string.appearance)) {
                item {
                    NavigationItemWidget(
                        icon = AppIcons.Palette,
                        title = stringResource(R.string.theme_settings),
                        description = stringResource(R.string.theme_settings_desc),
                        onClick = { onOpenTheme() },
                    )
                }
            }
        }
        item(key = "about") {
            SegmentedColumn(title = stringResource(R.string.other)) {
                item {
                    NavigationItemWidget(
                        icon = AppIcons.Info,
                        title = stringResource(R.string.about_detail),
                        description = BuildConfig.VERSION_NAME,
                        onClick = { onOpenAbout() },
                    )
                }
            }
        }
    }
}
