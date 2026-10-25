package com.personal.todo.data

import androidx.room.withTransaction
import com.personal.todo.data.db.Tag
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TaskEvent
import com.personal.todo.data.db.TaskList
import com.personal.todo.data.db.TaskTag
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatRule
import com.personal.todo.model.Schedule
import com.personal.todo.model.TaskEventType
import com.personal.todo.model.TaskSeries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
 * [now] is injectable so tests can use a deterministic clock, and [zone] so the
 * date boundary of section 4.1 can be tested across time zones.
 */
private const val RecurrenceHorizonDays = 365

/** 「今天」那条流多久重新读一次日期：60 秒足够，跨过午夜后最多晚一分钟翻页。 */
private const val TodayRefreshMillis = 60_000L

/**
 * 「逾期」那条流多久重新判一次时刻。
 *
 * 逾期是**按时刻**判的（说明文档 4.1）：任务 10:00 到期，9:00 就把 App 摆在面前一直看着，
 * 到 11:00 它就该是逾期了——但 Room 只在库里真的变了才发射，一天不动库就一天不重判，
 * 那段时间「逾期」视图与抽屉徽章会一直停在旧结果（台账 N6）。60 秒重判一次足够。
 */
private const val OverdueRefreshMillis = 60_000L

/**
 * 清单页顶部那条小结：未删除顶层任务的**总数**、其中**已完成**的条数，以及该清单累计的
 * **专注秒数**。任务页的「已完成 n / 共 m」与完成率都从这三个数算，不另存一份。
 */
data class ListSummary(val total: Int, val completed: Int, val focusSeconds: Int) {
    /** 完成率，0..1；一条任务都没有时算 0，不除零。 */
    val progress: Float get() = if (total <= 0) 0f else completed.toFloat() / total
}

/**
 * 一条**重复任务整条系列**的合计：系列里已经完成过几次、加起来专注了多久。
 *
 * 只在任务详情页的规则源那一行显示（`repeatRule != null` 的那条）；某个实例的详情页看到
 * 的是它自己那一天，不是整条系列。
 */
data class TaskSeriesTotals(val completedCount: Int, val focusSeconds: Int)

class TaskRepository(
    private val db: TodoDatabase,
    private val inboxName: String,
    private val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** [observeToday] 的重新读日期间隔；测试传 1 毫秒，这样跨天只等一次 tick。 */
    private val todayRefreshMillis: Long = TodayRefreshMillis,
    /** [observeNow] 的重新读时刻间隔；测试传 1 毫秒，这样「到点了」只等一次 tick。 */
    private val overdueRefreshMillis: Long = OverdueRefreshMillis
) {

    private val listDao = db.taskListDao()
    private val taskDao = db.taskDao()
    private val eventDao = db.taskEventDao()
    private val tagDao = db.tagDao()
    private val focusDao = db.focusDao()

    // ---------------------------------------------------------------- 标签
    //
    // 标签是多值属性、可以跨清单，和「清单＝归属地（单值）」分工明确（说明文档 4.4）。

    /** 全部标签，按 sortOrder / 名字排好——标签管理页和编辑器都读它。 */
    fun observeTags(): Flow<List<Tag>> = tagDao.observeTags()

    /**
     * taskId -> 这条任务身上的标签。列表卡片要给每条任务画标签小片，一次查询把
     * 全部连线拿回来在内存里分组，避免每条任务一次 join。
     */
    fun observeTagsByTask(): Flow<Map<Long, List<Tag>>> =
        combine(tagDao.observeTags(), tagDao.observeAllLinks()) { tags, links ->
            val byId = tags.associateBy { it.id }
            links.groupBy { it.taskId }
                .mapValues { (_, pairs) -> pairs.mapNotNull { byId[it.tagId] } }
        }

    /** 一条任务的标签，任务详情页读它。 */
    fun observeTagsOf(taskId: Long): Flow<List<Tag>> = tagDao.observeTagsOf(taskId)

    suspend fun createTag(name: String, color: Int): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val time = now()
        return tagDao.insert(
            Tag(name = trimmed, color = color, sortOrder = 0, createdAt = time, updatedAt = time)
        )
    }

    /** 改名与改色走同一个入口：标签就这两样可编辑的东西。 */
    suspend fun updateTag(tagId: Long, name: String, color: Int) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val tag = tagDao.getById(tagId) ?: return
        tagDao.update(tag.copy(name = trimmed, color = color, updatedAt = now()))
    }

    /**
     * 删掉标签。先把它的连线清干净，再删标签本身，都在一个事务里——绝不留一行指向
     * 已经消失的标签（两个外键都是 RESTRICT）。任务本身不受影响，只是少了这个标记。
     */
    suspend fun deleteTag(tagId: Long) = db.withTransaction {
        tagDao.clearLinksOfTag(tagId)
        tagDao.delete(tagId)
    }

    /** 把一条任务的标签整个换成 [tagIds]（先清后连，同一事务）。 */
    suspend fun setTaskTags(taskId: Long, tagIds: Collection<Long>) = db.withTransaction {
        tagDao.clearLinksOfTask(taskId)
        tagIds.toSet().forEach { tagId -> tagDao.link(TaskTag(taskId = taskId, tagId = tagId)) }
    }


    // ---------------------------------------------------------------- lists

    fun observeLists(): Flow<List<TaskList>> = listDao.observeAll()

    /**
     * Live open task count per list: the badge the drawer shows next to each
     * list. It excludes completed and soft deleted tasks as well as subtasks,
     * so it agrees with the rows the list view displays.
     */
    fun observeOpenTaskCountsByList(): Flow<Map<Long, Int>> =
        taskDao.observeOpenTaskCountsByList().map { rows ->
            rows.associate { it.listId to it.taskCount }
        }

    /** Live task count per list, used by the delete list dialog. */
    fun observeTaskCountsByList(): Flow<Map<Long, Int>> =
        taskDao.observeTaskCountsByList().map { rows ->
            rows.associate { it.listId to it.taskCount }
        }

    /**
     * 清单页顶部那条小结的数据：该清单「已完成 / 共」与累计专注秒数。
     *
     * 条数口径与清单行一致（未删除顶层任务、不含重复实例）；专注时长按**任务当前所在清单**
     * 汇总（`focus_sessions` join `tasks`），不用会话里的清单名快照——小结说的是「这个清单
     * 现在」，任务搬走后时间该跟着任务走。
     */
    fun observeListSummary(listId: Long): Flow<ListSummary> = combine(
        taskDao.observeListProgress(listId),
        focusDao.observeListFocusSeconds(listId)
    ) { progress, focusSeconds ->
        ListSummary(
            total = progress.total,
            completed = progress.completed,
            focusSeconds = focusSeconds
        )
    }

    /** 收件箱的小结；收件箱是内建清单，先把它的 id 确保出来再读。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInboxSummary(): Flow<ListSummary> =
        flow { emit(ensureInbox()) }.flatMapLatest { observeListSummary(it) }

    /** 一条重复任务整条系列的合计：完成过几次、一共专注了多久。 */
    fun observeSeriesTotals(sourceId: Long): Flow<TaskSeriesTotals> = combine(
        taskDao.observeSeriesCompletedCount(sourceId),
        focusDao.observeSeriesFocusSeconds(sourceId)
    ) { completedCount, focusSeconds ->
        TaskSeriesTotals(completedCount = completedCount, focusSeconds = focusSeconds)
    }

    /** Idempotent: returns the built-in inbox, creating it if it is missing. */
    suspend fun ensureInbox(): Long {
        listDao.getInbox()?.let { return it.id }
        val time = now()
        return listDao.insert(
            TaskList(name = inboxName, isInbox = true, sortOrder = 0, createdAt = time, updatedAt = time)
        )
    }

    suspend fun createList(name: String, icon: String? = null): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val time = now()
        val nextOrder = (listDao.maxUserSortOrder() ?: 0) + 1
        return listDao.insert(
            TaskList(
                name = trimmed,
                icon = cleanIcon(icon),
                isInbox = false,
                sortOrder = nextOrder,
                createdAt = time,
                updatedAt = time
            )
        )
    }

    /** 换名字与换图标走同一个入口；[icon] 传空就是把图标去掉。 */
    suspend fun renameList(listId: Long, name: String, icon: String?) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val list = listDao.getById(listId) ?: return
        listDao.update(list.copy(name = trimmed, icon = cleanIcon(icon), updatedAt = now()))
    }

    /** 空图标存成 null：这样「没有图标」只有一种表示，选「无」就是把这一列清空。 */
    private fun cleanIcon(icon: String?): String? = icon?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Moves a user list one step up (-1) or down (+1) in the drawer order
     * (spec section 3: lists can be created, renamed and sorted). The built-in
     * inbox is not part of the user order.
     *
     * @return true when the list actually moved; false at an edge or for the
     * inbox, so the UI can hide the button instead of failing silently.
     */
    suspend fun moveList(listId: Long, offset: Int): Boolean = db.withTransaction {
        if (offset == 0) return@withTransaction false
        val lists = listDao.getUserListsOrdered()
        val from = lists.indexOfFirst { it.id == listId }
        if (from < 0) return@withTransaction false
        val to = from + offset
        if (to !in lists.indices) return@withTransaction false
        val reordered = lists.toMutableList().apply { add(to, removeAt(from)) }
        val time = now()
        reordered.forEachIndexed { index, list ->
            if (list.sortOrder != index) listDao.update(list.copy(sortOrder = index, updatedAt = time))
        }
        true
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
        return db.withTransaction {
            tasks.forEach { taskDao.update(it.copy(listId = inboxId, updatedAt = time)) }
            listDao.deleteNonInboxById(listId)
            tasks.size
        }
    }

    // ---------------------------------------------------------------- tasks

    fun observeOpenTasksInList(listId: Long): Flow<List<Task>> =
        taskDao.observeOpenTasksInList(listId)

    /** Open tasks that live in the built-in inbox. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInboxTasks(): Flow<List<Task>> =
        flow { emit(ensureInbox()) }.flatMapLatest { taskDao.observeOpenTasksInList(it) }

    fun observeCompletedTasks(): Flow<List<Task>> = taskDao.observeCompletedTasks()

    /** Completed tasks of one list - the "已完成 N" group of that list view. */
    fun observeCompletedTasksInList(listId: Long): Flow<List<Task>> =
        taskDao.observeCompletedTasksInList(listId)

    /** Completed tasks of the built-in inbox, for the same folded group. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCompletedInboxTasks(): Flow<List<Task>> =
        flow { emit(ensureInbox()) }.flatMapLatest { taskDao.observeCompletedTasksInList(it) }

    /**
     * Completed tasks dated [date] - the folded group of the 今日 view and of a
     * calendar day. A finished task keeps the date it was given, so it stays in
     * the day it belongs to instead of moving to the day it was finished.
     */
    fun observeCompletedTasksOn(date: LocalDate): Flow<List<Task>> =
        taskDao.observeCompletedTasksOn(date)

    fun observeDeletedTasks(): Flow<List<Task>> = taskDao.observeDeletedTasks()

    // ----------------------------------------------------------- smart views
    //
    // 今日 / 未来 / 逾期 of spec section 4.1. They are queries over the dates,
    // never rewrites: an overdue task keeps its original date until the user
    // deliberately reschedules it.

    /** The local date "today" is resolved in the device's time zone. */
    fun today(): LocalDate = Schedule.todayOf(now(), zone)

    /**
     * 今天的日期，每 [todayRefreshMillis] 重新读一次。
     *
     * 进程活过午夜时，「今天 / 未来 / 逾期 / 四象限」必须跟着翻页，不能停在「这条流
     * 建立的那一天」：那些查询的日期是建流时定死的，而订阅它们的 ViewModel 长期住在
     * Activity 的 ViewModelStore 里，界面自己不会重新建流。日期没变时不重复发射。
     */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(todayRefreshMillis)
        }
    }.distinctUntilChanged()

    /** Open tasks planned for today or due today - each task appears once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayTasks(): Flow<List<Task>> =
        observeToday().flatMapLatest { taskDao.observeOpenTasksOnDate(it) }

    /**
     * Open tasks the calendar day of [date] lists: tasks dated that day and tasks
     * whose planned..due interval covers it. The 今日 view deliberately keeps the
     * exact rule, so this is only for the calendar.
     */
    fun observeOpenTasksCovering(date: LocalDate): Flow<List<Task>> =
        taskDao.observeOpenTasksCoveringDate(date)

    /** Open tasks dated after today, planned or due. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFutureTasks(): Flow<List<Task>> =
        observeToday().flatMapLatest { taskDao.observeOpenTasksAfterDate(it) }

    /** 今天完成的任务 - 「今天」页那个折叠分组用，日期同样跟着跨天翻页。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCompletedTasksToday(): Flow<List<Task>> =
        observeToday().flatMapLatest { taskDao.observeCompletedTasksOn(it) }

    /**
     * 「现在」，每 [observeNow] 对应的间隔重新读一次，值没变就不重复发射。
     *
     * 给「按时刻判」的那些流当一个节拍：光靠 Room 发射不够，库不动的时候时间也在走。
     */
    private fun observeNow(): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(overdueRefreshMillis)
        }
    }.distinctUntilChanged()

    /**
     * Open tasks past their effective overdue moment. An explicit due date
     * wins; without one, the end of the planned date is the fallback. The exact
     * instant is applied here rather than in SQL.
     *
     * 候选行只随「哪一天」重查（一天一次），**判定本身跟着 [observeNow] 每分钟重跑**：
     * 这样 10:00 到期的任务在 11:00 自己进逾期，不必等下一次写库（台账 N6）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeOverdueTasks(): Flow<List<Task>> =
        combine(
            observeToday().flatMapLatest { date -> taskDao.observeOpenTasksOverdueCandidate(date) },
            observeNow()
        ) { rows, currentTime ->
            rows.filter { task ->
                Schedule.isOverdue(
                    nowMillis = currentTime,
                    plannedDate = task.plannedDate,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    zone = zone
                )
            }
        }

    /**
     * 逾期任务的条数 - 抽屉里「逾期」那一行的红色徽章用。
     *
     * 口径不另写一遍，直接数 [observeOverdueTasks] 的结果：逾期判定要看「截止时刻」，
     * 那是算出来的、不是 SQL 里比得出来的，重复一份迟早会两处对不上。
     */
    fun observeOverdueCount(): Flow<Int> = observeOverdueTasks().map { it.size }

    /** Every open top level task, whatever its dates - the 任务选择器 uses this. */
    fun observeAllOpenTasks(): Flow<List<Task>> = taskDao.observeAllOpenTasks()

    /**
     * Open tasks the 四象限 may show: 只有日期还没过去的那些。
     *
     * 计划日期或截止日期早于今天的任务不进四象限（那是「决定接下来做什么」的地方，过期
     * 的该去清单/逾期里收）；**没有日期的任务照旧留着**，它们只是还没安排时间。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeQuadrantTasks(): Flow<List<Task>> =
        observeToday().flatMapLatest { taskDao.observeOpenTasksNotPast(it) }

    /**
     * Open tasks whose dates overlap [start]..[end] - the task dots of one
     * calendar month. Read only: it never shifts a date.
     */
    fun observeOpenTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>> =
        taskDao.observeOpenTasksSpanning(start, end)

    /**
     * Open *and* finished tasks overlapping [start]..[end]. The calendar reads
     * this one so that "显示已完成" can turn the finished ones on and off without
     * a second query; with the setting off the caller simply drops them.
     */
    fun observeTasksSpanning(start: LocalDate, end: LocalDate): Flow<List<Task>> =
        taskDao.observeTasksSpanning(start, end)

    fun observeSubtasks(parentTaskId: Long): Flow<List<Task>> =
        taskDao.observeSubtasks(parentTaskId)

    /** Every live subtask of every parent; the list screens group by parent themselves. */
    fun observeAllSubtasks(): Flow<List<Task>> = taskDao.observeAllSubtasks()

    /** Live subtask count per parent task; parents without subtasks are absent. */
    fun observeSubtaskCounts(): Flow<Map<Long, Int>> =
        taskDao.observeSubtaskCounts().map { rows ->
            rows.associate { it.taskId to it.subtaskCount }
        }

    fun observeEvents(taskId: Long): Flow<List<TaskEvent>> = eventDao.observeForTask(taskId)

    suspend fun getTask(taskId: Long): Task? = taskDao.getById(taskId)

    /** Live view of one task, used by the detail screen. */
    fun observeTask(taskId: Long): Flow<Task?> = taskDao.observeById(taskId)

    /**
     * 「这条任务现在还能起一次专注吗」：它必须存在、没被删、没完成、而且是顶层任务。
     *
     * 专注页在选择任务之后、真正开始之前复核一次：选中的那一刻它在列表里，之后用户可能
     * 到别的页面把它完成或者删掉。那时若还照着旧的选择起表，就会把一段专注记在一条已经
     * 结束的任务上（还会触发关联习惯的自动打卡）。子任务也不接受——专注只绑真实任务。
     */
    suspend fun isStartableForFocus(taskId: Long): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        return task.deletedAt == null && task.completedAt == null && task.parentTaskId == null
    }

    /**
     * Creates a task or a subtask. Titles are mandatory and trimmed; notes are
     * optional. Subtasks may only be created under a top level task of the same
     * list (one level only, spec section 4.3).
     *
     * Dates are optional and independent of each other: a planned date never
     * becomes a due date and vice versa (spec section 4.1). A due time without
     * a due date is refused, because there would be no day to attach it to.
     */
    suspend fun createTask(
        title: String,
        listId: Long,
        notes: String? = null,
        priority: Priority = Priority.DEFAULT,
        parentTaskId: Long? = null,
        plannedDate: LocalDate? = null,
        plannedTime: LocalTime? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        repeatRule: RepeatRule? = null
    ): Long {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
        require(plannedTime == null || plannedDate != null) { "计划时间必须依附于计划日期" }
        require(dueTime == null || dueDate != null) { "截止时间必须依附于截止日期" }
        val list = listDao.getById(listId) ?: throw IllegalArgumentException("清单不存在: $listId")

        if (parentTaskId != null) {
            val parent = taskDao.getById(parentTaskId)
                ?: throw IllegalArgumentException("父任务不存在: $parentTaskId")
            require(parent.parentTaskId == null) { "首版只支持一层子任务" }
            require(parent.deletedAt == null) { "父任务已被删除" }
            require(parent.listId == list.id) { "子任务必须与父任务在同一清单" }
        }

        val time = now()
        return db.withTransaction {
            val taskId = taskDao.insert(
                Task(
                    title = trimmedTitle,
                    notes = notes?.trim()?.ifEmpty { null },
                    listId = list.id,
                    parentTaskId = parentTaskId,
                    priority = priority,
                    createdAt = time,
                    updatedAt = time,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    repeatRule = repeatRule
                )
            )
            recordEvent(taskId, TaskEventType.CREATED, trimmedTitle, time)
            taskId
        }
    }

    /**
     * 把重复任务算出来的每一次落成**独立任务**（说明文档 4.5：规则 + 每次独立任务
     * 实例，不循环复用同一条 Task）。
     *
     * 只补 [today]..[today + horizonDays] 这一段：过去的日期不回溯，打开 app 不该凭空
     * 长出一排逾期任务。默认往前铺满**一年**——「每个工作日一直延续」要的就是这个，而
     * 生成器每次都从「今天」起算，所以冷启动重跑时这个窗口会随日期自然往前滑。已经
     * 生成过的那些天——以及带规则那条任务自己占的那一格——都会跳过，所以它幂等，可以
     * 在每次回到前台时放心再跑一遍。
     *
     * 生成出来的那一次带着源的标题、备注、优先级和清单，日期换成这一次的；源如果有
     * 结束日期，实例的结束日期按同样的偏移跟着走（「每周一 9:00-11:00」这种时间段也
     * 就自然重复了）。规则只留在源上，实例自己没有规则。
     *
     * @return 这次新建了几条。
     */
    suspend fun materializeRecurrences(
        today: LocalDate,
        horizonDays: Int = RecurrenceHorizonDays
    ): Int = db.withTransaction {
        val until = today.plusDays(horizonDays.toLong())
        var created = 0

        taskDao.getRepeatSources().forEach { source ->
            val rule = source.repeatRule ?: return@forEach
            val anchor = source.plannedDate ?: return@forEach

            // 已经有的（包括被删掉的、以及源自己那一格）都算「占住了」，绝不重复生成。
            val taken = taskDao.getOccurrencesOf(source.id).mapNotNull { it.plannedDate }.toMutableSet()
            if (!anchor.isBefore(today) && !anchor.isAfter(until)) taken += anchor

            val dueOffset = source.dueDate?.let { ChronoUnit.DAYS.between(anchor, it) }
            rule.occurrencesIn(today, until, anchor).forEach { date ->
                if (!taken.add(date)) return@forEach
                val time = now()
                val occurrenceId = taskDao.insert(
                    source.copy(
                        id = 0L,
                        completedAt = null,
                        deletedAt = null,
                        createdAt = time,
                        updatedAt = time,
                        plannedDate = date,
                        dueDate = dueOffset?.let { date.plusDays(it) },
                        repeatRule = null,
                        recurrenceSourceId = source.id
                    )
                )
                recordEvent(occurrenceId, TaskEventType.CREATED, source.title, time)
                // 标签连线跟源任务照搬过来：实例是一张独立的任务行，标签存在 task_tags
                // 里，不复制的话按标签筛「今天 / 未来 / 日历」时这些实例根本不会出现，
                // 专注开始的标签快照也会是空的（说明文档 4.5 与标签是同一件事）。
                tagDao.tagIdsOf(source.id).forEach { tagId ->
                    tagDao.link(TaskTag(taskId = occurrenceId, tagId = tagId))
                }
                created++
            }
        }
        created
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
     *
     * Passing null for a date clears it. Changing a date is always an explicit
     * user action; nothing here shifts a date on its own.
     */
    suspend fun updateTask(
        taskId: Long,
        title: String,
        notes: String?,
        priority: Priority,
        listId: Long,
        plannedDate: LocalDate? = null,
        plannedTime: LocalTime? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        repeatRule: RepeatRule? = null
    ) {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "任务标题不能为空" }
        require(plannedTime == null || plannedDate != null) { "计划时间必须依附于计划日期" }
        require(dueTime == null || dueDate != null) { "截止时间必须依附于截止日期" }
        val task = taskDao.getById(taskId) ?: throw IllegalArgumentException("任务不存在: $taskId")
        // A subtask lives in the same list as its parent; only top level tasks
        // can be moved between lists.
        val targetListId = if (task.parentTaskId != null) task.listId else listId
        requireNotNull(listDao.getById(targetListId)) { "清单不存在: $targetListId" }

        db.withTransaction {
            val time = now()
            taskDao.update(
                task.copy(
                    title = trimmedTitle,
                    notes = notes?.trim()?.ifEmpty { null },
                    priority = priority,
                    listId = targetListId,
                    updatedAt = time,
                    plannedDate = plannedDate,
                    plannedTime = plannedTime,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    repeatRule = repeatRule
                )
            )
            // 顶层任务换清单时，它的子任务跟着一起搬——否则持久化出来的父子会分属两个
            // 清单，与「父子同清单」这条不变式矛盾（子任务分支里 targetListId 就是原清单，
            // 不必再搬）。
            if (task.parentTaskId == null && task.listId != targetListId) {
                taskDao.moveSubtasksToList(taskId, targetListId, time)
            }
        }
    }

    /**
     * 编辑器保存一条任务的完整业务动作：任务字段与标签关系在**同一个事务**里落库。
     *
     * 之前编辑器自己分三步调用（建/改任务 → 设标签 → 生成重复实例），标签那一步失败就
     * 会留下「任务写进去了、标签没写上」的半完成状态，界面也说不清究竟存没存。收成一个
     * 事务后，这两步要么都成、要么都不落。重复实例生成是幂等的，仍留在事务之外单独跑。
     *
     * @return 落库后的任务 id。
     */
    suspend fun saveTask(
        taskId: Long?,
        title: String,
        listId: Long,
        notes: String?,
        priority: Priority,
        plannedDate: LocalDate?,
        plannedTime: LocalTime?,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        repeatRule: RepeatRule?,
        tagIds: Collection<Long>
    ): Long = db.withTransaction {
        val savedId = if (taskId == null) {
            createTask(
                title = title,
                listId = listId,
                notes = notes,
                priority = priority,
                plannedDate = plannedDate,
                plannedTime = plannedTime,
                dueDate = dueDate,
                dueTime = dueTime,
                repeatRule = repeatRule
            )
        } else {
            updateTask(
                taskId = taskId,
                title = title,
                notes = notes,
                priority = priority,
                listId = listId,
                plannedDate = plannedDate,
                plannedTime = plannedTime,
                dueDate = dueDate,
                dueTime = dueTime,
                repeatRule = repeatRule
            )
            taskId
        }
        setTaskTags(savedId, tagIds)
        savedId
    }

    /** @return true when the task just became completed. */
    suspend fun completeTask(taskId: Long): Boolean = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction false
        if (task.deletedAt != null || task.completedAt != null) return@withTransaction false
        val time = now()
        taskDao.update(task.copy(completedAt = time, updatedAt = time))
        recordEvent(task.id, TaskEventType.COMPLETED, task.title, time)
        true
    }

    /** @return true when a completed task was reopened. */
    suspend fun reopenTask(taskId: Long): Boolean = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction false
        if (task.deletedAt != null || task.completedAt == null) return@withTransaction false
        val time = now()
        taskDao.update(task.copy(completedAt = null, updatedAt = time))
        recordEvent(task.id, TaskEventType.REOPENED, task.title, time)
        true
    }

    /**
     * Soft deletes a task and - as the spec requires - its live subtasks.
     * Nothing is removed from the database, so the trash can bring the whole
     * set back. The ids of the cascaded subtasks are recorded in the parent's
     * DELETED event, so a later restore brings back exactly that set instead of
     * every subtask that happens to be deleted.
     *
     * @return the number of subtasks that were soft deleted together with it.
     */
    suspend fun deleteTask(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        if (task.deletedAt != null) return@withTransaction 0
        val time = now()
        val subtasks = taskDao.getLiveSubtasks(taskId)
        taskDao.markDeleted(taskId, time)
        recordEvent(
            task.id,
            TaskEventType.DELETED,
            task.title,
            time,
            detailsJson = encodeSubtaskIds(subtasks.map { it.id })
        )
        subtasks.forEach { subtask ->
            taskDao.markDeleted(subtask.id, time)
            recordEvent(subtask.id, TaskEventType.DELETED, subtask.title, time)
        }
        subtasks.size
    }

    /**
     * 删掉一整串重复任务：规则源和它生成过的每一次一起进回收站。
     *
     * 用户看到的是一件事在重复（说明文档 4.5），所以「以后都不再重复了」必须一次把整串
     * 收走——只删当前这一次的话，第二天它又长回来，看起来就像删不掉。
     *
     * 每次删除都只作用在**还活着**的行上：用户先前单独删掉的那一次已经有自己的 DELETED
     * 事件，不该被这次算进去，恢复整串时也就不会把它带回来（与父任务连带子任务的口径
     * 一致）。被这次删掉的每一行都在事件里留一个 `seriesSourceId` 记号，恢复据此还原
     * 同一批行，不必再另存一张表。
     *
     * @return 这次实际移入回收站的条数（含规则源自己）。
     */
    suspend fun deleteSeries(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        if (task.deletedAt != null) return@withTransaction 0
        val sourceId = TaskSeries.sourceIdOf(task) ?: return@withTransaction deleteTask(taskId)
        val time = now()
        val rows = taskDao.getSeriesRows(sourceId).filter { it.deletedAt == null }
        rows.forEach { row ->
            val subtasks = taskDao.getLiveSubtasks(row.id)
            if (taskDao.markDeleted(row.id, time) > 0) {
                recordEvent(
                    row.id,
                    TaskEventType.DELETED,
                    row.title,
                    time,
                    detailsJson = encodeSeriesDeletion(sourceId, subtasks.map { it.id })
                )
                // 整串删除对一个实例来说，也把它的子任务一起带走：子任务只在父任务详情里
                // 看得到，父任务没了却留着子任务，就等于把它们藏了起来。
                subtasks.forEach { subtask ->
                    if (taskDao.markDeleted(subtask.id, time) > 0) {
                        recordEvent(
                            subtask.id,
                            TaskEventType.DELETED,
                            subtask.title,
                            time,
                            detailsJson = encodeSeriesDeletion(sourceId, emptyList())
                        )
                    }
                }
            }
        }
        rows.size
    }

    /**
     * 恢复一整串重复任务：把「随整串一起删掉」的那些行原样带回来，连同它们的子任务。
     *
     * 判据是每一行自己最新的 DELETED 事件上的 `seriesSourceId` 记号：单独被删过的那一次
     * 没有这个记号，所以它留在回收站里——撤销的是刚才那一次整串删除，不是回收站里的所有
     * 东西。
     *
     * @return 恢复的行数（含子任务）；[taskId] 不属于重复任务时退回单条语义。
     */
    suspend fun restoreSeries(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        val sourceId = TaskSeries.sourceIdOf(task) ?: return@withTransaction restoreTask(taskId)
        val time = now()
        var restored = 0
        taskDao.getSeriesRows(sourceId)
            .filter { it.deletedAt != null }
            .filter { seriesMarkOf(it.id) == sourceId }
            .forEach { row ->
                restored += restoreOne(row, time)
                cascadedSubtaskIdsOf(row.id).forEach { subtaskId ->
                    taskDao.getById(subtaskId)
                        ?.takeIf { it.deletedAt != null }
                        ?.let { subtask -> restored += restoreOne(subtask, time) }
                }
            }
        restored
    }

    /**
     * 这一条属于一串**还在重复**的任务吗：它有源，而且那个源还活着、身上还带着规则。
     *
     * 详情页据此决定删除弹层里要不要给出「删除整条系列」——源已经被删掉的那些残留实例，
     * 后面不会再长出来了，那时「整条系列」这个选项只会让人多删几条历史。
     */
    suspend fun isInLiveSeries(taskId: Long): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        val sourceId = TaskSeries.sourceIdOf(task) ?: return false
        return taskDao.countLiveRepeatSource(sourceId) > 0
    }

    /**
     * Restores a soft deleted task.
     *
     * Restoring a parent brings back the subtasks that were deleted with it.
     * Restoring a subtask also restores its parent when that parent is still
     * deleted, so a visible subtask never hangs under an invisible parent.
     * Siblings are left untouched.
     *
     * 一个属于重复任务的实例若带着「随整串删除」的记号，恢复它就把整串带回来：用户在
     * 回收站里点的是「撤销那次删除」，心里想的就是那件事还在重复。
     *
     * @return the number of tasks (including the requested one) that were
     * restored.
     */
    suspend fun restoreTask(taskId: Long): Int = db.withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction 0
        if (task.deletedAt == null) return@withTransaction 0
        // 只有「属于某一串」的行才谈得上恢复整串：子任务也可能带着整串删除的记号，但它
        // 不属于那串任务，按单条恢复（连带它的父任务）就够了，也不必绕回 [restoreSeries]。
        if (TaskSeries.sourceIdOf(task) != null && seriesMarkOf(taskId) != null) {
            return@withTransaction restoreSeries(taskId)
        }
        val time = now()
        var restored = 0

        task.parentTaskId?.let { parentId ->
            taskDao.getById(parentId)
                ?.takeIf { it.deletedAt != null }
                ?.let { parent -> restored += restoreOne(parent, time) }
        }
        restored += restoreOne(task, time)
        // Only the subtasks that went down together with this task come back; a
        // subtask the user had deleted on its own stays in the trash.
        cascadedSubtaskIdsOf(taskId).forEach { subtaskId ->
            taskDao.getById(subtaskId)
                ?.takeIf { it.deletedAt != null }
                ?.let { subtask -> restored += restoreOne(subtask, time) }
        }
        restored
    }

    /**
     * 这一行最新的 DELETED 事件上写的「随哪一串一起删除」；单独删的那次没有这个记号，
     * 返回 null。
     */
    private suspend fun seriesMarkOf(taskId: Long): Long? =
        eventDao.getForTask(taskId)
            .lastOrNull { it.eventType == TaskEventType.DELETED }
            ?.detailsJson
            ?.let { SeriesSourcePattern.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }

    /**
     * The subtask ids that were cascaded with [taskId], read from its latest
     * DELETED event. Keeping this in the event table makes the history the
     * single source of truth and avoids a schema change for the bookkeeping.
     */
    private suspend fun cascadedSubtaskIdsOf(taskId: Long): List<Long> =
        parseSubtaskIds(
            eventDao.getForTask(taskId)
                .lastOrNull { it.eventType == TaskEventType.DELETED }
                ?.detailsJson
        )

    private fun parseSubtaskIds(detailsJson: String?): List<Long> =
        detailsJson
            ?.let { SubtaskIdsPattern.find(it)?.groupValues?.getOrNull(1) }
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { it.toLong() }
            .orEmpty()

    private fun encodeSubtaskIds(ids: List<Long>): String? =
        ids.takeIf { it.isNotEmpty() }?.let { """{"subtaskIds":[${it.joinToString(",")}]}""" }

    /** 整串删除时留在每一行 DELETED 事件上的记号，恢复整串时按它认人。 */
    private fun encodeSeriesDeletion(sourceId: Long, subtaskIds: List<Long>): String = buildString {
        append("""{"seriesSourceId":$sourceId""")
        // 子任务 id 用现成的 subtaskIds 口径，恢复那一条的连坐子任务仍然读得出来。
        if (subtaskIds.isNotEmpty()) append(""","subtaskIds":[${subtaskIds.joinToString(",")}]""")
        append('}')
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

    private companion object {
        /** Matches the id array of a `{"subtaskIds":[1,2]}` payload. */
        val SubtaskIdsPattern = Regex("""\[([0-9,]*)\]""")

        /** Matches the source id of a `{"seriesSourceId":7}` marker. */
        val SeriesSourcePattern = Regex("\"seriesSourceId\"\\s*:\\s*(\\d+)")
    }
}
