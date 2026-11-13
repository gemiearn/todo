package com.personal.todo.data

import android.content.Context

/**
 * 提醒的两个偏好。
 *
 * 和外观 / 专注参数 / 筛选一样存在 SharedPreferences 里：它们是**屏幕偏好**，不是任务
 * 数据，改一次既不需要迁移，也不该动到任何一条任务。
 *
 * 只有 [exactAlarmEnabled] 一个开关，以及「稍后提醒」等多久。默认都不打扰：
 * 非精确闹钟不需要额外权限，对绝大多数任务够用；用户嫌不准时自己去设置页打开准点。
 */
class ReminderSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    /**
     * 是否用精确闹钟。
     *
     * 打开它需要系统授予「闹钟和提醒」权限（Android 12+），没授权时会退回非精确，
     * 而不是抛异常——宁可晚几分钟，也不能让通知彻底不响。
     */
    fun readExactAlarm(): Boolean = prefs.getBoolean(KeyExactAlarm, false)

    fun writeExactAlarm(enabled: Boolean) {
        prefs.edit().putBoolean(KeyExactAlarm, enabled).apply()
    }

    /** 推迟只针对这一次提醒；原提醒改期后，旧的推迟记录自动失效。 */
    fun readSnooze(taskId: Long, reminderAt: Long): Long? {
        if (!prefs.contains("snooze_source_$taskId")) return null
        if (prefs.getLong("snooze_source_$taskId", Long.MIN_VALUE) != reminderAt) {
            clearSnooze(taskId)
            return null
        }
        return prefs.getLong("snooze_at_$taskId", Long.MIN_VALUE)
            .takeUnless { it == Long.MIN_VALUE }
    }

    fun writeSnooze(taskId: Long, reminderAt: Long, at: Long) {
        prefs.edit()
            .putLong("snooze_source_$taskId", reminderAt)
            .putLong("snooze_at_$taskId", at)
            .apply()
    }

    fun clearSnooze(taskId: Long) {
        prefs.edit().remove("snooze_source_$taskId").remove("snooze_at_$taskId").apply()
    }

    fun wasDelivered(taskId: Long, at: Long): Boolean =
        prefs.getLong("delivered_$taskId", Long.MIN_VALUE) == at

    fun markDelivered(taskId: Long, at: Long) {
        prefs.edit().putLong("delivered_$taskId", at).apply()
    }

    fun usesSharedAlarm(): Boolean = prefs.getBoolean("sharedAlarm", false)

    fun markSharedAlarm() {
        prefs.edit().putBoolean("sharedAlarm", true).apply()
    }

    /** 记录仍在等待系统投递的窗口；不能仅因到点就把它当成已经执行。 */
    data class ScheduledAlarm(val at: Long, val bootCount: Int, val exact: Boolean)

    fun readScheduledAlarm(): ScheduledAlarm? {
        if (!prefs.contains("scheduled_at")) return null
        return ScheduledAlarm(prefs.getLong("scheduled_at", 0L), prefs.getInt("scheduled_boot", -1),
            prefs.getBoolean("scheduled_exact", false))
    }

    fun writeScheduledAlarm(at: Long, bootCount: Int, exact: Boolean) {
        prefs.edit().putLong("scheduled_at", at).putInt("scheduled_boot", bootCount)
            .putBoolean("scheduled_exact", exact).apply()
    }

    fun clearScheduledAlarm() {
        prefs.edit().remove("scheduled_at").remove("scheduled_boot").remove("scheduled_exact").apply()
    }

    companion object {
        private const val Name = "reminder_settings"
        private const val KeyExactAlarm = "exactAlarm"

        /** 「稍后提醒」把任务往后推多久，分钟。 */
        const val SnoozeMinutes = 10
    }
}
