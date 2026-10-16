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
        priority: Priority = Priority.NOT_IMPORTANT_NOT_URGENT,
        createdAt: Long = id,
        plannedDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        repeatRule: RepeatRule? = null,
        recurrenceSourceId: Long? = null
    ) = Task(
        id = id,
        title = title,
        notes = notes,
        listId = 1L,
        priority = priority,
        createdAt = createdAt,
        updatedAt = createdAt,
        plannedDate = plannedDate,
        dueDate = dueDate,
        repeatRule = repeatRule,
        recurrenceSourceId = recurrenceSourceId
    )

    // ------------------------------------------------------- 重复任务的收敛

    @Test
    fun aRepeatingSeriesOnlyShowsItsNearestOpenOccurrence() {
        val source = task(1, plannedDate = LocalDate.of(2026, 10, 15), repeatRule = RepeatRule(RepeatKind.DAILY))
        val second = task(2, plannedDate = LocalDate.of(2026, 10, 16), recurrenceSourceId = 1L)
        val third = task(3, plannedDate = LocalDate.of(2026, 10, 17), recurrenceSourceId = 1L)
        val plain = task(9, title = "单独的任务")

        val visible = nearestOpenOccurrencePerSeries(listOf(source, second, third, plain))
        assertEquals("一串只露最近那条，别的任务照旧", listOf(1L, 9L), visible.map { it.id })
    }

    @Test
    fun theNextOccurrenceTakesOverWhenTheNearestOneIsGone() {
        val source = task(1, plannedDate = LocalDate.of(2026, 10, 15), repeatRule = RepeatRule(RepeatKind.DAILY))
        val second = task(2, plannedDate = LocalDate.of(2026, 10, 16), recurrenceSourceId = 1L)
        val third = task(3, plannedDate = LocalDate.of(2026, 10, 17), recurrenceSourceId = 1L)

        // 15 号做完之后打开态的查询就不会再带它进来，接上的是 16 号
        assertEquals(listOf(2L), nearestOpenOccurrencePerSeries(listOf(second, third)).map { it.id })
        // 两条都还没做时，最近的还是 15 号
        assertEquals(listOf(1L), nearestOpenOccurrencePerSeries(listOf(source, second)).map { it.id })
    }

    @Test
    fun tasksThatDoNotRepeatAreNeverGrouped() {
        val a = task(1, plannedDate = LocalDate.of(2026, 10, 15))
        val b = task(2, title = "另一个任务")
        val c = task(3, title = "第三个任务")

        assertEquals(
            "没有规则的任务一条都不会被并掉",
            listOf(1L, 2L, 3L),
            nearestOpenOccurrencePerSeries(listOf(a, b, c)).map { it.id }
        )
    }

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
        val earlierUrgent = task(4, plannedDate = LocalDate.of(2026, 10, 18), priority = Priority.IMPORTANT_URGENT)

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
        val first = task(1, priority = Priority.IMPORTANT_NOT_URGENT)
        val second = task(2, priority = Priority.IMPORTANT_NOT_URGENT)
        val urgent = task(3, priority = Priority.IMPORTANT_URGENT, plannedDate = LocalDate.of(2026, 12, 31))
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

    // ------------------------------------------------------------ 显示范围筛选

    private fun ranged(id: Long, listId: Long, priority: Priority) = Task(
        id = id,
        title = "任务 $id",
        listId = listId,
        priority = priority,
        createdAt = id,
        updatedAt = id
    )

    @Test
    fun anEmptyFilterKeepsEveryTask() {
        val tasks = listOf(ranged(1, 1L, Priority.NOT_IMPORTANT_NOT_URGENT))

        assertSame(tasks, filterByRange(tasks, TaskFilter()))
    }

    @Test
    fun theDimensionsAreAndedTogether() {
        val a = ranged(1, 1L, Priority.IMPORTANT_URGENT)
        val b = ranged(2, 2L, Priority.IMPORTANT_URGENT)
        val c = ranged(3, 1L, Priority.NOT_IMPORTANT_NOT_URGENT)

        val filter = TaskFilter(listIds = setOf(1L), priorities = setOf(Priority.IMPORTANT_URGENT))

        assertEquals(listOf(1L), filterByRange(listOf(a, b, c), filter).map { it.id })
    }

    @Test
    fun theValuesInsideOneDimensionAreOredTogether() {
        val a = ranged(1, 1L, Priority.NOT_IMPORTANT_NOT_URGENT)
        val b = ranged(2, 2L, Priority.NOT_IMPORTANT_NOT_URGENT)
        val c = ranged(3, 3L, Priority.NOT_IMPORTANT_NOT_URGENT)

        assertEquals(
            listOf(1L, 2L),
            filterByRange(listOf(a, b, c), TaskFilter(listIds = setOf(1L, 2L))).map { it.id }
        )
    }

    @Test
    fun theTagDimensionReadsTheTagsOfEachTask() {
        val a = ranged(1, 1L, Priority.NOT_IMPORTANT_NOT_URGENT)
        val b = ranged(2, 1L, Priority.NOT_IMPORTANT_NOT_URGENT)
        val tagsOf = mapOf(1L to setOf(7L), 2L to setOf(8L))

        val result = filterByRange(listOf(a, b), TaskFilter(tagIds = setOf(7L))) {
            tagsOf[it].orEmpty()
        }

        assertEquals(listOf(1L), result.map { it.id })
    }
}
