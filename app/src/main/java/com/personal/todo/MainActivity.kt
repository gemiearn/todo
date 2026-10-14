package com.personal.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.todo.data.TodoContainer
import com.personal.todo.ui.TodoApp
import com.personal.todo.ui.theme.TodoPomodoroTheme

/**
 * Single activity for the whole app.
 *
 * Phase 1 wires the task core: the repository is created once here and handed
 * to the Compose tree, which owns navigation, screens and view models.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = TodoContainer.repository(applicationContext)
        val focusRepository = TodoContainer.focusRepository(applicationContext)
        setContent {
            TodoPomodoroTheme {
                TodoApp(repository = repository, focusRepository = focusRepository)
            }
        }
    }
}
