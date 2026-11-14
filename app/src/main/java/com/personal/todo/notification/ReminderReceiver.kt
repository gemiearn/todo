package com.personal.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 闹钟到点时被系统叫起来的那一端。
 *
 * 它不关心 App 是不是在前台，也不关心进程有没有被杀——`AlarmManager` 派发的广播会把
 * 进程拉起来。发通知要查一次库（任务是否还活着、备注、清单名），所以用
 * `goAsync()` 把活干完再 `finish()`，而不是在 `onReceive` 里同步查库。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 旧版逐任务闹钟不再投递；启动迁移会撤掉它们。
        if (intent.action != ACTION_DISPATCH || !intent.hasExtra(EXTRA_REMINDER_AT)) return
        val appContext = context.applicationContext
        val pending = goAsync()
        val at = intent.getLongExtra(EXTRA_REMINDER_AT, 0L)
        ReminderDiagnostics.record(appContext, "系统闹钟广播收到 窗口=${ReminderDiagnostics.moment(at)}")
        // AlarmManager's wake lock covers onReceive, not the Room coroutine
        // after it returns. Keep CPU awake only for this bounded delivery.
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "todo:reminder-delivery")
        try {
            wakeLock?.acquire(10_000L)
        } catch (error: RuntimeException) {
            ReminderDiagnostics.record(appContext, "提醒唤醒失败=${error.javaClass.simpleName}")
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) { ReminderScheduler(appContext).dispatch(at) }
            } catch (error: Exception) {
                ReminderDiagnostics.record(appContext, "系统闹钟处理失败=${error.javaClass.simpleName}")
                Log.e("TaskReminder", "Unable to deliver alarm broadcast", error)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DISPATCH = "com.personal.todo.action.DISPATCH_REMINDERS"
        const val ACTION_REMIND = "com.personal.todo.action.REMIND"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_REMINDER_AT = "reminderAt"
    }
}
