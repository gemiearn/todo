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
