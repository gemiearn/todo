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
 * 提醒时刻的计算规则（需求文档 §6），不碰数据库也不碰设备：时钟和时区都由断言写死。
 *
 * 这几条是最容易含糊过去的地方，所以逐条钉住：
 *  - 锚点顺序是「截止优先、计划其次」，都只有日期时取当天 09:00；
 *  - 没有日期就不允许设提醒；
 *  - 改期时相对提醒跟着走，自定义时刻原地不动。
 */
class ReminderTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun atTime(dateTime: LocalDateTime, zone: ZoneId = shanghai): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    @Test
    fun aTaskWithoutAnyDateCannotHaveAReminder() {
        assertFalse(Reminder.canRemind(plannedDate = null, dueDate = null))
        assertNull(
            Reminder.anchorAt(
                plannedDate = null,
                plannedTime = null,
                dueDate = null,
                dueTime = null
            )
        )
        assertNull(
            Reminder.instantOf(
                kind = ReminderKind.ON_TIME,
                plannedDate = null,
                plannedTime = null,
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
    }

    @Test
    fun theDueDateWinsOverThePlannedDate() {
        // 同一天既有计划时间 10:00、又有截止时间 18:00：提醒挂在截止那一刻上。
        val at = Reminder.instantOf(
            kind = ReminderKind.ON_TIME,
            plannedDate = today,
            plannedTime = LocalTime.of(10, 0),
            dueDate = today,
            dueTime = LocalTime.of(18, 0),
            zone = shanghai
        )
        assertEquals(atTime(today.atTime(18, 0)), at?.toEpochMilli())
    }

    @Test
    fun aPlannedDateWithoutATimeFallsBackToNineInTheMorning() {
        val at = Reminder.instantOf(
            kind = ReminderKind.ON_TIME,
            plannedDate = today,
            plannedTime = null,
            dueDate = null,
            dueTime = null,
            zone = shanghai
        )
        assertEquals(atTime(today.atTime(Reminder.DEFAULT_HOUR, 0)), at?.toEpochMilli())
    }

    @Test
    fun anEarlyReminderSubtractsFromTheAnchor() {
        val at = Reminder.instantOf(
            kind = ReminderKind.BEFORE_15,
            plannedDate = today,
            plannedTime = LocalTime.of(9, 0),
            dueDate = null,
            dueTime = null,
            zone = shanghai
        )
        assertEquals(atTime(today.atTime(8, 45)), at?.toEpochMilli())
    }

    @Test
    fun aOneDayEarlyReminderLandsOnThePreviousDay() {
        val at = Reminder.instantOf(
            kind = ReminderKind.BEFORE_1D,
            plannedDate = today,
            plannedTime = LocalTime.of(8, 0),
            dueDate = null,
            dueTime = null,
            zone = shanghai
        )
        assertEquals(atTime(today.minusDays(1).atTime(8, 0)), at?.toEpochMilli())
    }

    @Test
    fun changingTheDateMovesARelativeReminderWithIt() {
        val moved = today.plusDays(3)
        val at = Reminder.epochMillisFor(
            kind = ReminderKind.BEFORE_15,
            customAt = null,
            plannedDate = moved,
            plannedTime = LocalTime.of(20, 0),
            dueDate = null,
            dueTime = null,
            zone = shanghai
        )
        assertEquals(atTime(moved.atTime(19, 45)), at)
    }

    @Test
    fun aCustomReminderStaysWhereTheUserPutIt() {
        val picked = atTime(today.plusDays(10).atTime(7, 30))
        val at = Reminder.epochMillisFor(
            kind = ReminderKind.CUSTOM,
            customAt = picked,
            // 任务后来被挪到了别的日子：自定义提醒不该跟着漂。
            plannedDate = today.plusDays(30),
            plannedTime = LocalTime.of(12, 0),
            dueDate = null,
            dueTime = null,
            zone = shanghai
        )
        assertEquals(picked, at)
    }

    @Test
    fun noKindMeansNoReminder() {
        assertNull(
            Reminder.epochMillisFor(
                kind = null,
                customAt = 123L,
                plannedDate = today,
                plannedTime = LocalTime.of(9, 0),
                dueDate = null,
                dueTime = null,
                zone = shanghai
            )
        )
    }

    @Test
    fun anUnknownKindFromStorageReadsAsNoReminder() {
        assertNull(ReminderKind.fromStored("SOMETHING_NEW"))
        assertNull(ReminderKind.fromStored(null))
        assertEquals(ReminderKind.BEFORE_30, ReminderKind.fromStored("BEFORE_30"))
    }
}
