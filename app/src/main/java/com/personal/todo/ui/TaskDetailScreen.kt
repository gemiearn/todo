package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.TaskSeriesTotals
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import com.personal.todo.data.db.TaskFocusTotals
import com.personal.todo.data.db.TaskList
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskDetailViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Task detail: status, subtasks and the event history.
 *
 * Completing/reopening a parent never touches its subtasks - every subtask has
 * its own checkbox. Only a top level task can have subtasks (one level only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    lists: List<TaskList>,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val subtaskAddFailures by viewModel.subtaskAddFailures.collectAsStateWithLifecycle()
    val focusTotals by viewModel.focusTotals.collectAsStateWithLifecycle()
    val seriesTotals by viewModel.seriesTotals.collectAsStateWithLifecycle()
    // 操作历史默认收起：整页事件流会把页面撑得很长，先说「有几条」，想看再点开。
    var eventsExpanded by rememberSaveable { mutableStateOf(false) }
    val nowMillis = rememberNowMillis()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.message_task_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val deleteLabel = stringResource(R.string.action_delete)
    val addFailedMessage = stringResource(R.string.message_add_subtask_failed)

    // Deleting a parent that still has subtasks asks once before: the subtasks
    // go to the trash together with it (spec section 4.3). A plain task is
    // deleted straight away and can be undone from the snackbar (section 4.2).
    var confirmDelete by remember { mutableStateOf(false) }
    val deleteWithUndo: () -> Unit = {
        viewModel.delete()
        scope.launch {
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore()
        }
    }

    // The add row is hidden for deleted tasks, but a refused add can still be
    // triggered from elsewhere - report it instead of letting it crash.
    // 用「失败次数」当 key：连点两次都失败时值会变，第二次也提示得到（原来的 Boolean
    // 在提示还没消失时不会变，第二次失败完全静默）。
    LaunchedEffect(subtaskAddFailures) {
        if (subtaskAddFailures > 0) {
            snackbarHostState.showSnackbar(addFailedMessage)
            viewModel.consumeSubtaskAddFailure()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val current = task
        if (current == null) {
            // First frame, before the task has been loaded.
            Box(Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                TaskHeader(
                    task = current,
                    lists = lists,
                    tags = tags,
                    nowMillis = nowMillis,
                    zone = zone,
                    onComplete = { viewModel.complete() },
                    onReopen = { viewModel.reopen() },
                    onRestore = { viewModel.restore() },
                    onEdit = onEdit,
                    onDelete = {
                        if (subtasks.isNotEmpty()) confirmDelete = true else deleteWithUndo()
                    }
                )
            }

            // 专注小结：这条任务一共专注了多久、几次、其中几个番茄（数据来自另一张表，
            // 由 view model 单独订阅）。重复任务的规则源还多一行整条系列的合计。
            item { FocusSummaryCard(totals = focusTotals, series = seriesTotals) }

            if (current.parentTaskId == null) {
                item { SectionTitle(stringResource(R.string.task_subtasks_title)) }
                if (subtasks.isEmpty()) {
                    item { InfoText(stringResource(R.string.empty_subtasks)) }
                }
                items(subtasks, key = { it.id }) { subtask ->
                    TaskCard(
                        task = subtask,
                        subtaskCount = 0,
                        checked = subtask.completedAt != null,
                        onCheckedChange = { viewModel.toggleSubtask(subtask) },
                        onClick = { onOpenTask(subtask.id) },
                        nowMillis = nowMillis,
                        zone = zone,
                        actionLabel = deleteLabel,
                        onAction = {
                            viewModel.deleteSubtask(subtask.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel)
                                if (result == SnackbarResult.ActionPerformed) viewModel.restoreSubtask(subtask.id)
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                    )
                }
                // A deleted parent must not offer "add subtask": the repository
                // refuses it (spec: no children under a deleted task).
                if (current.deletedAt == null) {
                    item { AddSubtaskRow(onAdd = viewModel::addSubtask) }
                }
            }

            // 操作历史：默认只留一行「操作历史（N 条）」，点一下才展开事件流。展开状态存在
            // rememberSaveable 里，转屏回来还在。
            item {
                if (events.isEmpty()) {
                    SectionTitle(stringResource(R.string.task_events_title))
                    InfoText(stringResource(R.string.empty_events))
                } else {
                    CollapsibleSectionTitle(
                        title = stringResource(R.string.task_events_title),
                        count = events.size,
                        expanded = eventsExpanded,
                        onToggle = { eventsExpanded = !eventsExpanded }
                    )
                }
            }
            if (eventsExpanded) {
                items(events, key = { it.id }) { event ->
                    EventRow(event)
                    HorizontalDivider()
                }
            }
        }
    }

    val deleteTarget = task
    if (confirmDelete && deleteTarget != null) {
        DeleteTaskConfirmDialog(
            taskTitle = deleteTarget.title,
            subtaskCount = subtasks.size,
            onConfirm = {
                confirmDelete = false
                deleteWithUndo()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun TaskHeader(
    task: Task,
    lists: List<TaskList>,
    tags: List<Tag>,
    nowMillis: Long,
    zone: ZoneId,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onRestore: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(task.title, style = MaterialTheme.typography.headlineSmall)
        task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(8.dp))
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TagChipRow(tags = tags)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (task.completedAt != null) {
                stringResource(R.string.task_status_completed)
            } else {
                stringResource(R.string.task_status_open)
            },
            style = MaterialTheme.typography.labelLarge
        )
        task.completedAt?.let { completedAt ->
            Text(
                text = stringResource(R.string.task_completed_at, formatTimestamp(completedAt)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = stringResource(R.string.task_priority_value, priorityLabel(task.priority)),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                R.string.task_list_value,
                lists.firstOrNull { it.id == task.listId }?.name.orEmpty()
            ),
            style = MaterialTheme.typography.bodySmall
        )
        // The dates of spec section 4.1, as the editor set them. Nothing here is
        // derived: an overdue task keeps the day the user chose.
        task.plannedDate?.let { date ->
            Text(
                text = stringResource(R.string.task_planned_date_value, calendarDateText(date, today)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        task.plannedTime?.let { time ->
            Text(
                text = stringResource(R.string.task_planned_time_value, detailTimeText(time)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        task.dueDate?.let { date ->
            Text(
                text = stringResource(R.string.task_due_date_value, calendarDateText(date, today)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        task.dueTime?.let { time ->
            Text(
                text = stringResource(R.string.task_due_time_value, detailTimeText(time)),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (task.deletedAt != null) {
                Button(onClick = onRestore) { Text(stringResource(R.string.action_restore)) }
            } else if (task.completedAt == null) {
                Button(onClick = onComplete) { Text(stringResource(R.string.action_complete)) }
            } else {
                Button(onClick = onReopen) { Text(stringResource(R.string.action_reopen)) }
            }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            if (task.deletedAt == null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** The due time as `HH:mm`, the same shape the editor stores. */
private fun detailTimeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 可折叠的分节标题：左边标题、右边「（N 条）」加一个上下箭头，整行可点。
 * 操作历史用它——默认收起，想看再点开。
 */
@Composable
private fun CollapsibleSectionTitle(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.task_events_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = null,
            tint = colors.dateMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 这条任务的「专注小结」：累计时长、专注次数、其中的番茄数。
 *
 * 一次都没专注过时只说一句话，不摆一排 0——那比空着更让人以为统计坏了。
 *
 * [series] 只有重复任务的规则源才有（实例页是 null）：那一行说的是**整条系列**，不是眼前
 * 这一条。系列还没完成过也没专注过时不显示，免得跟上面那句「还没有专注记录」重复。
 */
@Composable
private fun FocusSummaryCard(totals: TaskFocusTotals, series: TaskSeriesTotals?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LocalTodoColors.current.groupHeader)
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.task_focus_summary_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        if (totals.sessionCount == 0) {
            Text(
                text = stringResource(R.string.task_focus_summary_empty),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTodoColors.current.dateMuted
            )
        } else {
            Text(
                text = stringResource(
                    R.string.task_focus_summary_total,
                    durationText(totals.totalSeconds)
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.task_focus_summary_detail,
                    totals.sessionCount,
                    totals.pomodoroCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTodoColors.current.dateMuted
            )
        }

        if (series != null && (series.completedCount > 0 || series.focusSeconds > 0)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.task_focus_series,
                    series.completedCount,
                    durationText(series.focusSeconds)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = LocalTodoColors.current.dateMuted
            )
        }
    }
}

@Composable
private fun EventRow(event: TaskEvent) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "${eventLabel(event.eventType)} · ${formatTimestamp(event.eventAt)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(event.titleSnapshot, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddSubtaskRow(onAdd: (String) -> Unit) {
    // 这一行住在 LazyColumn 的 item 里，滚出视口就会被销毁重画：用 rememberSaveable，
    // 滚下去再滚回来时打到一半的字还在。
    var value by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text(stringResource(R.string.task_add_subtask_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                onAdd(value)
                value = ""
            },
            enabled = value.isNotBlank()
        ) {
            Text(stringResource(R.string.action_add))
        }
    }
}
