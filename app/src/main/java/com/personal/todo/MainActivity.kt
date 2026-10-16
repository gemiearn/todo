package com.personal.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.personal.todo.data.ThemeSettingsStore
import com.personal.todo.data.TodoContainer
import com.personal.todo.ui.TodoApp
import com.personal.todo.ui.theme.TodoPomodoroTheme

/**
 * Single activity for the whole app.
 *
 * Phase 1 wires the task core: the repository is created once here and handed
 * to the Compose tree, which owns navigation, screens and view models.
 *
 * 主题也在这里读一次并交给 Compose 当状态：设置页改一次就立刻重画整棵树，同时落盘。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = TodoContainer.repository(applicationContext)
        val focusRepository = TodoContainer.focusRepository(applicationContext)
        val themeStore = ThemeSettingsStore(applicationContext)
        setContent {
            var themeChoice by remember { mutableStateOf(themeStore.read()) }
            TodoPomodoroTheme(themeChoice = themeChoice) {
                TodoApp(
                    repository = repository,
                    focusRepository = focusRepository,
                    themeChoice = themeChoice,
                    onThemeChoiceChange = {
                        themeChoice = it
                        themeStore.write(it)
                    }
                )
            }
        }
    }
}
