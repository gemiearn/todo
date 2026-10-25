package com.personal.todo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** 记录页的根测试钩子。 */
internal const val FocusRecordsTag = "focus-records"

/** 编辑弹层里「时长（分钟）」那个输入框的测试钩子。 */
internal const val RecordMinutesFieldTag = "record-minutes-field"

/** 补记弹层里「时长（分钟）」那个输入框的测试钩子。 */
internal const val RecordAddMinutesFieldTag = "record-add-minutes-field"

/** 记录页右上角「补记专注」按钮的测试钩子。 */
internal const val RecordAddTag = "record-add"

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onAdd: (
        startedAt: Long,
        seconds: Int,
        taskId: Long?,
        title: String?,
        listName: String?,
        tagNames: List<String>,
        tagIds: List<Long>,
        asPomodoro: Boolean
    ) -> Unit,
    onDelete: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val all by sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<FocusSession?>(null) }
    var deleting by remember { mutableStateOf<FocusSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf("") }

    // 只列记录里真正出现过的标签：筛选器是给「已有的这些记录」用的，不是标签管理页。
    val tagsInRecords = remember(all) {
        all.flatMap { FocusTagSnapshot.names(it.tagNamesSnapshot) }
            .distinct()
            .sorted()
    }
    val visible = remember(all, tagFilter) {
        if (tagFilter.isEmpty()) {
            all
        } else {
            all.filter { FocusTagSnapshot.matches(it.tagNamesSnapshot, tagFilter) }
        }
    }
    // sessions 本来就是「新的在前」，groupBy 保持遇到顺序，所以日期与同一天里的记录
    // 都自然是从近到远。
    val byDay = remember(visible, zone) {
        visible.groupBy { localDateOf(it.startedAt, zone) }
    }

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
                },
                actions = {
                    IconButton(
                        onClick = { adding = true },
                        modifier = Modifier.testTag(RecordAddTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.record_add)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (all.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalTodoColors.current.dateMuted
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { adding = true }) {
                    Text(stringResource(R.string.record_add))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(FocusRecordsTag),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tagsInRecords.isNotEmpty()) {
                item(key = "tag-filter") {
                    RecordTagFilterRow(
                        tags = tagsInRecords,
                        selected = tagFilter,
                        onSelect = { tagFilter = it }
                    )
                }
            }
            // 饼图不在这页了（用户 2026-10-25）：它连同「按任务」的排行和那一天的记录一起
            // 成了独立的一页「专注占比」。这一页只做它擅长的两件事——逐条记录，以及改 / 补 / 删。
            if (visible.isEmpty()) {
                item(key = "filter-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.record_tag_filter_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalTodoColors.current.dateMuted
                        )
                        TextButton(onClick = { tagFilter = "" }) {
                            Text(stringResource(R.string.record_tag_filter_clear))
                        }
                    }
                }
            } else {
                byDay.forEach { (date, ofDay) ->
                    stickyHeader(key = "day-${date.toEpochDay()}") {
                        RecordDayHeader(date = date, records = ofDay, today = today)
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
    }

    if (adding) {
        RecordAddSheet(
            today = today,
            zone = zone,
            openTasks = openTasks,
            lists = lists,
            taskTags = taskTags,
            onDismiss = { adding = false },
            onConfirm = { startedAt, seconds, taskId, title, listName, tagNames, tagIds, asPomodoro ->
                onAdd(
                    startedAt,
                    seconds,
                    taskId,
                    title,
                    listName,
                    tagNames,
                    tagIds,
                    asPomodoro
                )
                adding = false
            }
        )
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

/** 日期分组头：日期 + 这一天的段数与总时长；滚动时吸顶，长列表不会忘记自己在哪一天。 */
@Composable
private fun RecordDayHeader(date: LocalDate, records: List<FocusSession>, today: LocalDate) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = calendarDateText(date, today),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(
                R.string.record_day_summary,
                records.size,
                durationText(records.sumOf { it.actualFocusSeconds })
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

/** 记录里的标签筛选：全部 + 记录中出现过的标签；标签多了会换行，不横向藏起来。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordTagFilterRow(tags: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RecordFilterPill(
            label = stringResource(R.string.record_tag_filter_all),
            selected = selected.isEmpty(),
            onClick = { onSelect("") }
        )
        tags.forEach { tag ->
            RecordFilterPill(
                label = "#$tag",
                selected = selected == tag,
                onClick = { onSelect(tag) }
            )
        }
    }
}

@Composable
private fun RecordFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.focusSurface else colors.groupHeader.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (selected) colors.focusAccent.copy(alpha = 0.35f) else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.focusAccent else colors.dateMuted,
            maxLines = 1
        )
    }
}

/**
 * 一条逐条记录：左边是开始 / 结束时刻，中间是任务名、状态和标签，右边是时长与 ⋮。
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
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    val startClock = "%02d:%02d".format(start.hour, start.minute)
    val endClock = "%02d:%02d".format(end.hour, end.minute)
    val endLabel = if (localDateOf(session.endedAt, zone) == localDateOf(session.startedAt, zone)) {
        endClock
    } else {
        "次日 $endClock"
    }
    val statusColor = if (session.status == FocusStatus.COMPLETED) colors.success else colors.dateOverdue
    val statusLabel = stringResource(
        if (session.status == FocusStatus.COMPLETED) {
            R.string.focus_status_completed
        } else {
            R.string.focus_status_interrupted
        }
    )
    val tags = FocusTagSnapshot.names(session.tagNamesSnapshot)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(start = 12.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(
                text = startClock,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.35f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.taskTitleSnapshot ?: stringResource(R.string.focus_task_none_label),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = durationText(session.actualFocusSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
                tags.take(2).forEach { tag ->
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.focusAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (tags.size > 2) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "+${tags.size - 2}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.dateMuted
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.record_more),
                    tint = colors.dateMuted
                )
            }
            TodoDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                items = listOf(
                    TodoMenuItem(
                        label = stringResource(R.string.action_edit),
                        icon = "✏️",
                        onClick = onEdit
                    ),
                    TodoMenuItem(
                        label = stringResource(R.string.action_delete),
                        icon = "🗑️",
                        danger = true,
                        onClick = onDelete
                    )
                )
            )
        }
    }
}

/**
 * 补记专注：没开计时器，事后把某一天的一段时间记进来。
 *
 * 开始时间 + 时长就是这条记录的全部；任务与标签是可选快照，和正常专注一样按开始那一刻
 * 记下来。补记默认算普通专注，勾「番茄」才进番茄计数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordAddSheet(
    today: LocalDate,
    zone: ZoneId,
    openTasks: List<Task>,
    lists: List<TaskList>,
    taskTags: Map<Long, List<Tag>>,
    onDismiss: () -> Unit,
    onConfirm: (
        startedAt: Long,
        seconds: Int,
        taskId: Long?,
        title: String?,
        listName: String?,
        tagNames: List<String>,
        tagIds: List<Long>,
        asPomodoro: Boolean
    ) -> Unit
) {
    val colors = LocalTodoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var date by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf(LocalTime.now(zone).withSecond(0).withNano(0)) }
    var minutesText by remember { mutableStateOf("25") }
    var asPomodoro by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }

    val minutes = minutesText.toIntOrNull()
    val valid = minutes != null && minutes in 1..1_440 && !date.isAfter(today)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.record_add_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.record_add_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.record_task_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedTask?.title ?: stringResource(R.string.focus_task_none_label),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickerOpen = true }) {
                        Text(stringResource(R.string.record_pick_task))
                    }
                    if (selectedTask != null) {
                        TextButton(onClick = { selectedTask = null }) {
                            Text(stringResource(R.string.record_unlink_task))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecordAddField(
                    label = stringResource(R.string.record_add_date),
                    value = calendarDateText(date, today),
                    onClick = { dateOpen = true },
                    modifier = Modifier.weight(1f)
                )
                RecordAddField(
                    label = stringResource(R.string.record_add_time),
                    value = "%02d:%02d".format(time.hour, time.minute),
                    onClick = { timeOpen = true },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = minutesText,
                onValueChange = { raw -> minutesText = raw.filter { it.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.record_minutes_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag(RecordAddMinutesFieldTag)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.record_add_count_as),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordFilterPill(
                        label = stringResource(R.string.record_add_normal),
                        selected = !asPomodoro,
                        onClick = { asPomodoro = false }
                    )
                    RecordFilterPill(
                        label = stringResource(R.string.record_add_pomodoro),
                        selected = asPomodoro,
                        onClick = { asPomodoro = true }
                    )
                }
            }

            if (date.isAfter(today)) {
                Text(
                    text = stringResource(R.string.record_add_future),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateOverdue
                )
            }

            Button(
                onClick = {
                    val minutesValue = minutes ?: return@Button
                    val startedAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    val tagList = selectedTask?.let { taskTags[it.id].orEmpty() }.orEmpty()
                    onConfirm(
                        startedAt,
                        minutesValue * 60,
                        selectedTask?.id,
                        selectedTask?.title,
                        selectedTask?.let { task ->
                            lists.firstOrNull { it.id == task.listId }?.name
                        },
                        tagList.map { it.name },
                        tagList.map { it.id },
                        asPomodoro
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.record_add_confirm))
            }
        }
    }

    if (pickerOpen) {
        TaskPickerDialog(
            tasks = openTasks,
            lists = lists,
            onPick = { task ->
                selectedTask = task
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }

    if (dateOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = LocalDate.ofEpochDay(it / 86_400_000L)
                    }
                    dateOpen = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dateOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (timeOpen) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text(stringResource(R.string.record_add_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    timeOpen = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { timeOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RecordAddField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.groupHeader.copy(alpha = 0.45f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.focusAccent,
                maxLines = 1
            )
        }
    }
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
