// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.github.wxxsfxyzm.intentx.data.intent.ProfileTransferCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentAccess(private val context: Context) {
    suspend fun retain(uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        name to context.contentResolver.getType(uri)
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open the selected document" }
        stream.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = it.read(buffer)
            while (count != -1) {
                require(output.size() + count <= ProfileTransferCodec.MAX_BYTES) { "Import exceeds the 1 MiB limit" }
                output.write(buffer, 0, count)
                count = it.read(buffer)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    suspend fun stageExport(content: String): String = withContext(Dispatchers.IO) {
        require(content.toByteArray(Charsets.UTF_8).size <= ProfileTransferCodec.MAX_BYTES) { "Export exceeds the 1 MiB limit" }
        val now = System.currentTimeMillis()
        context.cacheDir.listFiles()?.filter { it.name.startsWith("profile-export-") && now - it.lastModified() > 86_400_000 }?.forEach(File::delete)
        val id = UUID.randomUUID().toString()
        exportFile(id).writeText(content)
        id
    }

    suspend fun completeExport(uri: Uri?, id: String) = withContext(Dispatchers.IO) {
        val file = exportFile(id)
        try {
            if (uri != null) {
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot write the selected document" }.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun exportFile(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Invalid export identifier" }
        return File(context.cacheDir, "profile-export-$id.json")
    }
}
