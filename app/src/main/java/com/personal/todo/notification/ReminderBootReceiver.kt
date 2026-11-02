package com.personal.todo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统把这套闹钟清掉（或算错）之后，把它们重新设回去。
 *
 * 四种情况，处理方式不完全一样：
 *  - **开机**：`AlarmManager` 里的闹钟随进程一起消失，必须全部重设；但每条任务的
 *    绝对时刻仍然对，不必重算。
 *  - **App 更新**：同理，重设即可。
 *  - **改时区 / 改系统时间**：本地时间的含义变了。一条「本地 20:00」的提醒应当仍然在
 *    当地的 20:00 响，所以这两件事要**先按新时区重算 `reminderAt` 再重设**。自定义
 *    时刻不参与重算——那是用户亲手挑的那一个瞬间。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recalculate = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> false

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> true

            else -> return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(appContext).rescheduleAll(recalculate = recalculate)
            } catch (_: Exception) {
                // 重排失败不该变成崩溃：下次打开 App 时启动兜底还会再跑一次。
            } finally {
                pending.finish()
            }
        }
    }
}
