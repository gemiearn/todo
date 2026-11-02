package com.personal.todo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.personal.todo.R
import com.personal.todo.model.Reminder
import com.personal.todo.model.ReminderKind
import com.personal.todo.model.RepeatKind
import com.personal.todo.model.RepeatRule
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** 弹层上正在挑的那个时刻：日期页签挑计划的，时间段页签两个都能挑。 */
private enum class SheetTimeField { PLANNED, DUE }

/** 日期弹层默认落在哪个页签上。 */
internal enum class DateSheetTab(val index: Int) {
    DATE(0),
    RANGE(1)
}

/**
 * 日期弹层，照「待办时间」那张图来：顶部 ✕ ｜**日期 / 时间段**｜ ✓，日期页签上面是月历、
 * 下面两行「时间」和「重复」，最下面一行红色的「清除」。
 *
 * 「重复」放在这里而不是编辑器里，是因为它就是「日期」的第二种选法：规则只留在这条
 * 任务上，算出来的每一次都会落成**独立任务**（说明文档 4.5）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDateSheet(
    viewModel: TaskEditorViewModel,
    tab: DateSheetTab,
    onDismiss: () -> Unit
) {
    val colors = LocalTodoColors.current
    var tabIndex by rememberSaveable { mutableIntStateOf(tab.index) }

    // 弹层里的改动先落在这份草稿上，点 ✓ 才写回编辑器；✕、下滑、返回都当放弃。
    // 旧实现是「点一下立即改 viewModel」，✕ 与 ✓ 都只是关窗——用户以为 ✕ 是取消，
    // 其实日期早就改掉了，选错了也收不回来。
    var draftPlannedDate by remember { mutableStateOf(viewModel.plannedDate) }
    var draftPlannedTime by remember { mutableStateOf(viewModel.plannedTime) }
    var draftDueDate by remember { mutableStateOf(viewModel.dueDate) }
    var draftDueTime by remember { mutableStateOf(viewModel.dueTime) }
    var draftRule by remember { mutableStateOf(viewModel.repeatRule) }
    // 提醒和日期一样走草稿：点 ✓ 才写回编辑器。`draftCustomAt` 只在「自定义」时用，
    // 存的是用户手挑的绝对时刻；其余几种都靠 kind 在落库时按日期重算。
    var draftReminderKind by remember { mutableStateOf(viewModel.reminderKind) }
    var draftCustomAt by remember { mutableStateOf(viewModel.customReminderAt) }

    var month by remember { mutableStateOf(YearMonth.from(draftPlannedDate ?: viewModel.today)) }
    var dueMonth by remember { mutableStateOf(YearMonth.from(draftDueDate ?: draftPlannedDate ?: viewModel.today)) }
    var timeField by remember { mutableStateOf<SheetTimeField?>(null) }
    var duePickerOpen by remember { mutableStateOf(false) }
    var rulePickerOpen by remember { mutableStateOf(false) }
    var reminderPickerOpen by remember { mutableStateOf(false) }
    var customPickerOpen by remember { mutableStateOf(false) }
    var customDate by remember { mutableStateOf(draftPlannedDate ?: viewModel.today) }
    var customMonth by remember { mutableStateOf(YearMonth.from(draftPlannedDate ?: viewModel.today)) }
    var customTime by remember { mutableStateOf(DefaultSheetTime) }
    var customTimeOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 点 ✓ 才把草稿写回编辑器：日期先、重复后（规则没有起点时会落到计划日），提醒最后
    // ——日期一改，编辑器会按新日期重算提醒，顺序反了会把刚设的提醒清掉。
    fun confirm() {
        viewModel.onPlannedDateChange(draftPlannedDate)
        viewModel.onPlannedTimeChange(draftPlannedTime.takeIf { draftPlannedDate != null })
        viewModel.onDueDateChange(draftDueDate)
        viewModel.onDueTimeChange(draftDueTime.takeIf { draftDueDate != null })
        viewModel.onRepeatChange(draftRule)
        viewModel.onReminderChange(draftReminderKind, draftCustomAt)
        onDismiss()
    }

    // 有没有可以当锚点的日期。没有锚点就算不出提醒时刻，这一行要显示成「先设日期」。
    val draftCanRemind = Reminder.canRemind(draftPlannedDate, draftDueDate)

    // 通知权限只在**用户真的设了提醒**这一刻才要：一进 App 就弹是骚扰，而且那时用户还
    // 不知道自己会不会用提醒。拒绝了也不拦着设——设置页能重新打开，详情页会说清「不会响」。
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 结果不影响这次设置：拒绝只是「到点不弹」，任务本身照常保存 */ }

    fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SheetTabButton(
                        label = stringResource(R.string.editor_tab_date),
                        selected = tabIndex == DateSheetTab.DATE.index,
                        onClick = { tabIndex = DateSheetTab.DATE.index }
                    )
                    Spacer(Modifier.width(20.dp))
                    SheetTabButton(
                        label = stringResource(R.string.editor_range_tab),
                        selected = tabIndex == DateSheetTab.RANGE.index,
                        onClick = { tabIndex = DateSheetTab.RANGE.index }
                    )
                }
                IconButton(onClick = { confirm() }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_confirm)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (tabIndex == DateSheetTab.DATE.index) {
                MonthCalendar(
                    month = month,
                    selected = draftPlannedDate,
                    today = viewModel.today,
                    onSelect = { draftPlannedDate = it },
                    onStepMonth = { month = month.plusMonths(it) }
                )
                Spacer(Modifier.height(12.dp))
                Panel {
                    SheetValueRow(
                        label = stringResource(R.string.editor_time_label),
                        value = draftPlannedTime?.let { timeText(it) }
                            ?: stringResource(R.string.editor_value_none),
                        onClick = { timeField = SheetTimeField.PLANNED }
                    )
                    SheetValueRow(
                        label = stringResource(R.string.editor_repeat_label),
                        value = repeatText(draftRule),
                        onClick = { rulePickerOpen = true }
                    )
                    // 提醒：没有日期就没有锚点，这一行点不开，字面说清为什么。
                    SheetValueRow(
                        label = stringResource(R.string.editor_reminder_label),
                        value = if (!draftCanRemind) {
                            stringResource(R.string.reminder_need_date_short)
                        } else {
                            reminderValueText(
                                kind = draftReminderKind,
                                customAt = draftCustomAt,
                                plannedDate = draftPlannedDate,
                                plannedTime = draftPlannedTime,
                                dueDate = draftDueDate,
                                dueTime = draftDueTime
                            )
                        },
                        onClick = { if (draftCanRemind) reminderPickerOpen = true }
                    )
                }
            } else {
                Panel {
                    SheetValueRow(
                        label = stringResource(R.string.task_planned_date_label),
                        value = draftPlannedDate?.let { calendarDateText(it, viewModel.today) }
                            ?: stringResource(R.string.editor_value_none),
                        onClick = { /* 开始日期就在上面那个页签里挑 */ }
                    )
                    SheetValueRow(
                        label = stringResource(R.string.task_planned_time_label),
                        value = draftPlannedTime?.let { timeText(it) }
                            ?: stringResource(R.string.editor_value_none),
                        onClick = { timeField = SheetTimeField.PLANNED }
                    )
                    SheetValueRow(
                        label = stringResource(R.string.task_due_date_label),
                        value = draftDueDate?.let { calendarDateText(it, viewModel.today) }
                            ?: stringResource(R.string.editor_value_none),
                        // 点这一行要「打开日期选择器」，不是把截止日悄悄改成计划日/今天：
                        // 那样碰一下就把它改成逾期，而且全 App 再没有第二处能改回来。
                        onClick = {
                            dueMonth = YearMonth.from(draftDueDate ?: draftPlannedDate ?: viewModel.today)
                            duePickerOpen = true
                        }
                    )
                    SheetValueRow(
                        label = stringResource(R.string.task_due_time_label),
                        value = draftDueTime?.let { timeText(it) }
                            ?: stringResource(R.string.editor_value_none),
                        onClick = { timeField = SheetTimeField.DUE }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    draftRule = null
                    draftDueDate = null
                    draftDueTime = null
                    draftPlannedDate = null
                    draftPlannedTime = null
                    // 日期全没了，提醒的锚点也没了，跟着一起清掉。
                    draftReminderKind = null
                    draftCustomAt = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.action_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    timeField?.let { field ->
        val start = when (field) {
            SheetTimeField.PLANNED -> draftPlannedTime
            SheetTimeField.DUE -> draftDueTime
        } ?: DefaultSheetTime
        val timeState = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timeField = null },
            title = {
                Text(
                    stringResource(
                        if (field == SheetTimeField.PLANNED) {
                            R.string.dialog_planned_time_picker_title
                        } else {
                            R.string.dialog_time_picker_title
                        }
                    )
                )
            },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val time = LocalTime.of(timeState.hour, timeState.minute)
                        when (field) {
                            SheetTimeField.PLANNED -> {
                                // 一个时刻要挂在一天上，先没有日期就先落到今天。
                                if (draftPlannedDate == null) draftPlannedDate = viewModel.today
                                draftPlannedTime = time
                            }

                            SheetTimeField.DUE -> {
                                if (draftDueDate == null) {
                                    draftDueDate = draftPlannedDate ?: viewModel.today
                                }
                                draftDueTime = time
                            }
                        }
                        timeField = null
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { timeField = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (duePickerOpen) {
        AlertDialog(
            onDismissRequest = { duePickerOpen = false },
            title = { Text(stringResource(R.string.task_due_date_label)) },
            text = {
                MonthCalendar(
                    month = dueMonth,
                    selected = draftDueDate,
                    today = viewModel.today,
                    onSelect = {
                        draftDueDate = it
                        duePickerOpen = false
                    },
                    onStepMonth = { dueMonth = dueMonth.plusMonths(it) }
                )
            },
            confirmButton = {
                TextButton(onClick = { duePickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (rulePickerOpen) {
        RepeatRuleDialog(
            current = draftRule,
            onPick = {
                draftRule = it
                rulePickerOpen = false
            },
            onDismiss = { rulePickerOpen = false }
        )
    }

    if (reminderPickerOpen) {
        ReminderPickerDialog(
            current = draftReminderKind,
            onPick = { kind ->
                reminderPickerOpen = false
                if (kind != null) askNotificationPermissionIfNeeded()
                if (kind == ReminderKind.CUSTOM) {
                    // 自定义要挑一个具体时刻：先从锚点（或今天）起个头，再让用户改。
                    customDate = draftPlannedDate ?: draftDueDate ?: viewModel.today
                    customMonth = YearMonth.from(customDate)
                    customTime = DefaultSheetTime
                    customPickerOpen = true
                } else {
                    draftReminderKind = kind
                    draftCustomAt = null
                }
            },
            onDismiss = { reminderPickerOpen = false }
        )
    }

    if (customPickerOpen) {
        AlertDialog(
            onDismissRequest = { customPickerOpen = false },
            title = { Text(stringResource(R.string.reminder_kind_custom)) },
            text = {
                Column {
                    MonthCalendar(
                        month = customMonth,
                        selected = customDate,
                        today = viewModel.today,
                        onSelect = { customDate = it },
                        onStepMonth = { customMonth = customMonth.plusMonths(it) }
                    )
                    SheetValueRow(
                        label = stringResource(R.string.editor_time_label),
                        value = timeText(customTime),
                        onClick = { customTimeOpen = true }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftReminderKind = ReminderKind.CUSTOM
                        draftCustomAt = customDate.atTime(customTime)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        customPickerOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { customPickerOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (customTimeOpen) {
        val state = rememberTimePickerState(
            initialHour = customTime.hour,
            initialMinute = customTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { customTimeOpen = false },
            title = { Text(stringResource(R.string.dialog_time_picker_title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        customTime = LocalTime.of(state.hour, state.minute)
                        customTimeOpen = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { customTimeOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** 一个时刻都没有时的起点，和编辑器里那几个选择器保持一致。 */
private val DefaultSheetTime: LocalTime = LocalTime.of(9, 0)

@Composable
private fun SheetTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = if (selected) colors.accent else colors.dateMuted,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) colors.accent else Color.Transparent)
        )
    }
}

/** 一张圆角卡，里面是几行「标签 ｜ 值 ›」。 */
@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(LocalTodoColors.current.groupHeader, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        content = content
    )
}

@Composable
private fun SheetValueRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.dateMuted,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.dateMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ------------------------------------------------------------------ 月历

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onStepMonth: (Long) -> Unit
) {
    val colors = LocalTodoColors.current
    val firstOfMonth = month.atDay(1)
    val leading = firstOfMonth.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (leading + days + DaysInWeek - 1) / DaysInWeek

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_month_label, month.monthValue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onStepMonth(-1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.editor_previous_month),
                    tint = colors.dateMuted
                )
            }
            IconButton(onClick = { onStepMonth(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.editor_next_month),
                    tint = colors.dateMuted
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            (1..DaysInWeek).forEach { dayNumber ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(weekdayNarrowRes(DayOfWeek.of(dayNumber))),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                }
            }
        }

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until DaysInWeek).forEach { column ->
                    val dayNumber = row * DaysInWeek + column - leading + 1
                    Box(
                        modifier = Modifier.weight(1f).height(DayCellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                selected = date == selected,
                                today = date == today,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (selected) colors.accent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || today) FontWeight.Bold else null,
            color = when {
                selected -> Color.White
                today -> colors.accent
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

private val DayCellHeight = 42.dp
private const val DaysInWeek = 7

// -------------------------------------------------------------- 重复规则

/** 规则的写法在这里落成中文：每天 / 每两天 / 每周三 / 每月 15 号…… */
@Composable
private fun repeatText(rule: RepeatRule?): String {
    if (rule == null) return stringResource(R.string.editor_value_none)
    return when (rule.kind) {
        RepeatKind.DAILY -> stringResource(R.string.repeat_daily)
        RepeatKind.EVERY_OTHER_DAY -> stringResource(R.string.repeat_every_other_day)
        RepeatKind.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
        RepeatKind.WEEKENDS -> stringResource(R.string.repeat_weekends)
        RepeatKind.WEEKLY -> stringResource(
            R.string.repeat_weekly_on,
            stringResource(weekdayNarrowRes(rule.dayOfWeek ?: DayOfWeek.MONDAY))
        )

        RepeatKind.MONTHLY -> stringResource(R.string.repeat_monthly_on, rule.dayOfMonth ?: 1)
    }
}

private enum class RuleStep { LIST, WEEKLY, MONTHLY }

/** 选重复规则：先列六档，选「每周」「每月」再进一层挑星期几 / 几号。 */
@Composable
private fun RepeatRuleDialog(
    current: RepeatRule?,
    onPick: (RepeatRule?) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(RuleStep.LIST) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (step) {
                        RuleStep.LIST -> R.string.editor_repeat_label
                        RuleStep.WEEKLY -> R.string.repeat_weekly_title
                        RuleStep.MONTHLY -> R.string.repeat_monthly_title
                    }
                )
            )
        },
        text = {
            when (step) {
                RuleStep.LIST -> Column {
                    RuleOption(
                        label = stringResource(R.string.editor_repeat_none),
                        selected = current == null,
                        onClick = { onPick(null) }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_daily),
                        selected = current?.kind == RepeatKind.DAILY,
                        onClick = { onPick(RepeatRule(RepeatKind.DAILY)) }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_every_other_day),
                        selected = current?.kind == RepeatKind.EVERY_OTHER_DAY,
                        onClick = { onPick(RepeatRule(RepeatKind.EVERY_OTHER_DAY)) }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_weekdays),
                        selected = current?.kind == RepeatKind.WEEKDAYS,
                        onClick = { onPick(RepeatRule(RepeatKind.WEEKDAYS)) }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_weekends),
                        selected = current?.kind == RepeatKind.WEEKENDS,
                        onClick = { onPick(RepeatRule(RepeatKind.WEEKENDS)) }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_weekly),
                        selected = current?.kind == RepeatKind.WEEKLY,
                        onClick = { step = RuleStep.WEEKLY }
                    )
                    RuleOption(
                        label = stringResource(R.string.repeat_monthly),
                        selected = current?.kind == RepeatKind.MONTHLY,
                        onClick = { step = RuleStep.MONTHLY }
                    )
                }

                RuleStep.WEEKLY -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    (1..DaysInWeek).forEach { dayNumber ->
                        val day = DayOfWeek.of(dayNumber)
                        RuleOption(
                            label = stringResource(
                                R.string.repeat_weekly_on,
                                stringResource(weekdayNarrowRes(day))
                            ),
                            selected = current?.kind == RepeatKind.WEEKLY && current.dayOfWeek == day,
                            onClick = { onPick(RepeatRule(RepeatKind.WEEKLY, dayOfWeek = day)) }
                        )
                    }
                }

                RuleStep.MONTHLY -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    (1..DaysInMonth).forEach { dayOfMonth ->
                        RuleOption(
                            label = stringResource(R.string.repeat_monthly_on, dayOfMonth),
                            selected = current?.kind == RepeatKind.MONTHLY &&
                                current.dayOfMonth == dayOfMonth,
                            onClick = { onPick(RepeatRule(RepeatKind.MONTHLY, dayOfMonth = dayOfMonth)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun RuleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private const val DaysInMonth = 31

/** The time as `HH:mm`; the picker itself works on hour and minute. */
private fun timeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

// ------------------------------------------------------------------ 提醒

/** 选提醒：不提醒 + 六档相对时刻 + 自定义。 */
@Composable
private fun ReminderPickerDialog(
    current: ReminderKind?,
    onPick: (ReminderKind?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_reminder_label)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ReminderOption(
                    label = stringResource(R.string.editor_reminder_none),
                    selected = current == null,
                    onClick = { onPick(null) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_on_time),
                    selected = current == ReminderKind.ON_TIME,
                    onClick = { onPick(ReminderKind.ON_TIME) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_before_5),
                    selected = current == ReminderKind.BEFORE_5,
                    onClick = { onPick(ReminderKind.BEFORE_5) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_before_15),
                    selected = current == ReminderKind.BEFORE_15,
                    onClick = { onPick(ReminderKind.BEFORE_15) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_before_30),
                    selected = current == ReminderKind.BEFORE_30,
                    onClick = { onPick(ReminderKind.BEFORE_30) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_before_60),
                    selected = current == ReminderKind.BEFORE_60,
                    onClick = { onPick(ReminderKind.BEFORE_60) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_before_1d),
                    selected = current == ReminderKind.BEFORE_1D,
                    onClick = { onPick(ReminderKind.BEFORE_1D) }
                )
                ReminderOption(
                    label = stringResource(R.string.reminder_kind_custom),
                    selected = current == ReminderKind.CUSTOM,
                    onClick = { onPick(ReminderKind.CUSTOM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ReminderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun reminderKindText(kind: ReminderKind): String = when (kind) {
    ReminderKind.ON_TIME -> stringResource(R.string.reminder_kind_on_time)
    ReminderKind.BEFORE_5 -> stringResource(R.string.reminder_kind_before_5)
    ReminderKind.BEFORE_15 -> stringResource(R.string.reminder_kind_before_15)
    ReminderKind.BEFORE_30 -> stringResource(R.string.reminder_kind_before_30)
    ReminderKind.BEFORE_60 -> stringResource(R.string.reminder_kind_before_60)
    ReminderKind.BEFORE_1D -> stringResource(R.string.reminder_kind_before_1d)
    ReminderKind.CUSTOM -> stringResource(R.string.reminder_kind_custom)
}

/**
 * 「提醒」那一行右边显示什么：**算出来的具体时刻**跟在类型后面。
 *
 * 只写「提前 15 分钟」时用户还得自己心算；把 19:45 一并写出来，一眼就能确认是不是想要
 * 的那个点。自定义时刻没有类型可言，直接显示时刻。
 */
@Composable
private fun reminderValueText(
    kind: ReminderKind?,
    customAt: Long?,
    plannedDate: LocalDate?,
    plannedTime: LocalTime?,
    dueDate: LocalDate?,
    dueTime: LocalTime?
): String {
    if (kind == null) return stringResource(R.string.editor_reminder_none)
    if (kind == ReminderKind.CUSTOM) {
        return customAt?.let { reminderMomentText(it) }
            ?: stringResource(R.string.reminder_kind_custom)
    }
    val at = Reminder.instantOf(
        kind = kind,
        plannedDate = plannedDate,
        plannedTime = plannedTime,
        dueDate = dueDate,
        dueTime = dueTime,
        zone = ZoneId.systemDefault()
    ) ?: return reminderKindText(kind)
    val label = reminderKindText(kind)
    val moment = reminderMomentText(at.toEpochMilli())
    return "$label · $moment"
}

/** 时刻写成「20:00」，跨天时补上「11月5日」。 */
@Composable
private fun reminderMomentText(epochMillis: Long): String {
    val at = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val clock = timeText(at.toLocalTime())
    val today = LocalDate.now(ZoneId.systemDefault())
    return if (at.toLocalDate() == today) {
        clock
    } else {
        stringResource(R.string.reminder_moment_day, at.monthValue, at.dayOfMonth, clock)
    }
}
