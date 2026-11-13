package com.personal.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.todo.data.TodoContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知上那两个按钮的落点。
 *
 * 「完成」直接写库（走 [com.personal.todo.data.TaskRepository.completeTask]，它自己会把
 * 闹钟撤掉并补一条 COMPLETED 事件，和手动勾完走的是同一条路——统计、习惯自动打卡都不会
 * 因为是从通知点的而漏掉）。
 *
 * 「稍后」把这一次的推迟时刻保存在本地偏好，再排闹钟；重启仍可恢复，重复规则不变。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return
        val appContext = context.applicationContext
        val action = intent.action
        if (action != ACTION_COMPLETE && action != ACTION_SNOOZE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE ->
                        TodoContainer.repository(appContext).completeTask(taskId)

                    ACTION_SNOOZE ->
                        ReminderScheduler(appContext).snooze(taskId)
                }
            } catch (_: Exception) {
                // 同上：广播里不抛异常，否则一次点击变成一次崩溃。
            } finally {
                ReminderNotifications.cancel(appContext, taskId)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.personal.todo.action.COMPLETE"
        const val ACTION_SNOOZE = "com.personal.todo.action.SNOOZE"
        const val EXTRA_TASK_ID = "taskId"
    }
}
