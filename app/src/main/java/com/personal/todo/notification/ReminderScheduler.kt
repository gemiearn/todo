package com.personal.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.room.withTransaction
import com.personal.todo.data.ReminderGateway
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Reminder
import com.personal.todo.model.ReminderKind
import java.time.ZoneId

/** 一个应用只保留一个系统闹钟；到点批量通知，随后安排最近的下一次。
 * 所有读写在同一个 Room 事务内串行，避免启动重排、通知按钮和编辑任务互相覆盖。
 * 仓库本身的事务可重入，不另外持有跨事务的 Mutex。
 */
class ReminderScheduler(
    context: Context,
    private val database: () -> TodoDatabase = { DatabaseProvider.get(context) },
    private val now: () -> Long = System::currentTimeMillis,
    private val bootCount: () -> Int = {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }
) : ReminderGateway {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val settings = ReminderSettingsStore(appContext)

    override suspend fun sync(task: Task) {
        val db = database()
        db.withTransaction {
            if (task.reminderAt == null || task.completedAt != null || task.deletedAt != null) {
                settings.clearSnooze(task.id)
                ReminderNotifications.cancel(appContext, task.id)
            } else if (effectiveAt(task)?.let { it > now() } == true) {
                ReminderNotifications.cancel(appContext, task.id)
            }
            scheduleNext(db)
        }
    }

    override suspend fun cancel(taskId: Long) {
        val db = database()
        db.withTransaction {
            settings.clearSnooze(taskId)
            ReminderNotifications.cancel(appContext, taskId)
            cancelLegacyAlarm(taskId)
            scheduleNext(db)
        }
    }

    override suspend fun refresh() = rescheduleAll()

    suspend fun snooze(taskId: Long) {
        val db = database()
        db.withTransaction {
            val task = db.taskDao().getById(taskId) ?: return@withTransaction
            val originalAt = task.reminderAt ?: return@withTransaction
            if (task.completedAt != null || task.deletedAt != null) return@withTransaction
            settings.writeSnooze(taskId, originalAt,
                now() + ReminderSettingsStore.SnoozeMinutes * 60_000L)
            scheduleNext(db)
        }
    }

    /** 只有系统闹钟投递才发通知；冷启动保留待投递窗口，设备重启则只排未来。 */
    suspend fun dispatch(windowStart: Long) {
        val db = database()
        db.withTransaction {
            val time = now()
            if (windowStart > time) return@withTransaction
            db.taskDao().getPendingReminders().forEach { task ->
                val at = effectiveAt(task) ?: return@forEach
                if (at in windowStart..time && !settings.wasDelivered(task.id, at)) {
                    if (ReminderNotifications.show(appContext, task.id, at, db, time)) {
                        settings.markDelivered(task.id, at)
                    }
                }
            }
            // 先确认旧窗口已处理，下一次调度才不会把已经执行的 PendingIntent 当作仍在等。
            // 迟到的旧广播不能清除另一个更新的窗口。
            settings.readScheduledAlarm()?.takeIf { it.at == windowStart }?.let {
                cancelDispatchAlarm(it.at)
                settings.clearScheduledAlarm()
            }
            scheduleNext(db)
        }
    }

    suspend fun rescheduleAll(recalculate: Boolean = false) {
        val db = database()
        db.withTransaction {
            val tasks = db.taskDao().getPendingReminders()
            if (!settings.usesSharedAlarm()) {
                // 升级时撤掉 0.5.2 逐任务创建的闹钟，不能让旧闹钟继续成批弹出。
                tasks.forEach { cancelLegacyAlarm(it.id) }
                settings.markSharedAlarm()
            }
            tasks.forEach { task ->
                // 历史版本曾把自定义绝对时间复制给未来所有实例。只有复制来的值被清理；
                // 用户在某次实例上单独编辑的自定义时刻仍然保留。
                if (task.recurrenceSourceId != null && task.reminderKind == ReminderKind.CUSTOM) {
                    val source = db.taskDao().getById(task.recurrenceSourceId)
                    if (source?.reminderKind == ReminderKind.CUSTOM && source.reminderAt == task.reminderAt) {
                        db.taskDao().update(task.copy(reminderAt = null, reminderKind = null))
                        settings.clearSnooze(task.id)
                        return@forEach
                    }
                }
                if (recalculate) {
                    val at = Reminder.epochMillisFor(task.reminderKind, task.reminderAt,
                        task.plannedDate, task.plannedTime, task.dueDate, task.dueTime,
                        ZoneId.systemDefault())
                    if (at != task.reminderAt) {
                        val previous = task.reminderAt
                        val snooze = previous?.let { settings.readSnooze(task.id, it) }
                        if (at != null && snooze != null) settings.writeSnooze(task.id, at, snooze)
                        if (at != null && previous != null && settings.wasDelivered(task.id, previous)) {
                            settings.markDelivered(task.id, at)
                        }
                        db.taskDao().update(task.copy(reminderAt = at, updatedAt = now()))
                    }
                }
            }
            scheduleNext(db)
        }
    }

    private fun effectiveAt(task: Task): Long? {
        val at = task.reminderAt ?: return null
        return settings.readSnooze(task.id, at) ?: at
    }

    private suspend fun scheduleNext(db: TodoDatabase) {
        val time = now()
        val currentBoot = bootCount()
        val previous = settings.readScheduledAlarm()
        val outstanding = db.taskDao().getPendingReminders().mapNotNull { task ->
            effectiveAt(task)?.takeUnless { settings.wasDelivered(task.id, it) }
        }
        // 非精确闹钟可被系统延迟。到点后打开应用、生成重复任务、保存其他任务或修改设置，
        // 都不能取消尚未投递的窗口。重启后的旧窗口不保留，避免补发关机期间错过的提醒。
        val waitingAt = previous?.takeIf { alarm ->
            alarm.bootCount == currentBoot && alarm.at <= time &&
                outstanding.any { it in alarm.at..time }
        }?.at
        val at = waitingAt ?: outstanding.filter { it > time }.minOrNull()
        var exact = settings.readExactAlarm() && canScheduleExact()
        // 清理 0.5.3 使用的可变 extras PendingIntent。新窗口以 URI 区分，不能被下一次改写。
        cancelDispatchAlarm(null)
        if (previous != null && (previous.at != at || previous.bootCount != currentBoot)) {
            cancelDispatchAlarm(previous.at)
        }
        if (at == null) {
            settings.clearScheduledAlarm()
            return
        }
        if (waitingAt != null && previous?.exact == exact &&
            PendingIntent.getBroadcast(appContext, 0, dispatchIntent(at),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null) {
            // 不反复重新启动一个已经晚到的普通闹钟，否则系统的省电配额会再次推迟它。
            return
        }
        val pending = PendingIntent.getBroadcast(appContext, 0,
            dispatchIntent(at).putExtra(ReminderReceiver.EXTRA_REMINDER_AT, at),
            PendingIntent.FLAG_IMMUTABLE)
        val manager = alarmManager ?: return
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            exact = false
        }
        settings.writeScheduledAlarm(at, currentBoot, exact)
    }

    private fun dispatchIntent(at: Long?) = Intent(appContext, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_DISPATCH)
        .apply { if (at != null) data = Uri.parse("todo://reminders/$at") }

    private fun cancelDispatchAlarm(at: Long?) {
        PendingIntent.getBroadcast(appContext, 0, dispatchIntent(at),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager?.cancel(it)
            it.cancel()
        }
    }

    private fun cancelLegacyAlarm(taskId: Long) {
        val pending = PendingIntent.getBroadcast(appContext, taskId.toInt(),
            Intent(appContext, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarmManager?.cancel(pending)
        pending.cancel()
    }

    fun canScheduleExact(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true
}
