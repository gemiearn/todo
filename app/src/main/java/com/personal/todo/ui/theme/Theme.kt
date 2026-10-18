package com.personal.todo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.personal.todo.data.BackgroundStyle
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.ThemeChoice

/**
 * App theme: the fixed palette of the design, in light or dark.
 *
 * [themeChoice] is what the 设置页 lets the user pick: 跟随系统 (the default),
 * 浅色 or 深色. [colorStyle] picks the four-quadrant colours (经典 / 马卡龙 /
 * 莫兰迪) - it changes the quadrant tints and the priority bars, never the app's
 * own accent. The wallpaper based dynamic colors of phase 0 are deliberately
 * switched off: the screens follow the screenshots the user supplied.
 */
@Composable
fun TodoPomodoroTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    colorStyle: ColorStyle = ColorStyle.CLASSIC,
    backgroundStyle: BackgroundStyle = BackgroundStyle.PLAIN,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val colors = remember(darkTheme, colorStyle, backgroundStyle) {
        (if (darkTheme) DarkTodoColors else LightTodoColors)
            .copy(quadrants = colorStyle.quadrantColors())
            .tintBackground(backgroundStyle, darkTheme)
    }
    CompositionLocalProvider(LocalTodoColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content
        )
    }
}
