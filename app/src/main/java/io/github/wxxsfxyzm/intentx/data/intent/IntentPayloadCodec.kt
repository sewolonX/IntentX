// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.data.intent

import io.github.wxxsfxyzm.intentx.domain.intent.IntentSpec
import kotlinx.serialization.json.Json

/** Payload versions are independent of the database schema and shortcut capability version. */
class IntentPayloadCodec(private val json: Json) {
    val version: Int = 2

    fun encode(intent: IntentSpec): String = json.encodeToString(intent)

    fun decode(version: Int, payload: String): IntentSpec {
        require(version == this.version) { "Unsupported Intent payload version: $version" }
        return json.decodeFromString(payload)
    }
}
