package com.personal.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.personal.todo.data.ReminderGateway
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.data.db.Task
import com.personal.todo.model.Reminder
import java.time.ZoneId

/**
 * 把任务的提醒交给系统的闹钟服务。
 *
 * **为什么是 AlarmManager 而不是 WorkManager**：WorkManager 保证的是「最终会跑」，
 * 不保证「几点跑」——它会被批处理、被省电策略推迟，用来做「夜里同步一下数据」很合适，
 * 用来做「20:00 提醒你开会」就不合适。
 *
 * **精确与非精确**：Android 12 起，`setExactAndAllowWhileIdle` 需要「闹钟和提醒」
 * 权限。默认走非精确的 `setAndAllowWhileIdle`（不需要额外权限，Doze 下可能晚几分钟）；
 * 用户在设置页打开「准点提醒」后才升级到精确。权限在中途被系统收回时会抛
 * [SecurityException]，这里直接退回非精确，绝不因为一个权限把提醒整个丢掉。
 *
 * 用的是 `RTC_WAKEUP`：设备睡了也要把 CPU 叫起来，否则息屏半夜的提醒会等到天亮。
 */
class ReminderScheduler(context: Context) : ReminderGateway {

    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager? =
        appContext.getSystemService(AlarmManager::class.java)
    private val settings = ReminderSettingsStore(appContext)

    override fun sync(task: Task) {
        val at = task.reminderAt
        val now = System.currentTimeMillis()
        // 已完成的、已删除的、没设提醒的、时刻已经过去的，都归到「撤掉」。
        // 超过一个月的先不排：一条「每天重复」会铺出一年实例，若每条都占一个闹钟，
        // 系统里会躺着三百多个待触发的 alarm，开机重排一次就够卡一下了。窗口内的
        // 排上，走到窗口边缘时冷启动 / 跨天重排会自然接上。
        if (at == null || task.completedAt != null || task.deletedAt != null ||
            at <= now || at > now + HorizonMillis
        ) {
            cancel(task.id)
            return
        }
        setAt(task.id, at)
    }

    override fun cancel(taskId: Long) {
        // 用 NO_CREATE：只是撤，不该顺手在系统里造出一个再也没人用的 PendingIntent。
        val existing = PendingIntent.getBroadcast(
            appContext,
            taskId.toInt(),
            Intent(appContext, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager?.cancel(existing)
    }

    /** 「稍后提醒」：从此刻起往后推 [ReminderSettingsStore.SnoozeMinutes] 分钟。 */
    fun snooze(taskId: Long) {
        val at = System.currentTimeMillis() + ReminderSettingsStore.SnoozeMinutes * 60_000L
        setAt(taskId, at)
    }

    /**
     * 把所有还等着响的提醒重新交给系统。
     *
     * 开机、改时区、改系统时间之后必须跑一次：`AlarmManager` 里的闹钟在这些情况下
     * 会被系统清掉或算错时刻，不重排的话界面上还写着「提醒 20:00」，到点却一声不响。
     *
     * [recalculate] 为真时先按新的本地时区把 `reminderAt` 重算并落库：提醒是「本地
     * 20:00 响」，不是一个固定的 UTC 时刻，飞到另一个时区之后还得在当地的 20:00 响。
     * 自定义时刻（`CUSTOM`）不参与重算，那是用户亲手挑的那一刻。
     */
    suspend fun rescheduleAll(recalculate: Boolean = false) {
        val db = DatabaseProvider.get(appContext)
        db.taskDao().getPendingReminders().forEach { task ->
            val fixed = if (recalculate) {
                val at = Reminder.epochMillisFor(
                    kind = task.reminderKind,
                    customAt = task.reminderAt,
                    plannedDate = task.plannedDate,
                    plannedTime = task.plannedTime,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    zone = ZoneId.systemDefault()
                )
                if (at != task.reminderAt) {
                    db.taskDao().update(
                        task.copy(reminderAt = at, updatedAt = System.currentTimeMillis())
                    )
                }
                task.copy(reminderAt = at)
            } else {
                task
            }
            sync(fixed)
        }
    }

    /** 现在能不能用精确闹钟。31 以下没有这个限制。 */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    private fun setAt(taskId: Long, at: Long) {
        val manager = alarmManager ?: return
        val pending = reminderIntent(taskId)
        val exact = settings.readExactAlarm() && canScheduleExact()
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            // 权限刚被系统收回：退回非精确，晚几分钟总比不响好。
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /**
     * 一条任务一个 PendingIntent，`requestCode` 就是任务 id。
     *
     * 「稍后」用的也是它，所以推迟之后新的时刻会**覆盖**原来那个闹钟，不会出现同一条
     * 任务连着响两次。
     */
    private fun reminderIntent(taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            taskId.toInt(),
            Intent(appContext, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        /**
         * 最多提前多久把闹钟交给系统（见 [sync] 里的说明）。选 30 天：既不会让系统里
         * 堆着几百个 alarm，又足够覆盖普通待办的提醒范围。
         */
        const val HorizonMillis = 30L * 24L * 60L * 60L * 1000L
    }
}
