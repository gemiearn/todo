package com.personal.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.viewmodel.TaskCollection

/**
 * The list of one view (inbox / a user list / completed / trash).
 *
 * The view is a query result - the same task row is never copied around, and
 * what a row offers depends on the view: open tasks can be completed or
 * deleted, completed tasks can be reopened, trashed tasks can be restored.
 */
@Composable
fun TaskCollectionContent(
    collection: TaskCollection,
    tasks: List<Task>,
    subtaskCounts: Map<Long, Int>,
    emptyText: String,
    onOpenTask: (Long) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onRestore: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val deleteLabel = stringResource(R.string.action_delete)
    val reopenLabel = stringResource(R.string.action_reopen)
    val restoreLabel = stringResource(R.string.action_restore)

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(tasks, key = { it.id }) { task ->
            val subtaskCount = subtaskCounts[task.id] ?: 0
            when (collection) {
                TaskCollection.Trash -> TaskRow(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = null,
                    onCheckedChange = null,
                    onClick = { onOpenTask(task.id) },
                    actionLabel = restoreLabel,
                    onAction = { onRestore(task) }
                )

                TaskCollection.Completed -> TaskRow(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = true,
                    onCheckedChange = { onToggleComplete(task) },
                    onClick = { onOpenTask(task.id) },
                    actionLabel = reopenLabel,
                    onAction = { onToggleComplete(task) }
                )

                else -> TaskRow(
                    task = task,
                    subtaskCount = subtaskCount,
                    checked = task.completedAt != null,
                    onCheckedChange = { onToggleComplete(task) },
                    onClick = { onOpenTask(task.id) },
                    actionLabel = deleteLabel,
                    onAction = { onDelete(task) }
                )
            }
            HorizontalDivider()
        }
    }
}
