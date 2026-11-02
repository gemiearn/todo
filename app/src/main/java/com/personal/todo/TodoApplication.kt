package com.personal.todo

import android.app.Application
import com.personal.todo.data.CrashLog
import com.personal.todo.notification.ReminderNotifications
import com.personal.todo.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes the last uncaught exception to the app's own storage before the app
 * dies, so a crash on the phone can be read back from the 更多 page.
 *
 * The previous handler is still called, which keeps the normal Android crash
 * behaviour (the system crash dialog / log) exactly as it was.
 *
 * 另外做两件提醒相关的事：建通知渠道（渠道只在第一次需要，重复调用无害），以及
 * **冷启动兜底重排**——被系统「强行停止」过、或闹钟因为别的原因丢了的任务，在这里
 * 补回来。重排放在 IO 线程，不拖慢启动。
 */
class TodoApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashLog.record(this, error)
            previous?.uncaughtException(thread, error)
        }

        ReminderNotifications.ensureChannel(this)
        appScope.launch {
            runCatching { ReminderScheduler(this@TodoApplication).rescheduleAll() }
        }
    }
}
