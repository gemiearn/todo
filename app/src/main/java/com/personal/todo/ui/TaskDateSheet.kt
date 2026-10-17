package com.personal.todo.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.model.RepeatKind
import com.personal.todo.model.RepeatRule
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

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

    var month by remember { mutableStateOf(YearMonth.from(draftPlannedDate ?: viewModel.today)) }
    var dueMonth by remember { mutableStateOf(YearMonth.from(draftDueDate ?: draftPlannedDate ?: viewModel.today)) }
    var timeField by remember { mutableStateOf<SheetTimeField?>(null) }
    var duePickerOpen by remember { mutableStateOf(false) }
    var rulePickerOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 点 ✓ 才把草稿写回编辑器：日期先、重复后（规则没有起点时会落到计划日）。
    fun confirm() {
        viewModel.onPlannedDateChange(draftPlannedDate)
        viewModel.onPlannedTimeChange(draftPlannedTime.takeIf { draftPlannedDate != null })
        viewModel.onDueDateChange(draftDueDate)
        viewModel.onDueTimeChange(draftDueTime.takeIf { draftDueDate != null })
        viewModel.onRepeatChange(draftRule)
        onDismiss()
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
