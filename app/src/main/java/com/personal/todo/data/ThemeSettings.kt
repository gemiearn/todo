package com.personal.todo.data

import android.content.Context

/**
 * 主题三选：跟随系统 / 浅色 / 深色。
 *
 * 它只是看的方式，不是任务数据，所以存 SharedPreferences，不进数据库。默认「跟随系统」
 * 与旧行为一致——升级上来的用户主题不会突然变。
 */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Reads and writes the chosen [ThemeChoice]; a missing entry means 跟随系统. */
class ThemeSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    fun read(): ThemeChoice =
        ThemeChoice.entries.firstOrNull { it.name == prefs.getString(KeyChoice, null) }
            ?: ThemeChoice.SYSTEM

    fun write(choice: ThemeChoice) {
        prefs.edit().putString(KeyChoice, choice.name).apply()
    }

    private companion object {
        const val Name = "theme_settings"
        const val KeyChoice = "choice"
    }
}
