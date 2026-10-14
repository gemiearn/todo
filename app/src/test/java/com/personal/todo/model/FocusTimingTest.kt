package com.personal.todo.model

import com.personal.todo.data.db.FocusSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer arithmetic and the session totals of spec section 5. These are plain
 * JVM tests: the rules are kept free of Android types on purpose, so they can be
 * checked without a device or a database.
 */
class FocusTimingTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 10, 14)

    private fun state(
        mode: FocusMode = FocusMode.POMODORO,
        plannedSeconds: Int = 1500,
        lastResumeAtElapsed: Long = 0L,
        accumulatedFocusSeconds: Int = 0,
        running: Boolean = true
    ) = TimerState(
        mode = mode,
        plannedSeconds = plannedSeconds,
        startedAt = 0L,
        lastResumeAtElapsed = lastResumeAtElapsed,
        accumulatedFocusSeconds = accumulatedFocusSeconds,
        running = running
    )

    private fun session(
        mode: FocusMode,
        status: FocusStatus,
        actualFocusSeconds: Int,
        startedAt: Long
    ) = FocusSession(
        mode = mode,
        startedAt = startedAt,
        endedAt = startedAt,
        plannedSeconds = 1500,
        actualFocusSeconds = actualFocusSeconds,
        status = status
    )

    /** Midnight of a local day, so the day a session belongs to is unambiguous. */
    private fun noonOf(date: LocalDate): Long =
        date.atTime(12, 0).atZone(shanghai).toInstant().toEpochMilli()

    /** Just after local midnight: in UTC that instant is still the day before. */
    private fun earlyMorningOf(date: LocalDate): Long =
        date.atTime(3, 0).atZone(shanghai).toInstant().toEpochMilli()

    // ------------------------------------------------------------ 计时

    @Test
    fun aRunningSessionCountsTheMonotonicSpanOnTopOfTheBankedTime() {
        val running = state(lastResumeAtElapsed = 1_000L, accumulatedFocusSeconds = 100)

        assertEquals(110, FocusTiming.focusSeconds(running, 11_000L))
    }

    @Test
    fun aPausedSessionDoesNotMoveWhileTheClockRuns() {
        val paused = state(
            lastResumeAtElapsed = 1_000L,
            accumulatedFocusSeconds = 100,
            running = false
        )

        assertEquals(100, FocusTiming.focusSeconds(paused, 9_999_999L))
    }

    @Test
    fun aRebootedMonotonicClockIsNotCountedAsFocusTime() {
        val running = state(lastResumeAtElapsed = 500_000L, accumulatedFocusSeconds = 120)

        assertEquals(
            "重启后单调时钟归零，这段无法验证的时间不能被算作专注",
            120,
            FocusTiming.focusSeconds(running, 1_000L)
        )
    }

    @Test
    fun aTimedSessionCountsDownAndStopsAtZero() {
        val running = state(plannedSeconds = 1500, lastResumeAtElapsed = 0L)

        assertEquals(1500, FocusTiming.remainingSeconds(running, 0L))
        assertEquals(600, FocusTiming.remainingSeconds(running, 900_000L))
        assertEquals(0, FocusTiming.remainingSeconds(running, 1_500_000L))
        assertEquals(0, FocusTiming.remainingSeconds(running, 9_000_000L))
    }

    @Test
    fun aTimedSessionIsFinishedOnceItReachesItsTarget() {
        assertFalse(FocusTiming.reachedTarget(state(plannedSeconds = 1500), 1_499_000L))
        assertTrue(FocusTiming.reachedTarget(state(plannedSeconds = 1500), 1_500_000L))
        assertTrue("超过了目标时间也算到达", FocusTiming.reachedTarget(state(plannedSeconds = 1500), 9_000_000L))
    }

    @Test
    fun aTimedSessionNeverRecordsMoreThanItsTarget() {
        val running = state(plannedSeconds = 1500, lastResumeAtElapsed = 0L)

        assertEquals(
            "离开手机很久也不能把一个番茄记成三个小时",
            1500,
            FocusTiming.recordedFocusSeconds(running, 10_800_000L)
        )
        assertEquals(240, FocusTiming.recordedFocusSeconds(running, 240_000L))
    }

    @Test
    fun aStopwatchCountsUpAndHasNoTargetAtAll() {
        val running = state(mode = FocusMode.STOPWATCH, plannedSeconds = 0, lastResumeAtElapsed = 0L)

        assertNull(FocusTiming.remainingSeconds(running, 600_000L))
        assertFalse(FocusTiming.reachedTarget(running, 600_000L))
        assertEquals(600, FocusTiming.focusSeconds(running, 600_000L))
        assertEquals(
            "正向计时没有目标，记录的就是真实专注时长",
            3600,
            FocusTiming.recordedFocusSeconds(running, 3_600_000L)
        )
    }

    // ------------------------------------------------------------ 统计

    @Test
    fun interruptedSessionsCountTowardsTheTimeButNotTowardsThePomodoroCount() {
        val sessions = listOf(
            session(FocusMode.POMODORO, FocusStatus.COMPLETED, 1500, noonOf(today)),
            session(FocusMode.COUNTDOWN, FocusStatus.INTERRUPTED, 600, noonOf(today))
        )

        val stats = focusStatsOf(sessions, today, shanghai)

        assertEquals("中断的真实时长计入实际专注时间", 2100, stats.todayFocusSeconds)
        assertEquals("中断不算完整番茄", 1, stats.todayPomodoroCount)
    }

    @Test
    fun aCompletedCountdownIsNotAPomodoroEither() {
        val sessions = listOf(session(FocusMode.COUNTDOWN, FocusStatus.COMPLETED, 600, noonOf(today)))

        val stats = focusStatsOf(sessions, today, shanghai)

        assertEquals(600, stats.todayFocusSeconds)
        assertEquals(0, stats.todayPomodoroCount)
    }

    @Test
    fun theWeekTotalCoversTheIsoWeekAndExcludesOtherWeeks() {
        val thisMonday = today.minusDays(2)
        val lastSunday = thisMonday.minusDays(1)
        val sessions = listOf(
            session(FocusMode.POMODORO, FocusStatus.COMPLETED, 1500, noonOf(today)),
            session(FocusMode.STOPWATCH, FocusStatus.COMPLETED, 300, noonOf(thisMonday)),
            session(FocusMode.POMODORO, FocusStatus.COMPLETED, 2400, noonOf(lastSunday))
        )

        val stats = focusStatsOf(sessions, today, shanghai)

        assertEquals("上周日不属于本周", 1800, stats.weekFocusSeconds)
        assertEquals(1500, stats.todayFocusSeconds)
    }

    @Test
    fun theDayASessionBelongsToFollowsTheDeviceTimeZone() {
        // 03:00 in Shanghai is still 19:00 of the previous day in UTC, so this
        // instant sits on different dates in the two zones.
        val sessions = listOf(
            session(FocusMode.POMODORO, FocusStatus.COMPLETED, 1500, earlyMorningOf(today))
        )

        assertEquals(
            "上海时间下这次专注属于 10 月 14 日",
            1500,
            focusStatsOf(sessions, today, shanghai).todayFocusSeconds
        )
        assertEquals(
            "UTC 下同一个瞬间还是 10 月 13 日，不该计入 14 日",
            0,
            focusStatsOf(sessions, today, ZoneId.of("UTC")).todayFocusSeconds
        )
    }
}
