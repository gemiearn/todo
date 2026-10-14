package com.personal.todo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Date pattern is a formatting detail, the surrounding labels live in strings.xml. */
private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TimestampFormatter)

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.NORMAL -> R.string.priority_normal
        Priority.HIGH -> R.string.priority_high
        Priority.URGENT -> R.string.priority_urgent
    }
)

@Composable
fun eventLabel(type: TaskEventType): String = stringResource(
    when (type) {
        TaskEventType.CREATED -> R.string.event_created
        TaskEventType.COMPLETED -> R.string.event_completed
        TaskEventType.REOPENED -> R.string.event_reopened
        TaskEventType.DELETED -> R.string.event_deleted
        TaskEventType.RESTORED -> R.string.event_restored
    }
)

/**
 * One task row.
 *
 * [onCheckedChange] null means "no checkbox" (the trash lists rows that cannot
 * be toggled), [actionLabel]/[onAction] add the single secondary action of the
 * current view (delete / restore / reopen).
 */
@Composable
fun TaskRow(
    task: Task,
    subtaskCount: Int,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    onClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val priorityText = if (task.priority == Priority.NORMAL) null else priorityLabel(task.priority)
    val subtaskText = if (subtaskCount > 0) stringResource(R.string.task_subtask_count, subtaskCount) else null
    val supportText = listOfNotNull(priorityText, subtaskText).joinToString(" · ")

    val leading: (@Composable () -> Unit)? = onCheckedChange?.let { change ->
        { Checkbox(checked = checked == true, onCheckedChange = change) }
    }
    val trailing: (@Composable () -> Unit)? = if (actionLabel != null && onAction != null) {
        { TextButton(onClick = onAction) { Text(actionLabel) } }
    } else {
        null
    }
    val supporting: (@Composable () -> Unit)? = if (supportText.isEmpty()) {
        null
    } else {
        { Text(supportText, style = MaterialTheme.typography.bodySmall) }
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(task.title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = trailing
    )
}

/** Simple single line text dialog used for "new list" and "rename list". */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Confirmation shown before deleting a parent task that still has subtasks:
 * the spec (section 4.3) requires the user to be told that the subtasks go to
 * the trash together with their parent. It is only shown when subtasks exist -
 * a plain task keeps the plain "delete and undo" flow of section 4.2.
 */
@Composable
fun DeleteTaskConfirmDialog(
    taskTitle: String,
    subtaskCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_task_title)) },
        text = { Text(stringResource(R.string.dialog_delete_task_message, taskTitle, subtaskCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.dialog_delete_task_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun PriorityChips(
    selected: Priority,
    onSelect: (Priority) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Priority.entries.forEach { priority ->
            FilterChip(
                selected = priority == selected,
                onClick = { onSelect(priority) },
                label = { Text(priorityLabel(priority)) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun ListPicker(
    lists: List<TaskList>,
    selectedListId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = lists.firstOrNull { it.id == selectedListId }?.name.orEmpty()

    Column(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        onSelect(list.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
