// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.domain.intent

interface IntentUriCodec {
    fun parse(value: String): IntentSpec
    fun encode(spec: IntentSpec, operation: IntentOperation, format: Int): String
}
