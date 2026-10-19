package com.personal.todo.model

import com.personal.todo.data.db.FocusSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aggregation behind the 统计 page.
 *
 * These are the numbers a user compares week over week, so they are pinned down
 * away from any UI: a period is a plain date range, every chart is derived from
 * the sessions, and 休息 - a phase of the timer, never a session - can therefore
 * not appear in any of them.
 *
 * [today] is a Friday (2026-10-15), so the week around it starts on Monday the
 * 12th.
 */
class FocusReportTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 15)

    private fun session(
        mode: FocusMode = FocusMode.POMODORO,
        status: FocusStatus = FocusStatus.COMPLETED,
        seconds: Int = 1_500,
        day: LocalDate = today,
        startMinuteOfDay: Int = 9 * 60,
        endMinuteOfDay: Int = startMinuteOfDay + 30,
        taskId: Long? = 1L,
        title: String? = "写季度报告",
        listName: String? = "工作"
    ): FocusSession {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return FocusSession(
            taskId = taskId,
            taskTitleSnapshot = title,
            listNameSnapshot = listName,
            mode = mode,
            startedAt = dayStart + startMinuteOfDay * 60_000L,
            endedAt = dayStart + endMinuteOfDay * 60_000L,
            plannedSeconds = seconds,
            actualFocusSeconds = seconds,
            status = status
        )
    }

    @Test
    fun thePeriodsFollowTheCalendar() {
        assertEquals(
            FocusPeriod(today, today),
            focusPeriodOf(FocusPeriodUnit.DAY, today)
        )

        val week = focusPeriodOf(FocusPeriodUnit.WEEK, today)
        assertEquals("周从周一开始", LocalDate.of(2026, 10, 12), week.start)
        assertEquals(LocalDate.of(2026, 10, 18), week.end)
        assertEquals(7, week.days)

        val month = focusPeriodOf(FocusPeriodUnit.MONTH, today)
        assertEquals(LocalDate.of(2026, 10, 1), month.start)
        assertEquals(LocalDate.of(2026, 10, 31), month.end)
        assertEquals(31, month.days)
        assertTrue(month.contains(today))
        assertTrue("九月的那天不属于十月", !month.contains(LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun steppingMovesExactlyOnePeriod() {
        val week = focusPeriodOf(FocusPeriodUnit.WEEK, today)
        assertEquals(LocalDate.of(2026, 10, 19), week.shift(FocusPeriodUnit.WEEK, 1).start)
        assertEquals(LocalDate.of(2026, 10, 5), week.shift(FocusPeriodUnit.WEEK, -1).start)

        val month = focusPeriodOf(FocusPeriodUnit.MONTH, today)
        assertEquals(LocalDate.of(2026, 11, 1), month.shift(FocusPeriodUnit.MONTH, 1).start)
        assertEquals(LocalDate.of(2026, 11, 30), month.shift(FocusPeriodUnit.MONTH, 1).end)
        assertEquals(LocalDate.of(2026, 9, 1), month.shift(FocusPeriodUnit.MONTH, -1).start)

        val day = focusPeriodOf(FocusPeriodUnit.DAY, today)
        assertEquals(LocalDate.of(2026, 10, 16), day.shift(FocusPeriodUnit.DAY, 1).start)
    }

    @Test
    fun theAchievementRateIsTheCompletedShareOfEverything() {
        val totals = focusPeriodTotals(
            sessions = listOf(
                session(seconds = 1_500),
                // 中断的算真实专注时长，进分母不进分子
                session(status = FocusStatus.INTERRUPTED, seconds = 500),
                // 倒计时也计入时长，但不是番茄
                session(mode = FocusMode.COUNTDOWN, seconds = 900),
                // 昨天的不算今天的
                session(seconds = 600, day = today.minusDays(1))
            ),
            period = focusPeriodOf(FocusPeriodUnit.DAY, today),
            zone = zone
        )

        assertEquals(2_400, totals.completedSeconds)
        assertEquals(500, totals.interruptedSeconds)
        assertEquals(2_900, totals.seconds)
        assertEquals(2_400f / 2_900f, totals.completionRate, 0.0001f)
        assertEquals("只有完成的番茄钟才算番茄", 1, totals.pomodoros)
    }

    @Test
    fun theHourBarsOnlySpreadTheSegmentsThatActuallyRan() {
        // 09:00 跑了 15 分钟 → 暂停一小时 → 10:00 再跑 15 分钟。暂停那一小时不该落在任何小时上。
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nine = dayStart + 9 * 3_600_000L
        val ten = dayStart + 10 * 3_600_000L
        val quarter = 900_000L
        val session = session(
            seconds = 1_800,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 10 * 60 + 15
        ).copy(
            focusSegments = FocusSegments.append(
                FocusSegments.append(null, nine, nine + quarter),
                ten,
                ten + quarter
            )
        )

        val bars = focusHourBars(
            sessions = listOf(session),
            period = focusPeriodOf(FocusPeriodUnit.DAY, today),
            zone = zone
        )

        assertEquals("9 点只拿到真正跑的那 15 分钟", 900, bars.first { it.hour == 9 }.seconds)
        assertEquals("10 点也拿到 15 分钟", 900, bars.first { it.hour == 10 }.seconds)
        assertEquals("暂停的那一段什么都不落", 1_800, bars.sumOf { it.seconds })
    }

    @Test
    fun noSessionsAtAllMeansNoAchievementRate() {
        val totals = focusPeriodTotals(emptyList(), focusPeriodOf(FocusPeriodUnit.DAY, today), zone)
        assertEquals(0, totals.seconds)
        assertEquals("没有记录就不是 0% 达成，而是没有数据", 0f, totals.completionRate, 0f)
    }

    @Test
    fun allTimeTotalsCountEverySession() {
        val totals = focusAllTimeTotals(
            listOf(
                session(seconds = 1_500),
                session(seconds = 1_500, day = LocalDate.of(2026, 3, 3)),
                session(status = FocusStatus.INTERRUPTED, seconds = 600)
            )
        )
        assertEquals(3_000, totals.completedSeconds)
        assertEquals(600, totals.interruptedSeconds)
        assertEquals(2, totals.pomodoros)
    }

    @Test
    fun theTrendHasOneBarPerDayMonthAndYear() {
        val sessions = listOf(
            session(seconds = 600),
            session(seconds = 1_200, day = LocalDate.of(2026, 3, 3))
        )

        val week = focusTrendBars(sessions, FocusTrendUnit.WEEK, today, zone)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 12), week.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), week.last().date)
        assertEquals(600, week.first { it.date == today }.seconds)
        assertEquals(0, week.first { it.date == today.minusDays(1) }.seconds)

        val month = focusTrendBars(sessions, FocusTrendUnit.MONTH, today, zone)
        assertEquals("十月有 31 天", 31, month.size)
        assertEquals(600, month[today.dayOfMonth - 1].seconds)

        val year = focusTrendBars(sessions, FocusTrendUnit.YEAR, today, zone)
        assertEquals(12, year.size)
        assertEquals("每个月一根柱，柱子代表那个月的 1 号", LocalDate.of(2026, 3, 1), year[2].date)
        assertEquals(1_200, year[2].seconds)
        assertEquals(600, year[9].seconds)
    }

    @Test
    fun theDailyAverageSpreadsTheTotalOverThePeriod() {
        val period = focusPeriodOf(FocusPeriodUnit.WEEK, today)
        val totals = FocusPeriodTotals(completedSeconds = 2_100, interruptedSeconds = 0, pomodoros = 0)
        assertEquals(300, focusDailyAverage(totals, period))
    }

    @Test
    fun theRankingMergesARenamedTaskAndCanGroupByList() {
        val sessions = listOf(
            session(seconds = 600, taskId = 1L, title = "写季度报告", listName = "工作"),
            session(seconds = 900, taskId = 1L, title = "写季度报告（改名了）", listName = "个人"),
            session(seconds = 1_200, taskId = 2L, title = "整理周报", listName = "工作")
        )
        val period = focusPeriodOf(FocusPeriodUnit.DAY, today)

        val byTask = focusRanking(sessions, period, zone, FocusRankBy.TASK)
        assertEquals(2, byTask.size)
        assertEquals("同一个任务的多段专注要合并成一条", "写季度报告", byTask.first().label)
        assertEquals(1_500, byTask.first().seconds)
        assertEquals(2, byTask.first().sessions)
        assertEquals(1_200, byTask[1].seconds)

        val byList = focusRanking(sessions, period, zone, FocusRankBy.LIST)
        assertEquals(2, byList.size)
        assertEquals("工作 600 + 1200", "工作", byList.first().label)
        assertEquals(1_800, byList.first().seconds)
        assertEquals("个人", byList[1].label)
        assertEquals(900, byList[1].seconds)
    }

    @Test
    fun theRankingSaysWhenThereIsNoTaskOrNoListSnapshot() {
        val period = focusPeriodOf(FocusPeriodUnit.DAY, today)
        val orphan = session(taskId = null, title = null, listName = null)

        assertNull(focusRanking(listOf(orphan), period, zone, FocusRankBy.TASK).single().label)
        assertNull(focusRanking(listOf(orphan), period, zone, FocusRankBy.LIST).single().label)
    }

    @Test
    fun theRankingOnlyKeepsTheTopFewAndSkipsOtherPeriods() {
        val sessions = (1L..7L).map { id -> session(seconds = 300, taskId = id, title = "任务$id") } +
            session(seconds = 9_999, day = today.minusDays(1), taskId = 8L, title = "昨天的")

        val ranking = focusRanking(
            sessions,
            focusPeriodOf(FocusPeriodUnit.DAY, today),
            zone,
            FocusRankBy.TASK
        )
        assertEquals(5, ranking.size)
        assertTrue(ranking.all { it.seconds == 300 })
    }

    @Test
    fun theTimelineSpansAreMinutesOfTheDayAndSplitAcrossMidnight() {
        val spans = focusSpans(
            sessions = listOf(
                session(seconds = 3_600, startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60 + 30),
                // 23:30 开始，第二天 00:30 结束：拆成两天各一段
                session(seconds = 3_600, startMinuteOfDay = 23 * 60 + 30, endMinuteOfDay = 24 * 60 + 30),
                session(
                    seconds = 300,
                    status = FocusStatus.INTERRUPTED,
                    startMinuteOfDay = 14 * 60,
                    endMinuteOfDay = 14 * 60 + 20
                )
            ),
            period = focusPeriodOf(FocusPeriodUnit.WEEK, today),
            zone = zone
        )

        assertEquals("跨天的那段被拆成两段", 4, spans.size)
        val morning = spans.first()
        assertEquals(today, morning.date)
        assertEquals(9 * 60, morning.startMinute)
        assertEquals(10 * 60 + 30, morning.endMinute)
        assertTrue(!morning.interrupted)

        val evening = spans.first { it.startMinute == 23 * 60 + 30 }
        assertEquals(today, evening.date)
        assertEquals(24 * 60, evening.endMinute)

        val afterMidnight = spans.first { it.date == today.plusDays(1) }
        assertEquals(0, afterMidnight.startMinute)
        assertEquals(30, afterMidnight.endMinute)

        assertEquals(1, spans.count { it.interrupted })
    }

    @Test
    fun aSpanOutsideThePeriodIsNotDrawn() {
        val spans = focusSpans(
            sessions = listOf(session(seconds = 600, day = today.minusDays(10))),
            period = focusPeriodOf(FocusPeriodUnit.WEEK, today),
            zone = zone
        )
        assertTrue(spans.isEmpty())
    }

    @Test
    fun theTimelineOnlyDrawsTheRunsThatCount() {
        // 被杀 / 离开 App 那一段不计入专注，时间线上也不该被画成一段专注——否则就是
        // 「记录只有 10 分钟，横条却从 13 点横到 16 点」这种自相矛盾。
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val start = dayStart + 13 * 60 * 60_000L
        val tenMinutes = 10 * 60_000L
        val session = session(
            seconds = 600,
            startMinuteOfDay = 13 * 60,
            endMinuteOfDay = 16 * 60
        ).copy(focusSegments = FocusSegments.append(null, start, start + tenMinutes))

        val spans = focusSpans(
            sessions = listOf(session),
            period = focusPeriodOf(FocusPeriodUnit.DAY, today),
            zone = zone
        )

        assertEquals(1, spans.size)
        assertEquals(13 * 60, spans.single().startMinute)
        assertEquals(13 * 60 + 10, spans.single().endMinute)
    }

    @Test
    fun theHoursGetTheShareOfTheSpanTheyCover() {
        val bars = focusHourBars(
            sessions = listOf(
                // 09:00-11:00 中间专注了 60 分钟：按两小时各占一半摊开
                session(seconds = 3_600, startMinuteOfDay = 9 * 60, endMinuteOfDay = 11 * 60)
            ),
            period = focusPeriodOf(FocusPeriodUnit.MONTH, today),
            zone = zone
        )

        assertEquals(24, bars.size)
        assertEquals(1_800, bars[9].seconds)
        assertEquals(1_800, bars[10].seconds)
        assertEquals(0, bars[8].seconds)
        assertEquals(0, bars[11].seconds)
    }

    /**
     * 一个小时最多就是 60 分钟：一段专注再长，它摊到某一个钟点上的秒数都不会超过 3600。
     *
     * 这是「柱状图不会超过一小时」的算术根据——**前提是记录里的小段与时长一致**（改时长时
     * 两者一起改，见 `FocusRepository.updateSession`）。旧毛病是：小段只有 10 分钟、时长却
     * 改成 90 分钟，摊下来某一小时就成了 90 分钟，柱子就冲破一小时（用户 2026-10-19 报的）。
     */
    @Test
    fun anHourNeverGetsMoreThanOneHourOfFocus() {
        val bars = focusHourBars(
            sessions = listOf(
                // 09:00–10:30 真的专注了 90 分钟：小时 9 拿满 60 分钟，小时 10 拿 30 分钟。
                session(seconds = 5_400, startMinuteOfDay = 9 * 60, endMinuteOfDay = 10 * 60 + 30)
            ),
            period = focusPeriodOf(FocusPeriodUnit.MONTH, today),
            zone = zone
        )

        assertEquals("小时 9 拿满一小时", 3_600, bars[9].seconds)
        assertEquals("小时 10 拿剩下的 30 分钟", 1_800, bars[10].seconds)
        assertTrue("任何一个钟点都不会超过一小时", bars.all { it.seconds <= 3_600 })
    }

    /**
     * 小时图的纵轴跟着峰值走，但**封顶一小时**：一个钟点最多只有 60 分钟，轴长过 1h 只会
     * 把「这一小时做满了没有」弄模糊（用户 2026-10-19 要求「最多 60 分钟」）。
     */
    @Test
    fun theHourAxisFollowsThePeakButNeverPassesAnHour() {
        assertEquals("没有数据时不画轴", 0, focusHourAxisMinutes(0))
        assertEquals("10 分钟峰值 → 轴到 12 分钟", 12, focusHourAxisMinutes(600))
        assertEquals("59 分钟峰值 → 轴到 60 分钟", 60, focusHourAxisMinutes(3_540))
        assertEquals("做满一小时 → 满格", 60, focusHourAxisMinutes(3_600))
        assertEquals("就算有超过一小时的旧数据，轴也只到 60", 60, focusHourAxisMinutes(4_800))
    }

    /**
     * 改时长的上限＝到「紧随其后那条记录」开始的间距（防重叠，用户 2026-10-19 拍板）：
     * 拖过头会盖住后面那条，时间线上两段压在一起、那一小时还会被两条记录各摊一次。
     */
    @Test
    fun theLengthCapStopsAtTheNextRecordsStart() {
        val start = 1_000_000L
        val aDay = 24 * 60 * 60

        assertEquals("没有下一条 → 24 小时", aDay, focusRecordLengthCapSeconds(start, null, 600))
        assertEquals("下一条在 12 分钟后 → 最多 12 分钟", 720, focusRecordLengthCapSeconds(start, start + 720_000L, 600))
        assertEquals(
            "已经越过间距的老数据不强逼缩短，保住现值",
            5_400,
            focusRecordLengthCapSeconds(start, start + 720_000L, 5_400)
        )
        assertEquals("再远也还是 24 小时封顶", aDay, focusRecordLengthCapSeconds(start, start + 40L * 3_600_000L, 600))
    }

    @Test
    fun theYearHeatmapIsWholeWeeksOfThatYear() {
        val weeks = focusYearWeeks(listOf(session(seconds = 900)), 2026, zone)

        assertTrue("一年是 53 个周列左右", weeks.size >= 52)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals("每周从周一开始", DayOfWeek.MONDAY, weeks.first().first().date.dayOfWeek)

        val flatten = weeks.flatten()
        assertEquals(900, flatten.first { it.date == today }.seconds)
        assertEquals("今天之外的格子是空的", 0, flatten.first { it.date == today.minusDays(1) }.seconds)
        // 2026-01-01 是周四，所以第一个格子落在 2025 年，它不算 2026 的打卡。
        assertEquals(2025, weeks.first().first().date.year)
        assertEquals(0, weeks.first().first().seconds)
    }

    /**
     * 统计页「按标签筛」靠的就是这段快照编码：名字连成一串、读回来一个不少，
     * 匹配只认整名。没有标签时必须返回 null，别写一个空串把自己伪装成有标签。
     */
    @Test
    fun theTagSnapshotJoinsNamesAndMatchesWholeNamesOnly() {
        assertNull("没有标签就不写空串", FocusTagSnapshot.encode(emptyList()))
        assertNull(FocusTagSnapshot.encode(listOf("  ", "")))

        val snapshot = FocusTagSnapshot.encode(listOf("工作", "重要", "工作"))
        assertEquals("去重、去掉空白后按写进去的顺序", listOf("工作", "重要"), FocusTagSnapshot.names(snapshot))
        assertTrue(FocusTagSnapshot.matches(snapshot, "重要"))
        assertTrue(!FocusTagSnapshot.matches(snapshot, "个"))
        assertTrue("没有标签的记录不会被任何一个标签选中", !FocusTagSnapshot.matches(null, "工作"))
    }
}
