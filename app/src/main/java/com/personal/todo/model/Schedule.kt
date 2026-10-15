package com.personal.todo.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The date and time rules of spec section 4.1, kept as pure functions so they
 * can be tested without a database, a UI or the real clock.
 *
 * The rules implemented here:
 *  - a task without a due date can never become overdue;
 *  - a due date without a time is due at the end of that local day, a due date
 *    with a time is due at that moment;
 *  - an overdue task is never postponed automatically - nothing in this file
 *    changes a date, it only reads it;
 *  - the day a date falls on is decided in the device's time zone.
 */
object Schedule {

    /**
     * The instant at which [dueDate] turns into "overdue", or null when the
     * task has no due date (such a task is never overdue).
     */
    fun dueInstant(dueDate: LocalDate?, dueTime: LocalTime?, zone: ZoneId): Instant? {
        if (dueDate == null) return null
        return dueDate.atTime(dueTime ?: LocalTime.MAX).atZone(zone).toInstant()
    }

    fun isOverdue(nowMillis: Long, dueDate: LocalDate?, dueTime: LocalTime?, zone: ZoneId): Boolean {
        val due = dueInstant(dueDate, dueTime, zone) ?: return false
        return nowMillis > due.toEpochMilli()
    }

    fun todayOf(nowMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    /** Whole days from [today] to [date]; negative when the date has passed. */
    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /**
     * Label for a planned or upcoming date. Past dates fall back to the plain
     * calendar date, because only a due date can make a task overdue.
     */
    fun dateLabel(date: LocalDate?, today: LocalDate): DateLabel {
        if (date == null) return DateLabel.None
        return when (val days = daysUntil(date, today)) {
            0L -> DateLabel.Today
            1L -> DateLabel.Tomorrow
            -1L -> DateLabel.Yesterday
            in 2L..6L -> DateLabel.Weekday(date.dayOfWeek)
            else -> DateLabel.MonthDay(date.monthValue, date.dayOfMonth)
        }
    }

    /**
     * How long is left until a due moment - the countdown badge of a task card.
     *
     * Only a due date *with* a time has something to count down to: "due at the
     * end of the day" carries no moment, and an overdue task shows its overdue
     * marker instead of a countdown. Nothing here changes a date.
     */
    fun countdownTo(nowMillis: Long, dueDate: LocalDate?, dueTime: LocalTime?, zone: ZoneId): Countdown {
        if (dueDate == null || dueTime == null) return Countdown.None
        val due = dueInstant(dueDate, dueTime, zone) ?: return Countdown.None
        val minutes = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(nowMillis), due)
        return when {
            minutes <= 0L -> Countdown.None
            minutes < 60L -> Countdown.Minutes(minutes)
            minutes < MINUTES_PER_DAY -> Countdown.Hours(minutes / 60L)
            else -> Countdown.Days(minutes / MINUTES_PER_DAY)
        }
    }

    private const val MINUTES_PER_DAY = 24L * 60L

    /** Label for a due date, including the overdue state. */
    fun dueLabel(nowMillis: Long, dueDate: LocalDate?, dueTime: LocalTime?, zone: ZoneId): DateLabel {
        if (dueDate == null) return DateLabel.None
        if (!isOverdue(nowMillis, dueDate, dueTime, zone)) {
            return dateLabel(dueDate, todayOf(nowMillis, zone))
        }
        // Due today but the time has passed: overdue, yet "0 days" would read
        // wrong, so it gets the plain overdue marker.
        val days = ChronoUnit.DAYS.between(dueDate, todayOf(nowMillis, zone))
        return if (days <= 0L) DateLabel.Overdue else DateLabel.OverdueDays(days.toInt())
    }
}

/**
 * The relative date labels a card can show. The labels are resolved to Chinese
 * text by the UI layer, so the wording stays in string resources (spec 7.2).
 */
sealed interface DateLabel {
    /** No date at all. */
    data object None : DateLabel

    data object Today : DateLabel

    data object Tomorrow : DateLabel

    data object Yesterday : DateLabel

    /** Overdue today: the time has passed, but it is still the due date. */
    data object Overdue : DateLabel

    data class Weekday(val dayOfWeek: DayOfWeek) : DateLabel

    data class MonthDay(val month: Int, val day: Int) : DateLabel

    data class OverdueDays(val days: Int) : DateLabel
}

/** Time left until a due moment, as shown next to the date of a task card. */
sealed interface Countdown {
    /** Nothing to count down to: no due date, no due time, or already overdue. */
    data object None : Countdown

    data class Minutes(val minutes: Long) : Countdown

    data class Hours(val hours: Long) : Countdown

    data class Days(val days: Long) : Countdown
}
