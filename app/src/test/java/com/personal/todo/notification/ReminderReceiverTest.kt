package com.personal.todo.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.os.Looper
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.model.ReminderKind
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 不能只直接调用调度器：真实 PendingIntent 必须能唤起 Manifest 中的接收器并发通知。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderReceiverTest {
    @Test
    fun theScheduledPendingIntentReachesTheManifestReceiverAndPostsANotification() {
        val context = RuntimeEnvironment.getApplication<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val db = DatabaseProvider.get(context)
        try {
            val actualNow = System.currentTimeMillis()
            val scheduler = ReminderScheduler(context, now = { actualNow - 60_000L })
            val repository = TaskRepository(db, "收件箱", now = { actualNow - 60_000L }, reminders = scheduler)
            val id = runBlocking {
                repository.createTask("接收器回归", repository.ensureInbox(), plannedDate = LocalDate.now(),
                    reminderKind = ReminderKind.CUSTOM, customReminderAt = actualNow - 1_000L)
            }
            val alarms = context.getSystemService(AlarmManager::class.java)
            val pending = requireNotNull(shadowOf(alarms).scheduledAlarms.single().operation)
            pending.send()
            val notifications = context.getSystemService(NotificationManager::class.java)
            // goAsync() queries Room on IO. Drain broadcast delivery, then await that bounded work.
            for (attempt in 0 until 500) {
                shadowOf(Looper.getMainLooper()).idle()
                if (shadowOf(notifications).getNotification(id.toInt()) != null) break
                Thread.sleep(10L)
            }
            assertNotNull(shadowOf(notifications).getNotification(id.toInt()))
        } finally {
            db.close()
        }
    }
}
