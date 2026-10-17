package com.personal.todo.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date boundaries of spec section 4.1, tested without a database or a
 * device: every assertion fixes both the clock and the time zone.
 */
class ScheduleTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun atTime(dateTime: LocalDateTime, zone: ZoneId = shanghai): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    private val dueToday: LocalDate = LocalDate.of(2026, 10, 14)

    // ------------------------------------------------------------ 逾期判定

    @Test
    fun aTaskWithoutAnyDateIsNeverOverdue() {
        val farFuture = atTime(LocalDateTime.of(2030, 1, 1, 0, 0))

        assertFalse(
            Schedule.isOverdue(
                nowMillis = farFuture,
                plannedDate = null,
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
        assertNull(Schedule.overdueInstant(null, null, null, shanghai))
        assertNull(Schedule.dueInstant(null, LocalTime.of(9, 0), shanghai))
    }

    @Test
    fun aPlannedDateWithoutADueDateBecomesOverdueAfterThatDayEnds() {
        val plannedDate = dueToday
        val duringTheDay = atTime(LocalDateTime.of(2026, 10, 14, 18, 0))
        val nextMorning = atTime(LocalDateTime.of(2026, 10, 15, 0, 0, 1))

        assertFalse(
            "10:00 是开始时间，不是当天的截止时间",
            Schedule.isOverdue(
                nowMillis = duringTheDay,
                plannedDate = plannedDate,
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
        assertTrue(
            Schedule.isOverdue(
                nowMillis = nextMorning,
                plannedDate = plannedDate,
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
    }

    @Test
    fun anExplicitDueDateWinsOverThePlannedDateFallback() {
        val now = atTime(LocalDateTime.of(2026, 10, 14, 18, 0))

        assertFalse(
            "计划日期已过，但显式截止日期还没到",
            Schedule.isOverdue(
                nowMillis = now,
                plannedDate = dueToday.minusDays(1),
                dueDate = dueToday.plusDays(1),
                dueTime = null,
                zone = shanghai
            )
        )
        assertTrue(
            "显式截止时刻已过，即使计划日期还在未来",
            Schedule.isOverdue(
                nowMillis = now,
                plannedDate = dueToday.plusDays(1),
                dueDate = dueToday,
                dueTime = LocalTime.of(12, 0),
                zone = shanghai
            )
        )
    }

    @Test
    fun aDueDateWithoutATimeIsDueAtTheEndOfThatLocalDay() {
        val oneSecondBeforeMidnight = atTime(LocalDateTime.of(2026, 10, 14, 23, 59, 59))
        val midnight = atTime(LocalDateTime.of(2026, 10, 15, 0, 0, 0))

        assertFalse(
            "截止日当天未结束前不应算逾期",
            Schedule.isOverdue(
                oneSecondBeforeMidnight,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = null,
                zone = shanghai
            )
        )
        assertTrue(
            "进入次日零点即算逾期",
            Schedule.isOverdue(
                midnight,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = null,
                zone = shanghai
            )
        )
    }

    @Test
    fun aDueDateWithATimeIsOnlyOverdueAfterThatMoment() {
        val time = LocalTime.of(12, 20)
        val exactMoment = atTime(LocalDateTime.of(2026, 10, 14, 12, 20, 0))
        val oneMinuteLater = atTime(LocalDateTime.of(2026, 10, 14, 12, 21, 0))

        assertFalse(
            Schedule.isOverdue(
                exactMoment,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = time,
                zone = shanghai
            )
        )
        assertTrue(
            Schedule.isOverdue(
                oneMinuteLater,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = time,
                zone = shanghai
            )
        )
    }

    @Test
    fun theDayADateFallsOnIsDecidedInTheDeviceTimeZone() {
        // 2026-10-15 01:00 in Shanghai is still 2026-10-14 17:00 in UTC.
        val moment = atTime(LocalDateTime.of(2026, 10, 15, 1, 0), shanghai)

        assertTrue(
            Schedule.isOverdue(
                moment,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = null,
                zone = shanghai
            )
        )
        assertFalse(
            "按 UTC 解释时这一天还没结束",
            Schedule.isOverdue(
                moment,
                plannedDate = null,
                dueDate = dueToday,
                dueTime = null,
                zone = utc
            )
        )
    }

    @Test
    fun aPureDateIsNotShiftedByATimeZoneChange() {
        // The same stored date, evaluated in two zones, is still that date -
        // only the moment it ends differs.
        assertEquals(dueToday, Schedule.todayOf(atTime(LocalDateTime.of(2026, 10, 14, 12, 0)), shanghai))
        assertEquals(
            dueToday,
            Schedule.todayOf(atTime(LocalDateTime.of(2026, 10, 14, 23, 30)), utc)
        )
    }

    // ------------------------------------------------------------- 相对日期

    @Test
    fun relativeLabelsCoverTodayTomorrowWeekdayAndPlainDate() {
        val today = dueToday

        assertEquals(DateLabel.None, Schedule.dateLabel(null, today))
        assertEquals(DateLabel.Today, Schedule.dateLabel(today, today))
        assertEquals(DateLabel.Tomorrow, Schedule.dateLabel(today.plusDays(1), today))
        assertEquals(DateLabel.Yesterday, Schedule.dateLabel(today.minusDays(1), today))
        assertEquals(
            DateLabel.Weekday(today.plusDays(3).dayOfWeek),
            Schedule.dateLabel(today.plusDays(3), today)
        )
        assertEquals(DateLabel.MonthDay(11, 1), Schedule.dateLabel(LocalDate.of(2026, 11, 1), today))
    }

    @Test
    fun dueLabelsAddTheOverdueState() {
        val now = atTime(LocalDateTime.of(2026, 10, 14, 18, 0))

        assertEquals(DateLabel.None, Schedule.dueLabel(now, null, null, shanghai))
        assertEquals(DateLabel.Today, Schedule.dueLabel(now, dueToday, null, shanghai))
        assertEquals(
            DateLabel.OverdueDays(3),
            Schedule.dueLabel(now, dueToday.minusDays(3), null, shanghai)
        )
        assertEquals(
            DateLabel.Overdue,
            Schedule.dueLabel(now, dueToday, LocalTime.of(12, 0), shanghai)
        )
        assertEquals(
            "还剩的日期仍然显示为当天",
            DateLabel.Today,
            Schedule.dueLabel(now, dueToday, LocalTime.of(20, 0), shanghai)
        )
        assertEquals(
            "没有截止日期时，计划日期结束后显示逾期",
            DateLabel.OverdueDays(3),
            Schedule.overdueLabel(
                nowMillis = now,
                plannedDate = dueToday.minusDays(3),
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
    }

    // ------------------------------------------------------------- 倒计时

    @Test
    fun onlyADueMomentThatIsStillAheadHasACountdown() {
        val now = atTime(LocalDateTime.of(2026, 10, 14, 9, 0))

        assertEquals(Countdown.None, Schedule.countdownTo(now, null, null, shanghai))
        assertEquals(
            "只有截止日期没有时刻，没有可以倒计时的时刻",
            Countdown.None,
            Schedule.countdownTo(now, dueToday, null, shanghai)
        )
        assertEquals(
            "已经逾期的不再显示倒计时，改显示逾期标记",
            Countdown.None,
            Schedule.countdownTo(now, dueToday, LocalTime.of(8, 0), shanghai)
        )
    }

    @Test
    fun theCountdownSwitchesFromMinutesToHoursToDays() {
        val now = atTime(LocalDateTime.of(2026, 10, 14, 9, 0))

        assertEquals(
            Countdown.Minutes(30),
            Schedule.countdownTo(now, dueToday, LocalTime.of(9, 30), shanghai)
        )
        assertEquals(
            "满一小时就不再按分钟显示",
            Countdown.Hours(1),
            Schedule.countdownTo(now, dueToday, LocalTime.of(10, 0), shanghai)
        )
        assertEquals(
            Countdown.Hours(5),
            Schedule.countdownTo(now, dueToday, LocalTime.of(14, 0), shanghai)
        )
        assertEquals(
            "不足一天的仍按小时显示",
            Countdown.Hours(23),
            Schedule.countdownTo(now, dueToday.plusDays(1), LocalTime.of(8, 59), shanghai)
        )
        assertEquals(
            Countdown.Days(1),
            Schedule.countdownTo(now, dueToday.plusDays(1), LocalTime.of(9, 30), shanghai)
        )
    }

}
