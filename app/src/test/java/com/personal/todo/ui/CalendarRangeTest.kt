package com.personal.todo.ui

import com.personal.todo.data.db.Task
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a task reaches in the calendar views.
 *
 * A task that has both a planned date and a due date is on every day between the
 * two - in the month grid dots, in the 周 / 三日 columns and in the year overview,
 * which all read the same map. The rule is pinned down here, away from any UI, so
 * a rewrite of a view cannot quietly turn a span back into a single day.
 */
class CalendarRangeTest {

    private val start = LocalDate.of(2026, 7, 20)
    private val end = LocalDate.of(2026, 7, 25)

    private fun task(planned: LocalDate?, due: LocalDate?) = Task(
        id = 1L,
        title = "跨期任务",
        listId = 1L,
        createdAt = 0L,
        updatedAt = 0L,
        plannedDate = planned,
        dueDate = due
    )

    @Test
    fun aTaskWithBothDatesCoversEveryDayBetweenThem() {
        val byDate = tasksByDate(listOf(task(start, end)), start, end)

        assertEquals(6, byDate.size)
        var day = start
        while (!day.isAfter(end)) {
            assertTrue("$day 应该摊到这个任务", byDate[day]?.single()?.title == "跨期任务")
            day = day.plusDays(1)
        }
    }

    @Test
    fun aTaskWithOneDateSitsOnThatDayOnly() {
        assertEquals(setOf(start), tasksByDate(listOf(task(start, null)), start, end).keys)
        assertEquals(setOf(end), tasksByDate(listOf(task(null, end)), start, end).keys)
    }

    /** A view only ever walks its own days, however far a task reaches. */
    @Test
    fun theSpanIsClampedToTheDaysBeingDrawn() {
        val byDate = tasksByDate(listOf(task(start, end)), LocalDate.of(2026, 7, 23), end)

        assertEquals(setOf(LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 24), end), byDate.keys)
    }

    /** The two dates are plain facts: nothing assumes which one comes first. */
    @Test
    fun theEndsMayBeGivenInEitherOrder() {
        assertEquals(6, tasksByDate(listOf(task(end, start)), start, end).size)
    }
}
