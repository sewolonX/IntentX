// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

package io.github.wxxsfxyzm.intentx.framework.shortcut

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.wxxsfxyzm.intentx.R
import io.github.wxxsfxyzm.intentx.data.intent.IntentBuilder
import io.github.wxxsfxyzm.intentx.domain.catalog.SystemAppProvider
import io.github.wxxsfxyzm.intentx.domain.intent.IntentOperation
import io.github.wxxsfxyzm.intentx.domain.shortcut.IntentShortcut
import io.github.wxxsfxyzm.intentx.domain.shortcut.ShortcutRepository
import io.github.wxxsfxyzm.intentx.executor.IntentExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import timber.log.Timber

/** No executable fields are accepted from the caller; only a previously created capability. */
class ShortcutActivity : ComponentActivity() {
    private val repository: ShortcutRepository by inject()
    private val builder: IntentBuilder by inject()
    private val executor: IntentExecutor by inject()
    private val apps: SystemAppProvider by inject()

    companion object {
        private const val ACTION_EXECUTE = "io.github.wxxsfxyzm.intentx.action.EXECUTE_SHORTCUT"
        private const val SCHEME = "intentx"
        private const val HOST = "shortcut"

        internal fun createIntent(context: Context, shortcut: IntentShortcut): Intent = Intent(context, ShortcutActivity::class.java).apply {
            action = ACTION_EXECUTE
            data = Uri.Builder().scheme(SCHEME).authority(HOST)
                .appendPath(shortcut.id).appendPath(shortcut.token).build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never replay a broadcast when Android recreates this transient Activity.
        if (savedInstanceState != null) {
            Timber.d("Skipping shortcut replay after Activity recreation")
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val uri = intent.data
                require(intent.action == ACTION_EXECUTE && uri?.scheme == SCHEME && uri.host == HOST && uri.pathSegments.size == 2)
                val shortcut = requireNotNull(repository.resolve(uri.pathSegments[0], uri.pathSegments[1]))
                Timber.d("Executing authorized shortcut: id=%s, operation=%s, authorizer=%s", shortcut.id, shortcut.operation, shortcut.authorizer)
                val spec = shortcut.intent
                val disabled = if (spec.packageName != null && spec.className != null) {
                    when (shortcut.operation) {
                        IntentOperation.Activity -> apps.isActivityEnabled(spec.packageName, spec.className) == false
                        IntentOperation.Broadcast -> apps.isReceiverEnabled(spec.packageName, spec.className) == false
                    }
                } else {
                    false
                }
                if (disabled) {
                    Timber.w("Shortcut execution blocked: component is disabled")
                    showMessage(if (shortcut.operation == IntentOperation.Activity) R.string.editor_activity_disabled else R.string.editor_receiver_disabled)
                    return@launch
                }
                val target = withContext(Dispatchers.IO) { builder.build(spec, shortcut.operation) }
                val success = when (shortcut.operation) {
                    IntentOperation.Activity -> executor.startActivity(shortcut.authorizer, target)
                    IntentOperation.Broadcast -> executor.sendBroadcast(shortcut.authorizer, target)
                }
                if (!success) {
                    Timber.w("Shortcut execution rejected by Android")
                    showMessage(R.string.editor_launch_failed)
                } else if (shortcut.operation == IntentOperation.Broadcast) {
                    Timber.d("Shortcut broadcast submitted successfully")
                    showMessage(R.string.editor_broadcast_sent)
                } else {
                    Timber.d("Shortcut Activity launch submitted successfully")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Shortcut validation or execution failed")
                showMessage(R.string.shortcut_execution_failed)
            } finally {
                finish()
            }
        }
    }

    private fun showMessage(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
