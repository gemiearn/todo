package com.personal.todo.model

import com.personal.todo.data.db.Task
import java.time.LocalDate

/** How a task view is ordered (spec section 4.4). */
enum class TaskSort {
    /** Default: by the date the task carries, then by priority. */
    DEFAULT,

    /** Priority first, then the date. */
    PRIORITY,

    /** Creation time - the stable order a list falls back to. */
    CREATED
}

/**
 * Search over titles and notes (spec section 4.4).
 *
 * The query is trimmed, so a query of spaces only behaves like an empty one,
 * and matching ignores case. The rows handed in are already filtered by the
 * view they came from, so a search can never pull in a task from the trash or
 * from another list.
 */
fun filterTasks(tasks: List<Task>, query: String): List<Task> {
    val needle = query.trim()
    if (needle.isEmpty()) return tasks
    return tasks.filter { task ->
        task.title.contains(needle, ignoreCase = true) ||
            task.notes?.contains(needle, ignoreCase = true) == true
    }
}

/**
 * Orders a task list. Every comparison ends with the task id, so the order is
 * total and the sort is stable: two tasks that compare equal keep the relative
 * order their query returned them in.
 */
fun sortTasks(tasks: List<Task>, sort: TaskSort): List<Task> = when (sort) {
    TaskSort.DEFAULT -> tasks.sortedWith(
        compareBy<Task>(
            { orderDate(it) == null },
            { orderDate(it) },
            { -it.priority.rank },
            { it.id }
        )
    )

    TaskSort.PRIORITY -> tasks.sortedWith(
        compareBy<Task>(
            { -it.priority.rank },
            { orderDate(it) == null },
            { orderDate(it) },
            { it.id }
        )
    )

    TaskSort.CREATED -> tasks.sortedWith(compareBy<Task>({ it.createdAt }, { it.id }))
}

/**
 * The date a task is ordered by. The planned date wins over the due date, and a
 * task without any date has no place in the dated part of the order.
 */
private fun orderDate(task: Task): LocalDate? = task.plannedDate ?: task.dueDate
