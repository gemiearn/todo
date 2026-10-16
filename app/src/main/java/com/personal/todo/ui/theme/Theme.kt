package com.personal.todo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.personal.todo.data.ThemeChoice

/**
 * App theme: the fixed palette of the design, in light or dark.
 *
 * [themeChoice] is what the 设置页 lets the user pick: 跟随系统 (the default),
 * 浅色 or 深色. The wallpaper based dynamic colors of phase 0 are deliberately
 * switched off: the screens follow the screenshots the user supplied, and the
 * blue accent those screens are built around is not something the wallpaper
 * should replace.
 */
@Composable
fun TodoPomodoroTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    CompositionLocalProvider(
        LocalTodoColors provides if (darkTheme) DarkTodoColors else LightTodoColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content
        )
    }
}
