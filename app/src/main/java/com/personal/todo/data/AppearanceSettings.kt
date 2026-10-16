package com.personal.todo.data

import android.content.Context

/** 主题三选：跟随系统 / 浅色 / 深色。默认「跟随系统」，与升级前的行为一致。 */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * 色系三选：经典 / 马卡龙 / 莫兰迪。
 *
 * 它决定**四象限的四个颜色**，进而决定四象限卡片的底色、任务卡左边的色条、日历上的
 * 任务点，以及编辑器里的优先级结果点。全局强调色（FAB、选中态）不跟着变——色系换的
 * 是「象限的颜色」，不是应用的品牌色。
 */
enum class ColorStyle { CLASSIC, MACARON, MORANDI }

/** 外观偏好：主题与色系。两者都只是看的方式，存 SharedPreferences，不进数据库。 */
class AppearanceSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    fun readTheme(): ThemeChoice =
        ThemeChoice.entries.firstOrNull { it.name == prefs.getString(KeyTheme, null) }
            ?: ThemeChoice.SYSTEM

    fun writeTheme(choice: ThemeChoice) {
        prefs.edit().putString(KeyTheme, choice.name).apply()
    }

    fun readColorStyle(): ColorStyle =
        ColorStyle.entries.firstOrNull { it.name == prefs.getString(KeyColorStyle, null) }
            ?: ColorStyle.CLASSIC

    fun writeColorStyle(style: ColorStyle) {
        prefs.edit().putString(KeyColorStyle, style.name).apply()
    }

    private companion object {
        const val Name = "appearance_settings"
        const val KeyTheme = "theme"
        const val KeyColorStyle = "color_style"
    }
}
