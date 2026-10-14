package com.personal.todo

import android.app.Application
import com.personal.todo.data.CrashLog

/**
 * Writes the last uncaught exception to the app's own storage before the app
 * dies, so a crash on the phone can be read back from the 更多 page.
 *
 * The previous handler is still called, which keeps the normal Android crash
 * behaviour (the system crash dialog / log) exactly as it was.
 */
class TodoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashLog.record(this, error)
            previous?.uncaughtException(thread, error)
        }
    }
}
