// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

import kotlinx.serialization.Serializable

@Serializable
enum class OpeningPolicy { SystemDefault, AlwaysAsk, Application }
