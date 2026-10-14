package com.personal.todo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * App theme: system light/dark, with the fixed palette of the design.
 *
 * The wallpaper based dynamic colors of phase 0 are deliberately switched off
 * now: the screens follow the screenshots the user supplied, and the blue accent
 * those screens are built around is not something the wallpaper should replace.
 */
@Composable
fun TodoPomodoroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTodoColors provides if (darkTheme) DarkTodoColors else LightTodoColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content
        )
    }
}
