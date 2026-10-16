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

/**
 * 重复任务在库里是「每一次一条独立任务」（说明文档 4.5），但打开态的视图只该看到
 * **最近那一条还没做完的**——滴答清单也是这样：未来的那几次都在，列表里只露一个。
 *
 * 一次都没剩下（全做完了、或全被删了）时，这一串就整个从打开态视图里消失；历次完成
 * 各自留在「已完成」里，历史一点不受影响。不重复的任务原样透传，绝不会被并到一起。
 *
 * 这里只是「挑哪一条显示」，不改任何数据——所以日历仍然按天画它自己的点，那里每天
 * 一个才是对的。
 */
fun nearestOpenOccurrencePerSeries(tasks: List<Task>): List<Task> {
    val nearest = mutableMapOf<Long, Task>()
    tasks.forEach { task ->
        val series = seriesIdOf(task) ?: return@forEach
        val current = nearest[series]
        if (current == null || isEarlierThan(task, current)) nearest[series] = task
    }
    return tasks.filter { task ->
        val series = seriesIdOf(task) ?: return@filter true
        nearest[series]?.id == task.id
    }
}

/** 一条任务属于哪一串重复任务；不重复的任务是 null，永远单独出现。 */
private fun seriesIdOf(task: Task): Long? = when {
    task.recurrenceSourceId != null -> task.recurrenceSourceId
    task.repeatRule != null -> task.id
    else -> null
}

/** 同一个串里挑「最近那一条」：先比计划日期，再比 id，结果才是确定的。 */
private fun isEarlierThan(a: Task, b: Task): Boolean {
    val left = a.plannedDate
    val right = b.plannedDate
    return when {
        left != null && right != null -> if (left != right) left < right else a.id < b.id
        left != null -> true
        right != null -> false
        else -> a.id < b.id
    }
}
