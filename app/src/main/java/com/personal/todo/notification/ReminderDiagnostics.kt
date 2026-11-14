package com.personal.todo.notification

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.personal.todo.data.ReminderSettingsStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Bounded local history: no task titles, notes or automatic uploads. */
object ReminderDiagnostics {
    private const val Preferences = "reminder_diagnostics"
    private const val History = "history"
    private const val Limit = 60

    fun moment(at: Long): String = Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))

    @Synchronized
    fun record(context: Context, event: String) {
        // Diagnostics must never prevent scheduling or notification delivery.
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            val history = prefs.getString(History, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
            val line = "${moment(System.currentTimeMillis())} $event"
            prefs.edit().putString(History, (history + line).takeLast(Limit).joinToString("\n")).apply()
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun report(context: Context): String = buildString {
        val settings = ReminderSettingsStore(context)
        val version = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) version.longVersionCode else version.versionCode.toLong()
        appendLine("提醒诊断 ${version.versionName} ($code)")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("当前时间=${moment(System.currentTimeMillis())} 时区=${ZoneId.systemDefault()}")
        appendLine("通知可用=${ReminderNotifications.areEnabled(context)} 准点开关=${settings.readExactAlarm()} 精确闹钟授权=${ReminderScheduler(context).canScheduleExact()}")
        val power = context.getSystemService(PowerManager::class.java)
        appendLine("忽略电池优化=${power?.isIgnoringBatteryOptimizations(context.packageName)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appendLine("系统限制后台=${context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted}")
        }
        val alarm = settings.readScheduledAlarm()
        appendLine(if (alarm == null) "当前无等待闹钟" else
            "等待闹钟=${moment(alarm.at)} 精确=${alarm.exact} 开机编号=${alarm.bootCount}")
        appendLine("最近记录（通知请求成功不代表已经显示或响铃）：")
        append(context.applicationContext.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getString(History, "尚无记录"))
    }
}
