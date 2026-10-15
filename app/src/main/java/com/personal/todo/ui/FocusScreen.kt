package com.personal.todo.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.todo.R
import com.personal.todo.data.db.ActiveTimer
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.Schedule
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.FocusDurations
import com.personal.todo.viewmodel.FocusViewModel
import java.time.Instant
import java.time.ZoneId

private val RingSize = 220.dp
private val RingStroke = 14.dp

/** Tags the smoke tests use to drive the focus screen; the controls carry no text of their own. */
internal const val FocusStartTag = "focus-start"
internal const val FocusTaskRowTag = "focus-task-row"

internal fun focusModeTag(mode: FocusMode): String = "focus-mode-${mode.name.lowercase()}"

/**
 * The 专注 screen: the three timers of the app - 正向计时, 倒计时 and 番茄钟 -
 * plus what was actually focused today and this week.
 *
 * The screen only paints what the timer holds; the state machine itself lives in
 * [FocusViewModel] and [com.personal.todo.data.FocusRepository], which never let
 * unverified background time turn into a finished pomodoro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    lists: List<TaskList>,
    onBack: () -> Unit
) {
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val focusSeconds by viewModel.focusSeconds.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val openTasks by viewModel.openTasks.collectAsStateWithLifecycle()

    var taskPickerOpen by remember { mutableStateOf(false) }
    val colors = LocalTodoColors.current

    // Leaving the foreground banks the time focused so far, so a process death
    // cannot take more than the current second with it (spec section 5.2).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.checkpoint()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StatsHeader(
                todayText = stringResource(
                    R.string.focus_stats_today,
                    durationText(stats.todayFocusSeconds)
                ),
                pomodoroText = stringResource(R.string.focus_stats_pomodoros, stats.todayPomodoroCount),
                weekText = stringResource(
                    R.string.focus_stats_week,
                    durationText(stats.weekFocusSeconds)
                )
            )

            val active = timer
            if (active == null) {
                SetupPanel(
                    viewModel = viewModel,
                    colors = colors,
                    onPickTask = { taskPickerOpen = true }
                )
            } else {
                RunningPanel(
                    timer = active,
                    focusSeconds = focusSeconds,
                    remaining = remaining,
                    // A pomodoro counts where it is in the day's run of them.
                    pomodoroIndex = if (active.mode == FocusMode.POMODORO) {
                        stats.todayPomodoroCount + 1
                    } else {
                        null
                    },
                    colors = colors,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onFinish = viewModel::finish,
                    onInterrupt = viewModel::abandon
                )
            }

            Text(
                text = stringResource(R.string.focus_history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
            )

            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.dateMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                // The history is the day's work, so it is grouped by the day it
                // happened: a flat list of clock times is hard to read back.
                val today = Schedule.todayOf(System.currentTimeMillis(), viewModel.zone)
                val byDay = sessions.groupBy { Schedule.todayOf(it.startedAt, viewModel.zone) }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    byDay.forEach { (date, ofDay) ->
                        item(key = "history-day-${date.toEpochDay()}") {
                            HistoryDayHeader(label = calendarDateText(date, today))
                        }
                        items(ofDay, key = { it.id }) { session ->
                            SessionRow(session = session, zone = viewModel.zone)
                        }
                    }
                }
            }
        }
    }

    if (taskPickerOpen) {
        TaskPickerDialog(
            tasks = openTasks,
            lists = lists,
            onPick = {
                viewModel.linkTask(it)
                taskPickerOpen = false
            },
            onDismiss = { taskPickerOpen = false }
        )
    }
}

// ------------------------------------------------------------------ the stats

@Composable
private fun StatsHeader(todayText: String, pomodoroText: String, weekText: String) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCell(todayText, Modifier.weight(1f))
        StatCell(pomodoroText, Modifier.weight(1f))
        StatCell(weekText, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

// ---------------------------------------------------------------- the setup

/** No session running: pick a mode, a length and the task to focus on. */
@Composable
private fun SetupPanel(
    viewModel: FocusViewModel,
    colors: com.personal.todo.ui.theme.TodoColors,
    onPickTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        ModeSegments(selected = viewModel.mode, onSelect = viewModel::selectMode)

        Spacer(Modifier.height(12.dp))

        when (viewModel.mode) {
            FocusMode.POMODORO -> Text(
                text = stringResource(
                    R.string.focus_pomodoro_plan,
                    FocusDurations.FocusMinutes,
                    FocusDurations.ShortBreakMinutes,
                    FocusMode.POMODORO_PER_CYCLE,
                    FocusDurations.LongBreakMinutes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )

            FocusMode.COUNTDOWN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusDurations.CountdownChoices.forEach { minutes ->
                    FilterChip(
                        selected = viewModel.plannedMinutes == minutes,
                        onClick = { viewModel.selectMinutes(minutes) },
                        label = { Text(stringResource(R.string.focus_minutes_choice, minutes)) }
                    )
                }
            }

            FocusMode.STOPWATCH -> Text(
                text = stringResource(R.string.focus_stopwatch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }

        Spacer(Modifier.height(16.dp))

        TaskRow(title = viewModel.linkedTaskTitle, onPick = onPickTask)

        Spacer(Modifier.height(12.dp))

        // 计时必须绑在一个真实任务上：没有一个任务，就没有可统计的对象，所以
        // 选任务之前这个按钮根本点不动。
        Button(
            onClick = viewModel::start,
            enabled = viewModel.linkedTaskId != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FocusStartTag)
        ) {
            Text(stringResource(R.string.focus_action_start))
        }
    }
}

/** The three timers as one segmented control, the shape the reference apps use. */
@Composable
private fun ModeSegments(
    selected: FocusMode,
    onSelect: (FocusMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.groupHeader)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FocusMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .testTag(focusModeTag(mode))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(modeLabelRes(mode)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else colors.dateMuted
                )
            }
        }
    }
}

/**
 * The task this session works on. It is the first thing the screen asks for, and
 * [title] stays null until one is picked - there is no "none" to fall back on.
 */
@Composable
private fun TaskRow(title: String?, onPick: () -> Unit) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .clickable(onClick = onPick)
            .testTag(FocusTaskRowTag)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.focus_link_task),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted
            )
            Text(
                text = title ?: stringResource(R.string.focus_task_required),
                style = MaterialTheme.typography.bodyLarge,
                color = if (title == null) colors.dateMuted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stringResource(if (title == null) R.string.action_pick else R.string.action_change),
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent
        )
    }
}

// -------------------------------------------------------------- the running

/** A session is active: the ring, the number and the controls. */
@Composable
private fun RunningPanel(
    timer: ActiveTimer,
    focusSeconds: Int,
    remaining: Int?,
    pomodoroIndex: Int?,
    colors: com.personal.todo.ui.theme.TodoColors,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onInterrupt: () -> Unit
) {
    val fraction = ringFraction(timer, focusSeconds)
    val display = remaining ?: focusSeconds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(fraction = fraction, color = if (timer.running) colors.accent else colors.dateMuted)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockText(display, showHours = !timer.mode.timed || display >= 3600),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        if (timer.running) R.string.focus_running_label else R.string.focus_paused_label
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                pomodoroIndex?.let { index ->
                    Text(
                        text = stringResource(R.string.focus_pomodoro_index, index),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent
                    )
                }
                timer.taskTitleSnapshot?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (timer.running) {
                TextButton(onClick = onPause) { Text(stringResource(R.string.focus_action_pause)) }
            } else {
                TextButton(onClick = onResume) { Text(stringResource(R.string.focus_action_resume)) }
            }

            if (timer.mode.timed && remaining != null && remaining > 0) {
                // Ending a timed session before zero is 中断, and says so.
                TextButton(onClick = onInterrupt) { Text(stringResource(R.string.focus_action_interrupt)) }
            } else {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.focus_action_finish)) }
            }
        }
    }
}

/**
 * How much of the ring is filled. A timed session fills as it counts down; a
 * 正向计时 session has no target, so the ring follows the current minute.
 */
private fun ringFraction(timer: ActiveTimer, focusSeconds: Int): Float = when {
    timer.mode.timed && timer.plannedSeconds > 0 ->
        (focusSeconds.toFloat() / timer.plannedSeconds).coerceIn(0f, 1f)

    else -> (focusSeconds % 60) / 60f
}

@Composable
private fun ProgressRing(fraction: Float, color: Color) {
    val colors = LocalTodoColors.current
    Canvas(modifier = Modifier.size(RingSize)) {
        val stroke = RingStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = colors.groupHeader,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke)
        )
        if (fraction > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// -------------------------------------------------------------- the history

/** The day a group of sessions belongs to. */
@Composable
private fun HistoryDayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SessionRow(session: FocusSession, zone: ZoneId) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.taskTitleSnapshot ?: stringResource(modeLabelRes(session.mode)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = durationText(session.actualFocusSeconds),
                style = MaterialTheme.typography.labelLarge,
                color = if (session.status == FocusStatus.COMPLETED) colors.barNormal else colors.dateOverdue
            )
        }
        Text(
            text = sessionSubtitle(session, zone),
            style = MaterialTheme.typography.bodySmall,
            color = colors.dateMuted
        )
    }
}

/** `HH:mm` of the day it started, the mode, and how it ended. */
@Composable
private fun sessionSubtitle(session: FocusSession, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val clock = "%02d:%02d".format(start.hour, start.minute)
    val status = stringResource(
        if (session.status == FocusStatus.COMPLETED) R.string.focus_status_completed else R.string.focus_status_interrupted
    )
    return stringResource(R.string.focus_session_subtitle, clock, modeLabel(session.mode), status)
}

/** The mode name below the ring and in the history; the plan is only shown when it has one. */
@Composable
private fun modeLabel(mode: FocusMode): String = when (mode) {
    FocusMode.STOPWATCH -> stringResource(R.string.focus_mode_stopwatch)
    FocusMode.COUNTDOWN -> stringResource(R.string.focus_mode_countdown)
    FocusMode.POMODORO -> stringResource(R.string.focus_mode_pomodoro)
}

private fun modeLabelRes(mode: FocusMode): Int = when (mode) {
    FocusMode.STOPWATCH -> R.string.focus_mode_stopwatch
    FocusMode.COUNTDOWN -> R.string.focus_mode_countdown
    FocusMode.POMODORO -> R.string.focus_mode_pomodoro
}

// --------------------------------------------------------------- the picker

/**
 * Picks the task to focus on, grouped by the list it lives in - 收件箱 first, since
 * that is where a task lives before it has a home.
 *
 * There is deliberately no "no task" choice: a session always runs on a real task,
 * so nothing here can produce an unattached one.
 */
@Composable
private fun TaskPickerDialog(
    tasks: List<Task>,
    lists: List<TaskList>,
    onPick: (Task) -> Unit,
    onDismiss: () -> Unit
) {
    val orderedLists = lists.sortedByDescending { it.isInbox }
    val grouped = orderedLists
        .map { list -> list to tasks.filter { it.listId == list.id } }
        .filter { it.second.isNotEmpty() }
    // Should a task ever outlive the list it was grouped by, it still has to be
    // reachable rather than silently missing from the picker.
    val groupedTasks = grouped.flatMap { it.second }.toSet()
    val unlisted = tasks.filterNot { it in groupedTasks }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_dialog_pick_task_title)) },
        text = {
            if (tasks.isEmpty()) {
                Text(stringResource(R.string.focus_task_picker_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    grouped.forEach { (list, ofList) ->
                        item(key = "list-${list.id}") { PickerHeader(list.name) }
                        items(ofList, key = { it.id }) { task -> TaskPickRow(task, onPick) }
                    }
                    items(unlisted, key = { it.id }) { task -> TaskPickRow(task, onPick) }
                }
            }
        },
        // Picking a task is the action itself, so 取消 is the only button left.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PickerHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = LocalTodoColors.current.dateMuted,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun TaskPickRow(task: Task, onPick: (Task) -> Unit) {
    Text(
        text = task.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPick(task) }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    )
}

// ------------------------------------------------------------- the formatters

/** `mm:ss`, or `h:mm:ss` for a run that is already an hour long. */
internal fun clockText(seconds: Int, showHours: Boolean): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (showHours || hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, rest)
    } else {
        "%02d:%02d".format(minutes, rest)
    }
}

/** A focus time in words: minutes, seconds, or both. */
@Composable
private fun durationText(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.focus_duration_seconds, seconds)
    seconds % 60 == 0 -> stringResource(R.string.focus_duration_minutes, seconds / 60)
    else -> stringResource(R.string.focus_duration_minutes_seconds, seconds / 60, seconds % 60)
}
