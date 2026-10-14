package com.personal.todo.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.ActiveTimer
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Task
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStats
import com.personal.todo.model.FocusTiming
import com.personal.todo.model.TaskSort
import com.personal.todo.model.focusStatsOf
import com.personal.todo.model.sortTasks
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Presets of section 5.1: 25 minutes of focus, and the short / long breaks. */
object FocusDurations {
    const val FocusMinutes = 25
    const val ShortBreakMinutes = 5
    const val LongBreakMinutes = 15

    /** The length a fresh countdown starts with; one of [CountdownChoices]. */
    const val CountdownMinutes = 15

    /** The choices the 倒计时 mode offers. */
    val CountdownChoices = listOf(5, 15, 25)
}

/**
 * The focus screen state: the active session, its ticking numbers, the finished
 * history and the form the user starts a session with.
 *
 * The UI ticks once a second from a monotonic reading; the database is only
 * written when the session actually changes (spec section 5.2). A timed session
 * that reaches zero while the app is open is finished right away - the app never
 * finishes one from an unverified background span, because [FocusRepository]
 * brings a recovered session back paused.
 */
class FocusViewModel(
    private val focusRepository: FocusRepository,
    taskRepository: TaskRepository
) : ViewModel() {

    val zone: ZoneId = focusRepository.zone

    val timer: StateFlow<ActiveTimer?> = focusRepository.observeActiveTimer()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The monotonic reading the numbers below are computed from. */
    private val tick = MutableStateFlow(focusRepository.elapsedMillis())

    val focusSeconds: StateFlow<Int> = combine(timer, tick) { active, nowElapsed ->
        if (active == null) 0 else FocusTiming.focusSeconds(focusRepository.stateOf(active), nowElapsed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Seconds left for a timed mode; null for 正向计时, which has no target. */
    val remainingSeconds: StateFlow<Int?> = combine(timer, tick) { active, nowElapsed ->
        if (active == null) null else FocusTiming.remainingSeconds(focusRepository.stateOf(active), nowElapsed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessions: StateFlow<List<FocusSession>> = focusRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 今日 / 本周 totals, always recomputed from the sessions (spec section 5.3). */
    val stats: StateFlow<FocusStats> = focusRepository.observeSessions()
        .map { focusStatsOf(it, LocalDate.now(zone), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusStats(0, 0, 0))

    /** Open tasks the session can be linked to. */
    val openTasks: StateFlow<List<Task>> = taskRepository.observeAllOpenTasks()
        .map { sortTasks(it, TaskSort.DEFAULT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------ form state

    var mode by mutableStateOf(FocusMode.POMODORO)
        private set

    var plannedMinutes by mutableIntStateOf(FocusDurations.FocusMinutes)
        private set

    var linkedTaskId by mutableStateOf<Long?>(null)
        private set

    var linkedTaskTitle by mutableStateOf<String?>(null)
        private set

    /** Switching mode resets the length to that mode's usual value. */
    fun selectMode(value: FocusMode) {
        mode = value
        plannedMinutes = when (value) {
            FocusMode.POMODORO -> FocusDurations.FocusMinutes
            FocusMode.COUNTDOWN -> FocusDurations.CountdownMinutes
            FocusMode.STOPWATCH -> FocusDurations.FocusMinutes
        }
    }

    fun selectMinutes(minutes: Int) {
        plannedMinutes = minutes
    }

    /** Links the session to a task, or unlinks it when [task] is null. */
    fun linkTask(task: Task?) {
        linkedTaskId = task?.id
        linkedTaskTitle = task?.title
    }

    // ---------------------------------------------------------------- actions

    fun start() {
        viewModelScope.launch {
            val target = if (mode.timed) plannedMinutes * 60 else 0
            focusRepository.startTimer(
                mode = mode,
                plannedSeconds = target,
                taskId = linkedTaskId,
                taskTitleSnapshot = linkedTaskTitle
            )
        }
    }

    fun pause() {
        viewModelScope.launch { focusRepository.pauseTimer() }
    }

    fun resume() {
        viewModelScope.launch { focusRepository.resumeTimer() }
    }

    /** Ends the session as 已完成. */
    fun finish() {
        viewModelScope.launch { focusRepository.finishTimer() }
    }

    /** Ends the session early as 中断, keeping its real focus seconds. */
    fun abandon() {
        viewModelScope.launch { focusRepository.abandonTimer() }
    }

    /**
     * Called when the screen leaves the foreground: banks the time focused so
     * far so a process death cannot lose more than the current second.
     */
    fun checkpoint() {
        viewModelScope.launch { focusRepository.checkpointTimer() }
    }

    init {
        viewModelScope.launch {
            // A recovered session comes back paused: nothing that happened while
            // the process was gone is counted (spec section 5.2).
            focusRepository.recoverTimer()
            while (isActive) {
                val nowElapsed = focusRepository.elapsedMillis()
                tick.value = nowElapsed
                val active = timer.value
                if (active != null && active.running &&
                    FocusTiming.reachedTarget(focusRepository.stateOf(active), nowElapsed)
                ) {
                    focusRepository.finishTimer()
                }
                delay(TickMillis)
            }
        }
    }

    companion object {
        private const val TickMillis = 1_000L

        fun factory(focusRepository: FocusRepository, taskRepository: TaskRepository) = viewModelFactory {
            initializer { FocusViewModel(focusRepository, taskRepository) }
        }
    }
}
