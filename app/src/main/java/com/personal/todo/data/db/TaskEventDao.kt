package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskEventDao {

    @Insert
    suspend fun insert(event: TaskEvent)

    /** Newest event first. */
    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY eventAt DESC, id DESC")
    fun observeForTask(taskId: Long): Flow<List<TaskEvent>>

    /** Oldest event first - used by tests to check the real order of events. */
    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY eventAt ASC, id ASC")
    suspend fun getForTask(taskId: Long): List<TaskEvent>
}
