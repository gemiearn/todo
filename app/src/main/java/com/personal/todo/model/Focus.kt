package com.personal.todo.model

import com.personal.todo.data.db.FocusSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The three ways a session counts time (the modes the app offers):
 * 正向计时 counts up until the user ends it, 倒计时 counts down from a chosen
 * length, 番茄钟 is the classic 25 minute focus block.
 */
enum class FocusMode(val timed: Boolean) {
    STOPWATCH(false),
    COUNTDOWN(true),
    POMODORO(true),

    /** The four focus blocks after which a longer break is suggested. */
    ;

    companion object {
        /** 番茄钟 focus blocks in one cycle (spec section 5.1). */
        const val POMODORO_PER_CYCLE = 4
    }
}

/** How a finished session ended: it reached its end, or the user stopped it early. */
enum class FocusStatus { COMPLETED, INTERRUPTED }

/**
 * What the single active timer is counting right now: a focus block, or the break
 * that follows one (说明文档 5.1: 每轮专注之后有短休息，每几个番茄之后是长休息).
 *
 * A break is never written into the history: 休息时间不计入专注统计, so only a
 * [FOCUS] phase ends with a session row.
 */
enum class FocusPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK;

    val isBreak: Boolean get() = this != FOCUS
}

/**
 * The state of the single active session, as it is persisted between app runs
 * (spec section 5.2).
 *
 * The elapsed time of the current run is measured with a monotonic clock
 * ([lastResumeAtElapsed]); [accumulatedFocusSeconds] is what earlier runs of the
 * same session already banked. Pausing moves the current run into the bank and
 * stops the clock, so paused time is never counted.
 */
data class TimerState(
    val mode: FocusMode,
    val plannedSeconds: Int,
    val startedAt: Long,
    val lastResumeAtElapsed: Long,
    val accumulatedFocusSeconds: Int,
    val running: Boolean
)

/**
 * The timer arithmetic of spec section 5.2, kept free of Android types so it can
 * be tested directly.
 *
 * A negative span means the monotonic clock restarted (the device rebooted)
 * between two readings: that span cannot be trusted and is therefore not
 * counted, instead of producing a bogus negative or huge focus time.
 */
object FocusTiming {

    /** Focus seconds of the session so far. Paused time never counts. */
    fun focusSeconds(state: TimerState, nowElapsedMillis: Long): Int {
        val banked = if (state.accumulatedFocusSeconds > 0) state.accumulatedFocusSeconds else 0
        if (!state.running) return banked
        val sinceResume = nowElapsedMillis - state.lastResumeAtElapsed
        val running = if (sinceResume > 0) (sinceResume / 1000).toInt() else 0
        return banked + running
    }

    /** Seconds left for a timed mode, or null for 正向计时 which has no target. */
    fun remainingSeconds(state: TimerState, nowElapsedMillis: Long): Int? {
        if (!state.mode.timed) return null
        return (state.plannedSeconds - focusSeconds(state, nowElapsedMillis)).coerceAtLeast(0)
    }

    /** True once a timed session has counted down to zero. */
    fun reachedTarget(state: TimerState, nowElapsedMillis: Long): Boolean =
        state.mode.timed && focusSeconds(state, nowElapsedMillis) >= state.plannedSeconds

    /**
     * The focus seconds a session records when it ends. A timed session never
     * records more than its target: the extra time is not verified focus.
     */
    fun recordedFocusSeconds(state: TimerState, nowElapsedMillis: Long): Int {
        val focus = focusSeconds(state, nowElapsedMillis)
        return if (state.mode.timed) minOf(focus, state.plannedSeconds) else focus
    }
}

/** 每日 / 每周 focus totals, always derived from the sessions (spec section 5.3). */
data class FocusStats(
    val todayFocusSeconds: Int,
    val todayPomodoroCount: Int,
    val weekFocusSeconds: Int
)

/**
 * Sums the finished sessions of spec section 5.3.
 *
 * An interrupted session counts its real focus seconds towards the totals but
 * never towards the 番茄 count, and nothing is stored as a second, separately
 * maintained total that could drift away from the sessions.
 */
fun focusStatsOf(sessions: List<FocusSession>, today: LocalDate, zone: ZoneId): FocusStats {
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    var todaySeconds = 0
    var todayPomodoros = 0
    var weekSeconds = 0

    sessions.forEach { session ->
        val day = localDateOf(session.startedAt, zone)
        if (day == today) {
            todaySeconds += session.actualFocusSeconds
            if (session.mode == FocusMode.POMODORO && session.status == FocusStatus.COMPLETED) {
                todayPomodoros++
            }
        }
        if (!day.isBefore(weekStart) && !day.isAfter(today)) {
            weekSeconds += session.actualFocusSeconds
        }
    }

    return FocusStats(
        todayFocusSeconds = todaySeconds,
        todayPomodoroCount = todayPomodoros,
        weekFocusSeconds = weekSeconds
    )
}

/** The local day an absolute instant belongs to, in the device's time zone. */
fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** 专注量的一个小结：时长与番茄数。 */
data class FocusTotals(val seconds: Int, val pomodoros: Int)

/** 某一天的专注量，趋势图与热力图的格子都用它。 */
data class FocusDay(val date: LocalDate, val seconds: Int, val pomodoros: Int)

/**
 * 一个任务的专注量。标题用的是当时的快照：任务后来改了名或被删掉，这份排行仍然
 * 读得懂；[title] 为空表示那条记录本来就没有关联任务。
 */
data class FocusTaskFocus(val title: String?, val seconds: Int, val sessions: Int)

/**
 * 统计页的数据，形状照着滴答清单 / 番茄ToDo 那一页来：三张小节（今天 / 本周 /
 * 累计）、最近几天的趋势、按任务的排行、以及最近若干周的打卡热力图。
 *
 * Everything is derived from the stored sessions, so 休息 never shows up here - a
 * break is a phase of the timer, not a session.
 */
data class FocusReport(
    val today: FocusTotals,
    val week: FocusTotals,
    val total: FocusTotals,
    /** 从早到晚，最后一个永远是今天。 */
    val lastDays: List<FocusDay>,
    /** 按专注时长降序，只留前几个。 */
    val topTasks: List<FocusTaskFocus>,
    /** 从最早的一周开始，每周 7 天（周一起）。 */
    val heatmapWeeks: List<List<FocusDay>>
)

/**
 * Aggregates the sessions of [today] and before into a [FocusReport].
 *
 * The rules match [focusStatsOf] exactly: an interrupted session still counts its
 * real focus seconds, but only a completed 番茄钟 counts as a 番茄. Nothing here is
 * stored separately, so the numbers can never drift away from the sessions.
 */
fun focusReportOf(
    sessions: List<FocusSession>,
    today: LocalDate,
    zone: ZoneId,
    days: Int = ReportDays,
    weeks: Int = ReportWeeks,
    topTasks: Int = ReportTopTasks
): FocusReport {
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val firstDay = today.minusDays((days - 1).toLong())
    val heatmapStart = weekStart.minusWeeks((weeks - 1).toLong())

    val secondsByDay = mutableMapOf<LocalDate, Int>()
    val pomodorosByDay = mutableMapOf<LocalDate, Int>()
    // 同一个任务可能改过名，所以按 id 归并；没有关联任务的记录按当时的标题归并。
    val byTask = linkedMapOf<String, FocusTaskFocus>()

    sessions.forEach { session ->
        val day = localDateOf(session.startedAt, zone)
        secondsByDay[day] = (secondsByDay[day] ?: 0) + session.actualFocusSeconds
        if (session.mode == FocusMode.POMODORO && session.status == FocusStatus.COMPLETED) {
            pomodorosByDay[day] = (pomodorosByDay[day] ?: 0) + 1
        }

        val key = session.taskId?.let { "id:$it" } ?: "title:${session.taskTitleSnapshot.orEmpty()}"
        val seen = byTask[key]
        byTask[key] = FocusTaskFocus(
            title = seen?.title ?: session.taskTitleSnapshot,
            seconds = (seen?.seconds ?: 0) + session.actualFocusSeconds,
            sessions = (seen?.sessions ?: 0) + 1
        )
    }

    fun dayOf(date: LocalDate) = FocusDay(
        date = date,
        // 今天之后的格子是空的：热力图最后一周会画到未来几天。
        seconds = if (date.isAfter(today)) 0 else secondsByDay[date] ?: 0,
        pomodoros = if (date.isAfter(today)) 0 else pomodorosByDay[date] ?: 0
    )

    fun totalsBetween(from: LocalDate, to: LocalDate): FocusTotals {
        var seconds = 0
        var pomodoros = 0
        var day = from
        while (!day.isAfter(to)) {
            seconds += secondsByDay[day] ?: 0
            pomodoros += pomodorosByDay[day] ?: 0
            day = day.plusDays(1)
        }
        return FocusTotals(seconds, pomodoros)
    }

    val heatmapDays = (0 until weeks * DaysPerWeek).map { heatmapStart.plusDays(it.toLong()) }

    return FocusReport(
        today = totalsBetween(today, today),
        week = totalsBetween(weekStart, today),
        total = FocusTotals(secondsByDay.values.sum(), pomodorosByDay.values.sum()),
        lastDays = (0 until days).map { dayOf(firstDay.plusDays(it.toLong())) },
        topTasks = byTask.values.sortedByDescending { it.seconds }.take(topTasks),
        heatmapWeeks = heatmapDays.chunked(DaysPerWeek).map { week -> week.map(::dayOf) }
    )
}

private const val DaysPerWeek = 7

/** 趋势图看最近一周，热力图看最近一个季度。 */
private const val ReportDays = 7
private const val ReportWeeks = 12
private const val ReportTopTasks = 5
