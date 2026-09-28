// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.wxxsfxyzm.intentx.domain.intent.OpeningApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpeningAppResolver(private val context: Context) {
    suspend fun find(uri: String, mimeType: String?): List<OpeningApp> = withContext(Dispatchers.IO) {
        val data = Uri.parse(uri)
        require(!data.scheme.isNullOrBlank()) { "Enter a complete URI or select a file" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (mimeType != null) {
                setDataAndType(data, mimeType)
            } else {
                setData(data)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map {
            OpeningApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString())
        }.distinctBy { it.packageName }.sortedBy { it.name }
    }
}
