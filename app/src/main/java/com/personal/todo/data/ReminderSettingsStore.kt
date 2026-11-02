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

    companion object {
        private const val Name = "reminder_settings"
        private const val KeyExactAlarm = "exactAlarm"

        /** 「稍后提醒」把任务往后推多久，分钟。 */
        const val SnoozeMinutes = 10
    }
}
