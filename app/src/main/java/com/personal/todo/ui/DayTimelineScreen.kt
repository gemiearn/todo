package com.personal.todo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personal.todo.R
import com.personal.todo.data.db.Task
import com.personal.todo.model.Schedule
import com.personal.todo.ui.theme.LocalTodoColors
import java.time.LocalTime
import java.time.ZoneId

/** Tag the smoke tests use to tell the day timeline from the plain day list. */
internal const val DayTimelineTag = "day-timeline"

/** Width of the time gutter on the left of a timed row. */
private val TimeColumnWidth = 48.dp

/**
 * One day of the calendar as a timeline.
 *
 * The axis is the planned time, not the deadline: it answers "when do I mean to
 * start this", which is the question a day plan asks. Tasks with no planned time
 * have no place on that axis, so they sit in an 全天 block on top - the same
 * answer the 收件箱 gives for a task that has no date yet.
 *
 * A task that is past its due moment is called out in red on its time, but
 * nothing is moved: no due date is shifted and no task is silently rescheduled.
 */
@Composable
fun DayTimelineScreen(
    tasks: List<Task>,
    subtaskCounts: Map<Long, Int>,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onComplete: (Task) -> Unit,
    onReopen: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        EmptyDayTimeline(modifier = modifier)
        return
    }

    val nowMillis = rememberNowMillis()
    val allDay = remember(tasks) { tasks.filter { it.plannedTime == null } }
    // Paired with its time so the row needs no null assertion to read the axis.
    val timed = remember(tasks) {
        tasks.mapNotNull { task -> task.plannedTime?.let { time -> time to task } }
            .sortedBy { it.first }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(DayTimelineTag),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allDay.isNotEmpty()) {
            item(key = "all-day-header") {
                Text(
                    text = stringResource(R.string.calendar_all_day),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalTodoColors.current.dateMuted
                )
            }
            items(allDay, key = { "all-day-${it.id}" }) { task ->
                SwipeableTaskCard(
                    task = task,
                    subtaskCount = subtaskCounts[task.id] ?: 0,
                    checked = false,
                    onCheckedChange = { checked -> if (checked) onComplete(task) else onReopen(task) },
                    onClick = { onOpenTask(task.id) },
                    nowMillis = nowMillis,
                    zone = zone,
                    onSwipeComplete = { onComplete(task) },
                    onSwipeDelete = { onDelete(task) }
                )
            }
        }

        items(timed, key = { "timed-${it.second.id}" }) { (time, task) ->
            TimedRow(
                time = time,
                task = task,
                subtaskCount = subtaskCounts[task.id] ?: 0,
                nowMillis = nowMillis,
                zone = zone,
                onOpenTask = onOpenTask,
                onComplete = onComplete,
                onReopen = onReopen,
                onDelete = onDelete
            )
        }
    }
}

/** The empty day of the reference screenshot: nothing planned, nothing to do. */
@Composable
private fun EmptyDayTimeline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(DayTimelineTag)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.empty_day_timeline),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.empty_day_timeline_hint),
            style = MaterialTheme.typography.bodySmall,
            color = LocalTodoColors.current.dateMuted,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** One task on the axis: its planned time on the left, the card on the right. */
@Composable
private fun TimedRow(
    time: LocalTime,
    task: Task,
    subtaskCount: Int,
    nowMillis: Long,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onComplete: (Task) -> Unit,
    onReopen: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    val colors = LocalTodoColors.current
    // The explicit deadline wins; otherwise the planned day's end is the
    // fallback. The planned time is only where the row sits.
    val overdue = Schedule.isOverdue(
        nowMillis = nowMillis,
        plannedDate = task.plannedDate,
        dueDate = task.dueDate,
        dueTime = task.dueTime,
        zone = zone
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = clockTimeText(time),
            style = MaterialTheme.typography.labelLarge,
            color = if (overdue) colors.dateOverdue else colors.accent,
            modifier = Modifier
                .width(TimeColumnWidth)
                .padding(top = 12.dp)
        )
        SwipeableTaskCard(
            task = task,
            subtaskCount = subtaskCount,
            checked = false,
            onCheckedChange = { checked -> if (checked) onComplete(task) else onReopen(task) },
            onClick = { onOpenTask(task.id) },
            nowMillis = nowMillis,
            zone = zone,
            onSwipeComplete = { onComplete(task) },
            onSwipeDelete = { onDelete(task) },
            modifier = Modifier.weight(1f)
        )
    }
}
