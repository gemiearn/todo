package com.personal.todo.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.personal.todo.data.FocusSettings
import com.personal.todo.data.FocusSound
import com.personal.todo.data.db.ActiveTimer
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskList
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusPhase
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.Schedule
import com.personal.todo.ui.theme.LocalTodoColors
import com.personal.todo.viewmodel.FocusDurations
import com.personal.todo.viewmodel.FocusViewModel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlinx.coroutines.delay

private val RingSize = 240.dp
private val RingStroke = 20.dp

/** Even the shortest countdown fills this much of the setup ring, so it never reads as broken. */
private const val MinVisibleArc = 0.12f

/** Tags the smoke tests use to drive the focus screen; the controls carry no text of their own. */
internal const val FocusStartTag = "focus-start"
internal const val FocusTaskRowTag = "focus-task-row"

/**
 * 专注时长圆环：只有倒计时才带这个 tag，因为只有它能拖。
 *
 * 番茄钟的时长在它自己的参数卡里选、正计时没有目标，所以那两个模式下圆环只是预览，
 * 不是控件——一个不能用的控件不该出现在树里。
 */
internal const val FocusLengthRingTag = "focus-length-ring"

/** The scrolling column of the focus page: the tests use it to reach the history. */
internal const val FocusListTag = "focus-list"

internal fun focusModeTag(mode: FocusMode): String = "focus-mode-${mode.name.lowercase()}"

/**
 * The 专注 screen: the three timers of the app - 正向计时, 倒计时 and 番茄钟 -
 * plus what was actually focused today and this week.
 *
 * The screen only paints what the timer holds; the state machine itself lives in
 * [FocusViewModel] and [com.personal.todo.data.FocusRepository], which never let
 * unverified background time turn into a finished pomodoro.
 *
 * [onBack] null means this screen *is* a root of the bottom bar: the shell already
 * draws the top bar and the tab strip around it, so this screen draws neither and
 * only fills the space it is given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    lists: List<TaskList>,
    onBack: (() -> Unit)?,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val focusSeconds by viewModel.focusSeconds.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val openTasks by viewModel.openTasks.collectAsStateWithLifecycle()

    // Two tasks in two lists can carry the same title, so the focus card names the
    // list the linked task lives in as well.
    val linkedListName = remember(viewModel.linkedTaskId, openTasks, lists) {
        openTasks.firstOrNull { it.id == viewModel.linkedTaskId }
            ?.let { task -> lists.firstOrNull { it.id == task.listId }?.name }
    }

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

    // The ambience is part of the session, not of the screen: it starts and stops
    // with the timer itself.
    AmbientSound(sound = viewModel.settings.sound, playing = timer?.running == true)

    Scaffold(
        topBar = {
            if (onBack != null) {
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
        },
        // As a tab root this screen sits inside the shell's Scaffold: taking the
        // window insets a second time would push everything down again.
        contentWindowInsets = if (onBack == null) {
            WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        modifier = modifier
    ) { innerPadding ->
        // The ring, the controls and the history scroll as one column: they are taller
        // than a phone screen now, and a list nested inside a Column would both fight
        // it for the height and hide the history completely.
        val active = timer
        val today = Schedule.todayOf(System.currentTimeMillis(), viewModel.zone)
        val historyByDay = sessions.groupBy { Schedule.todayOf(it.startedAt, viewModel.zone) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(FocusListTag),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatsHeader(
                        todayText = stringResource(
                            R.string.focus_stats_today,
                            durationText(stats.todayFocusSeconds)
                        ),
                        pomodoroText = stringResource(
                            R.string.focus_stats_pomodoros,
                            stats.todayPomodoroCount
                        ),
                        weekText = stringResource(
                            R.string.focus_stats_week,
                            durationText(stats.weekFocusSeconds)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    // 统计是另一页（照滴答清单点左上角统计图标的做法），入口就放在这张
                    // 小结旁边，不用去「更多」里绕。
                    TextButton(
                        onClick = onOpenStats,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.focus_stats_open))
                    }
                }
            }

            item(key = "panel") {
                if (active == null) {
                    SetupPanel(
                        viewModel = viewModel,
                        linkedListName = linkedListName,
                        onPickTask = { taskPickerOpen = true }
                    )
                } else {
                    RunningPanel(
                        timer = active,
                        focusSeconds = focusSeconds,
                        remaining = remaining,
                        // A pomodoro counts where it is in the day's run of them; a
                        // break is not one of them.
                        pomodoroIndex = if (active.mode == FocusMode.POMODORO && !active.phase.isBreak) {
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
            }

            item(key = "history-title") {
                Text(
                    text = stringResource(R.string.focus_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
                )
            }

            if (historyByDay.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        text = stringResource(R.string.focus_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.dateMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            } else {
                // The history is the day's work, so it is grouped by the day it
                // happened: a flat list of clock times is hard to read back.
                historyByDay.forEach { (date, ofDay) ->
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
private fun StatsHeader(
    todayText: String,
    pomodoroText: String,
    weekText: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = modifier
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

/** No session running: pick a mode, a task, a length - and read it off the ring. */
@Composable
private fun SetupPanel(
    viewModel: FocusViewModel,
    linkedListName: String?,
    onPickTask: () -> Unit
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        ModeSegments(selected = viewModel.mode, onSelect = viewModel::selectMode)

        Spacer(Modifier.height(12.dp))

        FocusTaskRow(
            title = viewModel.linkedTaskTitle,
            listName = linkedListName,
            onPick = onPickTask
        )

        Spacer(Modifier.height(12.dp))

        PlannedRing(
            mode = viewModel.mode,
            minutes = viewModel.plannedLengthMinutes,
            onSetMinutes = viewModel::selectMinutes
        )

        Spacer(Modifier.height(12.dp))

        when (viewModel.mode) {
            // 番茄钟的四个参数就是说明文档 5.1 的那四个数，现在可以调（照 ui-5）。
            FocusMode.POMODORO -> PomodoroParameters(
                settings = viewModel.settings,
                onFocusMinutes = viewModel::selectFocusMinutes,
                onShortBreak = viewModel::selectShortBreakMinutes,
                onLongBreak = viewModel::selectLongBreakMinutes,
                onCycle = viewModel::selectLongBreakEvery
            )

            FocusMode.COUNTDOWN -> CountdownLengthPicker(
                minutes = viewModel.plannedMinutes,
                onSelect = viewModel::selectMinutes
            )

            FocusMode.STOPWATCH -> Text(
                text = stringResource(R.string.focus_stopwatch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dateMuted
            )
        }

        Spacer(Modifier.height(12.dp))

        SoundPicker(selected = viewModel.settings.sound, onSelect = viewModel::selectSound)

        Spacer(Modifier.height(16.dp))

        // 计时必须绑在一个真实任务上：没有一个任务，就没有可统计的对象，所以
        // 选任务之前这个按钮根本点不动。
        Button(
            // 开始的这一刻把清单名一起记进 session，统计页就能按清单排行——
            // 任务以后换了清单，也不会改写已经发生的这段专注。
            onClick = { viewModel.start(linkedListName) },
            enabled = viewModel.linkedTaskId != null,
            colors = ButtonDefaults.buttonColors(containerColor = colors.focusAccent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FocusStartTag)
        ) {
            Text(stringResource(R.string.focus_action_start))
        }
    }
}

/** The three timers as one segmented control, the shape the reference app uses. */
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
                    .background(if (isSelected) colors.focusAccent else Color.Transparent)
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
 * The task this session works on, with the list it lives in - two tasks in two
 * lists can carry the same title, so the list is named next to it.
 *
 * [title] stays null until one is picked: there is no "none" to fall back on,
 * because a session always runs on a real task.
 */
@Composable
private fun FocusTaskRow(
    title: String?,
    listName: String?,
    onPick: () -> Unit
) {
    val colors = LocalTodoColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .clickable(onClick = onPick)
            .testTag(FocusTaskRowTag)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_link_task),
                style = MaterialTheme.typography.labelMedium,
                color = colors.dateMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(if (title == null) R.string.action_pick else R.string.action_change),
                style = MaterialTheme.typography.labelLarge,
                color = colors.focusAccent
            )
        }

        Spacer(Modifier.height(6.dp))

        if (title == null) {
            Text(
                text = stringResource(R.string.focus_task_required),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.dateMuted
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.focusAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(50))
                        .background(colors.focusSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                listName?.let { name ->
                    Text(
                        text = stringResource(R.string.focus_task_list, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dateMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * The ring before a session starts: it shows the length that is picked, and 倒计时 lets
 * that length be dragged straight on the ring itself.
 *
 * The drag replaces the ± buttons and the slider the page used to carry: one gesture on the
 * thing that already shows the length, instead of three controls writing the same number.
 * 番茄钟's length lives in its own parameter card and 正向计时 has no target at all, so
 * neither of those modes makes its ring draggable.
 */
@Composable
private fun PlannedRing(
    mode: FocusMode,
    minutes: Int,
    onSetMinutes: (Int) -> Unit
) {
    val colors = LocalTodoColors.current
    val draggable = mode == FocusMode.COUNTDOWN
    // Called whether or not it is used, so the composition never has a conditional slot.
    val dragModifier = Modifier.ringDrag(minutes, onSetMinutes)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FocusRing(
            fraction = plannedFraction(mode, minutes),
            tint = colors.focusAccent,
            modifier = if (draggable) dragModifier.testTag(FocusLengthRingTag) else Modifier
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    // 正向计时没有目标，未启动时就老老实实停在 0:00:00，而不是番茄钟那个
                    // 25 分钟——那个数字从来不属于它。
                    text = clockText(
                        seconds = if (mode == FocusMode.STOPWATCH) 0 else minutes * 60,
                        showHours = mode == FocusMode.STOPWATCH || minutes >= 60
                    ),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(plannedHintRes(mode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dateMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp)
                )
            }
        }
    }
}

/**
 * 在圆环上拖拽调倒计时时长。
 *
 * 角度映射：一整圈 360° 正好覆盖 5–300 分钟这个范围，并且每 5 分钟吸附一档——刻度和
 * 原来的滑条完全一样，只是手势换到了圆环上。只认「从按下的那一点转过了多少度」，所以
 * 手指按住不动不会把值改掉。
 */
@Composable
private fun Modifier.ringDrag(minutes: Int, onMinutes: (Int) -> Unit): Modifier {
    // 拖拽时 minutes 一直在变，pointerInput 的 key 必须是常量：key 一变成新值，手势监听
    // 就会重启，手指还没抬起来拖拽就断了。所以用 rememberUpdatedState 把最新的值和回调
    // 带进那个不会重启的协程里。
    val latestMinutes = rememberUpdatedState(minutes)
    val latestOnMinutes = rememberUpdatedState(onMinutes)
    return this.then(
        remember {
            Modifier.pointerInput(Unit) {
                var startAngle = 0f
                var startMinutes = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        startAngle = ringAngle(offset, size.width, size.height)
                        startMinutes = latestMinutes.value
                    },
                    onDrag = { change, _ ->
                        val turned = degreesBetween(
                            from = startAngle,
                            to = ringAngle(change.position, size.width, size.height)
                        )
                        latestOnMinutes.value(
                            roundToStep(startMinutes + turned / 360f * MinutesPerTurn)
                        )
                    }
                )
            }
        }
    )
}

/** One full turn of the ring covers the whole countdown range. */
private const val MinutesPerTurn =
    (FocusDurations.MaxMinutes - FocusDurations.MinMinutes).toFloat()

/** 触点在圆环里的角度：0° 在正右方，顺时针为正。 */
private fun ringAngle(offset: Offset, width: Int, height: Int): Float = Math.toDegrees(
    atan2(
        (offset.y - height / 2f).toDouble(),
        (offset.x - width / 2f).toDouble()
    )
).toFloat()

/** 从 [from] 转到 [to] 的度数，取最近的那一圈，落在 (-180, 180]。 */
private fun degreesBetween(from: Float, to: Float): Float {
    var delta = to - from
    while (delta <= -180f) delta += 360f
    while (delta > 180f) delta -= 360f
    return delta
}

/**
 * 倒计时的那四个快捷档。
 *
 * 精确选值的活儿交给圆环上的拖拽（见 [ringDrag]）：快捷档负责「常用的那几个」，
 * 拖拽负责「其它任何值」，两者写的是同一个时长。
 */
@Composable
private fun CountdownLengthPicker(
    minutes: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalTodoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FocusDurations.CountdownChoices.forEach { choice ->
            FilterChip(
                selected = minutes == choice,
                onClick = { onSelect(choice) },
                label = {
                    Text(
                        text = stringResource(R.string.focus_minutes_choice, choice),
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.focusSurface,
                    selectedLabelColor = colors.focusAccent
                )
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.focus_length_selected, lengthText(minutes)),
            style = MaterialTheme.typography.labelLarge,
            color = colors.focusAccent,
            maxLines = 1
        )
    }
}

/** 番茄钟的四个参数，每个一张卡片、各自一组选项（照 ui-5）。 */
@Composable
private fun PomodoroParameters(
    settings: FocusSettings,
    onFocusMinutes: (Int) -> Unit,
    onShortBreak: (Int) -> Unit,
    onLongBreak: (Int) -> Unit,
    onCycle: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ParameterCard(
            label = stringResource(R.string.focus_param_focus),
            value = stringResource(R.string.focus_minutes_choice, settings.focusMinutes),
            choices = FocusSettings.FocusMinuteChoices,
            onSelect = onFocusMinutes,
            choiceText = { stringResource(R.string.focus_minutes_choice, it) },
            modifier = Modifier.weight(1f)
        )
        ParameterCard(
            label = stringResource(R.string.focus_param_short_break),
            value = stringResource(R.string.focus_minutes_choice, settings.shortBreakMinutes),
            choices = FocusSettings.ShortBreakChoices,
            onSelect = onShortBreak,
            choiceText = { stringResource(R.string.focus_minutes_choice, it) },
            modifier = Modifier.weight(1f)
        )
        ParameterCard(
            label = stringResource(R.string.focus_param_long_break),
            value = stringResource(R.string.focus_minutes_choice, settings.longBreakMinutes),
            choices = FocusSettings.LongBreakChoices,
            onSelect = onLongBreak,
            choiceText = { stringResource(R.string.focus_minutes_choice, it) },
            modifier = Modifier.weight(1f)
        )
        ParameterCard(
            label = stringResource(R.string.focus_param_cycle),
            value = stringResource(R.string.focus_cycle_value, settings.longBreakEvery),
            choices = FocusSettings.LongBreakEveryChoices,
            onSelect = onCycle,
            choiceText = { stringResource(R.string.focus_cycle_value, it) },
            modifier = Modifier.weight(1f)
        )
    }
}

/** One parameter card: its name, the current value, and its own little menu. */
@Composable
private fun ParameterCard(
    label: String,
    value: String,
    choices: List<Int>,
    choiceText: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTodoColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.dateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dateMuted
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choiceText(choice)) },
                    onClick = {
                        onSelect(choice)
                        open = false
                    }
                )
            }
        }
    }
}

/** The ambient loops a session can play; 关闭 means silence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPicker(selected: FocusSound, onSelect: (FocusSound) -> Unit) {
    val colors = LocalTodoColors.current
    val context = LocalContext.current

    // 点一下就要听得见。The picker only exists before a session starts, so nothing is
    // playing yet: without a preview a tap would look like a dead chip.
    var preview by remember { mutableStateOf<FocusSound?>(null) }
    DisposableEffect(preview) {
        val sound = preview
        val player = if (sound != null && sound != FocusSound.NONE) {
            runCatching { createAmbiencePlayer(context, sound, looping = false) }.getOrNull()
        } else {
            null
        }
        onDispose { releaseAmbiencePlayer(player) }
    }
    LaunchedEffect(preview) {
        if (preview != null) {
            delay(SoundPreviewMillis)
            preview = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.focus_sound_title),
            style = MaterialTheme.typography.labelMedium,
            color = colors.dateMuted
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusSound.entries.forEach { sound ->
                FilterChip(
                    selected = sound == selected,
                    onClick = {
                        onSelect(sound)
                        preview = sound
                    },
                    label = { Text(stringResource(sound.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.focusSurface,
                        selectedLabelColor = colors.focusAccent
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.focus_sound_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dateMuted
        )
    }
}

/** The ambience player, or null when the sound has no file (关闭) or cannot be played. */
private fun createAmbiencePlayer(
    context: android.content.Context,
    sound: FocusSound,
    looping: Boolean
): MediaPlayer? = MediaPlayer.create(context, sound.soundRes)?.apply {
    isLooping = looping
    setVolume(AmbienceVolume, AmbienceVolume)
    start()
}

private fun releaseAmbiencePlayer(player: MediaPlayer?) {
    val playing = player ?: return
    runCatching { if (playing.isPlaying) playing.stop() }
    playing.release()
}

/**
 * Loops the chosen ambience while a session runs.
 *
 * The player lives in a [DisposableEffect]: it is created with the sound and the
 * running flag, and released the moment either changes - so a paused timer is
 * silent and nothing keeps playing once the user leaves.
 */
@Composable
private fun AmbientSound(sound: FocusSound, playing: Boolean) {
    val context = LocalContext.current
    DisposableEffect(sound, playing) {
        val player = if (playing && sound.soundRes != 0) {
            runCatching { createAmbiencePlayer(context, sound, looping = true) }.getOrNull()
        } else {
            null
        }
        onDispose { releaseAmbiencePlayer(player) }
    }
}

/** Ambience sits behind the timer, never in front of it. */
private const val AmbienceVolume = 0.5f

/** How long a tapped ambience plays as a preview. */
private const val SoundPreviewMillis = 2_500L

/** How much of the ring is filled before the session starts. */
private fun plannedFraction(mode: FocusMode, minutes: Int): Float = when (mode) {
    // Both timed modes draw how much of their own range this session is, so dragging the
    // countdown ring visibly grows or shrinks it. Even the shortest session keeps a visible
    // stub instead of an apparently broken empty ring.
    FocusMode.POMODORO ->
        (minutes.toFloat() / FocusDurations.MaxPomodoroMinutes).coerceAtLeast(MinVisibleArc)

    FocusMode.COUNTDOWN ->
        (minutes.toFloat() / FocusDurations.MaxMinutes).coerceAtLeast(MinVisibleArc)

    // 正向计时 has no target to be a fraction of.
    FocusMode.STOPWATCH -> 0f
}

private fun plannedHintRes(mode: FocusMode): Int = when (mode) {
    FocusMode.POMODORO -> R.string.focus_ring_break_hint
    FocusMode.COUNTDOWN -> R.string.focus_ring_drag_hint
    FocusMode.STOPWATCH -> R.string.focus_ring_stopwatch_hint
}

/** Snaps a raw reading to the nearest step, so the length is always a real choice. */
private fun roundToStep(raw: Float): Int =
    (Math.round(raw / FocusDurations.MinuteStep) * FocusDurations.MinuteStep)
        .coerceIn(FocusDurations.MinMinutes, FocusDurations.MaxMinutes)

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
        FocusRing(
            fraction = fraction,
            tint = if (timer.running) colors.focusAccent else colors.dateMuted
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockText(display, showHours = !timer.mode.timed || display >= 3600),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (timer.phase.isBreak) {
                        stringResource(
                            if (timer.phase == FocusPhase.LONG_BREAK) {
                                R.string.focus_break_long_running
                            } else {
                                R.string.focus_break_short_running
                            }
                        )
                    } else {
                        stringResource(
                            if (timer.running) R.string.focus_running_label else R.string.focus_paused_label
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.dateMuted
                )
                if (timer.phase.isBreak && !timer.running) {
                    Text(
                        text = stringResource(R.string.focus_paused_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.dateMuted
                    )
                }
                pomodoroIndex?.let { index ->
                    Text(
                        text = stringResource(R.string.focus_pomodoro_index, index),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.focusAccent
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

            if (timer.phase.isBreak) {
                // A break is never counted and never looks like a session: skipping it
                // just clears the timer, so the next 番茄 starts fresh.
                TextButton(onClick = onInterrupt) { Text(stringResource(R.string.focus_action_skip_break)) }
            } else if (timer.mode.timed && remaining != null && remaining > 0) {
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

/**
 * The ring the page is built around: the tomato track with the filled part drawn
 * over it, and whatever the state has to say inside.
 *
 * The same ring is drawn before a session starts (showing the length that was
 * picked, so dragging the slider visibly grows it) and while one runs (showing the
 * time going by). Starting a session therefore does not move the page under the
 * user's eyes.
 */
@Composable
private fun FocusRing(
    fraction: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalTodoColors.current
    Box(modifier = modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.focusTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (fraction > 0f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
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
                color = if (session.status == FocusStatus.COMPLETED) colors.success else colors.dateOverdue
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

/** A session length in words: 「45 分钟」/「2 小时」/「2 小时 15 分」. */
@Composable
private fun lengthText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.focus_duration_minutes, rest)
        rest == 0 -> stringResource(R.string.focus_duration_hours, hours)
        else -> stringResource(R.string.focus_duration_hours_minutes, hours, rest)
    }
}
