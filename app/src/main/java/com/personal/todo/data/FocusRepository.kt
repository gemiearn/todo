package com.personal.todo.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.personal.todo.data.db.ActiveTimer
import com.personal.todo.data.db.FocusSession
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.FocusTiming
import com.personal.todo.model.TimerState
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * The focus timer and its history (spec section 5).
 *
 * Two clocks are used on purpose:
 *  - [elapsed] is a monotonic reading and drives the running time, so a wall
 *    clock change or a time zone change cannot move the timer;
 *  - [now] is the wall clock and is only used to stamp the session for the
 *    history and the daily totals.
 *
 * A session is written exactly once, when it ends, and writing it is in the same
 * transaction as clearing the active row: there is never a finished session
 * without its timer going away, and never a timer that lost its session.
 */
class FocusRepository(
    private val db: TodoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = { SystemClock.elapsedRealtime() },
    val zone: ZoneId = ZoneId.systemDefault()
) {

    private val focusDao = db.focusDao()

    /**
     * Set by [recoverTimer]. The recovery belongs to the process, not to a
     * screen: the focus screen is created again on every visit, and pausing a
     * running session each time would silently freeze the timer.
     */
    @Volatile
    private var recoveredThisProcess = false

    /** The running session, or null when there is none. */
    fun observeActiveTimer(): Flow<ActiveTimer?> = focusDao.observeActiveTimer()

    /** Every finished session, newest first. */
    fun observeSessions(): Flow<List<FocusSession>> = focusDao.observeSessions()

    /** The monotonic reading the timer arithmetic is based on. */
    fun elapsedMillis(): Long = elapsed()

    /** The pure timing state of a stored timer. */
    fun stateOf(timer: ActiveTimer): TimerState = TimerState(
        mode = timer.mode,
        plannedSeconds = timer.plannedSeconds,
        startedAt = timer.startedAt,
        lastResumeAtElapsed = timer.lastResumeAtElapsed,
        accumulatedFocusSeconds = timer.accumulatedFocusSeconds,
        running = timer.running
    )

    /**
     * Starts a session. Refused while another one is active: spec section 5.1
     * allows only one running session, and silently replacing it would throw
     * away the time the user already focused.
     *
     * 正向计时 has no target and stores 0; the timed modes always store a
     * positive target.
     */
    suspend fun startTimer(
        mode: FocusMode,
        plannedSeconds: Int,
        taskId: Long? = null,
        taskTitleSnapshot: String? = null
    ): Boolean = db.withTransaction {
        if (focusDao.getActiveTimer() != null) return@withTransaction false
        val time = now()
        focusDao.upsertActiveTimer(
            ActiveTimer(
                mode = mode,
                taskId = taskId,
                taskTitleSnapshot = taskTitleSnapshot,
                plannedSeconds = if (mode.timed) plannedSeconds.coerceAtLeast(MinTimedSeconds) else 0,
                startedAt = time,
                lastResumeAtElapsed = elapsed(),
                accumulatedFocusSeconds = 0,
                running = true,
                updatedAt = time
            )
        )
        true
    }

    /**
     * Pauses the session. The time focused so far is banked first, so the paused
     * span itself is never counted as focus time (spec section 5.2).
     */
    suspend fun pauseTimer(): Boolean = db.withTransaction {
        val timer = focusDao.getActiveTimer() ?: return@withTransaction false
        if (!timer.running) return@withTransaction false
        focusDao.upsertActiveTimer(
            timer.bankCurrentRun(now(), elapsed()).copy(running = false)
        )
        true
    }

    /** Resumes a paused session; the paused time is not counted. */
    suspend fun resumeTimer(): Boolean = db.withTransaction {
        val timer = focusDao.getActiveTimer() ?: return@withTransaction false
        if (timer.running) return@withTransaction false
        focusDao.upsertActiveTimer(
            timer.copy(lastResumeAtElapsed = elapsed(), running = true, updatedAt = now())
        )
        true
    }

    /**
     * Banks the time focused so far and keeps running.
     *
     * This is the checkpoint the app takes when it leaves the foreground: the
     * UI may tick every second, but the database must not be written that often
     * (spec section 5.2), so the banked value is what survives a process death.
     */
    suspend fun checkpointTimer(): Boolean = db.withTransaction {
        val timer = focusDao.getActiveTimer() ?: return@withTransaction false
        if (!timer.running) return@withTransaction false
        focusDao.upsertActiveTimer(timer.bankCurrentRun(now(), elapsed()))
        true
    }

    /**
     * Runs once when the process starts (spec section 5.2).
     *
     * A timer that survived a process death cannot prove what happened in
     * between, so that span is dropped and the session comes back paused: the
     * app never turns unverified background time into a finished pomodoro, and
     * the user decides when to carry on.
     *
     * Idempotent for the lifetime of this process: a screen that is opened again
     * must not pause a session the user has meanwhile resumed.
     */
    suspend fun recoverTimer(): Boolean {
        if (recoveredThisProcess) return false
        recoveredThisProcess = true
        return db.withTransaction {
            val timer = focusDao.getActiveTimer() ?: return@withTransaction false
            if (!timer.running) return@withTransaction false
            focusDao.upsertActiveTimer(
                timer.copy(running = false, lastResumeAtElapsed = elapsed(), updatedAt = now())
            )
            true
        }
    }

    /** Ends the session as 已完成 - a stopwatch the user finished, or a timed run that reached zero. */
    suspend fun finishTimer(): Boolean = endTimer(FocusStatus.COMPLETED)

    /** Ends the session early as 中断, keeping its real focus seconds (spec section 5.2). */
    suspend fun abandonTimer(): Boolean = endTimer(FocusStatus.INTERRUPTED)

    private suspend fun endTimer(status: FocusStatus): Boolean = db.withTransaction {
        val timer = focusDao.getActiveTimer() ?: return@withTransaction false
        val elapsedNow = elapsed()
        focusDao.insertSession(
            FocusSession(
                taskId = timer.taskId,
                taskTitleSnapshot = timer.taskTitleSnapshot,
                mode = timer.mode,
                startedAt = timer.startedAt,
                endedAt = now(),
                plannedSeconds = timer.plannedSeconds,
                actualFocusSeconds = FocusTiming.recordedFocusSeconds(stateOf(timer), elapsedNow),
                status = status
            )
        )
        focusDao.clearActiveTimer()
        true
    }

    /**
     * Moves the current run into the bank and restarts it at [atElapsed]. The
     * bank plus the live run is exactly the focus time of the session.
     */
    private fun ActiveTimer.bankCurrentRun(atWallClock: Long, atElapsed: Long): ActiveTimer =
        copy(
            accumulatedFocusSeconds = FocusTiming.focusSeconds(stateOf(this), atElapsed),
            lastResumeAtElapsed = atElapsed,
            updatedAt = atWallClock
        )

    private companion object {
        /** Nobody focuses for less than a minute; a shorter target is a typo. */
        const val MinTimedSeconds = 60
    }
}
