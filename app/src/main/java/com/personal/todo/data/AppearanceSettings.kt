package com.personal.todo.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

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

/**
 * 背景风格：给整页的底色掺一层很淡的「纸色」，让几种风格一眼能分开，又不影响卡片和文字
 * 的对比度。**只动底色**（页面底 / 分组底 / 卡片底），不碰强调色、不碰四象限色。
 *
 * 默认 [PLAIN]＝现在的灰底，什么也不掺。以后要做真正的渐变或背景图，只要在这里加档、
 * 在主题层铺一层画刷即可，不必再动各个页面。
 */
enum class BackgroundStyle { PLAIN, WARM, MINT, LILAC, DUSK }

/**
 * 应用图标六选。
 *
 * 桌面上的那个入口不由 MainActivity 自己提供，而是 [AndroidManifest.xml] 里的 6 个
 * `<activity-alias>`：每个 alias 挂一套自己的 `android:icon`，同一时刻只有一个是
 * enabled 的。切换就是 [applyLauncherIcon] —— 启用自己、禁用其余 5 个。
 *
 * [aliasName] 必须和清单里的 alias 名一字不差；这是两张表之间唯一的联系，改名时两边
 * 一起改。顺序就是设置页里的顺序（A / 默认 / D / E / F / G），默认那版是 [RING]。
 */
enum class LauncherIcon(val aliasName: String) {
    /** A·番茄。 */
    TOMATO("MainActivityTomato"),

    /** B·计时环（安装后默认那版）。 */
    RING("MainActivityRing"),

    /** D·时钟面。 */
    CLOCK("MainActivityClock"),

    /** E·四色方块。 */
    QUAD("MainActivityQuad"),

    /** F·青柠渐变 + 大勾。 */
    LIME("MainActivityLime"),

    /** G·暖阳渐变 + 大勾。 */
    SUN("MainActivitySun")
}

/**
 * 把桌面图标换成 [icon]：启用它那个 alias、禁用其余 5 个。
 *
 * `DONT_KILL_APP`：切图标不该杀掉正在跑的 App，代价是系统桌面会把这个入口重建一次，
 * 看上去像刚装了一遍。幂等，重复调用没有副作用。
 *
 * 清单里 [LauncherIcon.RING] 是 `android:enabled="true"`，所以首装、清数据之后什么都不
 * 做也有一版图标——偏好里存的只是「用户上次选了哪版」，两边天然对得上。
 */
fun applyLauncherIcon(context: Context, icon: LauncherIcon) {
    val packageManager = context.packageManager
    val packageName = context.packageName
    LauncherIcon.entries.forEach { candidate ->
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "$packageName.${candidate.aliasName}"),
            if (candidate == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }
}

/**
 * 外观偏好：主题、色系、背景风格与桌面图标。前三者只是「看的方式」，最后一个也只是「桌面
 * 入口用哪套图」，都存 SharedPreferences，不进数据库。
 */
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

    fun readBackgroundStyle(): BackgroundStyle =
        BackgroundStyle.entries.firstOrNull { it.name == prefs.getString(KeyBackgroundStyle, null) }
            ?: BackgroundStyle.PLAIN

    fun writeBackgroundStyle(style: BackgroundStyle) {
        prefs.edit().putString(KeyBackgroundStyle, style.name).apply()
    }

    /** 上次选的那版桌面图标；没选过就是清单里默认启用的 [LauncherIcon.RING]。 */
    fun readLauncherIcon(): LauncherIcon =
        LauncherIcon.entries.firstOrNull { it.name == prefs.getString(KeyLauncherIcon, null) }
            ?: LauncherIcon.RING

    fun writeLauncherIcon(icon: LauncherIcon) {
        prefs.edit().putString(KeyLauncherIcon, icon.name).apply()
    }

    private companion object {
        const val Name = "appearance_settings"
        const val KeyTheme = "theme"
        const val KeyColorStyle = "color_style"
        const val KeyBackgroundStyle = "background_style"
        const val KeyLauncherIcon = "launcher_icon"
    }
}
