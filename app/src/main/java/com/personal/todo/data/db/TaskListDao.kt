package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {

    /** Inbox first, then the user defined order. */
    @Query("SELECT * FROM task_lists ORDER BY isInbox DESC, sortOrder ASC, createdAt ASC, id ASC")
    fun observeAll(): Flow<List<TaskList>>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getById(id: Long): TaskList?

    @Query("SELECT * FROM task_lists WHERE isInbox = 1 LIMIT 1")
    suspend fun getInbox(): TaskList?

    @Query("SELECT MAX(sortOrder) FROM task_lists WHERE isInbox = 0")
    suspend fun maxUserSortOrder(): Int?

    @Insert
    suspend fun insert(list: TaskList): Long

    @Update
    suspend fun update(list: TaskList)

    /**
     * Deletes a list only when it is not the inbox. Returns the number of
     * deleted rows, so callers can detect a refused delete.
     */
    @Query("DELETE FROM task_lists WHERE id = :id AND isInbox = 0")
    suspend fun deleteNonInboxById(id: Long): Int
}
