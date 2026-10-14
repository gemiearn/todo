package com.personal.todo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The focus timer and its history (spec section 5).
 *
 * The active timer is a single row, so it is addressed by its fixed id instead
 * of being passed around: reading, replacing and clearing it can never touch a
 * second row by accident.
 */
@Dao
interface FocusDao {

    @Query("SELECT * FROM active_timer WHERE id = ${ActiveTimer.SingleTimerId}")
    fun observeActiveTimer(): Flow<ActiveTimer?>

    @Query("SELECT * FROM active_timer WHERE id = ${ActiveTimer.SingleTimerId}")
    suspend fun getActiveTimer(): ActiveTimer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveTimer(timer: ActiveTimer)

    @Query("DELETE FROM active_timer WHERE id = ${ActiveTimer.SingleTimerId}")
    suspend fun clearActiveTimer()

    @Insert
    suspend fun insertSession(session: FocusSession): Long

    /** Every finished session, newest first - the history and the totals. */
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC, id DESC")
    fun observeSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC, id DESC")
    suspend fun getSessions(): List<FocusSession>
}
