package com.personal.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.CrashLog
import com.personal.todo.ui.theme.LocalTodoColors

/**
 * Shows the last crash the app recorded, so it can be read and copied on the
 * phone itself - the phone has no development tools attached to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.read(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = text
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.crash_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.crash_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTodoColors.current.dateMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(text = current, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = {
                    CrashLog.clear(context)
                    text = null
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.crash_log_clear))
            }
        }
    }
}
