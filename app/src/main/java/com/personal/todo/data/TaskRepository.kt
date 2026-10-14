package com.personal.todo.data

import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import com.personal.todo.data.db.TaskList
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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
 * [now] is injectable so tests can use a deterministic clock.
 */
class TaskRepository(
    private val db: TodoDatabase,
    private val inboxName: String,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val listDao = db.taskListDao()
    private val taskDao = db.taskDao()
    private val eventDao = db.taskEventDao()

    // ---------------------------------------------------------------- lists

    fun observeLists(): Flow<List<TaskList>> = listDao.observeAll()

    /** Live task count per list, used by the drawer and the delete dialog. */
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
        tasks.forEach { taskDao.update(it.copy(listId = inboxId, updatedAt = time)) }
        listDao.deleteNonInboxById(listId)
        return tasks.size
    }

    // ---------------------------------------------------------------- tasks

    fun observeOpenTasksInList(listId: Long): Flow<List<Task>> =
        taskDao.observeOpenTasksInList(listId)

    /** Open tasks that live in the built-in inbox. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInboxTasks(): Flow<List<Task>> =
        flow { emit(ensureInbox()) }.flatMapLatest { taskDao.observeOpenTasksInList(it) }

    fun observeCompletedTasks(): Flow<List<Task>> = taskDao.observeCompletedTasks()

    fun observeDeletedTasks(): Flow<List<Task>> = taskDao.observeDeletedTasks()

    fun observeSubtasks(parentTaskId: Long): Flow<List<Task>> =
        taskDao.observeSubtasks(parentTaskId)

    /** Live subtask count per parent task; parents without subtasks are absent. */
    fun observeSubtaskCounts(): Flow<Map<Long, Int>> =
        taskDao.observeSubtaskCounts().map { rows ->
            rows.associate { it.taskId to it.subtaskCount }
        }

    fun observeEvents(taskId: Long): Flow<List<TaskEvent>> = eventDao.observeForTask(taskId)

    suspend fun getTask(taskId: Long): Task? = taskDao.getById(taskId)

    /**
     * Creates a task or a subtask. Titles are mandatory and trimmed; notes are
     * optional. Subtasks may only be created under a top level task of the same
     * list (one level only, spec section 4.3).
     */
    suspend fun createTask(
        title: String,
        listId: Long,
        notes: String? = null,
        priority: Priority = Priority.NORMAL,
        parentTaskId: Long? = null
    ): Long {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
        val list = listDao.getById(listId) ?: throw IllegalArgumentException("清单不存在: $listId")

        if (parentTaskId != null) {
            val parent = taskDao.getById(parentTaskId)
                ?: throw IllegalArgumentException("父任务不存在: $parentTaskId")
            require(parent.parentTaskId == null) { "首版只支持一层子任务" }
            require(parent.deletedAt == null) { "父任务已被删除" }
            require(parent.listId == list.id) { "子任务必须与父任务在同一清单" }
        }

        val time = now()
        val taskId = taskDao.insert(
            Task(
                title = trimmedTitle,
                notes = notes?.trim()?.ifEmpty { null },
                listId = list.id,
                parentTaskId = parentTaskId,
                priority = priority,
                createdAt = time,
                updatedAt = time
            )
        )
        recordEvent(taskId, TaskEventType.CREATED, trimmedTitle, time)
        return taskId
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
     */
    suspend fun updateTask(
        taskId: Long,
        title: String,
        notes: String?,
        priority: Priority,
        listId: Long
    ) {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
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
                updatedAt = now()
            )
        )
    }

    /** @return true when the task just became completed. */
    suspend fun completeTask(taskId: Long): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        if (task.deletedAt != null || task.completedAt != null) return false
        val time = now()
        taskDao.update(task.copy(completedAt = time, updatedAt = time))
        recordEvent(task.id, TaskEventType.COMPLETED, task.title, time)
        return true
    }

    /** @return true when a completed task was reopened. */
    suspend fun reopenTask(taskId: Long): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        if (task.deletedAt != null || task.completedAt == null) return false
        val time = now()
        taskDao.update(task.copy(completedAt = null, updatedAt = time))
        recordEvent(task.id, TaskEventType.REOPENED, task.title, time)
        return true
    }

    /**
     * Soft deletes a task and - as the spec requires - its live subtasks.
     * Nothing is removed from the database, so the trash can bring the whole
     * set back.
     *
     * @return the number of subtasks that were soft deleted together with it.
     */
    suspend fun deleteTask(taskId: Long): Int {
        val task = taskDao.getById(taskId) ?: return 0
        if (task.deletedAt != null) return 0
        val time = now()
        val subtasks = taskDao.getLiveSubtasks(taskId)
        taskDao.markDeleted(taskId, time)
        recordEvent(
            task.id,
            TaskEventType.DELETED,
            task.title,
            time,
            detailsJson = if (subtasks.isEmpty()) null else """{"subtaskCount":${subtasks.size}}"""
        )
        subtasks.forEach { subtask ->
            taskDao.markDeleted(subtask.id, time)
            recordEvent(subtask.id, TaskEventType.DELETED, subtask.title, time)
        }
        return subtasks.size
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
    suspend fun restoreTask(taskId: Long): Int {
        val task = taskDao.getById(taskId) ?: return 0
        if (task.deletedAt == null) return 0
        val time = now()
        var restored = 0

        val parentId = task.parentTaskId
        if (parentId != null) {
            val parent = taskDao.getById(parentId)
            if (parent != null && parent.deletedAt != null) {
                restored += restoreOne(parent, time)
            }
        }
        restored += restoreOne(task, time)
        taskDao.getDeletedSubtasks(taskId).forEach { subtask ->
            restored += restoreOne(subtask, time)
        }
        return restored
    }

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
}
