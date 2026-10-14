package com.personal.todo.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The last uncaught exception, kept as a plain file in the app's own storage.
 *
 * This app is used on a phone that has no development environment attached to
 * it, so "it crashes" is otherwise impossible to diagnose: the stack trace is
 * written here and can be read (and copied) from the 更多 page. The file holds
 * one crash - the newest one - and is never uploaded anywhere.
 */
object CrashLog {

    private const val FileName = "last-crash.txt"

    /** Records the crash, replacing the previous one. Never throws itself. */
    fun record(context: Context, error: Throwable) {
        runCatching {
            logFile(context).writeText(stackTraceOf(error))
        }
    }

    fun read(context: Context): String? = runCatching {
        val file = logFile(context)
        if (file.exists()) file.readText().ifBlank { null } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    private fun logFile(context: Context): File =
        File(context.applicationContext.filesDir, FileName)

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
    }
}
