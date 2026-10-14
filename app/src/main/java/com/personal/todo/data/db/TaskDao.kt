package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
