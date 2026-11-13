package com.personal.todo.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.personal.todo.MainActivity
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.ReminderKind
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 真正串起 Room、系统闹钟、通知和仓库，使用固定时钟而不等待真实时间流逝。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderIntegrationTest {
    private lateinit var context: Application
    private lateinit var db: TodoDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var repository: TaskRepository
    private lateinit var manager: AlarmManager
    private lateinit var notifications: NotificationManager
    private lateinit var settings: ReminderSettingsStore
    private var now = System.currentTimeMillis()
    private var inboxId = 0L

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java)
            .allowMainThreadQueries().build()
        manager = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        settings = ReminderSettingsStore(context)
        scheduler = newScheduler()
        repository = TaskRepository(db, "收件箱", now = { now }, reminders = scheduler)
        inboxId = repository.ensureInbox()
        ReminderNotifications.ensureChannel(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newScheduler() = ReminderScheduler(context, database = { db }, now = { now })

    private suspend fun task(at: Long = now + 60_000L): Task {
        val id = repository.createTask(
            title = "开会", listId = inboxId, plannedDate = LocalDate.now(),
            reminderKind = ReminderKind.CUSTOM, customReminderAt = at
        )
        return requireNotNull(repository.getTask(id))
    }

    private fun scheduledAt(): Long = shadowOf(manager).scheduledAlarms.single().triggerAtTime

    @Test
    fun aReminderBeyondThirtyDaysIsScheduledWithoutAnotherAppLaunch() = runBlocking {
        val task = task(now + 90L * 86_400_000L)
        assertEquals(task.reminderAt, scheduledAt())
    }

    @Test
    fun editingATimeReplacesTheOldAlarm() = runBlocking {
        val task = task()
        repository.updateTask(
            task.id, task.title, task.notes, task.priority, task.listId,
            plannedDate = task.plannedDate, reminderKind = ReminderKind.CUSTOM,
            customReminderAt = now + 120_000L
        )
        assertEquals(now + 120_000L, scheduledAt())
    }

    @Test
    fun completingATaskCancelsBothAlarmAndVisibleNotification() = runBlocking {
        val task = task(System.currentTimeMillis() - 1_000L)
        ReminderNotifications.show(context, task.id, db = db)
        assertNotNull(shadowOf(notifications).getNotification(task.id.toInt()))
        scheduler.snooze(task.id)
        assertTrue(repository.completeTask(task.id))
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertNull(shadowOf(notifications).getNotification(task.id.toInt()))
        assertNull(settings.readSnooze(task.id, requireNotNull(task.reminderAt)))
    }

    @Test
    fun deletingATaskCancelsItsReminderAndRestoringReschedulesIt() = runBlocking {
        val task = task()
        repository.deleteTask(task.id)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        repository.restoreTask(task.id)
        assertEquals(task.reminderAt, scheduledAt())
    }

    @Test
    fun aPastReminderIsNotRescheduledOnRestart() = runBlocking {
        task(now - 1_000L)
        newScheduler().rescheduleAll()
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun snoozeSurvivesSchedulerRecreationAndAlarmLoss() = runBlocking {
        val task = task(now - 1_000L)
        scheduler.snooze(task.id)
        val postponed = now + 600_000L
        assertEquals(postponed, scheduledAt())
        manager.cancel(shadowOf(manager).scheduledAlarms.single().operation)
        newScheduler().rescheduleAll()
        assertEquals(postponed, scheduledAt())
        assertEquals(ReminderKind.CUSTOM, repository.getTask(task.id)?.reminderKind)
    }

    @Test
    fun editingTheOriginalReminderInvalidatesTheOldSnooze() = runBlocking {
        val task = task(now - 1_000L)
        scheduler.snooze(task.id)
        val changed = task.copy(reminderAt = now + 120_000L)
        db.taskDao().update(changed)
        scheduler.sync(changed)
        assertEquals(changed.reminderAt, scheduledAt())
    }

    @Test
    fun completedAndDeletedTasksCannotBeSnoozedFromAnOldNotification() = runBlocking {
        val completed = task()
        val deleted = task()
        repository.completeTask(completed.id)
        repository.deleteTask(deleted.id)
        scheduler.snooze(completed.id)
        scheduler.snooze(deleted.id)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun notificationContainsTheTaskAndBothActions() = runBlocking {
        val task = task(System.currentTimeMillis() - 1_000L)
        ReminderNotifications.show(context, task.id, task.reminderAt, db)
        val notification = requireNotNull(shadowOf(notifications).getNotification(task.id.toInt()))
        val open = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertEquals(task.id, open.getLongExtra(MainActivity.EXTRA_OPEN_TASK_ID, 0L))
        assertEquals(
            listOf(ReminderActionReceiver.ACTION_COMPLETE, ReminderActionReceiver.ACTION_SNOOZE),
            notification.actions.map { shadowOf(it.actionIntent).savedIntent.action }
        )
    }

    @Test
    fun anAlreadyDispatchedAlarmCannotNotifyAfterRescheduling() = runBlocking {
        val task = task(System.currentTimeMillis() - 1_000L)
        val oldAt = requireNotNull(task.reminderAt)
        db.taskDao().update(task.copy(reminderAt = oldAt - 60_000L))
        ReminderNotifications.show(context, task.id, oldAt, db)
        assertNull(shadowOf(notifications).getNotification(task.id.toInt()))
    }

    @Test
    fun restartAtDeliveryTimeDoesNotRemoveTheVisibleNotification() = runBlocking {
        val task = task(System.currentTimeMillis() - 1_000L)
        ReminderNotifications.show(context, task.id, db = db)
        newScheduler().rescheduleAll()
        assertNotNull(shadowOf(notifications).getNotification(task.id.toInt()))
    }

    @Test
    fun snoozedNotificationUsesThePersistedDeliveryTime() = runBlocking {
        val task = task(now - 1_000L)
        scheduler.snooze(task.id)
        val at = now + 600_000L
        // 通知函数使用真实系统时钟；模拟恢复了一条已经到点的推迟记录。
        val deliveredAt = System.currentTimeMillis() - 500L
        settings.writeSnooze(task.id, requireNotNull(task.reminderAt), deliveredAt)
        ReminderNotifications.show(context, task.id, at, db)
        assertNull(shadowOf(notifications).getNotification(task.id.toInt()))
        ReminderNotifications.show(context, task.id, deliveredAt, db)
        assertNotNull(shadowOf(notifications).getNotification(task.id.toInt()))
    }

    @Test
    fun aDisabledReminderChannelIsReportedAsDisabled() {
        notifications.createNotificationChannel(
            NotificationChannel(ReminderNotifications.CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_NONE)
        )
        assertFalse(ReminderNotifications.areEnabled(context))
    }

    @Test
    fun deniedNotificationPermissionDoesNotBreakSavingOrScheduling() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val future = task()
        assertEquals(future.reminderAt, scheduledAt())
        val past = task(System.currentTimeMillis() - 1_000L)
        ReminderNotifications.show(context, past.id, db = db)
        assertNull(shadowOf(notifications).getNotification(past.id.toInt()))
    }
}
