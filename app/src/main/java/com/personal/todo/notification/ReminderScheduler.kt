package com.personal.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
    private val now: () -> Long = System::currentTimeMillis
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

    /** 只有真正收到系统闹钟才发送到期通知。开机/冷启动仅排未来，绝不补发过期事项。 */
    suspend fun dispatch(windowStart: Long) {
        val db = database()
        db.withTransaction {
            val time = now()
            if (windowStart > time) return@withTransaction
            db.taskDao().getPendingReminders().forEach { task ->
                val at = effectiveAt(task) ?: return@forEach
                if (at in windowStart..time && !settings.wasDelivered(task.id, at)) {
                    ReminderNotifications.show(appContext, task.id, at, db, time)
                    settings.markDelivered(task.id, at)
                }
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
        val at = db.taskDao().getPendingReminders().mapNotNull { task ->
            effectiveAt(task)?.takeIf { it > time && !settings.wasDelivered(task.id, it) }
        }.minOrNull()
        if (at == null) {
            PendingIntent.getBroadcast(appContext, 0, dispatchIntent(),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager?.cancel(it)
            }
            return
        }
        val pending = PendingIntent.getBroadcast(appContext, 0,
            dispatchIntent().putExtra(ReminderReceiver.EXTRA_REMINDER_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = alarmManager ?: return
        try {
            if (settings.readExactAlarm() && canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun dispatchIntent() = Intent(appContext, ReminderReceiver::class.java)
        .setAction(ReminderReceiver.ACTION_DISPATCH)

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
