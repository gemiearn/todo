package com.personal.todo.data

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.personal.todo.R

/**
 * 专注页的偏好：番茄钟的四个参数与背景音。默认值就是说明文档 5.1 里的
 * 25 分钟专注 / 5 分钟短休息 / 15 分钟长休息 / 每 4 个番茄长休息一次。
 *
 * These are screen preferences, not task data, so they live in SharedPreferences
 * rather than in the database: changing a number neither needs a migration nor
 * touches a single task.
 */
data class FocusSettings(
    val focusMinutes: Int = DefaultFocusMinutes,
    val shortBreakMinutes: Int = DefaultShortBreakMinutes,
    val longBreakMinutes: Int = DefaultLongBreakMinutes,
    val longBreakEvery: Int = DefaultLongBreakEvery,
    val sound: FocusSound = FocusSound.NONE
) {
    companion object {
        const val DefaultFocusMinutes = 25
        const val DefaultShortBreakMinutes = 5
        const val DefaultLongBreakMinutes = 15
        const val DefaultLongBreakEvery = 4

        /** The presets the 专注时长 card offers, minutes. */
        val FocusMinuteChoices = listOf(15, 20, 25, 30, 45, 60)

        /** The presets the two break cards offer, minutes. */
        val ShortBreakChoices = listOf(3, 5, 10)
        val LongBreakChoices = listOf(10, 15, 20, 30)

        /** How many pomodoros can sit between two long breaks. */
        val LongBreakEveryChoices = listOf(2, 3, 4, 5, 6)
    }
}

/** The ambient loops a focus session can play; [NONE] is silence. */
enum class FocusSound(@RawRes val soundRes: Int, @StringRes val labelRes: Int) {
    NONE(0, R.string.focus_sound_none),
    RAIN(R.raw.focus_rain, R.string.focus_sound_rain),
    WAVES(R.raw.focus_waves, R.string.focus_sound_waves),
    CAFE(R.raw.focus_cafe, R.string.focus_sound_cafe),
    FIRE(R.raw.focus_fire, R.string.focus_sound_fire)
}

/** Reads and writes [FocusSettings]; a missing entry means "the default". */
class FocusSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Name, Context.MODE_PRIVATE)

    fun read(): FocusSettings = FocusSettings(
        focusMinutes = prefs.getInt(KeyFocusMinutes, FocusSettings.DefaultFocusMinutes),
        shortBreakMinutes = prefs.getInt(KeyShortBreak, FocusSettings.DefaultShortBreakMinutes),
        longBreakMinutes = prefs.getInt(KeyLongBreak, FocusSettings.DefaultLongBreakMinutes),
        longBreakEvery = prefs.getInt(KeyLongBreakEvery, FocusSettings.DefaultLongBreakEvery),
        sound = FocusSound.entries.firstOrNull { it.name == prefs.getString(KeySound, null) }
            ?: FocusSound.NONE
    )

    fun write(settings: FocusSettings) {
        prefs.edit()
            .putInt(KeyFocusMinutes, settings.focusMinutes)
            .putInt(KeyShortBreak, settings.shortBreakMinutes)
            .putInt(KeyLongBreak, settings.longBreakMinutes)
            .putInt(KeyLongBreakEvery, settings.longBreakEvery)
            .putString(KeySound, settings.sound.name)
            .apply()
    }

    private companion object {
        const val Name = "focus_settings"
        const val KeyFocusMinutes = "focus_minutes"
        const val KeyShortBreak = "short_break_minutes"
        const val KeyLongBreak = "long_break_minutes"
        const val KeyLongBreakEvery = "long_break_every"
        const val KeySound = "sound"
    }
}
