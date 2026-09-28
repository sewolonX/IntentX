// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.wxxsfxyzm.intentx.domain.intent.IntentTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TemplateAvailability(private val context: Context) {
    suspend fun available(): Set<IntentTemplate> = withContext(Dispatchers.IO) {
        IntentTemplate.entries.filter {
            context.packageManager.resolveActivity(Intent(it.action), PackageManager.MATCH_DEFAULT_ONLY) != null
        }.toSet()
    }
}
