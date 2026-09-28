// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

enum class IntentTemplate(val action: String) {
    Wifi("android.settings.WIFI_SETTINGS"),
    Bluetooth("android.settings.BLUETOOTH_SETTINGS"),
    Applications("android.settings.APPLICATION_SETTINGS"),
    DateTime("android.settings.DATE_SETTINGS"),
}
