// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors
package io.github.wxxsfxyzm.intentx.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.wxxsfxyzm.intentx.domain.settings.model.preferences.PredictiveBackAnimation
import io.github.wxxsfxyzm.intentx.domain.settings.model.preferences.ThemeState
import io.github.wxxsfxyzm.intentx.ui.animation.predictiveback.installerNavTransition
import io.github.wxxsfxyzm.intentx.ui.page.main.IntentXDestination
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.SettingsSharedViewModel
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.about.AboutPage
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.about.OpenSourceLicensePage
import io.github.wxxsfxyzm.intentx.ui.page.main.settings.preferred.theme.ThemeSettingsPage
import io.github.wxxsfxyzm.intentx.ui.util.rememberDeviceCornerRadius
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

@Composable
fun InstallerNavContainer(uiState: ThemeState) {
    val sharedViewModel: SettingsSharedViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity,
    )

    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember(backStack) { Navigator(backStack) }
    val onBack = remember(navigator) { { navigator.pop() } }
    val useBlur = uiState.useBlur

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        val navCornerRadius = rememberDeviceCornerRadius(defaultRadius = 0.dp)
        val roundAllCorners = uiState.predictiveBackAnimation == PredictiveBackAnimation.AOSP ||
            uiState.predictiveBackAnimation == PredictiveBackAnimation.Scale ||
            uiState.predictiveBackAnimation == PredictiveBackAnimation.Classic
        val backdropColor = MaterialTheme.colorScheme.surfaceContainer
        val effects = remember(navCornerRadius, roundAllCorners, backdropColor) {
            NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = if (roundAllCorners && navCornerRadius <= 0.dp) 32.dp else navCornerRadius,
                cornerClipMode = if (roundAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
                dimAmount = 0.5f,
                backdropColor = backdropColor,
                blockInputDuringTransition = false,
            )
        }
        val transition = remember(uiState.predictiveBackAnimation, uiState.predictiveBackExitDirection) {
            installerNavTransition(
                animation = uiState.predictiveBackAnimation,
                exitDirection = uiState.predictiveBackExitDirection,
            )
        }
        val swipeBackDirection = when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
        }
        val interceptPredictiveBack =
            uiState.predictiveBackAnimation == PredictiveBackAnimation.None && backStack.size > 1

        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            transition = transition,
            effects = effects,
        ) {
            entry<Route.Main> {
                InstallerNavEntry(interceptPredictiveBack, onBack) {
                    Material3MainPageWrapper(uiState, sharedViewModel)
                }
            }
            entry<Route.Editor>(swipeDismiss = NavSwipeDirection.None) { route ->
                InstallerNavEntry(interceptPredictiveBack, onBack) {
                    IntentXDestination(route, useBlur)
                }
            }
            entry<Route.Activities>(swipeDismiss = swipeBackDirection) { route ->
                InstallerNavEntry(interceptPredictiveBack, onBack) {
                    IntentXDestination(route, useBlur)
                }
            }
            entry<Route.Templates>(swipeDismiss = swipeBackDirection) { route ->
                InstallerNavEntry(interceptPredictiveBack, onBack) { IntentXDestination(route, useBlur) }
            }
            entry<Route.Import>(swipeDismiss = NavSwipeDirection.None) { route ->
                InstallerNavEntry(interceptPredictiveBack, onBack) { IntentXDestination(route, useBlur) }
            }
            entry<Route.Theme>(swipeDismiss = swipeBackDirection) {
                InstallerNavEntry(interceptPredictiveBack, onBack) { ThemeSettingsPage() }
            }
            entry<Route.About>(swipeDismiss = swipeBackDirection) {
                InstallerNavEntry(interceptPredictiveBack, onBack) { AboutPage(useBlur) }
            }
            entry<Route.OpenSourceLicense>(swipeDismiss = swipeBackDirection) {
                InstallerNavEntry(interceptPredictiveBack, onBack) { OpenSourceLicensePage(useBlur) }
            }
        }
    }
}

@Composable
private fun InstallerNavEntry(interceptPredictiveBack: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    content()
}
