package com.personal.todo.model

import com.personal.todo.data.db.Task
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The search and sort rules of spec section 4.4. These are plain JVM tests: the
 * functions only look at the rows a view already handed them.
 */
class TaskQueryTest {

    private fun task(
        id: Long,
        title: String = "任务 $id",
        notes: String? = null,
        priority: Priority = Priority.NORMAL,
        createdAt: Long = id,
        plannedDate: LocalDate? = null,
        dueDate: LocalDate? = null
    ) = Task(
        id = id,
        title = title,
        notes = notes,
        listId = 1L,
        priority = priority,
        createdAt = createdAt,
        updatedAt = createdAt,
        plannedDate = plannedDate,
        dueDate = dueDate
    )

    // ---------------------------------------------------------------- 搜索

    @Test
    fun anEmptyOrBlankQueryReturnsTheListUntouched() {
        val tasks = listOf(task(1), task(2))

        assertSame(tasks, filterTasks(tasks, ""))
        assertSame(tasks, filterTasks(tasks, "   "))
    }

    @Test
    fun theQueryIsTrimmedAndMatchedWithoutCase() {
        val tasks = listOf(task(1, title = "Write Report"), task(2, title = "读一本书"))

        assertEquals(listOf(1L), filterTasks(tasks, "  report  ").map { it.id })
    }

    @Test
    fun notesAreSearchedAsWell() {
        val tasks = listOf(
            task(1, title = "买牛奶", notes = "超市，顺便买鸡蛋"),
            task(2, title = "买菜", notes = null)
        )

        assertEquals(listOf(1L), filterTasks(tasks, "鸡蛋").map { it.id })
        assertEquals(emptyList<Long>(), filterTasks(tasks, "超市买菜").map { it.id })
    }

    @Test
    fun aQueryThatMatchesNothingReturnsAnEmptyList() {
        val tasks = listOf(task(1, title = "买牛奶"))

        assertEquals(0, filterTasks(tasks, "写报告").size)
    }

    // ---------------------------------------------------------------- 排序

    @Test
    fun theDefaultOrderIsByDateThenPriorityAndUndatedTasksComeLast() {
        val undated = task(1)
        val later = task(2, plannedDate = LocalDate.of(2026, 10, 20))
        val earlierNormal = task(3, plannedDate = LocalDate.of(2026, 10, 18))
        val earlierUrgent = task(4, plannedDate = LocalDate.of(2026, 10, 18), priority = Priority.URGENT)

        val sorted = sortTasks(listOf(undated, later, earlierNormal, earlierUrgent), TaskSort.DEFAULT)

        assertEquals(listOf(4L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun theDefaultOrderUsesTheDueDateWhenThereIsNoPlannedDate() {
        val planned = task(1, plannedDate = LocalDate.of(2026, 10, 25))
        val dueSooner = task(2, dueDate = LocalDate.of(2026, 10, 16))

        assertEquals(listOf(2L, 1L), sortTasks(listOf(planned, dueSooner), TaskSort.DEFAULT).map { it.id })
    }

    @Test
    fun orderingByPriorityIgnoresTheDateAndKeepsTheRelativeOrderOfEquals() {
        val first = task(1, priority = Priority.HIGH)
        val second = task(2, priority = Priority.HIGH)
        val urgent = task(3, priority = Priority.URGENT, plannedDate = LocalDate.of(2026, 12, 31))
        val normal = task(4)

        val sorted = sortTasks(listOf(first, second, urgent, normal), TaskSort.PRIORITY)

        assertEquals("同优先级的任务保持原有先后", listOf(3L, 1L, 2L, 4L), sorted.map { it.id })
    }

    @Test
    fun orderingByCreationTimeIsChronological() {
        val newest = task(1, createdAt = 300)
        val oldest = task(2, createdAt = 100)
        val middle = task(3, createdAt = 200)

        val sorted = sortTasks(listOf(newest, oldest, middle), TaskSort.CREATED)

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun searchingAndSortingComposeInEitherOrder() {
        val tasks = listOf(
            task(1, title = "报告 A", plannedDate = LocalDate.of(2026, 10, 20)),
            task(2, title = "报告 B", plannedDate = LocalDate.of(2026, 10, 10)),
            task(3, title = "别的")
        )

        val result = sortTasks(filterTasks(tasks, "报告"), TaskSort.DEFAULT)

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }
}
