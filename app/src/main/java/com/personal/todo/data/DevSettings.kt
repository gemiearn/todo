package com.personal.todo.data

import android.content.Context

/**
 * 开发用设置的落盘口（用户 2026-10-21：崩溃日志不该在启动时直接糊到用户脸上）。
 *
 * 这里只有两件事：
 *  - [readShowCrashOnLaunch]：下次启动时要不要把上次的崩溃日志弹出来。**默认关**——正常
 *    用户永远看不到它，只有开发者在「设置 → 开发者声明（连点五下）→ 开发者选项」里
 *    手动打开时才会出现。
 *  - [readDeveloperUnlocked]：那一组「开发者选项」入口是否显示在设置页上。解锁与再次隐藏
 *    都只是翻这一位，不碰任何任务 / 专注数据。
 *
 * 和外观设置一样存 SharedPreferences：它们是「怎么看」，不是应用的数据。
 */
class DevSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    /** 设置页上要不要显示「开发者选项」那一组入口。 */
    fun readDeveloperUnlocked(): Boolean = prefs.getBoolean(KeyUnlocked, false)

    fun writeDeveloperUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KeyUnlocked, unlocked).apply()
    }

    /** 启动时是否弹出上一次的崩溃日志。默认关。 */
    fun readShowCrashOnLaunch(): Boolean = prefs.getBoolean(KeyShowCrashOnLaunch, false)

    fun writeShowCrashOnLaunch(show: Boolean) {
        prefs.edit().putBoolean(KeyShowCrashOnLaunch, show).apply()
    }

    companion object {
        /** 「开发者声明」要连点几下才解锁开发者选项。 */
        const val TapsToUnlock = 5

        private const val Name = "dev_settings"
        private const val KeyUnlocked = "developer_unlocked"
        private const val KeyShowCrashOnLaunch = "show_crash_on_launch"
    }
}
