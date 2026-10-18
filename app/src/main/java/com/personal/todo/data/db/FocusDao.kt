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

    /**
     * 一条任务上的专注合计：累计秒数、session 条数、以及其中「跑完的番茄钟」个数。
     *
     * 口径与统计页一致：只有 `status = COMPLETED` 的番茄才算番茄，中断的不算；休息是计时器
     * 的另一个阶段、根本不写 session，所以天然不在里面。没有记录时三列都是 0（不是 null）。
     */
    @Query(
        """
        SELECT COALESCE(SUM(actualFocusSeconds), 0) AS totalSeconds,
               COUNT(*) AS sessionCount,
               COALESCE(
                   SUM(CASE WHEN mode = 'POMODORO' AND status = 'COMPLETED' THEN 1 ELSE 0 END), 0
               ) AS pomodoroCount
        FROM focus_sessions
        WHERE taskId = :taskId
        """
    )
    fun observeTaskTotals(taskId: Long): Flow<TaskFocusTotals>

    /**
     * 一条**重复任务整条系列**的累计专注秒数：规则源自己 + 它生成的所有实例。
     *
     * 任务详情页只在规则源那一行显示这个数（「本系列…」）；实例页读的是 [observeTaskTotals]，
     * 只看自己那一天。**删掉的实例照旧计入**——删任务不删专注历史是硬规则（说明文档 4.2），
     * 那段时间真的花掉了，就不该因为后来把任务挪进回收站而从系列小结里消失。
     */
    @Query(
        """
        SELECT COALESCE(SUM(actualFocusSeconds), 0)
        FROM focus_sessions
        WHERE taskId = :sourceId
           OR taskId IN (SELECT id FROM tasks WHERE recurrenceSourceId = :sourceId)
        """
    )
    fun observeSeriesFocusSeconds(sourceId: Long): Flow<Int>

    /**
     * 一个清单的累计专注秒数：`focus_sessions` 按**任务当前所在清单**汇总。
     *
     * 用 join 而不是 `listNameSnapshot`——清单小结说的是「这个清单现在」，快照只说明当时
     * 在哪；任务搬走后，时间该跟着任务走。
     */
    @Query(
        """
        SELECT COALESCE(SUM(s.actualFocusSeconds), 0) AS totalSeconds
        FROM focus_sessions s
        JOIN tasks t ON t.id = s.taskId
        WHERE t.listId = :listId
        """
    )
    fun observeListFocusSeconds(listId: Long): Flow<Int>
}

/** 一条任务上的专注合计；见 [FocusDao.observeTaskTotals]。 */
data class TaskFocusTotals(
    val totalSeconds: Int,
    val sessionCount: Int,
    val pomodoroCount: Int
)
