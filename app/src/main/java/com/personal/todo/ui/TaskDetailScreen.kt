package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 20.dp)
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
    val colors = LocalTodoColors.current
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val accent = priorityColor(task.priority)
    val shape = RoundedCornerShape(22.dp)
    val deleted = task.deletedAt != null
    val completed = task.completedAt != null
    val statusText = when {
        deleted -> stringResource(R.string.nav_trash)
        completed -> stringResource(R.string.task_status_completed)
        else -> stringResource(R.string.task_status_open)
    }
    val statusColor = when {
        deleted -> colors.dateMuted
        completed -> colors.success
        else -> accent
    }
    val listName = lists.firstOrNull { it.id == task.listId }?.name.orEmpty()
    val planText = task.plannedDate?.let { date ->
        buildString {
            append(calendarDateText(date, today))
            task.plannedTime?.let { append(" ").append(detailTimeText(it)) }
        }
    }.orEmpty()
    val dueText = task.dueDate?.let { date ->
        buildString {
            append(calendarDateText(date, today))
            task.dueTime?.let { append(" ").append(detailTimeText(it)) }
        }
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.10f),
                spotColor = accent.copy(alpha = 0.12f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.card, lerp(colors.card, accent, 0.055f))
                )
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailStatusChip(text = statusText, color = statusColor)
            Spacer(Modifier.weight(1f))
            PriorityPill(label = priorityLabel(task.priority), color = accent)
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (task.repeatRule != null || task.recurrenceSourceId != null) {
            Spacer(Modifier.height(7.dp))
            RepeatPill()
        }

        task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.groupHeader.copy(alpha = 0.42f))
                    .padding(12.dp)
            )
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagChipRow(tags = tags)
        }

        Spacer(Modifier.height(14.dp))
        // 两列属性卡：清单 / 优先级、计划 / 截止。时间并进日期那一行，不再一列
        // 「计划日期」「计划时间」「截止日期」「截止时间」四行把卡片撑长。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailProperty(
                icon = "📁",
                label = stringResource(R.string.task_property_list),
                value = listName,
                modifier = Modifier.weight(1f)
            )
            DetailProperty(
                icon = "🎯",
                label = stringResource(R.string.task_property_priority),
                value = priorityLabel(task.priority),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailProperty(
                icon = "📅",
                label = stringResource(R.string.task_property_planned),
                value = planText,
                modifier = Modifier.weight(1f)
            )
            DetailProperty(
                icon = "⏳",
                label = stringResource(R.string.task_property_due),
                value = dueText,
                modifier = Modifier.weight(1f)
            )
        }
        task.completedAt?.let { completedAt ->
            Spacer(Modifier.height(10.dp))
            DetailMetaRow(
                icon = "✅",
                text = stringResource(R.string.task_completed_at, formatTimestamp(completedAt))
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val primaryColor = if (completed) colors.success else accent
            Button(
                onClick = when {
                    deleted -> onRestore
                    completed -> onReopen
                    else -> onComplete
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = onPriorityColor(primaryColor)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        deleted -> stringResource(R.string.action_restore)
                        completed -> stringResource(R.string.action_reopen)
                        else -> stringResource(R.string.action_complete)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.action_edit))
            }
            if (!deleted) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.dateOverdue)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

/** 详情页状态胶囊：完成 / 未完成 / 回收站。 */
@Composable
private fun DetailStatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/** 标题右上角的优先级小签：一个色点 + 文字，不再单独占一个 48dp 的序号方块。 */
@Composable
private fun PriorityPill(label: String, color: Color) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.groupHeader.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 「重复任务」小签：符号 + 文字，详情页比列表多这一句说明。 */
@Composable
private fun RepeatPill() {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.groupHeader.copy(alpha = 0.55f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepeatMark()
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.task_repeat_mark),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

/** 两列属性格里的一格：小标题 + 值；值太长就省略，不把卡片撑高。 */
@Composable
private fun DetailProperty(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val shown = if (value.isBlank()) {
        stringResource(R.string.task_property_unset)
    } else {
        value
    }
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.groupHeader.copy(alpha = 0.38f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = shown,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (value.isBlank()) colors.dateMuted else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 详情页一行元信息：小图标 + 一句「字段：值」。 */
@Composable
private fun DetailMetaRow(icon: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text(text = icon, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalTodoColors.current.accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.groupHeader.copy(alpha = 0.42f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.task_events_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
        Spacer(Modifier.width(4.dp))
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
    val colors = LocalTodoColors.current
    val accent = colors.focusAccent
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.08f),
                spotColor = accent.copy(alpha = 0.10f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.card, lerp(colors.card, accent, 0.05f))
                )
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⏱️", fontSize = 15.sp)
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.task_focus_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        if (totals.sessionCount == 0) {
            Text(
                text = stringResource(R.string.task_focus_summary_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.task_focus_summary_total_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.dateMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = durationText(totals.totalSeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                Text(
                    text = stringResource(
                        R.string.task_focus_summary_detail,
                        totals.sessionCount,
                        totals.pomodoroCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
            }
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
                color = colors.dateMuted
            )
        }
    }
}

@Composable
private fun EventRow(event: TaskEvent) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.45f))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${eventLabel(event.eventType)} · ${formatTimestamp(event.eventAt)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = event.titleSnapshot,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalTodoColors.current.groupHeader.copy(alpha = 0.42f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text(stringResource(R.string.task_add_subtask_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
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
