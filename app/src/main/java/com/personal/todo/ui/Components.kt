package com.personal.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.Countdown
import com.personal.todo.model.DateLabel
import com.personal.todo.model.Priority
import com.personal.todo.model.Schedule
import com.personal.todo.model.TaskEventType
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Date pattern is a formatting detail, the surrounding labels live in strings.xml. */
private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val CardShape = RoundedCornerShape(14.dp)

/** Width of the coloured bar along the left edge of a task card. */
private val BarWidth = 4.dp

/** How often the "now" used by the date labels and countdowns is refreshed. */
private const val NowRefreshMillis = 30_000L

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TimestampFormatter)

/**
 * A stored time of day as `HH:mm`, the same shape the editor and the detail
 * screen write. Used by the calendar views that put a time next to a task.
 */
internal fun clockTimeText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/**
 * The current moment, refreshed while the screen is shown, so the relative
 * dates ("今天", "明天") and the countdowns keep up with the clock instead of
 * freezing at the moment the list was first composed.
 */
@Composable
fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NowRefreshMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}

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

/** A relative date of [Schedule] as Chinese text; the wording lives in strings.xml. */
@Composable
fun dateLabelText(label: DateLabel): String? = when (label) {
    DateLabel.None -> null
    DateLabel.Today -> stringResource(R.string.date_today)
    DateLabel.Tomorrow -> stringResource(R.string.date_tomorrow)
    DateLabel.Yesterday -> stringResource(R.string.date_yesterday)
    DateLabel.Overdue -> stringResource(R.string.date_overdue)
    is DateLabel.Weekday -> stringResource(weekdayRes(label.dayOfWeek))
    is DateLabel.MonthDay -> stringResource(R.string.date_month_day, label.month, label.day)
    is DateLabel.OverdueDays -> stringResource(R.string.date_overdue_days, label.days)
}

@Composable
private fun countdownText(countdown: Countdown): String? = when (countdown) {
    Countdown.None -> null
    is Countdown.Minutes -> stringResource(R.string.countdown_minutes, countdown.minutes)
    is Countdown.Hours -> stringResource(R.string.countdown_hours, countdown.hours)
    is Countdown.Days -> stringResource(R.string.countdown_days, countdown.days)
}

private fun weekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_mon
    DayOfWeek.TUESDAY -> R.string.weekday_tue
    DayOfWeek.WEDNESDAY -> R.string.weekday_wed
    DayOfWeek.THURSDAY -> R.string.weekday_thu
    DayOfWeek.FRIDAY -> R.string.weekday_fri
    DayOfWeek.SATURDAY -> R.string.weekday_sat
    DayOfWeek.SUNDAY -> R.string.weekday_sun
}

/**
 * The date a task shows next to its title: the due date when it has one (with
 * its overdue state), otherwise the planned date, otherwise the day it was
 * finished. Returns the text together with the colour of that state, so the
 * card and the quadrant row draw the same date the same way.
 */
@Composable
internal fun taskDateLabel(task: Task, nowMillis: Long, zone: ZoneId): Pair<String?, Color> {
    val colors = LocalTodoColors.current
    val completed = task.completedAt != null
    val overdue = !completed && Schedule.isOverdue(nowMillis, task.dueDate, task.dueTime, zone)

    val label = when {
        task.dueDate != null ->
            Schedule.dueLabel(nowMillis, task.dueDate, task.dueTime, zone)

        else -> Schedule.dateLabel(task.plannedDate, Schedule.todayOf(nowMillis, zone))
    }
    // A finished task that never had a date still shows when it was finished.
    val text = dateLabelText(label)
        ?: task.completedAt?.let { completedAtText(it, zone) }
    val color = when {
        completed -> colors.doneInk
        overdue -> colors.dateOverdue
        label == DateLabel.Today -> colors.dateToday
        else -> colors.dateMuted
    }
    return text to color
}

/**
 * One plain date as it reads on the editor and the detail screen: 今天 / 明天 /
 * 昨天 when it is close to the given day, otherwise the month and day.
 */
@Composable
internal fun calendarDateText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)
}

/**
 * One task as the screenshots show it: a white rounded card with a coloured bar
 * on its left edge, a square checkbox, the title, and on the right the date the
 * task belongs to - blue when that date is today, grey otherwise, red once it is
 * overdue - with the remaining time below it while a due moment is still ahead.
 *
 * [onCheckedChange] null means "no checkbox" (the trash lists rows that cannot be
 * toggled), [actionLabel]/[onAction] add the single secondary action of the
 * current view (delete / restore / reopen).
 */
@Composable
fun TaskCard(
    task: Task,
    subtaskCount: Int,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    onClick: () -> Unit,
    nowMillis: Long,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalTodoColors.current
    val completed = task.completedAt != null

    val (dateText, dateColor) = taskDateLabel(task, nowMillis, zone)

    val countdown = if (completed) {
        Countdown.None
    } else {
        Schedule.countdownTo(nowMillis, task.dueDate, task.dueTime, zone)
    }
    val countdownLabel = countdownText(countdown)

    val barColor = when (task.priority) {
        Priority.NORMAL -> colors.barNormal
        Priority.HIGH -> colors.barHigh
        Priority.URGENT -> colors.barUrgent
    }
    val titleColor = if (completed) colors.doneInk else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .drawBehind {
                drawRect(color = barColor, size = Size(BarWidth.toPx(), size.height))
            }
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onCheckedChange != null) {
            Checkbox(
                checked = checked == true,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = if (completed) colors.doneInk else colors.accent,
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtaskCount > 0) {
                Text(
                    text = stringResource(R.string.task_subtask_count, subtaskCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted
                )
            }
        }

        if (dateText != null || countdownLabel != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (dateText != null) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelLarge,
                        color = dateColor,
                        maxLines = 1
                    )
                }
                if (countdownLabel != null) {
                    Text(
                        text = countdownLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted,
                        maxLines = 1
                    )
                }
            }
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * The same card, plus the two swipe gestures of the screenshots: swiping right
 * finishes the task, swiping left deletes it. The card itself never disappears -
 * it springs back, and the action (with its undo) is handled by the caller.
 */
@Composable
fun SwipeableTaskCard(
    task: Task,
    subtaskCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    nowMillis: Long,
    zone: ZoneId,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeComplete()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never settle into a dismissed state: the row is not removed here,
            // it returns to its place while the task itself is already updated.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val background = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> colors.accent
                SwipeToDismissBoxValue.EndToStart -> colors.dateOverdue
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardShape)
                    .background(background),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Delete
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    ) {
        TaskCard(
            task = task,
            subtaskCount = subtaskCount,
            checked = checked,
            onCheckedChange = onCheckedChange,
            onClick = onClick,
            nowMillis = nowMillis,
            zone = zone
        )
    }
}

/** The date a task was finished on, for a task that never had a date of its own. */
@Composable
private fun completedAtText(completedAt: Long, zone: ZoneId): String {
    val date = Schedule.todayOf(completedAt, zone)
    return stringResource(R.string.date_month_day, date.monthValue, date.dayOfMonth)
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
