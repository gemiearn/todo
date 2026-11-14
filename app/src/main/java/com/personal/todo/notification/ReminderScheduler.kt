package com.personal.todo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.room.withTransaction
import com.personal.todo.data.ReminderGateway
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Reminder
import com.personal.todo.model.ReminderKind
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

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
        ReminderDiagnostics.record(appContext, "保存任务#${task.id} 提醒=${task.reminderAt?.let(ReminderDiagnostics::moment) ?: "无"} 类型=${task.reminderKind} 完成=${task.completedAt != null} 删除=${task.deletedAt != null}")
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

    override suspend fun refresh() = rescheduleAll(restoreAlarm = false)

    /** 前台不必等普通系统闹钟的批处理窗口；与广播共用去重和任务状态检查。 */
    suspend fun watchWhileForeground() {
        try {
            rescheduleAll()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("TaskReminder", "Unable to restore reminders", error)
        }
        while (currentCoroutineContext().isActive) {
            try {
                dispatchDue()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("TaskReminder", "Unable to deliver foreground reminder", error)
            }
            delay(1_000L)
        }
    }

    /** 只处理本次开机已经排定的窗口，不能补发过去未排定或关机错过的提醒。 */
    suspend fun dispatchDue() {
        val alarm = settings.readScheduledAlarm() ?: return
        if (alarm.bootCount != bootCount() || alarm.at > now()) return
        if (!ReminderNotifications.areEnabled(appContext)) return
        dispatch(alarm.at, source = "应用前台")
    }

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

    /** 系统广播与前台到点检查共用投递；冷启动保留待投递窗口，重启只排未来。 */
    suspend fun dispatch(windowStart: Long, source: String = "系统闹钟") {
        ReminderDiagnostics.record(appContext, "$source 开始处理窗口=${ReminderDiagnostics.moment(windowStart)}")
        val db = database()
        db.withTransaction {
            val time = now()
            if (windowStart > time) return@withTransaction
            db.taskDao().getPendingReminders().forEach { task ->
                val at = effectiveAt(task) ?: return@forEach
                if (at in windowStart..time && !settings.wasDelivered(task.id, at)) {
                    val submitted = ReminderNotifications.show(appContext, task.id, at, db, time)
                    ReminderDiagnostics.record(appContext, "$source 任务#${task.id} 通知请求成功=$submitted")
                    if (submitted) {
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

    suspend fun rescheduleAll(recalculate: Boolean = false, restoreAlarm: Boolean = true) {
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
            scheduleNext(db, restoreAlarm)
        }
    }

    private fun effectiveAt(task: Task): Long? {
        val at = task.reminderAt ?: return null
        return settings.readSnooze(task.id, at) ?: at
    }

    private suspend fun scheduleNext(db: TodoDatabase, restoreAlarm: Boolean = false) {
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
            ReminderDiagnostics.record(appContext, "调度结束：无等待提醒")
            return
        }
        if (!restoreAlarm && previous?.at == at && previous.bootCount == currentBoot && previous.exact == exact &&
            PendingIntent.getBroadcast(appContext, 0, dispatchIntent(at),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null) {
            // 日常编辑不重复启动同一个闹钟，以免普通闹钟的可投递窗口不断后移。
            // 冷启动及系统恢复必须重设：PendingIntent 存在不代表系统仍保有闹钟。
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
        ReminderDiagnostics.record(appContext, "系统闹钟已设置=${ReminderDiagnostics.moment(at)} 精确=$exact")
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
