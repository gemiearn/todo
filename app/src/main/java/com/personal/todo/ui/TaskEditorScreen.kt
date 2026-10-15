package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Priority
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The tag the smoke tests use to type a title into the editor. */
internal const val EditorTitleTag = "editor-title"

/** The tag the smoke tests use to reach the icon toolbar of the editor. */
internal const val EditorToolbarTag = "editor-toolbar"

/**
 * The four groups the icon toolbar switches between. The reference strips the
 * user supplied show one, therefore the panel underneath only ever shows the
 * rows of the selected group instead of one long list.
 */
private enum class EditorGroup(val labelRes: Int, val icon: ImageVector) {
    DATE(R.string.editor_tab_date, Icons.Default.DateRange),
    PRIORITY(R.string.task_priority_label, Icons.Default.Star),
    LIST(R.string.editor_tab_list, Icons.AutoMirrored.Filled.List),
    NOTES(R.string.editor_tab_notes, Icons.Default.Edit)
}

/**
 * 编辑任务 (a pushed page). Laid out the way the 创建任务UI-4/5 reference strips
 * are: ✕ and ↑ in the bar, the title with its date pill and the description row
 * in the first card, an icon toolbar underneath, and a panel card that shows the
 * rows of the selected group.
 *
 * All the fields the editor had before are still here and nothing new was added;
 * only 优先级 changed shape - 重要 and 紧急 are now two switches, because a task's
 * quadrant *is* its priority (see [Priority]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = { SaveIconButton(onClick = viewModel::save) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_title_edit),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            TaskEditorBody(viewModel = viewModel, showQuickDates = false)
        }
    }
}

/**
 * 新建任务的底部弹层 (the 创建任务UI-2 shape): the same body as the page, plus the
 * 今天 / 明天 / 下周 / 下个月 / 选日期 shortcut row that makes adding a task a
 * two-tap job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    viewModel: TaskEditorViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalTodoColors.current.card
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            TaskEditorBody(viewModel = viewModel, showQuickDates = true)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

/** The purple ↑ of the reference: the one way to save a task. */
@Composable
private fun SaveIconButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = LocalTodoColors.current.accent,
            contentColor = Color.White
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.action_save)
        )
    }
}

/**
 * The editor content, shared by the page and the bottom sheet: 标题卡 (title, its
 * date pill, the description row), the icon toolbar and the panel of the
 * selected group. It also owns the date / time dialogs, so both callers get the
 * same behaviour without repeating it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorBody(
    viewModel: TaskEditorViewModel,
    showQuickDates: Boolean,
    modifier: Modifier = Modifier
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()

    var groupIndex by rememberSaveable { mutableIntStateOf(EditorGroup.DATE.ordinal) }
    val group = EditorGroup.entries[groupIndex]

    var plannedPickerOpen by remember { mutableStateOf(false) }
    var plannedTimePickerOpen by remember { mutableStateOf(false) }
    var duePickerOpen by remember { mutableStateOf(false) }
    var dueTimePickerOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TitleCard(viewModel = viewModel, onOpenGroup = { groupIndex = it.ordinal })

        if (showQuickDates) {
            Spacer(Modifier.height(10.dp))
            QuickDates(
                today = viewModel.today,
                onPick = { viewModel.onPlannedDateChange(it) },
                onPickExact = { plannedPickerOpen = true }
            )
        }

        Spacer(Modifier.height(14.dp))
        EditorToolbar(selected = group, onSelect = { groupIndex = it.ordinal })

        Spacer(Modifier.height(12.dp))
        PanelCard {
            when (group) {
                EditorGroup.DATE -> DateGroup(
                    viewModel = viewModel,
                    onPickPlanned = { plannedPickerOpen = true },
                    onPickPlannedTime = { plannedTimePickerOpen = true },
                    onPickDue = { duePickerOpen = true },
                    onPickDueTime = { dueTimePickerOpen = true }
                )

                EditorGroup.PRIORITY -> PriorityGroup(viewModel = viewModel)

                EditorGroup.LIST -> ListGroup(viewModel = viewModel, lists = lists)

                EditorGroup.NOTES -> NotesGroup(viewModel = viewModel)
            }
        }
    }

    if (plannedPickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(viewModel.plannedDate ?: viewModel.today)
        )
        DatePickerDialog(
            onDismissRequest = { plannedPickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { viewModel.onPlannedDateChange(utcMillisToLocalDate(it)) }
                        plannedPickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { plannedPickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (duePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(viewModel.dueDate ?: viewModel.today)
        )
        DatePickerDialog(
            onDismissRequest = { duePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { viewModel.onDueDateChange(utcMillisToLocalDate(it)) }
                        duePickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { duePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (plannedTimePickerOpen) {
        val start = viewModel.plannedTime ?: DefaultTimeOfDay
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { plannedTimePickerOpen = false },
            title = { Text(stringResource(R.string.dialog_planned_time_picker_title)) },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onPlannedTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                        plannedTimePickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { plannedTimePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (dueTimePickerOpen) {
        val start = viewModel.dueTime ?: DefaultTimeOfDay
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { dueTimePickerOpen = false },
            title = { Text(stringResource(R.string.dialog_time_picker_title)) },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDueTimeChange(LocalTime.of(timeState.hour, timeState.minute))
                        dueTimePickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dueTimePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ------------------------------------------------------------------ 第一张卡

/** 标题 + 日期胶囊同行，下面是「添加描述」，就是参考图里的第一张卡。 */
@Composable
private fun TitleCard(viewModel: TaskEditorViewModel, onOpenGroup: (EditorGroup) -> Unit) {
    val colors = LocalTodoColors.current
    val planned = viewModel.plannedDate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = { Text(stringResource(R.string.task_title_label)) },
                isError = viewModel.titleHasError,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .weight(1f)
                    .testTag(EditorTitleTag)
            )
            Spacer(Modifier.width(8.dp))
            DatePill(
                text = planned?.let { dateAndTimeText(it, viewModel.plannedTime, viewModel.today) },
                onClick = { onOpenGroup(EditorGroup.DATE) }
            )
        }

        if (viewModel.titleHasError) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.task_title_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenGroup(EditorGroup.NOTES) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = colors.dateMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = viewModel.notes.ifBlank { stringResource(R.string.editor_add_notes) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (viewModel.notes.isBlank()) colors.dateMuted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** 标题旁边的日期胶囊：有计划日期就显示「明天 15:00」，没有就显示「添加日期」。 */
@Composable
private fun DatePill(text: String?, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (text == null) colors.groupHeader else colors.focusSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text ?: stringResource(R.string.editor_add_date),
            style = MaterialTheme.typography.labelLarge,
            color = if (text == null) colors.dateMuted else colors.accent,
            maxLines = 1
        )
    }
}

/** 弹层专用的快捷日期行：今天 / 明天 / 下周 / 下个月 / 选日期。 */
@Composable
private fun QuickDates(today: LocalDate, onPick: (LocalDate) -> Unit, onPickExact: () -> Unit) {
    val colors = LocalTodoColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickDateChip(
            label = stringResource(R.string.date_today),
            onClick = { onPick(today) }
        )
        QuickDateChip(
            label = stringResource(R.string.date_tomorrow),
            onClick = { onPick(today.plusDays(1)) }
        )
        // 「下周 / 下个月」按同一个日子往后推，不做「下周一」那种额外推断。
        QuickDateChip(
            label = stringResource(R.string.editor_quick_next_week),
            onClick = { onPick(today.plusWeeks(1)) }
        )
        QuickDateChip(
            label = stringResource(R.string.editor_quick_next_month),
            onClick = { onPick(today.plusMonths(1)) }
        )
        QuickDateChip(
            label = stringResource(R.string.editor_quick_pick_date),
            onClick = onPickExact,
            tint = colors.accent
        )
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalTodoColors.current.dateMuted
) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.groupHeader)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------- 图标工具条

@Composable
private fun EditorToolbar(selected: EditorGroup, onSelect: (EditorGroup) -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag(EditorToolbarTag),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EditorGroup.entries.forEach { group ->
            val isSelected = group == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(group) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.accent else colors.dateMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(group.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.accent else colors.dateMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/** 工具条下面的面板卡：只显示当前那一组的行。 */
@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalTodoColors.current.card)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        content = content
    )
}

// ---------------------------------------------------------------- 各组的行

@Composable
private fun DateGroup(
    viewModel: TaskEditorViewModel,
    onPickPlanned: () -> Unit,
    onPickPlannedTime: () -> Unit,
    onPickDue: () -> Unit,
    onPickDueTime: () -> Unit
) {
    PanelValueRow(
        label = stringResource(R.string.task_planned_date_label),
        valueText = viewModel.plannedDate?.let { calendarDateText(it, viewModel.today) }
            ?: stringResource(R.string.date_not_set),
        isSet = viewModel.plannedDate != null,
        pickEnabled = true,
        onPick = onPickPlanned,
        onClear = { viewModel.onPlannedDateChange(null) }
    )
    PanelValueRow(
        label = stringResource(R.string.task_planned_time_label),
        valueText = viewModel.plannedTime?.let { timeText(it) }
            ?: stringResource(
                if (viewModel.plannedDate == null) R.string.planned_time_needs_date else R.string.date_not_set
            ),
        isSet = viewModel.plannedTime != null,
        pickEnabled = viewModel.plannedDate != null,
        onPick = onPickPlannedTime,
        onClear = { viewModel.onPlannedTimeChange(null) }
    )
    PanelValueRow(
        label = stringResource(R.string.task_due_date_label),
        valueText = viewModel.dueDate?.let { calendarDateText(it, viewModel.today) }
            ?: stringResource(R.string.date_not_set),
        isSet = viewModel.dueDate != null,
        pickEnabled = true,
        onPick = onPickDue,
        onClear = { viewModel.onDueDateChange(null) }
    )
    PanelValueRow(
        label = stringResource(R.string.task_due_time_label),
        valueText = viewModel.dueTime?.let { timeText(it) }
            ?: stringResource(
                if (viewModel.dueDate == null) R.string.due_time_needs_date else R.string.date_not_set
            ),
        isSet = viewModel.dueTime != null,
        pickEnabled = viewModel.dueDate != null,
        onPick = onPickDueTime,
        onClear = { viewModel.onDueTimeChange(null) }
    )
}

/** 优先级就是四象限：两个开关，下面一行实时说清楚落进哪一格。 */
@Composable
private fun PriorityGroup(viewModel: TaskEditorViewModel) {
    val colors = LocalTodoColors.current

    DimensionSwitch(
        label = stringResource(R.string.editor_toggle_important),
        checked = viewModel.priority.important,
        onCheckedChange = viewModel::onImportantChange
    )
    DimensionSwitch(
        label = stringResource(R.string.editor_toggle_urgent),
        checked = viewModel.priority.urgent,
        onCheckedChange = viewModel::onUrgentChange
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(priorityColor(viewModel.priority))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.editor_priority_result, priorityLabel(viewModel.priority)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
    }
}

@Composable
private fun DimensionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent
            )
        )
    }
}

@Composable
private fun ListGroup(viewModel: TaskEditorViewModel, lists: List<TaskList>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.task_list_label),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTodoColors.current.dateMuted
        )
        Spacer(Modifier.height(6.dp))
        ListPicker(
            lists = lists,
            selectedListId = viewModel.listId,
            onSelect = viewModel::onListChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NotesGroup(viewModel: TaskEditorViewModel) {
    OutlinedTextField(
        value = viewModel.notes,
        onValueChange = viewModel::onNotesChange,
        placeholder = { Text(stringResource(R.string.editor_add_notes)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

/** One row of a panel: label on the left, the value and a clear ✕ on the right. */
@Composable
private fun PanelValueRow(
    label: String,
    valueText: String,
    isSet: Boolean,
    pickEnabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pickEnabled, onClick = onPick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (pickEnabled) {
                if (isSet) colors.accent else colors.dateMuted
            } else {
                colors.dateMuted
            },
            maxLines = 1
        )
        if (isSet && pickEnabled) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = colors.dateMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Spacer(Modifier.width(ClearSlot))
        }
    }
}

/** Keeps the rows aligned whether or not the clear button is shown. */
private val ClearSlot = 32.dp

/** The hour a time picker starts on when the task has no time there yet. */
private val DefaultTimeOfDay: LocalTime = LocalTime.of(9, 0)

/** 「明天 15:00」 / 「明天」: the text of the date pill beside the title. */
@Composable
private fun dateAndTimeText(date: LocalDate, time: LocalTime?, today: LocalDate): String {
    val day = calendarDateText(date, today)
    return if (time == null) day else "$day ${timeText(time)}"
}

/** The time as `HH:mm`; the picker itself works on hour and minute. */
private fun timeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** The material date picker works in UTC midnight millis, so the date never shifts. */
private fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
