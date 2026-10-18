package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The date rules of spec section 4.1 against a real (in memory) Room database:
 * the "今日" view merges duplicates, "逾期" follows the explicit due moment or
 * the planned-date fallback, and an overdue task is never postponed by the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDateViewTest {

    private lateinit var db: TodoDatabase
    private lateinit var repository: TaskRepository
    private var clock = 0L
    private var inboxId = 0L

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 10, 14)

    private fun millisOf(dateTime: LocalDateTime, zone: ZoneId = shanghai): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = millisOf(LocalDateTime.of(2026, 10, 14, 9, 0))
        repository = TaskRepository(db = db, inboxName = "收件箱", now = { clock }, zone = shanghai)
        inboxId = repository.ensureInbox()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------- 今日视图

    @Test
    fun todayViewShowsATaskBothPlannedAndDueTodayExactlyOnce() = runBlocking {
        val taskId = repository.createTask(
            title = "今天要做",
            listId = inboxId,
            plannedDate = today,
            dueDate = today
        )

        val todayTasks = repository.observeTodayTasks().first()

        assertEquals(listOf(taskId), todayTasks.map { it.id })
    }

    @Test
    fun todayViewIgnoresTasksDatedOnOtherDays() = runBlocking {
        val todayTask = repository.createTask("今天完成", inboxId, dueDate = today)
        repository.createTask("明天完成", inboxId, dueDate = today.plusDays(1))
        repository.createTask("没有日期", inboxId)

        assertEquals(listOf(todayTask), repository.observeTodayTasks().first().map { it.id })
    }

    @Test
    fun todayViewLeavesOutCompletedAndDeletedTasks() = runBlocking {
        val open = repository.createTask("未完成", inboxId, dueDate = today)
        val completed = repository.createTask("已完成", inboxId, dueDate = today)
        val deleted = repository.createTask("已删除", inboxId, dueDate = today)
        repository.completeTask(completed)
        repository.deleteTask(deleted)

        assertEquals(listOf(open), repository.observeTodayTasks().first().map { it.id })
    }

    // ------------------------------------------------------------- 未来视图

    @Test
    fun futureViewListsLaterDatesOnly() = runBlocking {
        repository.createTask("今天", inboxId, plannedDate = today)
        val tomorrow = repository.createTask("明天", inboxId, plannedDate = today.plusDays(1))
        val nextWeek = repository.createTask("下周截止", inboxId, dueDate = today.plusDays(7))

        assertEquals(
            setOf(tomorrow, nextWeek),
            repository.observeFutureTasks().first().map { it.id }.toSet()
        )
    }

    /**
     * 跨过午夜后，今天 / 逾期这些视图要**自己**翻页，而不是停在「流建立的那一天」。
     *
     * 订阅它们的 view model 长期住在 Activity 的 ViewModelStore 里，界面自己不会重建
     * 流，所以日期必须由流自己去读：这里把 tick 间隔调成 1 毫秒，跨天只等一次 tick。
     */
    @Test
    fun theDateDrivenViewsFollowAMidnightRollover() = runBlocking {
        val ticking = TaskRepository(
            db = db,
            inboxName = "收件箱",
            now = { clock },
            zone = shanghai,
            todayRefreshMillis = 1
        )
        val taskId = ticking.createTask("今天要做", inboxId, plannedDate = today)

        assertEquals(listOf(taskId), ticking.observeTodayTasks().first { it.isNotEmpty() }.map { it.id })
        assertTrue("计划日期是今天：还没逾期", ticking.observeOverdueTasks().first().isEmpty())

        // 跨过午夜。不重启进程、不重建任何一条流。
        clock = millisOf(LocalDateTime.of(2026, 10, 15, 0, 0, 30))

        assertEquals(
            "跨天后今天视图应该自己空了",
            emptyList<Long>(),
            withTimeout(5_000) { ticking.observeTodayTasks().first { it.isEmpty() } }
                .map { it.id }
        )
        assertEquals(
            "跨天后这条任务应该自己进逾期",
            listOf(taskId),
            withTimeout(5_000) { ticking.observeOverdueTasks().first { it.isNotEmpty() } }
                .map { it.id }
        )
    }

    // ------------------------------------------------------------- 逾期视图

    /**
     * 逾期是按**时刻**判的：库一动不动的时候，时间自己也在走。
     *
     * 任务今天 10:00 到期，9:00 起一直把这一页摆在面前到 11:00——中间**一次库都不写**，
     * 这条流也必须自己把它算成逾期（台账 N6：原来只在 Room 发射时求一次 `now()`，于是
     * 逾期视图与抽屉徽章会一直停在旧结果）。tick 间隔调成 1 毫秒，到点只要等一次 tick。
     */
    @Test
    fun theOverdueViewFollowsTheClockWithoutWaitingForAWrite() = runBlocking {
        val ticking = TaskRepository(
            db = db,
            inboxName = "收件箱",
            now = { clock },
            zone = shanghai,
            overdueRefreshMillis = 1
        )
        val taskId = ticking.createTask(
            "十点到期",
            inboxId,
            dueDate = today,
            dueTime = LocalTime.of(10, 0)
        )

        val emitted = mutableListOf<List<Long>>()
        val collector = launch {
            ticking.observeOverdueTasks().collect { rows -> emitted += rows.map { task -> task.id } }
        }
        try {
            withTimeout(5_000) { while (emitted.isEmpty()) delay(5) }
            assertTrue("9:00 还没到点，不该是逾期", emitted.last().isEmpty())

            // 把钟拨到 11:00，**不碰数据库**：只有那条时刻节拍能让它变。
            clock = millisOf(LocalDateTime.of(2026, 10, 14, 11, 0))
            withTimeout(5_000) { while (emitted.last().isEmpty()) delay(5) }
            assertEquals(listOf(taskId), emitted.last())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun aTaskWithoutAnyDateIsNeverOverdue() = runBlocking {
        repository.createTask("没有日期", inboxId)
        clock = millisOf(LocalDateTime.of(2026, 12, 1, 9, 0))

        assertTrue(repository.observeOverdueTasks().first().isEmpty())
    }

    @Test
    fun aPlannedDateWithoutADueDateBecomesOverdueAfterThatDayEnds() = runBlocking {
        val taskId = repository.createTask(
            "只有计划日期",
            inboxId,
            plannedDate = today.minusDays(1),
            plannedTime = LocalTime.of(10, 0)
        )

        assertEquals(
            listOf(taskId),
            repository.observeOverdueTasks().first().map { it.id }
        )
    }

    @Test
    fun aTaskPlannedForTodayIsNotOverdueUntilTheNextDay() = runBlocking {
        val taskId = repository.createTask(
            "今天 10:00 开始",
            inboxId,
            plannedDate = today,
            plannedTime = LocalTime.of(10, 0)
        )

        clock = millisOf(LocalDateTime.of(2026, 10, 14, 18, 0))
        assertTrue("开始时间不参与逾期判断", repository.observeOverdueTasks().first().isEmpty())

        clock = millisOf(LocalDateTime.of(2026, 10, 15, 0, 0, 1))
        assertEquals(
            listOf(taskId),
            repository.observeOverdueTasks().first().map { it.id }
        )
    }

    @Test
    fun anExplicitFutureDueDateOverridesAPastPlannedDate() = runBlocking {
        repository.createTask(
            "计划昨天，明天截止",
            inboxId,
            plannedDate = today.minusDays(1),
            dueDate = today.plusDays(1)
        )

        assertTrue(repository.observeOverdueTasks().first().isEmpty())
    }

    @Test
    fun overdueViewFollowsTheDueMoment() = runBlocking {
        val overdueTask = repository.createTask("昨天截止", inboxId, dueDate = today.minusDays(1))
        val laterToday = repository.createTask("今天 12:20 截止", inboxId, dueDate = today, dueTime = LocalTime.of(12, 20))

        assertEquals(
            "12:20 之前不算逾期",
            listOf(overdueTask),
            repository.observeOverdueTasks().first().map { it.id }
        )

        clock = millisOf(LocalDateTime.of(2026, 10, 14, 12, 30))

        assertEquals(
            listOf(overdueTask, laterToday),
            repository.observeOverdueTasks().first().map { it.id }.sorted()
        )
    }

    @Test
    fun aDateOnlyDueDateTurnsOverdueAtTheNextLocalMidnight() = runBlocking {
        repository.createTask("今天截止", inboxId, dueDate = today)

        clock = millisOf(LocalDateTime.of(2026, 10, 14, 23, 59, 59))
        assertTrue("当天结束前不算逾期", repository.observeOverdueTasks().first().isEmpty())

        clock = millisOf(LocalDateTime.of(2026, 10, 15, 0, 0, 1))
        assertEquals(1, repository.observeOverdueTasks().first().size)
    }

    @Test
    fun anOverdueTaskKeepsItsOriginalDate() = runBlocking {
        val taskId = repository.createTask("昨天截止", inboxId, dueDate = today.minusDays(1))
        clock = millisOf(LocalDateTime.of(2026, 11, 20, 9, 0))

        repository.observeOverdueTasks().first()

        val task = requireNotNull(repository.getTask(taskId))
        assertEquals(
            "逾期不自动顺延：日期必须停在用户设定的那一天",
            today.minusDays(1),
            task.dueDate
        )
        assertEquals(LocalDate.of(2026, 11, 20), repository.today())
    }

    @Test
    fun theDeviceTimeZoneDecidesWhichDayItIs() = runBlocking {
        repository.createTask("今天截止", inboxId, dueDate = today)
        clock = millisOf(LocalDateTime.of(2026, 10, 15, 1, 0), shanghai)

        assertEquals(1, repository.observeOverdueTasks().first().size)

        val utcRepository = TaskRepository(db, "收件箱", now = { clock }, zone = ZoneId.of("UTC"))
        assertTrue(
            "在 UTC 下这一天还没结束，任务不应逾期",
            utcRepository.observeOverdueTasks().first().isEmpty()
        )
    }

    // --------------------------------------------------------- 已完成分组

    @Test
    fun completedTasksAreKeptPerListAndLeaveTheOpenList() = runBlocking {
        val otherListId = repository.createList("工作")
        val completedInbox = repository.createTask("收件箱里完成", inboxId)
        val completedOther = repository.createTask("工作里完成", otherListId)
        repository.createTask("收件箱里未完成", inboxId)
        repository.completeTask(completedInbox)
        repository.completeTask(completedOther)

        assertEquals(
            listOf(completedInbox),
            repository.observeCompletedInboxTasks().first().map { it.id }
        )
        assertEquals(
            listOf(completedOther),
            repository.observeCompletedTasksInList(otherListId).first().map { it.id }
        )
        assertEquals(
            "已完成的任务离开未完成列表，但不会消失",
            listOf("收件箱里未完成"),
            repository.observeInboxTasks().first().map { it.title }
        )
    }

    @Test
    fun theCompletedGroupLeavesOutDeletedTasksAndSubtasks() = runBlocking {
        val parent = repository.createTask("父任务", inboxId)
        val subtask = repository.addSubtask(parent, "子任务")
        val deleted = repository.createTask("已完成又被删除", inboxId)
        repository.completeTask(parent)
        repository.completeTask(subtask)
        repository.completeTask(deleted)
        repository.deleteTask(deleted)

        assertEquals(
            "子任务跟着父任务显示在详情页，回收站里的任务不显示",
            listOf(parent),
            repository.observeCompletedInboxTasks().first().map { it.id }
        )
    }

    // --------------------------------------------------- 日历页的日期区间

    @Test
    fun theCalendarRangeQueryReturnsEachTaskOnceAndIgnoresDatesOutsideTheRange() = runBlocking {
        val plannedInside = repository.createTask("计划在本月", inboxId, plannedDate = LocalDate.of(2026, 10, 3))
        val dueInside = repository.createTask("截止在本月", inboxId, dueDate = LocalDate.of(2026, 10, 20))
        val insideTwice = repository.createTask(
            "同月两个日期",
            inboxId,
            plannedDate = LocalDate.of(2026, 10, 10),
            dueDate = LocalDate.of(2026, 10, 25)
        )
        repository.createTask("上个月", inboxId, plannedDate = LocalDate.of(2026, 9, 30))
        repository.createTask("下个月", inboxId, dueDate = LocalDate.of(2026, 11, 1))
        repository.createTask("没有日期", inboxId)

        val inOctober = repository
            .observeOpenTasksSpanning(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
            .first()

        assertEquals(
            "一个任务即使两个日期都落在本月也只返回一次",
            setOf(plannedInside, dueInside, insideTwice),
            inOctober.map { it.id }.toSet()
        )
    }

    @Test
    fun theCalendarRangeQueryLeavesOutFinishedAndDeletedTasks() = runBlocking {
        val open = repository.createTask("未完成", inboxId, plannedDate = LocalDate.of(2026, 10, 5))
        val completed = repository.createTask("已完成", inboxId, plannedDate = LocalDate.of(2026, 10, 6))
        val deleted = repository.createTask("已删除", inboxId, plannedDate = LocalDate.of(2026, 10, 7))
        repository.completeTask(completed)
        repository.deleteTask(deleted)

        val range = repository
            .observeOpenTasksSpanning(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
            .first()

        assertEquals(listOf(open), range.map { it.id })
    }

    @Test
    fun theCalendarRangeQueryIncludesBothRangeEnds() = runBlocking {
        val firstDay = repository.createTask("月初", inboxId, plannedDate = LocalDate.of(2026, 10, 1))
        val lastDay = repository.createTask("月末", inboxId, dueDate = LocalDate.of(2026, 10, 31))

        val range = repository
            .observeOpenTasksSpanning(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
            .first()

        assertEquals(setOf(firstDay, lastDay), range.map { it.id }.toSet())
    }

    // --------------------------------------------------- 某日已完成分组

    @Test
    fun completedTasksOfADayKeepTheDayTheyWereGiven() = runBlocking {
        val doneToday = repository.createTask("今天完成", inboxId, plannedDate = today)
        val doneByDueDate = repository.createTask("今天截止", inboxId, dueDate = today)
        val doneOtherDay = repository.createTask("明天完成", inboxId, plannedDate = today.plusDays(1))
        val finishedWithoutADate = repository.createTask("没有日期", inboxId)
        listOf(doneToday, doneByDueDate, doneOtherDay, finishedWithoutADate)
            .forEach { repository.completeTask(it) }

        val completedToday = repository.observeCompletedTasksOn(today).first()

        assertEquals(
            "完成时间不影响归属的那一天",
            setOf(doneToday, doneByDueDate),
            completedToday.map { it.id }.toSet()
        )
    }

    @Test
    fun aTaskStillOpenDoesNotShowUpInTheCompletedGroupOfItsDay() = runBlocking {
        repository.createTask("还没做", inboxId, plannedDate = today)
        val deleted = repository.createTask("已完成又被删除", inboxId, plannedDate = today)
        repository.completeTask(deleted)
        repository.deleteTask(deleted)

        assertTrue(repository.observeCompletedTasksOn(today).first().isEmpty())
    }

    // ------------------------------------------------------------- 存储格式

    @Test
    fun aDueTimeWithoutADueDateIsRejected() = runBlocking {
        val rejected = try {
            repository.createTask("没有截止日期", inboxId, dueTime = LocalTime.of(9, 0))
            false
        } catch (error: IllegalArgumentException) {
            true
        }

        assertTrue(rejected)
    }

    @Test
    fun datesAreStoredAsPlainTextWithoutTimeZoneShift() = runBlocking {
        val taskId = repository.createTask(
            title = "带日期的任务",
            listId = inboxId,
            plannedDate = today,
            dueDate = today,
            dueTime = LocalTime.of(12, 20)
        )

        db.openHelper.readableDatabase
            .query("SELECT plannedDate, dueDate, dueTime FROM tasks WHERE id = ?", arrayOf<Any?>(taskId))
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("2026-10-14", cursor.getString(0))
                assertEquals("2026-10-14", cursor.getString(1))
                assertEquals("12:20", cursor.getString(2))
            }
    }

    @Test
    fun clearingADateKeepsTheOtherValues() = runBlocking {
        val taskId = repository.createTask(
            title = "任务",
            listId = inboxId,
            notes = "备注",
            priority = Priority.IMPORTANT_NOT_URGENT,
            plannedDate = today,
            dueDate = today.plusDays(2)
        )

        repository.updateTask(
            taskId = taskId,
            title = "任务",
            notes = "备注",
            priority = Priority.IMPORTANT_NOT_URGENT,
            listId = inboxId,
            plannedDate = null,
            dueDate = today.plusDays(2)
        )

        val task = requireNotNull(repository.getTask(taskId))
        assertNull(task.plannedDate)
        assertEquals(today.plusDays(2), task.dueDate)
        assertEquals(Priority.IMPORTANT_NOT_URGENT, task.priority)
        assertEquals("备注", task.notes)
    }

    // --------------------------------------------------- 日历的区间显示

    @Test
    fun theCalendarRangeKeepsAnIntervalThatOnlyPassesThroughTheMonth() = runBlocking {
        // Planned in September, due in November: the interval crosses October
        // without either end being inside it.
        val crossing = repository.createTask(
            "跨月的事",
            inboxId,
            plannedDate = LocalDate.of(2026, 9, 28),
            dueDate = LocalDate.of(2026, 11, 3)
        )
        repository.createTask("完全在十一月", inboxId, plannedDate = LocalDate.of(2026, 11, 10))

        val inOctober = repository
            .observeOpenTasksSpanning(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
            .first()

        assertEquals(listOf(crossing), inOctober.map { it.id })
    }

    @Test
    fun theCalendarDayListShowsATaskOnEveryDayOfItsInterval() = runBlocking {
        val spanning = repository.createTask(
            "三天的活",
            inboxId,
            plannedDate = LocalDate.of(2026, 10, 12),
            dueDate = LocalDate.of(2026, 10, 14)
        )
        val singleDay = repository.createTask("只有一天", inboxId, plannedDate = LocalDate.of(2026, 10, 20))
        repository.createTask("区间之外", inboxId, plannedDate = LocalDate.of(2026, 11, 1))

        val days = listOf(11, 12, 13, 14, 15).map { day ->
            repository.observeOpenTasksCovering(LocalDate.of(2026, 10, day)).first().map { it.id }
        }

        assertEquals(
            "区间内的每一天都能看到这条任务，两端也一样",
            listOf(emptyList(), listOf(spanning), listOf(spanning), listOf(spanning), emptyList()),
            days
        )
        assertEquals(
            listOf(singleDay),
            repository.observeOpenTasksCovering(LocalDate.of(2026, 10, 20)).first().map { it.id }
        )
    }

    @Test
    fun theTodayViewIsNotWidenedByTheIntervalRule() = runBlocking {
        // Both dates are away from today, so 今日 must not pick it up even though
        // the interval covers today; only the calendar may show it today.
        val spanning = repository.createTask(
            "跨过今天的事",
            inboxId,
            plannedDate = today.minusDays(4),
            dueDate = today.plusDays(4)
        )

        assertTrue(
            "今日视图仍然只收计划日期或截止日期为今天的任务",
            repository.observeTodayTasks().first().isEmpty()
        )
        assertEquals(
            listOf(spanning),
            repository.observeOpenTasksCovering(today).first().map { it.id }
        )
    }

    @Test
    fun theIntervalRuleOnlyAppliesToTheCalendarAndNeverChangesADate() = runBlocking {
        val taskId = repository.createTask(
            "跨月的事",
            inboxId,
            plannedDate = LocalDate.of(2026, 9, 28),
            dueDate = LocalDate.of(2026, 11, 3)
        )

        repository.observeOpenTasksCovering(LocalDate.of(2026, 10, 15)).first()

        val task = requireNotNull(repository.getTask(taskId))
        assertEquals("任务日期只用于显示，不会被改写", LocalDate.of(2026, 9, 28), task.plannedDate)
        assertEquals(LocalDate.of(2026, 11, 3), task.dueDate)
    }

    // ----------------------------------------------------------- 计划时间

    @Test
    fun aPlannedTimeNeedsAPlannedDate() = runBlocking {
        val rejected = try {
            repository.createTask("只有时间", inboxId, plannedTime = LocalTime.of(9, 0))
            false
        } catch (error: IllegalArgumentException) {
            true
        }

        assertTrue("计划时间不能脱离计划日期存在", rejected)
    }

    @Test
    fun thePlannedTimeIsKeptSeparateFromTheDueTime() = runBlocking {
        val taskId = repository.createTask(
            title = "上午写报告",
            listId = inboxId,
            plannedDate = today,
            plannedTime = LocalTime.of(9, 0),
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(18, 0)
        )

        val task = requireNotNull(repository.getTask(taskId))
        assertEquals("开始做的时间", LocalTime.of(9, 0), task.plannedTime)
        assertEquals("最晚完成的时间", LocalTime.of(18, 0), task.dueTime)

        repository.updateTask(
            taskId = taskId,
            title = "上午写报告",
            notes = null,
            priority = Priority.NOT_IMPORTANT_NOT_URGENT,
            listId = inboxId,
            plannedDate = null,
            plannedTime = null,
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(18, 0)
        )

        val updated = requireNotNull(repository.getTask(taskId))
        assertNull("清掉计划日期时计划时间一并清掉", updated.plannedTime)
        assertEquals("截止时间不受影响", LocalTime.of(18, 0), updated.dueTime)
    }

    // ----------------------------------------------------- 编辑器的日期字段

    @Test
    fun editingOneDateKeepsTheOthersAndCanClearTheTimeOnItsOwn() = runBlocking {
        val taskId = repository.createTask(
            title = "带日期",
            listId = inboxId,
            plannedDate = today,
            dueDate = today.plusDays(1),
            dueTime = LocalTime.of(18, 30)
        )

        repository.updateTask(
            taskId = taskId,
            title = "带日期",
            notes = null,
            priority = Priority.NOT_IMPORTANT_NOT_URGENT,
            listId = inboxId,
            plannedDate = today.plusDays(3),
            dueDate = today.plusDays(1),
            dueTime = null
        )

        val task = requireNotNull(repository.getTask(taskId))
        assertEquals("计划日期可以单独改", today.plusDays(3), task.plannedDate)
        assertEquals("截止日期不受影响", today.plusDays(1), task.dueDate)
        assertNull("只清掉截止时间，日期还在", task.dueTime)
    }

    @Test
    fun aRefusedUpdateLeavesTheStoredTaskUntouched() = runBlocking {
        val taskId = repository.createTask(
            title = "带日期",
            listId = inboxId,
            dueDate = today,
            dueTime = LocalTime.of(8, 0)
        )

        val rejected = try {
            repository.updateTask(
                taskId = taskId,
                title = "带日期",
                notes = null,
                priority = Priority.NOT_IMPORTANT_NOT_URGENT,
                listId = inboxId,
                plannedDate = today,
                dueDate = null,
                dueTime = LocalTime.of(9, 0)
            )
            false
        } catch (error: IllegalArgumentException) {
            true
        }

        assertTrue("截止时间不能脱离截止日期存在", rejected)
        val task = requireNotNull(repository.getTask(taskId))
        assertEquals("被拒绝的编辑不能改动已保存的数据", today, task.dueDate)
        assertEquals(LocalTime.of(8, 0), task.dueTime)
    }
}
