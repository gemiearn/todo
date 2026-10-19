package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.model.focusRecordLengthCapSeconds
import com.personal.todo.model.localDateOf
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 记录页的根测试钩子。 */
internal const val FocusRecordsTag = "focus-records"

/** 编辑弹层里「时长（分钟）」那个输入框的测试钩子。 */
internal const val RecordMinutesFieldTag = "record-minutes-field"

/**
 * 专注记录：逐条列出每次专注，可以换关联任务、改时长，也可以删掉。
 *
 * 记录只有这一处能改 / 删——统计页只画图表（用户 2026-10-19：记录该是一个能跳过去的
 * 地方，不是塞在专注页抽屉里的整块内容）。
 *
 * 时长按**整分钟**改（用户看不到秒级的秒数，按分钟想事情）；它至少 1 分钟，最多 24 小时。
 * 关联任务只有两种改法：换成另一个**还没做完**的任务，或者整个取消关联——这里不做
 * 「粘」一个已完成任务，选择器口径与专注页一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRecordsScreen(
    sessions: Flow<List<FocusSession>>,
    openTasks: List<Task>,
    lists: List<TaskList>,
    taskTags: Map<Long, List<Tag>>,
    zone: ZoneId,
    today: LocalDate,
    onUpdate: (
        sessionId: Long,
        taskId: Long?,
        title: String?,
        listName: String?,
        tagNames: List<String>,
        tagIds: List<Long>,
        seconds: Int
    ) -> Unit,
    onDelete: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val all by sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<FocusSession?>(null) }
    var deleting by remember { mutableStateOf<FocusSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_history_title)) },
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
        modifier = modifier
    ) { innerPadding ->
        if (all.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@Scaffold
        }

        // sessions 本来就是「新的在前」，groupBy 保持遇到顺序，所以日期与同一天里的记录
        // 都自然是从近到远。
        val byDay = remember(all, zone) { all.groupBy { localDateOf(it.startedAt, zone) } }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(FocusRecordsTag),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            byDay.forEach { (date, ofDay) ->
                item(key = "day-${date.toEpochDay()}") {
                    Text(
                        text = calendarDateText(date, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalTodoColors.current.dateMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                    )
                }
                items(ofDay, key = { "record-${it.id}" }) { session ->
                    RecordRow(
                        session = session,
                        zone = zone,
                        onEdit = { editing = session },
                        onDelete = { deleting = session }
                    )
                }
            }
        }
    }

    editing?.let { session ->
        // 防重叠（用户 2026-10-19 拍板）：延长不许越过**紧随其后那条记录**的起点。上限由
        // 仓库那一层最终把关（`FocusRepository.updateSession`），这里只是提前把「为什么改不长」
        // 说给用户听，并挡住越界的输入。
        val nextStart = remember(all, session) {
            all.filter { it.id != session.id && it.startedAt > session.startedAt }
                .minOfOrNull { it.startedAt }
        }
        val capMinutes = remember(nextStart, session) {
            focusRecordLengthCapSeconds(session.startedAt, nextStart, session.actualFocusSeconds) / 60
        }.coerceAtLeast(1)
        RecordEditSheet(
            session = session,
            maxMinutes = capMinutes,
            limitHint = nextStart?.let {
                stringResource(
                    R.string.record_length_limit,
                    nextStartLabel(it, session.startedAt, zone),
                    capMinutes
                )
            },
            openTasks = openTasks,
            lists = lists,
            taskTags = taskTags,
            onDismiss = { editing = null },
            onConfirm = { target, minutes ->
                onUpdate(
                    session.id,
                    target.taskId,
                    target.title,
                    target.listName,
                    target.tagNames,
                    target.tagIds,
                    minutes * 60
                )
                editing = null
            }
        )
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.record_delete_title)) },
            text = { Text(stringResource(R.string.record_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session.id)
                    deleting = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * 一条逐条记录：任务名、时长，下面一行是开始时刻与结果，最右一个 ⋮ 是「修改 / 删除」。
 *
 * 刻意**不写计时模式**（正计时 / 倒计时 / 番茄钟）：用户说记录不必区分是哪种计时，
 * 三种模式都是同一件事的投入，写上去只是噪音。
 */
@Composable
private fun RecordRow(
    session: FocusSession,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTodoColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.taskTitleSnapshot ?: stringResource(R.string.focus_task_none_label),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = durationText(session.actualFocusSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (session.status == FocusStatus.COMPLETED) colors.success else colors.dateOverdue
                )
            }
            Text(
                text = sessionSubtitle(session, zone),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.record_more),
                    tint = colors.dateMuted
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * 开始到结束那一刻（`09:00–09:10`）与结果；不带计时模式。
 *
 * 结束时刻用 `endedAt`；跨过午夜的那种（22:30–次日 01:00）在结束那头写「次日 HH:mm」，
 * 免得两组数字看起来像同一天的。
 */
@Composable
private fun sessionSubtitle(session: FocusSession, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    val startClock = "%02d:%02d".format(start.hour, start.minute)
    val endClock = "%02d:%02d".format(end.hour, end.minute)
    val span = if (localDateOf(session.endedAt, zone) == localDateOf(session.startedAt, zone)) {
        "$startClock–$endClock"
    } else {
        "$startClock–次日 $endClock"
    }
    val status = stringResource(
        if (session.status == FocusStatus.COMPLETED) {
            R.string.focus_status_completed
        } else {
            R.string.focus_status_interrupted
        }
    )
    return stringResource(R.string.focus_session_subtitle, span, status)
}

/** 「后面那条记录」的时刻：同一天只写 `HH:mm`，跨天就带上月日。 */
private fun nextStartLabel(nextStart: Long, sessionStart: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(nextStart).atZone(zone)
    val clock = "%02d:%02d".format(at.hour, at.minute)
    return if (localDateOf(nextStart, zone) == localDateOf(sessionStart, zone)) {
        clock
    } else {
        "${at.monthValue}-${at.dayOfMonth} $clock"
    }
}

/** 弹层里「关联任务」这一项当前的选择：保留原来的、换成新挑的、或者清掉。 */
private sealed interface TaskChoice {
    object Keep : TaskChoice
    data class Replace(val task: Task) : TaskChoice
    object Clear : TaskChoice
}

/** 编辑弹层确认时交给上层的那一份「这条记录改成什么」。 */
private data class RecordTarget(
    val taskId: Long?,
    val title: String?,
    val listName: String?,
    val tagNames: List<String>,
    val tagIds: List<Long>
)

/**
 * 修改一条记录的弹层：换关联任务（或取消关联）、改时长。
 *
 * 只改时长时，任务、清单名与标签那几份快照**原样保留**（不被顺手改写）；换任务后，它们
 * 才跟着换成那条任务现在的样子——与开始一次专注时记快照的口径一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditSheet(
    session: FocusSession,
    /** 时长输入框允许的上限（分钟）：到紧随其后那条记录开始的间距（防重叠）。 */
    maxMinutes: Int,
    /** 上限真的生效时给的说明（后面那条记录是什么时候）；没有下一条时为 null。 */
    limitHint: String?,
    openTasks: List<Task>,
    lists: List<TaskList>,
    taskTags: Map<Long, List<Tag>>,
    onDismiss: () -> Unit,
    onConfirm: (RecordTarget, Int) -> Unit
) {
    val colors = LocalTodoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choice by remember { mutableStateOf<TaskChoice>(TaskChoice.Keep) }
    var minutesText by remember {
        mutableStateOf(((session.actualFocusSeconds + 30) / 60).coerceAtLeast(1).toString())
    }
    var pickerOpen by remember { mutableStateOf(false) }

    val currentTitle = when (val value = choice) {
        is TaskChoice.Replace -> value.task.title
        TaskChoice.Clear -> null
        TaskChoice.Keep -> session.taskTitleSnapshot
    }
    val minutes = minutesText.toIntOrNull()
    val minutesValid = minutes != null && minutes in 1..maxMinutes

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.record_edit_title),
                style = MaterialTheme.typography.titleLarge
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.record_task_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTitle ?: stringResource(R.string.focus_task_none_label),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickerOpen = true }) {
                        Text(stringResource(R.string.record_pick_task))
                    }
                    if (choice != TaskChoice.Clear && currentTitle != null) {
                        TextButton(onClick = { choice = TaskChoice.Clear }) {
                            Text(stringResource(R.string.record_unlink_task))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = minutesText,
                onValueChange = { raw -> minutesText = raw.filter { it.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.record_minutes_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag(RecordMinutesFieldTag)
            )
            if (limitHint != null) {
                Text(
                    text = limitHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }

            Button(
                onClick = { if (minutes != null) onConfirm(resolveTarget(session, choice, lists, taskTags), minutes) },
                enabled = minutesValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickerOpen) {
        TaskPickerDialog(
            tasks = openTasks,
            lists = lists,
            onPick = { task ->
                choice = TaskChoice.Replace(task)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

/** 把弹层里那份选择落成一份快照：保留 = 原样搬回，换任务 = 按那条任务现在解析，清掉 = 全空。 */
private fun resolveTarget(
    session: FocusSession,
    choice: TaskChoice,
    lists: List<TaskList>,
    taskTags: Map<Long, List<Tag>>
): RecordTarget = when (choice) {
    TaskChoice.Keep -> RecordTarget(
        taskId = session.taskId,
        title = session.taskTitleSnapshot,
        listName = session.listNameSnapshot,
        tagNames = FocusTagSnapshot.names(session.tagNamesSnapshot),
        tagIds = FocusTagSnapshot.ids(session.tagIdsSnapshot)
    )

    is TaskChoice.Replace -> {
        val tags = taskTags[choice.task.id].orEmpty()
        RecordTarget(
            taskId = choice.task.id,
            title = choice.task.title,
            listName = lists.firstOrNull { it.id == choice.task.listId }?.name,
            tagNames = tags.map { it.name },
            tagIds = tags.map { it.id }
        )
    }

    TaskChoice.Clear -> RecordTarget(null, null, null, emptyList(), emptyList())
}
