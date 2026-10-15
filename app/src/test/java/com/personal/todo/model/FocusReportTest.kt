package com.personal.todo.model

import com.personal.todo.data.db.FocusSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aggregation behind the 统计 page.
 *
 * These are the numbers a user compares week over week, so they are pinned down
 * away from any UI: the same rules as the header ([focusStatsOf]), an entry per
 * day in the trend, one entry per task in the ranking, and a whole number of weeks
 * in the heatmap.
 */
class FocusReportTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 15)

    private fun session(
        mode: FocusMode = FocusMode.POMODORO,
        status: FocusStatus = FocusStatus.COMPLETED,
        seconds: Int = 1_500,
        day: LocalDate = today,
        taskId: Long? = 1L,
        title: String? = "写季度报告"
    ) = FocusSession(
        taskId = taskId,
        taskTitleSnapshot = title,
        mode = mode,
        startedAt = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        endedAt = day.atTime(9, 30).atZone(zone).toInstant().toEpochMilli(),
        plannedSeconds = seconds,
        actualFocusSeconds = seconds,
        status = status
    )

    @Test
    fun theTotalsFollowTheHeaderRules() {
        val report = focusReportOf(
            sessions = listOf(
                session(seconds = 1_500),
                session(seconds = 600, day = today.minusDays(1), taskId = 2L, title = "整理周报"),
                // 中断的算时长，不算番茄
                session(status = FocusStatus.INTERRUPTED, seconds = 300),
                // 倒计时不是番茄
                session(mode = FocusMode.COUNTDOWN, seconds = 900)
            ),
            today = today,
            zone = zone
        )

        assertEquals(1_500 + 300 + 900, report.today.seconds)
        assertEquals("只有完成的番茄钟才算番茄", 1, report.today.pomodoros)
        assertEquals("本周从周一开始", 3_300, report.week.seconds)
        assertEquals("累计就是所有 session 的时长", 3_300, report.total.seconds)
        // 今天 1 个、昨天 1 个；中断的那个和倒计时都不算。
        assertEquals(2, report.total.pomodoros)
    }

    @Test
    fun theTrendEndsTodayAndHasOneEntryPerDay() {
        val report = focusReportOf(
            sessions = listOf(
                session(seconds = 1_200, day = today.minusDays(3)),
                session(seconds = 600)
            ),
            today = today,
            zone = zone
        )

        assertEquals(7, report.lastDays.size)
        assertEquals(today, report.lastDays.last().date)
        assertEquals(today.minusDays(6), report.lastDays.first().date)
        assertEquals(1_200, report.lastDays[3].seconds)
        assertEquals("没有记录的那天就是零", 0, report.lastDays[1].seconds)
        assertEquals(600, report.lastDays.last().seconds)
    }

    @Test
    fun theHeatmapIsWholeWeeksEndingWithThisOne() {
        val report = focusReportOf(
            sessions = listOf(session(seconds = 900)),
            today = today,
            zone = zone
        )

        assertEquals(12, report.heatmapWeeks.size)
        assertTrue(report.heatmapWeeks.all { it.size == 7 })
        assertEquals("每周从周一开始", DayOfWeek.MONDAY, report.heatmapWeeks.first().first().date.dayOfWeek)
        // 今天（周四）落在最后一周的第四个格子里，之后的格子是空的。
        val lastWeek = report.heatmapWeeks.last()
        assertEquals(today, lastWeek[today.dayOfWeek.value - 1].date)
        assertEquals(900, lastWeek[today.dayOfWeek.value - 1].seconds)
        lastWeek.drop(today.dayOfWeek.value).forEach { day ->
            assertTrue("今天之后的格子不该有记录", day.date.isAfter(today))
            assertEquals(0, day.seconds)
        }
    }

    @Test
    fun tasksAreRankedByTimeAndARenameStaysOneEntry() {
        val report = focusReportOf(
            sessions = listOf(
                session(seconds = 600, taskId = 1L, title = "写季度报告"),
                session(seconds = 900, taskId = 1L, title = "写季度报告（改名了）"),
                session(seconds = 1_200, taskId = 2L, title = "整理周报")
            ),
            today = today,
            zone = zone,
            topTasks = 5
        )

        assertEquals(2, report.topTasks.size)
        // 同一个任务的两段专注合起来 1500 秒，排在 1200 秒的整理周报前面。
        assertEquals("写季度报告", report.topTasks.first().title)
        assertEquals(1_500, report.topTasks.first().seconds)
        assertEquals(2, report.topTasks.first().sessions)
        assertEquals("同一个任务的多段专注要合并成一条", 1_200, report.topTasks[1].seconds)
        assertEquals("用第一次见到的标题", "整理周报", report.topTasks[1].title)
    }

    @Test
    fun theRankingOnlyKeepsTheTopFew() {
        val report = focusReportOf(
            sessions = (1L..7L).map { id ->
                session(seconds = 300, taskId = id, title = "任务$id")
            },
            today = today,
            zone = zone
        )

        assertEquals(5, report.topTasks.size)
        assertTrue(report.topTasks.all { it.seconds == 300 })
    }
}
