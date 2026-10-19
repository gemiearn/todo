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
    // 把 Material 的页面底 / 顶栏底也挂到同一份页面底色上（含「背景风格」掺色）。
    // 否则每个页面 `Scaffold` 默认画的还是未掺色的 scheme 底：壳层与推入页颜色不一致，
    // 切页时看着就像亮了一层 / 暗了一层。卡片、弹层那几档（surfaceContainerLow…）不动。
    val scheme = remember(darkTheme, colors) {
        (if (darkTheme) DarkScheme else LightScheme).copy(
            background = colors.canvas,
            surface = colors.canvas,
            surfaceVariant = colors.canvas,
            surfaceContainer = colors.canvas
        )
    }
    CompositionLocalProvider(LocalTodoColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
