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

/** 一个清单的进度：顶层任务总数与其中已完成数；见 [TaskDao.observeListProgress]。 */
data class ListProgress(val total: Int, val completed: Int)

@Dao
interface TaskDao {

    /**
     * Top level tasks (no parent) of one list that are still open: not
     * completed and not soft deleted. Subtasks are shown in the parent's
     * detail screen, so they are excluded from the list level queries.
     *
     * 重复任务算下来的一次次也是独立任务（说明文档 4.5），但它们**不进清单行**：一条
     * 「每天重复」会铺满未来一年，清单会被 365 条实例淹掉（用户看到的就是收件箱 366）。
     * 清单里只留**规则源**那一条代表整条系列；实例照旧按日期出现在「今天 / 日历 / 未来」，
     * 那才是它们要被勾掉的地方。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE listId = :listId
          AND parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND recurrenceSourceId IS NULL
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
     *
     * 重复任务的实例同样不进这个分组：一条每天重复的任务会留下几百条已完成实例，把
     * 「已完成 N」撑成几百；它们按日期归在「今天 / 日历」里，那里才看得到。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE listId = :listId
          AND parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NOT NULL
          AND recurrenceSourceId IS NULL
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

    /**
     * Completed top level tasks dated [date]: the "已完成 N" group of the 今日
     * view and of one calendar day. Like every date query here it only reads
     * the stored dates.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NOT NULL
          AND (plannedDate = :date OR dueDate = :date)
        ORDER BY completedAt DESC, id DESC
        """
    )
    fun observeCompletedTasksOn(date: LocalDate): Flow<List<Task>>

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
     *
     * 重复任务的实例也不计入（见 [observeOpenTasksInList]）：整条系列只算规则源那一条，
     * 否则一条「每天重复」会让收件箱徽章变成 366。
     */
    @Query(
        """
        SELECT listId, COUNT(*) AS taskCount FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND recurrenceSourceId IS NULL
        GROUP BY listId
        """
    )
    fun observeOpenTaskCountsByList(): Flow<List<ListTaskCount>>

    /**
     * 一个清单的进度：未删除顶层任务总数、以及其中已完成的条数。
     *
     * 与清单行同一口径（不含子任务、不含重复任务的实例），所以清单页顶部那条小结的
     * 「已完成 n / 共 m」和下面列表、折叠分组对得上。没有任务时两列都是 0。
     */
    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN completedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS completed
        FROM tasks
        WHERE listId = :listId
          AND parentTaskId IS NULL
          AND deletedAt IS NULL
          AND recurrenceSourceId IS NULL
        """
    )
    fun observeListProgress(listId: Long): Flow<ListProgress>

    /**
     * 一条重复任务的**整条系列**已经完成过几次：规则源自己 + 它生成的所有实例。
     *
     * 任务详情页只在规则源那一行显示它（「本系列完成 k 次 · 共专注 X」），实例页不显示
     * ——实例只代表那天那一次。口径与清单行相反：这里**要**数实例，因为问的就是系列。
     * 删掉的实例不算（`deletedAt IS NULL`），重复打勾又取消的也只算当前状态。
     */
    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE deletedAt IS NULL
          AND completedAt IS NOT NULL
          AND (id = :sourceId OR recurrenceSourceId = :sourceId)
        """
    )
    fun observeSeriesCompletedCount(sourceId: Long): Flow<Int>

    /**
     * Every not soft deleted task per list, in any state and including
     * subtasks: the number of tasks a list delete would move to the inbox.
     *
     * 重复任务的实例同样不数：它们确实会被一起移走，但用户心里那是「一条重复任务」，
     * 报 366 只会吓人；这里报的是**源**的条数。
     */
    @Query(
        """
        SELECT listId, COUNT(*) AS taskCount FROM tasks
        WHERE deletedAt IS NULL
          AND recurrenceSourceId IS NULL
        GROUP BY listId
        """
    )
    fun observeTaskCountsByList(): Flow<List<ListTaskCount>>

    // -------------------------------------------------------- smart views
    //
    // 今日 / 未来 / 逾期 (spec section 4.1). They only read dates - no query
    // here ever rewrites a date, so an overdue task can never be postponed
    // automatically. "逾期" itself needs the current time and the planned-date
    // fallback, so these queries narrow the rows and the repository applies the
    // exact rule in Kotlin.

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
     * Open top level tasks whose effective overdue date is not after [date]:
     * the candidate set for the overdue view before the exact moment is taken
     * into account. An explicit due date wins; plannedDate is only the fallback
     * when dueDate is null.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (
                (dueDate IS NOT NULL AND dueDate <= :date)
                OR (dueDate IS NULL AND plannedDate IS NOT NULL AND plannedDate <= :date)
              )
        """
    )
    fun observeOpenTasksOverdueCandidate(date: LocalDate): Flow<List<Task>>

    /** Every open top level task, whatever its dates - the搜索 / 任务选择器. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
        """
    )
    fun observeAllOpenTasks(): Flow<List<Task>>

    /**
     * Open top level tasks whose dates have not passed: the quadrant view.
     *
     * 四象限是「决定接下来做什么」的地方，所以已经过期的不进这里——计划日期或截止日期
     * 早于 [date] 的都排除。**没有日期的任务照旧留着**：它们只是还没安排时间，并不是
     * 过去的事。还没做完的旧任务仍然在它所属的清单/收件箱里。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (plannedDate IS NULL OR plannedDate >= :date)
          AND (dueDate IS NULL OR dueDate >= :date)
        """
    )
    fun observeOpenTasksNotPast(date: LocalDate): Flow<List<Task>>

    /**
     * Open top level tasks the calendar may draw on [date]: dated exactly that
     * day, or spanning it because the planned date and the due date form an
     * interval around it. The calendar shows a task on every day of its interval
     * (a display decision), while the 今日 view keeps the exact rule.
     *
     * 两个端点的先后不限：`CalendarScreen` 铺日期时按较小 / 较大端点归一化，所以这里
     * 也两个方向都判——计划日晚于截止日的任务同样算「盖住这一天」，两边口径一致。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (
                plannedDate = :date
                OR dueDate = :date
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND plannedDate <= :date AND dueDate >= :date)
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND dueDate <= :date AND plannedDate >= :date)
              )
        """
    )
    fun observeOpenTasksCoveringDate(date: LocalDate): Flow<List<Task>>

    /**
     * Open top level tasks whose dates overlap [start]..[end], planned or due.
     * The calendar draws a task's dots across the whole interval between its
     * planned and its due date, so an interval that merely passes through the
     * range counts too. Read only: it never shifts a date.
     *
     * 端点先后不限：正序与倒序两个方向都判，与日历按较小 / 较大端点归一化的铺法一致。
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND completedAt IS NULL
          AND (
                (plannedDate BETWEEN :start AND :end)
                OR (dueDate BETWEEN :start AND :end)
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND plannedDate <= :end AND dueDate >= :start)
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND dueDate <= :end AND plannedDate >= :start)
              )
        """
    )
    fun observeOpenTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>>

    /**
     * Same span as [observeOpenTasksSpanning] but *including finished tasks*: the
     * calendar can be asked to show completed work ("显示已完成"), and a finished
     * task still keeps the day it was given. Same read-only rule - no date moves.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND deletedAt IS NULL
          AND (
                (plannedDate BETWEEN :start AND :end)
                OR (dueDate BETWEEN :start AND :end)
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND plannedDate <= :end AND dueDate >= :start)
                OR (plannedDate IS NOT NULL AND dueDate IS NOT NULL
                    AND dueDate <= :end AND plannedDate >= :start)
              )
        """
    )
    fun observeTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    /**
     * 所有还活着、带重复规则的任务：生成器以它们为源，每次算一遍它们该有的那几次。
     * 带规则的只能是顶层任务，子任务不重复。
     */
    @Query("SELECT * FROM tasks WHERE repeatRule IS NOT NULL AND deletedAt IS NULL AND parentTaskId IS NULL")
    suspend fun getRepeatSources(): List<Task>

    /**
     * 某个源已经生成过的所有次，**连进过回收站的也算**：生成器靠它去重，所以被用户
     * 删掉的那一次不该被下一次补生成又变出来。
     */
    @Query("SELECT * FROM tasks WHERE recurrenceSourceId = :sourceId")
    suspend fun getOccurrencesOf(sourceId: Long): List<Task>

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

    /**
     * 把一个父任务的**全部**子任务搬到另一个清单（含已完成、已删的）。
     *
     * 移动父任务必须连同子任务一起走：父子同清单是「子任务只在父任务详情里出现、且与
     * 父任务同属一地」这条不变式的前提，只改父任务那一行会让库里出现归属不一致的父子。
     */
    @Query("UPDATE tasks SET listId = :listId, updatedAt = :at WHERE parentTaskId = :parentTaskId")
    suspend fun moveSubtasksToList(parentTaskId: Long, listId: Long, at: Long): Int

    @Query("UPDATE tasks SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markDeleted(id: Long, at: Long): Int

    @Query("UPDATE tasks SET deletedAt = NULL, updatedAt = :at WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun markRestored(id: Long, at: Long): Int
}
