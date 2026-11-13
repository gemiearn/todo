package com.personal.todo.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.todo.R
import com.personal.todo.notification.ReminderNotifications

/** 从系统设置返回时同步状态，也检查用户单独关闭提醒渠道的情况。 */
@Composable
internal fun ReminderPermissionHint() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember(context) { mutableStateOf(ReminderNotifications.areEnabled(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = ReminderNotifications.areEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!enabled) {
        Text(
            stringResource(R.string.settings_reminder_permission_hint),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL_ID)
                )
            }
        }) {
            Text(stringResource(R.string.reminder_enable_notifications))
        }
    }
}
