package com.personal.todo.notification

import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.MainActivity
import com.personal.todo.data.TodoContainer
import com.personal.todo.model.ReminderKind
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 真正的 Android 系统闹钟验收：无应用前台页面，息屏并进入 Doze，由广播唤起后发通知。 */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    @Test(timeout = 120_000L)
    fun onTimeReminderPostsWhileTheAppIsInBackgroundAndTheScreenIsOff() {
        val context = instrumentation.targetContext
        val repository = TodoContainer.repository(context)
        val settings = ReminderSettingsStore(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        var id: Long? = null
        var activity: ActivityScenario<MainActivity>? = null
        try {
            shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
            settings.writeExactAlarm(true)
            assertTrue("Exact alarm permission was not granted", ReminderScheduler(context).canScheduleExact())
            assertTrue("Notifications are blocked", ReminderNotifications.areEnabled(context))
            activity = ActivityScenario.launch(MainActivity::class.java)
            val target = Instant.ofEpochMilli(System.currentTimeMillis() + 15_000L).atZone(ZoneId.systemDefault())
            val taskId = runBlocking {
                repository.createTask("锁屏准时提醒回归", repository.ensureInbox(),
                    plannedDate = target.toLocalDate(), plannedTime = target.toLocalTime(),
                    reminderKind = ReminderKind.ON_TIME)
            }
            id = taskId
            val at = requireNotNull(runBlocking { repository.getTask(taskId) }?.reminderAt)
            assertTrue("Reminder was not scheduled as an exact alarm",
                requireNotNull(settings.readScheduledAlarm()).exact)

            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
            assertFalse("App must have left the foreground before waiting for the alarm",
                activity.state.isAtLeast(Lifecycle.State.STARTED))
            shell("dumpsys battery unplug")
            shell("input keyevent KEYCODE_SLEEP")
            val idleResult = shell("dumpsys deviceidle force-idle")
            assertTrue("Device did not enter idle: $idleResult", idleResult.contains("idle", ignoreCase = true))

            val deadline = SystemClock.elapsedRealtime() + 75_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                if (notifications.activeNotifications.any { it.id == taskId.toInt() } &&
                    settings.wasDelivered(taskId, at)) break
                Thread.sleep(250L)
            }
            assertTrue("The actual alarm broadcast did not post a notification while screen was off",
                notifications.activeNotifications.any { it.id == taskId.toInt() })
            assertTrue("Reminder receiver did not acknowledge delivery", settings.wasDelivered(taskId, at))
            val report = ReminderDiagnostics.report(context)
            assertTrue("Notification must come from the system alarm, not foreground recovery: $report",
                report.contains("系统闹钟 任务#$taskId 通知请求成功=true"))
            assertFalse("Foreground recovery must not deliver this reminder: $report",
                report.contains("应用前台 任务#$taskId 通知请求成功=true"))
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent KEYCODE_WAKEUP")
            activity?.close()
            id?.let { runBlocking { repository.deleteTask(it) } }
        }
    }
}
