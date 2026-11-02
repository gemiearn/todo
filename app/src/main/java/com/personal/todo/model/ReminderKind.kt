package com.personal.todo.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 一个任务的提醒是「相对哪个时刻算出来的」（说明文档 §6：首版一个任务一个提醒）。
 *
 * 只存偏移、不存文案：界面上的「提前 15 分钟」由字符串资源翻译，枚举本身不认识中文
 * （spec 7.2 要求所有面向用户的文字都在资源里）。
 *
 * [CUSTOM] 是唯一一个「不跟日期走」的取值：用户在提醒面板里直接挑了一个时刻，那个时刻
 * 就原样存着，任务改期也不动它。其余几种都是锚点减去偏移，改期后必须重算——否则把任务
 * 从周三挪到周五，提醒会留在周三。
 */
enum class ReminderKind(
    /** 相对锚点提前多少分钟；[CUSTOM] 没有偏移，它由用户直接给时刻。 */
    val minutesBefore: Long?
) {
    ON_TIME(0L),
    BEFORE_5(5L),
    BEFORE_15(15L),
    BEFORE_30(30L),
    BEFORE_60(60L),
    BEFORE_1D(24L * 60L),
    CUSTOM(null);

    companion object {
        /**
         * 按名字读回来。认不出来就当作「没有提醒」——宁可少响一次，也不能因为一个
         * 来自未来的枚举名把整条任务读不出来。
         */
        fun fromStored(value: String?): ReminderKind? =
            value?.let { stored -> entries.firstOrNull { it.name == stored } }
    }
}

/**
 * 提醒时刻的计算规则，写成一个纯函数集合，方便不碰数据库直接测。
 *
 * **锚点（定死，避免歧义）**：有截止日期就用截止日期；没有就用计划日期；两者都没有
 * 就不允许设提醒。锚点的时刻分别是 `dueTime` / `plannedTime`，缺省时取当天
 * [DEFAULT_HOUR] 点——「今天要交」这种只有日期没钟点的任务，早上提醒一次是合理的，
 * 而「提前 15 分钟」在没钟点时可算，界面也会把算出来的具体时刻显示出来。
 */
object Reminder {

    const val DEFAULT_HOUR = 9

    /** 锚点时刻；一个日期都没有时返回 null，也就是「这条任务没法设提醒」。 */
    fun anchorAt(
        plannedDate: LocalDate?,
        plannedTime: LocalTime?,
        dueDate: LocalDate?,
        dueTime: LocalTime?
    ): LocalDateTime? {
        val date = dueDate ?: plannedDate ?: return null
        val time = (if (dueDate != null) dueTime else plannedTime)
            ?: LocalTime.of(DEFAULT_HOUR, 0)
        return date.atTime(time)
    }

    /** 这条任务现在能不能设提醒：至少要有一个日期可以当锚点。 */
    fun canRemind(plannedDate: LocalDate?, dueDate: LocalDate?): Boolean =
        (dueDate ?: plannedDate) != null

    /** 相对提醒算出来的绝对时刻；[ReminderKind.CUSTOM] 不在这里算。 */
    fun instantOf(
        kind: ReminderKind,
        plannedDate: LocalDate?,
        plannedTime: LocalTime?,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        zone: ZoneId
    ): Instant? {
        val offset = kind.minutesBefore ?: return null
        val anchor = anchorAt(plannedDate, plannedTime, dueDate, dueTime) ?: return null
        return anchor.minusMinutes(offset).atZone(zone).toInstant()
    }

    /**
     * 落库要存的那个绝对毫秒。
     *
     * [kind] 为空就是没有提醒。[ReminderKind.CUSTOM] 直接用 [customAt]（用户挑的时刻），
     * 其余按当前日期重算——新建和改期走的都是它，所以「把任务从周三挪到周五，提醒跟着
     * 挪到周五」是同一个函数保证的；而用户手挑的那个时刻不会被顺手改掉。
     */
    fun epochMillisFor(
        kind: ReminderKind?,
        customAt: Long?,
        plannedDate: LocalDate?,
        plannedTime: LocalTime?,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        zone: ZoneId
    ): Long? {
        if (kind == null) return null
        if (kind == ReminderKind.CUSTOM) return customAt
        return instantOf(kind, plannedDate, plannedTime, dueDate, dueTime, zone)?.toEpochMilli()
    }
}
