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
import com.personal.todo.data.FocusSettings
import com.personal.todo.data.FocusSettingsStore
import com.personal.todo.data.FocusSound
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.ActiveTimer
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.Task
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusPhase
import com.personal.todo.model.FocusStats
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.model.FocusTiming
import com.personal.todo.model.TaskSort
import com.personal.todo.model.focusStatsOf
import com.personal.todo.model.nearestOpenOccurrencePerSeries
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
    val CountdownChoices = listOf(5, 15, 25, 45)

    /**
     * The range the 倒计时 slider covers: five minutes up to five hours, in five
     * minute steps. The presets above are the common lengths; the slider is how a
     * user picks anything else, without typing.
     */
    const val MinMinutes = 5
    const val MaxMinutes = 5 * 60
    const val MinuteStep = 5

    /** The range the 番茄钟 focus block is drawn against on the ring. */
    const val MaxPomodoroMinutes = 90
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
    private val taskRepository: TaskRepository,
    private val settingsStore: FocusSettingsStore
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
    val stats: StateFlow<FocusStats> = combine(
        focusRepository.observeSessions(),
        // 「今天」跟着跨天翻页：原来只在 sessions 发射时才重算，23:59 打开专注页过零点
        // 后，顶部「今日 xx 分钟」会一直停在昨天，直到又产生一条专注记录。
        taskRepository.observeToday()
    ) { sessions, today -> focusStatsOf(sessions, today, zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusStats(0, 0, 0))

    /**
     * Open tasks the session can be linked to.
     *
     * 一串重复任务在这里也只露**最近那一条还没做完的**：库里的每一次都在，但拿一串
     * 「每天」的任务去选择器里当然不该出现一整屏同样的标题。收拢只发生在选择列表，
     * 不影响任何已存的记录。
     */
    val openTasks: StateFlow<List<Task>> = taskRepository.observeAllOpenTasks()
        .map { sortTasks(nearestOpenOccurrencePerSeries(it), TaskSort.DEFAULT) }
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

    /** 番茄钟的四个参数与背景音：改了立刻生效，也记进偏好，下次进来还是这些值。 */
    var settings by mutableStateOf(settingsStore.read())
        private set

    /** The length the current mode runs for: 番茄钟 from the settings, 倒计时 from the picker. */
    val plannedLengthMinutes: Int
        get() = if (mode == FocusMode.POMODORO) settings.focusMinutes else plannedMinutes

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
        plannedMinutes = minutes.coerceIn(FocusDurations.MinMinutes, FocusDurations.MaxMinutes)
    }

    fun selectFocusMinutes(minutes: Int) {
        updateSettings { it.copy(focusMinutes = minutes) }
    }

    fun selectShortBreakMinutes(minutes: Int) {
        updateSettings { it.copy(shortBreakMinutes = minutes) }
    }

    fun selectLongBreakMinutes(minutes: Int) {
        updateSettings { it.copy(longBreakMinutes = minutes) }
    }

    fun selectLongBreakEvery(count: Int) {
        updateSettings { it.copy(longBreakEvery = count) }
    }

    fun selectSound(sound: FocusSound) {
        updateSettings { it.copy(sound = sound) }
    }

    private fun updateSettings(change: (FocusSettings) -> FocusSettings) {
        val updated = change(settings)
        settings = updated
        settingsStore.write(updated)
    }

    /**
     * Links the session to the task it is working on.
     *
     * There is no "no task" choice on purpose: a session that is not attached to a
     * real task cannot be reported per task later, so the numbers would slowly stop
     * meaning anything. A session always runs on a task the user picked from the
     * inbox or a list.
     */
    fun linkTask(task: Task) {
        linkedTaskId = task.id
        linkedTaskTitle = task.title
    }

    /** 清掉当前的选择。选中的任务在别处被完成 / 删除 / 变子任务之后，它就不该再留在
     * 专注页上——界面据此把「开始」按回去，起表前的复核也用同一条判断。
     */
    fun clearLink() {
        linkedTaskId = null
        linkedTaskTitle = null
    }

    /**
     * 改一条历史记录：换掉它的关联任务（传 null ＝取消关联），以及改这段专注的时长。
     *
     * 任务、标题、清单名与标签快照都由调用方（记录页的编辑弹层）按那时那刻的数据解析后
     * 传进来——与开始一次专注时记快照的口径一致：换了任务，快照就换成那条任务现在的样子；
     * 只改时长（任务没动）时，调用方把原来的快照原样传回，不做任何改写。
     */
    fun updateSession(
        sessionId: Long,
        taskId: Long?,
        taskTitleSnapshot: String?,
        listNameSnapshot: String?,
        tagNames: List<String>,
        tagIds: List<Long>,
        seconds: Int
    ) {
        viewModelScope.launch {
            focusRepository.updateSession(
                sessionId = sessionId,
                taskId = taskId,
                taskTitleSnapshot = taskTitleSnapshot,
                listNameSnapshot = listNameSnapshot,
                tagNamesSnapshot = FocusTagSnapshot.encode(tagNames),
                tagIdsSnapshot = FocusTagSnapshot.encodeIds(tagIds),
                actualFocusSeconds = seconds
            )
        }
    }

    /** 删掉一条历史记录（用户主动删这一条）。 */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { focusRepository.deleteSession(sessionId) }
    }

    // ---------------------------------------------------------------- actions

    /**
     * Starts the session. Without a linked task nothing happens - the button that
     * calls this is disabled until one is picked, and this is the second half of
     * the same rule, so no path can start an unattached session.
     *
     * [listName] is the 清单 the task is in right now. It is stored with the
     * session so the statistics page can rank time by list later, even after the
     * task has moved somewhere else.
     *
     * [tagNames] are the 标签 the task carries right now, for the same reason: the
     * statistics page can filter the whole page by one tag afterwards, and moving
     * or renaming a tag later never rewrites what already happened.
     *
     * [tagIds] are those same tags' ids and the one that actually decides identity:
     * a tag renamed later still matches its old sessions by id, while the name
     * snapshot is what the history keeps showing.
     */
    fun start(
        listName: String? = null,
        tagNames: List<String> = emptyList(),
        tagIds: List<Long> = emptyList()
    ) {
        val taskId = linkedTaskId ?: return
        val title = linkedTaskTitle
        val target = if (mode.timed) plannedLengthMinutes * 60 else 0
        val tagSnapshot = FocusTagSnapshot.encode(tagNames)
        val tagIdSnapshot = FocusTagSnapshot.encodeIds(tagIds)
        viewModelScope.launch {
            // 选中的那一刻任务还在列表里，之后可能被完成 / 删除 / 挪成子任务。起表前
            // 复核一次：不合格就清掉这个失效的选择并放弃，绝不把一段专注记在一条已经
            // 结束的任务上。
            if (!taskRepository.isStartableForFocus(taskId)) {
                clearLink()
                return@launch
            }
            focusRepository.startTimer(
                mode = mode,
                plannedSeconds = target,
                taskId = taskId,
                taskTitleSnapshot = title,
                listNameSnapshot = listName,
                tagNamesSnapshot = tagSnapshot,
                tagIdsSnapshot = tagIdSnapshot
            )
        }
    }

    fun pause() {
        viewModelScope.launch { focusRepository.pauseTimer() }
    }

    fun resume() {
        viewModelScope.launch { focusRepository.resumeTimer() }
    }

    /**
     * Ends the session as 已完成. A 番茄钟 that reached its end is followed by its
     * break, which is the promise the screen makes: 25 分钟专注之后是 5 分钟休息,
     * 每几个番茄之后是长休息.
     */
    fun finish() {
        viewModelScope.launch { finishAndMaybeBreak() }
    }

    /**
     * Writes the finished focus session, then starts the break that follows it.
     *
     * The break is another *phase of the same timer*, never a session: 休息时间
     * 不计入专注统计. Ending a break (reaching zero, or 跳过休息) simply clears the
     * timer, and the next 番茄 is a fresh start.
     */
    private suspend fun finishAndMaybeBreak() {
        val active = timer.value ?: return
        val finishedAFocusPomodoro = active.mode == FocusMode.POMODORO && !active.phase.isBreak
        if (!focusRepository.finishTimer()) return
        if (!finishedAFocusPomodoro) return

        val doneToday = focusRepository.completedPomodorosOn(LocalDate.now(zone))
        val longBreak = doneToday > 0 && doneToday % settings.longBreakEvery == 0
        focusRepository.startBreak(
            phase = if (longBreak) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK,
            plannedSeconds = (if (longBreak) {
                settings.longBreakMinutes
            } else {
                settings.shortBreakMinutes
            }) * 60,
            taskId = active.taskId,
            taskTitleSnapshot = active.taskTitleSnapshot
        )
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
            // 选中的任务被完成 / 删除之后，这个失效的选择要自己清掉，免得按钮还亮着、
            // 点下去才被起表前的复核拒绝。
            //
            // 判据必须是**不折叠**的那份清单（`observeAllOpenTasks`）：页面上给用户看的
            // `openTasks` 会把一串重复任务只留最近那条，拿它判会把「用户点的就是后面某一次」
            // 误当成「任务没了」而清掉选择（这正是 2026-10-19 用户报的「点▶没有带上任务」）。
            // Room 的流第一次发射就是库里的真实结果，没有「初值是空的＝还没加载」的问题。
            taskRepository.observeAllOpenTasks().collect { open ->
                val id = linkedTaskId ?: return@collect
                if (open.none { it.id == id }) clearLink()
            }
        }
        viewModelScope.launch {
            // A recovered session keeps running from where it was: nothing that
            // happened while the process was gone is counted (spec section 5.2),
            // but the timer itself is not parked on 暂停.
            focusRepository.recoverTimer()
            while (isActive) {
                val nowElapsed = focusRepository.elapsedMillis()
                tick.value = nowElapsed
                val active = timer.value
                if (active != null && active.running &&
                    FocusTiming.reachedTarget(focusRepository.stateOf(active), nowElapsed)
                ) {
                    finishAndMaybeBreak()
                }
                delay(TickMillis)
            }
        }
    }

    companion object {
        private const val TickMillis = 1_000L

        fun factory(
            focusRepository: FocusRepository,
            taskRepository: TaskRepository,
            settingsStore: FocusSettingsStore
        ) = viewModelFactory {
            initializer { FocusViewModel(focusRepository, taskRepository, settingsStore) }
        }
    }
}
