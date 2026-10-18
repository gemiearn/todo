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
 * [listNameSnapshot] is the same idea for the 清单 the task was in: the statistics
 * page ranks time by list, and a task that moves to another list later must not
 * rewrite what already happened. It is null for sessions recorded before the
 * column existed, and those are reported as 未关联.
 *
 * [tagNamesSnapshot] is the same idea once more for the 标签 the task carried when
 * the session started: the names are joined by the Unit Separator
 * ([com.personal.todo.model.FocusTagSnapshot]), so the 统计页 can filter the whole
 * page by one tag without a link table. Null means "no tags" - both for a session
 * whose task had none and for every session recorded before the column existed.
 *
 * [tagIdsSnapshot] is the **identity** half of the same snapshot: the tag ids,
 * comma separated. Names change and tags get deleted, ids do not - so the 统计页
 * can filter by a tag that still exists and keep a renamed tag's old sessions
 * under it. Null on sessions recorded before v12; those fall back to the name.
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
    val listNameSnapshot: String? = null,
    /** 开始时任务身上的标签名，Unit Separator 连成一串；见 [com.personal.todo.model.FocusTagSnapshot]。 */
    val tagNamesSnapshot: String? = null,
    /** 开始时任务身上的标签 id，逗号连成一串；老记录为空时退回按名字筛。 */
    val tagIdsSnapshot: String? = null,
    val mode: FocusMode,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    val actualFocusSeconds: Int,
    val status: FocusStatus,
    /**
     * 这次会话里**真正在跑**的那几小段，`开始-结束` 毫秒对用 `|` 连起来。
     *
     * 统计页的「最佳专注时间」按这些小段往 24 小时里摊，所以暂停的空档不会落进去。null
     * 表示没有这一段信息——v11 之前记录的会话，以及没能确认小段的会话——统计时退回
     * 「按 startedAt→endedAt 整段摊」的老口径，历史不变。
     */
    val focusSegments: String? = null,
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
    /** The 清单 the linked task was in when the session started; see [FocusSession]. */
    val listNameSnapshot: String? = null,
    /** The 标签 the linked task carried when the session started; see [FocusSession]. */
    val tagNamesSnapshot: String? = null,
    /** The tag ids that go with [tagNamesSnapshot]; see [FocusSession.tagIdsSnapshot]. */
    val tagIdsSnapshot: String? = null,
    /** Target of a timed mode, 0 for 正向计时. */
    val plannedSeconds: Int,
    val startedAt: Long,
    val lastResumeAtElapsed: Long,
    /**
     * 当前这一小段开始的**墙上时钟**时刻，用来在暂停/结束/恢复时把这一小段闭合进
     * [focusSegments]（单调时钟只能量长度，量不出「落在几点」）。
     *
     * 老行由迁移补成 [startedAt]；v11 起每次开始/继续/恢复都会写上它。
     */
    @ColumnInfo(defaultValue = "0") val lastResumeAtWallClock: Long = 0L,
    /** 这次会话已经跑完的小段；见 [FocusSession.focusSegments]。 */
    val focusSegments: String? = null,
    val accumulatedFocusSeconds: Int,
    val running: Boolean,
    val updatedAt: Long
) {
    companion object {
        const val SingleTimerId = 1
    }
}
