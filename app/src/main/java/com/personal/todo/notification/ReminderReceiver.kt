package com.personal.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟到点时被系统叫起来的那一端。
 *
 * 它不关心 App 是不是在前台，也不关心进程有没有被杀——`AlarmManager` 派发的广播会把
 * 进程拉起来。发通知要查一次库（任务是否还活着、备注、清单名），所以用
 * `goAsync()` 把活干完再 `finish()`，而不是在 `onReceive` 里同步查库。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotifications.show(appContext, taskId)
            } catch (_: Exception) {
                // 广播里不让异常冒出去：抛出去会变成一次崩溃，而用户只是没收到提醒。
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.personal.todo.action.REMIND"
        const val EXTRA_TASK_ID = "taskId"
    }
}
