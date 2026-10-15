package com.personal.todo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusPhase
import com.personal.todo.model.FocusStatus

/**
 * One finished focus session (spec section 7.3), the single source of truth for
 * 专注历史.
 *
 * [taskTitleSnapshot] keeps the title as it was when the session started, so the
 * history stays readable even after the task is renamed or soft deleted. The
 * foreign key uses RESTRICT like [TaskEvent]: tasks are never physically
 * deleted, so a session can never lose its task silently.
 *
 * [plannedSeconds] is the target of a timed mode and 0 for 正向计时, which has
 * no target. [actualFocusSeconds] is the real focus time; for a session that was
 * stopped early it is smaller than the target, and [status] says so.
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("taskId"), Index("startedAt")]
)
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long? = null,
    val taskTitleSnapshot: String? = null,
    val mode: FocusMode,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    val actualFocusSeconds: Int,
    val status: FocusStatus,
    val note: String? = null
)

/**
 * The single active timer (spec section 5.2).
 *
 * It is a separate table from [FocusSession] because a session is only written
 * once it ends: this row is the recoverable running state, not history. The id is
 * pinned to [SingleTimerId], so the app can only ever have one timer - starting
 * a second one is refused instead of silently replacing the first.
 *
 * [lastResumeAtElapsed] is a monotonic reading, so the running span is immune to
 * wall clock and time zone changes; [startedAt] is the wall clock moment the
 * session began, used for the history and the daily totals.
 */
@Entity(tableName = "active_timer")
data class ActiveTimer(
    @PrimaryKey val id: Int = SingleTimerId,
    val mode: FocusMode,
    /**
     * A focus block, or the break after it. The default is written into the schema
     * so a timer stored before this column existed comes back as a focus block.
     */
    @ColumnInfo(defaultValue = "FOCUS") val phase: FocusPhase = FocusPhase.FOCUS,
    val taskId: Long? = null,
    val taskTitleSnapshot: String? = null,
    /** Target of a timed mode, 0 for 正向计时. */
    val plannedSeconds: Int,
    val startedAt: Long,
    val lastResumeAtElapsed: Long,
    val accumulatedFocusSeconds: Int,
    val running: Boolean,
    val updatedAt: Long
) {
    companion object {
        const val SingleTimerId = 1
    }
}
