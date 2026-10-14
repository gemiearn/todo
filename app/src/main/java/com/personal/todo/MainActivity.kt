package com.personal.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.personal.todo.ui.theme.TodoPomodoroTheme

/**
 * Single activity for the whole app.
 *
 * Phase 0 only wires the Compose entry point so the project can be built and
 * installed. Navigation, task management and the pomodoro timer arrive in
 * later phases.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoPomodoroTheme {
                SkeletonScreen()
            }
        }
    }
}

@Composable
private fun SkeletonScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.skeleton_placeholder),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
