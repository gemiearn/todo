package com.personal.todo.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 重复的形状：每天 / 每两天 / 每个工作日 / 每个周末 / 每周几 / 每月几号。 */
enum class RepeatKind { DAILY, EVERY_OTHER_DAY, WEEKDAYS, WEEKENDS, WEEKLY, MONTHLY }

/**
 * 一条「每隔多久来一次」的规则。
 *
 * 算出来的每一次都会落成**独立任务**（说明文档 4.5：规则 + 每次独立任务实例，不循环
 * 复用同一条 Task），所以一次完成、一次改期、一次删除都只影响那一次。规则本身只留在
 * 带规则的那条任务上。
 *
 * [dayOfWeek] 只有 [RepeatKind.WEEKLY] 用，[dayOfMonth] 只有 [RepeatKind.MONTHLY] 用。
 * 算次时的相位（比如「每两天」从哪天起算）由调用方给的 anchor 决定，也就是带规则那
 * 条任务的开始日期。
 */
data class RepeatRule(
    val kind: RepeatKind,
    val dayOfWeek: DayOfWeek? = null,
    val dayOfMonth: Int? = null
) {

    /** 存进 `tasks.repeatRule` 的紧凑写法：`DAILY` / `WEEKLY:3` / `MONTHLY:15`。 */
    fun toStored(): String = when (kind) {
        RepeatKind.WEEKLY -> "WEEKLY:${(dayOfWeek ?: DayOfWeek.MONDAY).value}"
        RepeatKind.MONTHLY -> "MONTHLY:${(dayOfMonth ?: 1).coerceIn(1, 31)}"
        else -> kind.name
    }

    /**
     * [after]（含）之后算出来的最近一次。已经过了起点就按形状往后找，所以「每两天」
     * 会一直沿着 anchor 的相位走，不会因为中间一段时间没打开 app 而错位。
     *
     * [RepeatKind.MONTHLY] 遇到没有那一天的月份（31 号遇上小月）落到当月最后一天，
     * 不跳过那个月。
     */
    fun nextOnOrAfter(after: LocalDate, anchor: LocalDate): LocalDate {
        val from = if (after.isBefore(anchor)) anchor else after
        return when (kind) {
            RepeatKind.DAILY -> from

            RepeatKind.EVERY_OTHER_DAY -> {
                val days = ChronoUnit.DAYS.between(anchor, from)
                if (days <= 0L) anchor else anchor.plusDays(((days + 1) / 2) * 2)
            }

            RepeatKind.WEEKDAYS -> nextMatching(from) { it.dayOfWeek.value <= WeekdayLast }
            RepeatKind.WEEKENDS -> nextMatching(from) { it.dayOfWeek.value >= WeekendFirst }
            RepeatKind.WEEKLY -> nextMatching(from) { it.dayOfWeek == (dayOfWeek ?: DayOfWeek.MONDAY) }
            RepeatKind.MONTHLY -> nextMonthly(from)
        }
    }

    /** [from]..[to]（都含）之间的每一次，按时间升序。 */
    fun occurrencesIn(from: LocalDate, to: LocalDate, anchor: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var day = nextOnOrAfter(from, anchor)
        while (!day.isAfter(to) && dates.size < MaxOccurrences) {
            dates += day
            day = nextOnOrAfter(day.plusDays(1), anchor)
        }
        return dates
    }

    private fun nextMatching(from: LocalDate, matches: (LocalDate) -> Boolean): LocalDate {
        var day = from
        repeat(WeekDays) {
            if (matches(day)) return day
            day = day.plusDays(1)
        }
        return day
    }

    private fun nextMonthly(from: LocalDate): LocalDate {
        val wanted = (dayOfMonth ?: 1).coerceIn(1, 31)
        val thisMonth = from.withDayOfMonth(minOf(wanted, from.lengthOfMonth()))
        if (!thisMonth.isBefore(from)) return thisMonth
        val nextMonth = from.plusMonths(1).withDayOfMonth(1)
        return nextMonth.withDayOfMonth(minOf(wanted, nextMonth.lengthOfMonth()))
    }

    companion object {
        fun fromStored(value: String?): RepeatRule? {
            val stored = value?.takeIf { it.isNotBlank() } ?: return null
            val name = stored.substringBefore(':')
            val argument = stored.substringAfter(':', "").toIntOrNull()
            return when (name) {
                RepeatKind.WEEKLY.name -> argument
                    ?.let { RepeatRule(RepeatKind.WEEKLY, dayOfWeek = DayOfWeek.of(it.coerceIn(1, WeekDays))) }

                RepeatKind.MONTHLY.name -> argument
                    ?.let { RepeatRule(RepeatKind.MONTHLY, dayOfMonth = it.coerceIn(1, 31)) }

                else -> RepeatKind.entries.firstOrNull { it.name == name }?.let { RepeatRule(it) }
            }
        }
    }
}

private const val WeekDays = 7
private const val WeekdayLast = 5
private const val WeekendFirst = 6

/** 一次生成里的上限，防一条坏规则算不完。 */
private const val MaxOccurrences = 400
