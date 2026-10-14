package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Number of live subtasks of one parent task, keyed by the parent task id. */
data class TaskSubtaskCount(val taskId: Long, val subtaskCount: Int)

/** Number of live (not soft deleted) tasks per list. */
data class ListTaskCount(val listId: Long, val taskCount: Int)

@Dao
interface TaskDao {

    /**
     * Top level tasks (no parent) of one list that are still open: not
     * completed and not soft deleted. Subtasks are shown in the parent's
     * detail screen, so they are excluded from the list level queries.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE listId = :listId
          AND parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeOpenTasksInList(listId: Long): Flow<List<Task>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NOT NULL
        ORDER BY completedAt DESC, id DESC
        """
    )
    fun observeCompletedTasks(): Flow<List<Task>>

    /**
     * Completed top level tasks of one list: the tasks the list view folds away
     * behind its "已完成 N" header, so a finished task leaves the working list
     * without disappearing.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE listId = :listId
          AND parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NOT NULL
        ORDER BY completedAt DESC, id DESC
        """
    )
    fun observeCompletedTasksInList(listId: Long): Flow<List<Task>>

    /**
     * Everything in the trash, including subtasks. A subtask can be deleted on
     * its own, so the trash must be able to show and restore it.
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id DESC")
    fun observeDeletedTasks(): Flow<List<Task>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId = :parentTaskId AND deletedAt IS NULL
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeSubtasks(parentTaskId: Long): Flow<List<Task>>

    @Query(
        """
        SELECT parentTaskId AS taskId, COUNT(*) AS subtaskCount FROM tasks
        WHERE parentTaskId IS NOT NULL AND deletedAt IS NULL
        GROUP BY parentTaskId
        """
    )
    fun observeSubtaskCounts(): Flow<List<TaskSubtaskCount>>

    /**
     * Open top level tasks per list: not completed and not soft deleted. This
     * is the number the drawer shows next to a list and it matches exactly the
     * rows the list view can display, so completing or trashing a task changes
     * it immediately. Subtasks belong to their parent's detail screen and are
     * therefore not counted here.
     */
    @Query(
        """
        SELECT listId, COUNT(*) AS taskCount FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
        GROUP BY listId
        """
    )
    fun observeOpenTaskCountsByList(): Flow<List<ListTaskCount>>

    /**
     * Every not soft deleted task per list, in any state and including
     * subtasks: the number of tasks a list delete would move to the inbox.
     */
    @Query(
        """
        SELECT listId, COUNT(*) AS taskCount FROM tasks
        WHERE deletedAt IS NULL
        GROUP BY listId
        """
    )
    fun observeTaskCountsByList(): Flow<List<ListTaskCount>>

    // -------------------------------------------------------- smart views
    //
    // 今日 / 未来 / 逾期 (spec section 4.1). They only read dates - no query
    // here ever rewrites a date, so an overdue task can never be postponed
    // automatically. "逾期" itself needs the current time (a due date with a
    // time is not overdue before that moment), so these queries narrow the
    // rows and the repository applies the exact rule in Kotlin.

    /**
     * Open top level tasks planned for [date] or due on [date]. One query with
     * `OR`, so a task with both dates set on the same day is returned once -
     * the "今日" view can never show a task twice (spec section 4.1).
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (plannedDate = :date OR dueDate = :date)
        """
    )
    fun observeOpenTasksOnDate(date: LocalDate): Flow<List<Task>>

    /** Open top level tasks dated after [date] (planned or due). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (plannedDate > :date OR dueDate > :date)
        """
    )
    fun observeOpenTasksAfterDate(date: LocalDate): Flow<List<Task>>

    /**
     * Open top level tasks whose due date is not after [date]: the candidate
     * set for the overdue view before the exact moment is taken into account.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND dueDate IS NOT NULL
          AND dueDate <= :date
        """
    )
    fun observeOpenTasksDueNotAfter(date: LocalDate): Flow<List<Task>>

    /** Every open top level task, whatever its dates - the quadrant view. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
        """
    )
    fun observeAllOpenTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    /**
     * Live view of one task. The detail screen observes this instead of keeping
     * a snapshot, so a change made anywhere else (completing the task from the
     * list, for example) is reflected immediately.
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentTaskId AND deletedAt IS NULL")
    suspend fun getLiveSubtasks(parentTaskId: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    suspend fun getAllInList(listId: Long): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markDeleted(id: Long, at: Long): Int

    @Query("UPDATE tasks SET deletedAt = NULL, updatedAt = :at WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun markRestored(id: Long, at: Long): Int
}
