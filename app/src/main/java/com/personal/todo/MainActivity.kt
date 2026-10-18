package com.personal.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.personal.todo.data.AppearanceSettingsStore
import com.personal.todo.data.TodoContainer
import com.personal.todo.data.applyLauncherIcon
import com.personal.todo.ui.TodoApp
import com.personal.todo.ui.theme.TodoPomodoroTheme

/**
 * Single activity for the whole app.
 *
 * Phase 1 wires the task core: the repository is created once here and handed
 * to the Compose tree, which owns navigation, screens and view models.
 *
 * 外观（主题、色系、背景与桌面图标）也在这里读一次并交给 Compose 当状态：设置页改一次就
 * 立刻重画整棵树，同时落盘；桌面图标那项还多一步——去系统里换掉启用的 activity-alias。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = TodoContainer.repository(applicationContext)
        val focusRepository = TodoContainer.focusRepository(applicationContext)
        val habitRepository = TodoContainer.habitRepository(applicationContext)
        val appearance = AppearanceSettingsStore(applicationContext)
        // 桌面图标是系统层面的东西（启用哪个 activity-alias），所以要一个 Context；
        // 用 applicationContext，别把 Activity 攥在闭包里。
        val context = applicationContext
        setContent {
            var themeChoice by remember { mutableStateOf(appearance.readTheme()) }
            var colorStyle by remember { mutableStateOf(appearance.readColorStyle()) }
            var backgroundStyle by remember { mutableStateOf(appearance.readBackgroundStyle()) }
            var launcherIcon by remember { mutableStateOf(appearance.readLauncherIcon()) }
            TodoPomodoroTheme(
                themeChoice = themeChoice,
                colorStyle = colorStyle,
                backgroundStyle = backgroundStyle
            ) {
                TodoApp(
                    repository = repository,
                    focusRepository = focusRepository,
                    habitRepository = habitRepository,
                    themeChoice = themeChoice,
                    onThemeChoiceChange = {
                        themeChoice = it
                        appearance.writeTheme(it)
                    },
                    colorStyle = colorStyle,
                    onColorStyleChange = {
                        colorStyle = it
                        appearance.writeColorStyle(it)
                    },
                    backgroundStyle = backgroundStyle,
                    onBackgroundStyleChange = {
                        backgroundStyle = it
                        appearance.writeBackgroundStyle(it)
                    },
                    launcherIcon = launcherIcon,
                    onLauncherIconChange = {
                        launcherIcon = it
                        appearance.writeLauncherIcon(it)
                        applyLauncherIcon(context, it)
                    }
                )
            }
        }
    }
}
