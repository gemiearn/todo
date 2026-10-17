package com.personal.todo.data

import androidx.room.withTransaction
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import com.personal.todo.data.db.TaskList
import com.personal.todo.data.db.TaskTag
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatRule
import com.personal.todo.model.Schedule
import com.personal.todo.model.TaskEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * All task operations of phase 1 live here.
 *
 * The rules implemented below come straight from the spec:
 *  - completing stores a completion time and appends a COMPLETED event;
 *  - reopening clears the completion time, appends REOPENED and keeps the old
 *    COMPLETED event (history is append-only);
 *  - editing a completed task never reopens it automatically;
 *  - finishing a parent does not finish its subtasks and vice versa;
 *  - delete is always a soft delete, and deleting a parent also soft deletes
 *    its subtasks (restoring the parent brings them back);
 *  - deleting a list never deletes tasks: they are moved to the inbox first;
 *  - the inbox cannot be deleted.
 *
 * Every operation is a suspend function that runs off the main thread, and
 * every read that the UI observes is exposed as a [Flow].
 *
 * [now] is injectable so tests can use a deterministic clock, and [zone] so the
 * date boundary of section 4.1 can be tested across time zones.
 */
private const val RecurrenceHorizonDays = 365

class TaskRepository(
    private val db: TodoDatabase,
    private val inboxName: String,
    private val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault()
) {

    private val listDao = db.taskListDao()
    private val taskDao = db.taskDao()
    private val eventDao = db.taskEventDao()
    private val tagDao = db.tagDao()

    // ---------------------------------------------------------------- 标签
    //
    // 标签是多值属性、可以跨清单，和「清单＝归属地（单值）」分工明确（说明文档 4.4）。

    /** 全部标签，按 sortOrder / 名字排好——标签管理页和编辑器都读它。 */
    fun observeTags(): Flow<List<Tag>> = tagDao.observeTags()

    /**
     * taskId -> 这条任务身上的标签。列表卡片要给每条任务画标签小片，一次查询把
     * 全部连线拿回来在内存里分组，避免每条任务一次 join。
     */
    fun observeTagsByTask(): Flow<Map<Long, List<Tag>>> =
        combine(tagDao.observeTags(), tagDao.observeAllLinks()) { tags, links ->
            val byId = tags.associateBy { it.id }
            links.groupBy { it.taskId }
                .mapValues { (_, pairs) -> pairs.mapNotNull { byId[it.tagId] } }
        }

    /** 一条任务的标签，任务详情页读它。 */
    fun observeTagsOf(taskId: Long): Flow<List<Tag>> = tagDao.observeTagsOf(taskId)

    suspend fun createTag(name: String, color: Int): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val time = now()
        return tagDao.insert(
            Tag(name = trimmed, color = color, sortOrder = 0, createdAt = time, updatedAt = time)
        )
    }

    /** 改名与改色走同一个入口：标签就这两样可编辑的东西。 */
    suspend fun updateTag(tagId: Long, name: String, color: Int) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val tag = tagDao.getById(tagId) ?: return
        tagDao.update(tag.copy(name = trimmed, color = color, updatedAt = now()))
    }

    /**
     * 删掉标签。先把它的连线清干净，再删标签本身，都在一个事务里——绝不留一行指向
     * 已经消失的标签（两个外键都是 RESTRICT）。任务本身不受影响，只是少了这个标记。
     */
    suspend fun deleteTag(tagId: Long) = db.withTransaction {
        tagDao.clearLinksOfTag(tagId)
        tagDao.delete(tagId)
    }

    /** 把一条任务的标签整个换成 [tagIds]（先清后连，同一事务）。 */
    suspend fun setTaskTags(taskId: Long, tagIds: Collection<Long>) = db.withTransaction {
        tagDao.clearLinksOfTask(taskId)
        tagIds.toSet().forEach { tagId -> tagDao.link(TaskTag(taskId = taskId, tagId = tagId)) }
    }


    // ---------------------------------------------------------------- lists

    fun observeLists(): Flow<List<TaskList>> = listDao.observeAll()

    /**
     * Live open task count per list: the badge the drawer shows next to each
     * list. It excludes completed and soft deleted tasks as well as subtasks,
     * so it agrees with the rows the list view displays.
     */
    fun observeOpenTaskCountsByList(): Flow<Map<Long, Int>> =
        taskDao.observeOpenTaskCountsByList().map { rows ->
            rows.associate { it.listId to it.taskCount }
        }

    /** Live task count per list, used by the delete list dialog. */
    fun observeTaskCountsByList(): Flow<Map<Long, Int>> =
        taskDao.observeTaskCountsByList().map { rows ->
            rows.associate { it.listId to it.taskCount }
        }

    /** Idempotent: returns the built-in inbox, creating it if it is missing. */
    suspend fun ensureInbox(): Long {
        listDao.getInbox()?.let { return it.id }
        val time = now()
        return listDao.insert(
            TaskList(name = inboxName, isInbox = true, sortOrder = 0, createdAt = time, updatedAt = time)
        )
    }

    suspend fun createList(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val time = now()
        val nextOrder = (listDao.maxUserSortOrder() ?: 0) + 1
        return listDao.insert(
            TaskList(name = trimmed, isInbox = false, sortOrder = nextOrder, createdAt = time, updatedAt = time)
        )
    }

    suspend fun renameList(listId: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val list = listDao.getById(listId) ?: return
        listDao.update(list.copy(name = trimmed, updatedAt = now()))
    }

    /**
     * Moves a user list one step up (-1) or down (+1) in the drawer order
     * (spec section 3: lists can be created, renamed and sorted). The built-in
     * inbox is not part of the user order.
     *
     * @return true when the list actually moved; false at an edge or for the
     * inbox, so the UI can hide the button instead of failing silently.
     */
    suspend fun moveList(listId: Long, offset: Int): Boolean = db.withTransaction {
        if (offset == 0) return@withTransaction false
        val lists = listDao.getUserListsOrdered()
        val from = lists.indexOfFirst { it.id == listId }
        if (from < 0) return@withTransaction false
        val to = from + offset
        if (to !in lists.indices) return@withTransaction false
        val reordered = lists.toMutableList().apply { add(to, removeAt(from)) }
        val time = now()
        reordered.forEachIndexed { index, list ->
            if (list.sortOrder != index) listDao.update(list.copy(sortOrder = index, updatedAt = time))
        }
        true
    }

    /**
     * Deletes a list the only allowed way: every task of that list - including
     * soft deleted ones, otherwise the foreign key would block the delete - is
     * moved into the inbox, then the list row is removed. Tasks are never
     * deleted here (spec section 4.2).
     *
     * @return the number of tasks that were moved to the inbox.
     */
    suspend fun deleteListMovingTasksToInbox(listId: Long): Int {
        val list = listDao.getById(listId) ?: return 0
        require(!list.isInbox) { "收件箱不能删除" }
        val inboxId = ensureInbox()
        val tasks = taskDao.getAllInList(listId)
        val time = now()
        return db.withTransaction {
            tasks.forEach { taskDao.update(it.copy(listId = inboxId, updatedAt = time)) }
            listDao.deleteNonInboxById(listId)
            tasks.size
        }
    }

    // ---------------------------------------------------------------- tasks

    fun observeOpenTasksInList(listId: Long): Flow<List<Task>> =
        taskDao.observeOpenTasksInList(listId)

    /** Open tasks that live in the built-in inbox. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInboxTasks(): Flow<List<Task>> =
        flow { emit(ensureInbox()) }.flatMapLatest { taskDao.observeOpenTasksInList(it) }

    fun observeCompletedTasks(): Flow<List<Task>> = taskDao.observeCompletedTasks()

    /** Completed tasks of one list - the "已完成 N" group of that list view. */
    fun observeCompletedTasksInList(listId: Long): Flow<List<Task>> =
        taskDao.observeCompletedTasksInList(listId)

    /** Completed tasks of the built-in inbox, for the same folded group. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCompletedInboxTasks(): Flow<List<Task>> =
        flow { emit(ensureInbox()) }.flatMapLatest { taskDao.observeCompletedTasksInList(it) }

    /**
     * Completed tasks dated [date] - the folded group of the 今日 view and of a
     * calendar day. A finished task keeps the date it was given, so it stays in
     * the day it belongs to instead of moving to the day it was finished.
     */
    fun observeCompletedTasksOn(date: LocalDate): Flow<List<Task>> =
        taskDao.observeCompletedTasksOn(date)

    fun observeDeletedTasks(): Flow<List<Task>> = taskDao.observeDeletedTasks()

    // ----------------------------------------------------------- smart views
    //
    // 今日 / 未来 / 逾期 of spec section 4.1. They are queries over the dates,
    // never rewrites: an overdue task keeps its original date until the user
    // deliberately reschedules it.

    /** The local date "today" is resolved in the device's time zone. */
    fun today(): LocalDate = Schedule.todayOf(now(), zone)

    /** Open tasks planned for today or due today - each task appears once. */
    fun observeTodayTasks(): Flow<List<Task>> = taskDao.observeOpenTasksOnDate(today())

    /**
     * Open tasks the calendar day of [date] lists: tasks dated that day and tasks
     * whose planned..due interval covers it. The 今日 view deliberately keeps the
     * exact rule, so this is only for the calendar.
     */
    fun observeOpenTasksCovering(date: LocalDate): Flow<List<Task>> =
        taskDao.observeOpenTasksCoveringDate(date)

    /** Open tasks dated after today, planned or due. */
    fun observeFutureTasks(): Flow<List<Task>> = taskDao.observeOpenTasksAfterDate(today())

    /**
     * Open tasks past their due moment. Only the due date can make a task
     * overdue, and a due date with a time is not overdue until that moment, so
     * the exact rule is applied here rather than in SQL.
     */
    fun observeOverdueTasks(): Flow<List<Task>> =
        taskDao.observeOpenTasksDueNotAfter(today()).map { rows -> rows.filter(::isOverdue) }

    /** Every open top level task, whatever its dates - the 任务选择器 uses this. */
    fun observeAllOpenTasks(): Flow<List<Task>> = taskDao.observeAllOpenTasks()

    /**
     * Open tasks the 四象限 may show: 只有日期还没过去的那些。
     *
     * 计划日期或截止日期早于今天的任务不进四象限（那是「决定接下来做什么」的地方，过期
     * 的该去清单/逾期里收）；**没有日期的任务照旧留着**，它们只是还没安排时间。
     */
    fun observeQuadrantTasks(): Flow<List<Task>> = taskDao.observeOpenTasksNotPast(today())

    /**
     * Open tasks whose dates overlap [start]..[end] - the task dots of one
     * calendar month. Read only: it never shifts a date.
     */
    fun observeOpenTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>> =
        taskDao.observeOpenTasksSpanning(start, end)

    /**
     * Open *and* finished tasks overlapping [start]..[end]. The calendar reads
     * this one so that "显示已完成" can turn the finished ones on and off without
     * a second query; with the setting off the caller simply drops them.
     */
    fun observeTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>> =
        taskDao.observeTasksSpanning(start, end)

    /** True when this task is past its due moment. */
    fun isOverdue(task: Task): Boolean =
        Schedule.isOverdue(now(), task.dueDate, task.dueTime, zone)

    fun observeSubtasks(parentTaskId: Long): Flow<List<Task>> =
        taskDao.observeSubtasks(parentTaskId)

    /** Live subtask count per parent task; parents without subtasks are absent. */
    fun observeSubtaskCounts(): Flow<Map<Long, Int>> =
        taskDao.observeSubtaskCounts().map { rows ->
            rows.associate { it.taskId to it.subtaskCount }
        }

    fun observeEvents(taskId: Long): Flow<List<TaskEvent>> = eventDao.observeForTask(taskId)

    suspend fun getTask(taskId: Long): Task? = taskDao.getById(taskId)

    /** Live view of one task, used by the detail screen. */
    fun observeTask(taskId: Long): Flow<Task?> = taskDao.observeById(taskId)

    /**
     * Creates a task or a subtask. Titles are mandatory and trimmed; notes are
     * optional. Subtasks may only be created under a top level task of the same
     * list (one level only, spec section 4.3).
     *
     * Dates are optional and independent of each other: a planned date never
     * becomes a due date and vice versa (spec section 4.1). A due time without
     * a due date is refused, because there would be no day to attach it to.
     */
    suspend fun createTask(
        title: String,
        listId: Long,
        notes: String? = null,
        priority: Priority = Priority.DEFAULT,
        parentTaskId: Long? = null,
        plannedDate: LocalDate? = null,
        plannedTime: LocalTime? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        repeatRule: RepeatRule? = null
    ): Long {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
        require(plannedTime == null || plannedDate != null) { "计划时间必须依附于计划日期" }
        require(dueTime == null || dueDate != null) { "截止时间必须依附于截止日期" }
        val list = listDao.getById(listId) ?: throw IllegalArgumentException("清单不存在: $listId")

        if (parentTaskId != null) {
            val parent = taskDao.getById(parentTaskId)
                ?: throw IllegalArgumentException("父任务不存在: $parentTaskId")
            require(parent.parentTaskId == null) { "首版只支持一层子任务" }
            require(parent.deletedAt == null) { "父任务已被删除" }
            require(parent.listId == list.id) { "子任务必须与父任务在同一清单" }
        }

        val time = now()
        return db.withTransaction {
            val taskId = taskDao.insert(
                Task(
                    title = trimmedTitle,
                    notes = notes?.trim()?.ifEmpty { null },
                    listId = list.id,
                    parentTaskId = parentTaskId,
                    priority = priority,
                    createdAt = time,
                    updatedAt = time,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    repeatRule = repeatRule
                )
            )
            recordEvent(taskId, TaskEventType.CREATED, trimmedTitle, time)
            taskId
        }
    }

    /**
     * 把重复任务算出来的每一次落成**独立任务**（说明文档 4.5：规则 + 每次独立任务
     * 实例，不循环复用同一条 Task）。
     *
     * 只补 [today]..[today + horizonDays] 这一段：过去的日期不回溯，打开 app 不该凭空
     * 长出一排逾期任务。默认往前铺满**一年**——「每个工作日一直延续」要的就是这个，而
     * 生成器每次都从「今天」起算，所以冷启动重跑时这个窗口会随日期自然往前滑。已经
     * 生成过的那些天——以及带规则那条任务自己占的那一格——都会跳过，所以它幂等，可以
     * 在每次回到前台时放心再跑一遍。
     *
     * 生成出来的那一次带着源的标题、备注、优先级和清单，日期换成这一次的；源如果有
     * 结束日期，实例的结束日期按同样的偏移跟着走（「每周一 9:00-11:00」这种时间段也
     * 就自然重复了）。规则只留在源上，实例自己没有规则。
     *
     * @return 这次新建了几条。
     */
    suspend fun materializeRecurrences(
        today: LocalDate,
        horizonDays: Int = RecurrenceHorizonDays
    ): Int = db.withTransaction {
        val until = today.plusDays(horizonDays.toLong())
        var created = 0

        taskDao.getRepeatSources().forEach { source ->
            val rule = source.repeatRule ?: return@forEach
            val anchor = source.plannedDate ?: return@forEach

            // 已经有的（包括被删掉的、以及源自己那一格）都算「占住了」，绝不重复生成。
            val taken = taskDao.getOccurrencesOf(source.id).mapNotNull { it.plannedDate }.toMutableSet()
            if (!anchor.isBefore(today) && !anchor.isAfter(until)) taken += anchor

            val dueOffset = source.dueDate?.let { ChronoUnit.DAYS.between(anchor, it) }
            rule.occurrencesIn(today, until, anchor).forEach { date ->
                if (!taken.add(date)) return@forEach
                val time = now()
                val occurrenceId = taskDao.insert(
                    source.copy(
                        id = 0L,
                        completedAt = null,
                        deletedAt = null,
                        createdAt = time,
                        updatedAt = time,
                        plannedDate = date,
                        dueDate = dueOffset?.let { date.plusDays(it) },
                        repeatRule = null,
                        recurrenceSourceId = source.id
                    )
                )
                recordEvent(occurrenceId, TaskEventType.CREATED, source.title, time)
                created++
            }
        }
        created
    }

    suspend fun addSubtask(parentTaskId: Long, title: String): Long {
        val parent = taskDao.getById(parentTaskId)
            ?: throw IllegalArgumentException("父任务不存在: $parentTaskId")
        return createTask(title = title, listId = parent.listId, parentTaskId = parent.id)
    }

    /**
     * Updates the editable fields of a task. A completed task stays completed:
     * the user has to reopen it explicitly (spec section 4.2). Renaming is not
     * a history event - the history keeps the historical titles it already
     * stored.
     *
     * Passing null for a date clears it. Changing a date is always an explicit
     * user action; nothing here shifts a date on its own.
     */
    suspend fun updateTask(
        taskId: Long,
        title: String,
        notes: String?,
        priority: Priority,
        listId: Long,
        plannedDate: LocalDate? = null,
        plannedTime: LocalTime? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        repeatRule: RepeatRule? = null
    ) {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
        require(plannedTime == null || plannedDate != null) { "计划时间必须依附于计划日期" }
        require(dueTime == null || dueDate != null) { "截止时间必须依附于截止日期" }
        val task = taskDao.getById(taskId) ?: throw IllegalArgumentException("任务不存在: $taskId")
        // A subtask lives in the same list as its parent; only top level tasks
        // can be moved between lists.
        val targetListId = if (task.parentTaskId != null) task.listId else listId
        requireNotNull(listDao.getById(targetListId)) { "清单不存在: $targetListId" }

        taskDao.update(
            task.copy(
                title = trimmedTitle,
                notes = notes?.trim()?.ifEmpty { null },
                priority = priority,
                listId = targetListId,
                updatedAt = now(),
                plannedDate = plannedDate,
                plannedTime = plannedTime,
                dueDate = dueDate,
                dueTime = dueTime,
                repeatRule = repeatRule
            )
        )
    }

    /** @return true when the task just became completed. */
    suspend fun completeTask(taskId: Long): Boolean = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction false
        if (task.deletedAt != null || task.completedAt != null) return@withTransaction false
        val time = now()
        taskDao.update(task.copy(completedAt = time, updatedAt = time))
        recordEvent(task.id, TaskEventType.COMPLETED, task.title, time)
        true
    }

    /** @return true when a completed task was reopened. */
    suspend fun reopenTask(taskId: Long): Boolean = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction false
        if (task.deletedAt != null || task.completedAt == null) return@withTransaction false
        val time = now()
        taskDao.update(task.copy(completedAt = null, updatedAt = time))
        recordEvent(task.id, TaskEventType.REOPENED, task.title, time)
        true
    }

    /**
     * Soft deletes a task and - as the spec requires - its live subtasks.
     * Nothing is removed from the database, so the trash can bring the whole
     * set back. The ids of the cascaded subtasks are recorded in the parent's
     * DELETED event, so a later restore brings back exactly that set instead of
     * every subtask that happens to be deleted.
     *
     * @return the number of subtasks that were soft deleted together with it.
     */
    suspend fun deleteTask(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        if (task.deletedAt != null) return@withTransaction 0
        val time = now()
        val subtasks = taskDao.getLiveSubtasks(taskId)
        taskDao.markDeleted(taskId, time)
        recordEvent(
            task.id,
            TaskEventType.DELETED,
            task.title,
            time,
            detailsJson = encodeSubtaskIds(subtasks.map { it.id })
        )
        subtasks.forEach { subtask ->
            taskDao.markDeleted(subtask.id, time)
            recordEvent(subtask.id, TaskEventType.DELETED, subtask.title, time)
        }
        subtasks.size
    }

    /**
     * Restores a soft deleted task.
     *
     * Restoring a parent brings back the subtasks that were deleted with it.
     * Restoring a subtask also restores its parent when that parent is still
     * deleted, so a visible subtask never hangs under an invisible parent.
     * Siblings are left untouched.
     *
     * @return the number of tasks (including the requested one) that were
     * restored.
     */
    suspend fun restoreTask(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        if (task.deletedAt == null) return@withTransaction 0
        val time = now()
        var restored = 0

        task.parentTaskId?.let { parentId ->
            taskDao.getById(parentId)
                ?.takeIf { it.deletedAt != null }
                ?.let { parent -> restored += restoreOne(parent, time) }
        }
        restored += restoreOne(task, time)
        // Only the subtasks that went down together with this task come back; a
        // subtask the user had deleted on its own stays in the trash.
        cascadedSubtaskIdsOf(taskId).forEach { subtaskId ->
            taskDao.getById(subtaskId)
                ?.takeIf { it.deletedAt != null }
                ?.let { subtask -> restored += restoreOne(subtask, time) }
        }
        restored
    }

    /**
     * The subtask ids that were cascaded with [taskId], read from its latest
     * DELETED event. Keeping this in the event table makes the history the
     * single source of truth and avoids a schema change for the bookkeeping.
     */
    private suspend fun cascadedSubtaskIdsOf(taskId: Long): List<Long> =
        parseSubtaskIds(
            eventDao.getForTask(taskId)
                .lastOrNull { it.eventType == TaskEventType.DELETED }
                ?.detailsJson
        )

    private fun parseSubtaskIds(detailsJson: String?): List<Long> =
        detailsJson
            ?.let { SubtaskIdsPattern.find(it)?.groupValues?.getOrNull(1) }
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { it.toLong() }
            .orEmpty()

    private fun encodeSubtaskIds(ids: List<Long>): String? =
        ids.takeIf { it.isNotEmpty() }?.let { """{"subtaskIds":[${it.joinToString(",")}]}""" }

    private suspend fun restoreOne(task: Task, time: Long): Int {
        val changed = taskDao.markRestored(task.id, time)
        if (changed > 0) {
            recordEvent(task.id, TaskEventType.RESTORED, task.title, time)
        }
        return changed
    }

    private suspend fun recordEvent(
        taskId: Long,
        type: TaskEventType,
        titleSnapshot: String,
        at: Long,
        detailsJson: String? = null
    ) {
        eventDao.insert(
            TaskEvent(
                taskId = taskId,
                eventAt = at,
                eventType = type,
                titleSnapshot = titleSnapshot,
                detailsJson = detailsJson
            )
        )
    }

    private companion object {
        /** Matches the id array of a `{"subtaskIds":[1,2]}` payload. */
        val SubtaskIdsPattern = Regex("""\[([0-9,]*)\]""")
    }
}
