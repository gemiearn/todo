package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Business rule tests for the phase 1 task core. They run on the JVM against a
 * real (in memory) Room database, so the DAO queries, the foreign keys and the
 * event table are all exercised - only the Android UI is left out.
 *
 * The clock is injected and only moves when a test explicitly advances it, so
 * ordering assertions (createdAt / eventAt) are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskRepositoryTest {

    private lateinit var db: TodoDatabase
    private lateinit var repository: TaskRepository
    private var clock = 1_000L
    private var inboxId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db = db, inboxName = "收件箱", now = { clock })
        inboxId = repository.ensureInbox()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------- 四象限

    /**
     * 四象限只画日期还没过去的事：计划日期或截止日期早于今天的不进（过期的事该去清单/
     * 逾期里收），**没有日期的照旧留着**——它们只是还没安排时间，不是过去的事。
     */
    @Test
    fun theQuadrantSkipsPastDatesButKeepsUndatedAndToday() = runBlocking {
        val today = repository.today()
        repository.createTask("没日期", inboxId)
        repository.createTask("今天计划", inboxId, plannedDate = today)
        repository.createTask("明天", inboxId, plannedDate = today.plusDays(1))
        repository.createTask("昨天计划", inboxId, plannedDate = today.minusDays(1))
        repository.createTask("上周截止", inboxId, dueDate = today.minusDays(7))
        repository.createTask("今天截止", inboxId, dueDate = today)

        val titles = repository.observeQuadrantTasks().first().map { it.title }

        assertEquals(
            "过期的不进四象限，无日期 / 今天 / 未来的都留着",
            setOf("没日期", "今天计划", "明天", "今天截止"),
            titles.toSet()
        )
    }

    // ------------------------------------------------------------- lists

    @Test
    fun inboxIsCreatedOnceAndIsTheOnlyBuiltInList() = runBlocking {
        val again = repository.ensureInbox()

        assertEquals(inboxId, again)
        val lists = repository.observeLists().first()
        assertEquals(1, lists.size)
        assertTrue(lists.single().isInbox)
        assertEquals("收件箱", lists.single().name)
    }

    @Test
    fun creatingAListTrimsTheNameAndRejectsBlank() = runBlocking {
        val listId = repository.createList("  工作  ", "📋")

        assertEquals("工作", db.taskListDao().getById(listId)?.name)
        assertEquals("📋", db.taskListDao().getById(listId)?.icon)
        // 空图标与没写一样，都存成 null（选「无」就是把这一列清掉）。
        assertEquals(null, db.taskListDao().getById(repository.createList("生活", " "))?.icon)
        assertFailsWithIllegalArgument { repository.createList("   ") }
    }

    @Test
    fun renamingAListTrimsTheNameAndRejectsBlank() = runBlocking {
        val listId = repository.createList("工作", "📋")
        repository.renameList(listId, "  个人事务 ", "🏠")

        assertEquals("个人事务", db.taskListDao().getById(listId)?.name)
        assertEquals("换图标与换名字走同一个入口", "🏠", db.taskListDao().getById(listId)?.icon)
        repository.renameList(listId, "个人事务", "")
        assertEquals("选「无」就把图标清空", null, db.taskListDao().getById(listId)?.icon)
        assertFailsWithIllegalArgument { repository.renameList(listId, " ", "🏠") }
    }

    @Test
    fun deletingAListMovesEveryTaskToTheInboxIncludingDeletedOnes() = runBlocking {
        val listId = repository.createList("工作")
        val open = repository.createTask("任务 A", listId)
        val deleted = repository.createTask("任务 B", listId)
        advanceClock()
        repository.deleteTask(deleted)

        val moved = repository.deleteListMovingTasksToInbox(listId)

        assertEquals(2, moved)
        assertNull(db.taskListDao().getById(listId))
        assertEquals(inboxId, repository.getTask(open)?.listId)
        assertEquals(inboxId, repository.getTask(deleted)?.listId)
        // The deleted task is still in the trash - a list delete never deletes tasks.
        assertNotNull(repository.getTask(deleted)?.deletedAt)
        assertEquals(1, repository.observeLists().first().size)
    }

    @Test
    fun theInboxCannotBeDeleted() = runBlocking {
        assertFailsWithIllegalArgument { repository.deleteListMovingTasksToInbox(inboxId) }

        assertEquals(1, repository.observeLists().first().size)
    }

    @Test
    fun movingAUserListReordersTheUserListsButLeavesTheInboxFirst() = runBlocking {
        val work = repository.createList("工作")
        repository.createList("生活")
        val study = repository.createList("学习")
        assertEquals(listOf("工作", "生活", "学习"), userListNames())

        assertTrue(repository.moveList(study, -1))
        assertEquals(listOf("工作", "学习", "生活"), userListNames())

        assertTrue(repository.moveList(work, 1))
        assertEquals(listOf("学习", "工作", "生活"), userListNames())

        // The inbox is pinned: it is never part of the user order.
        assertEquals("收件箱", repository.observeLists().first().first().name)
    }

    @Test
    fun movingAListBeyondTheOrderChangesNothing() = runBlocking {
        val work = repository.createList("工作")
        val life = repository.createList("生活")

        assertFalse(repository.moveList(work, -1)) // already first
        assertFalse(repository.moveList(life, 1))  // already last

        assertEquals(listOf("工作", "生活"), userListNames())
    }

    @Test
    fun theInboxIsNeverPartOfTheReorderableUserLists() = runBlocking {
        repository.createList("工作")

        assertFalse(repository.moveList(inboxId, -1))
        assertFalse(repository.moveList(inboxId, 1))

        assertEquals(listOf("工作"), userListNames())
    }

    private suspend fun userListNames(): List<String> =
        repository.observeLists().first()
            .filter { !it.isInbox }
            .map { it.name }

    // -------------------------------------------------------- task basics

    @Test
    fun creatingATaskRequiresATitleAndCreatesACreatedEvent() = runBlocking {
        assertFailsWithIllegalArgument { repository.createTask("   ", inboxId) }

        val taskId = repository.createTask("写周报", inboxId, notes = "  含下季度计划  ", priority = Priority.IMPORTANT_NOT_URGENT)
        val task = requireNotNull(repository.getTask(taskId))
        assertEquals("写周报", task.title)
        assertEquals("含下季度计划", task.notes)
        assertEquals(Priority.IMPORTANT_NOT_URGENT, task.priority)
        assertNull(task.completedAt)
        assertNull(task.deletedAt)

        val events = db.taskEventDao().getForTask(taskId)
        assertEquals(1, events.size)
        assertEquals(TaskEventType.CREATED, events.single().eventType)
        assertEquals("写周报", events.single().titleSnapshot)
    }

    @Test
    fun blankNotesAreStoredAsNull() = runBlocking {
        val taskId = repository.createTask("任务", inboxId, notes = "   ")

        assertNull(repository.getTask(taskId)?.notes)
    }

    @Test
    fun anOpenTaskDisappearsFromTheListWhenCompletedAndComesBackWhenReopened() = runBlocking {
        val taskId = repository.createTask("整理桌面", inboxId)

        assertEquals(listOf(taskId), repository.observeOpenTasksInList(inboxId).first().map { it.id })

        assertTrue(repository.completeTask(taskId))
        assertTrue(repository.observeOpenTasksInList(inboxId).first().isEmpty())
        assertEquals(listOf(taskId), repository.observeCompletedTasks().first().map { it.id })

        advanceClock()
        assertTrue(repository.reopenTask(taskId))
        assertEquals(listOf(taskId), repository.observeOpenTasksInList(inboxId).first().map { it.id })
        assertTrue(repository.observeCompletedTasks().first().isEmpty())
    }

    @Test
    fun completingTwiceOrReopeningAnOpenTaskChangesNothing() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)

        // Reopening (or completing) a task in the wrong state is a no-op.
        assertFalse(repository.reopenTask(taskId))

        assertTrue(repository.completeTask(taskId))
        advanceClock()
        assertFalse(repository.completeTask(taskId))
        assertTrue(repository.reopenTask(taskId))
        advanceClock()
        assertFalse(repository.reopenTask(taskId))

        // No-op calls leave no trace in the history either.
        val events = db.taskEventDao().getForTask(taskId).map { it.eventType }
        assertEquals(
            listOf(TaskEventType.CREATED, TaskEventType.COMPLETED, TaskEventType.REOPENED),
            events
        )
    }

    @Test
    fun reopeningKeepsTheEarlierCompletionEvent() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        repository.completeTask(taskId)
        advanceClock()
        repository.reopenTask(taskId)

        val events = db.taskEventDao().getForTask(taskId)

        assertEquals(
            listOf(TaskEventType.CREATED, TaskEventType.COMPLETED, TaskEventType.REOPENED),
            events.map { it.eventType }
        )
        assertNull(repository.getTask(taskId)?.completedAt)
    }

    @Test
    fun editingACompletedTaskDoesNotReopenItAndKeepsTheCompletionTime() = runBlocking {
        val taskId = repository.createTask("写周报", inboxId)
        repository.completeTask(taskId)
        val completedAt = repository.getTask(taskId)?.completedAt
        advanceClock()

        repository.updateTask(taskId, title = "写周报（补充）", notes = null, priority = Priority.IMPORTANT_URGENT, listId = inboxId)

        val task = requireNotNull(repository.getTask(taskId))
        assertEquals(completedAt, task.completedAt)
        assertEquals("写周报（补充）", task.title)
        assertEquals(Priority.IMPORTANT_URGENT, task.priority)
    }

    @Test
    fun observingOneTaskReflectsChangesMadeElsewhere() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        assertNull(repository.observeTask(taskId).first()?.completedAt)

        // Completed from the list screen, not from the detail screen.
        repository.completeTask(taskId)

        // The detail screen reads this same query, so it can never show a stale
        // snapshot of the task.
        val observed = requireNotNull(repository.observeTask(taskId).first())
        assertNotNull(observed.completedAt)
        assertEquals("任务", observed.title)
    }

    @Test
    fun theHistoryKeepsTheTitleSnapshotOfEarlierEvents() = runBlocking {
        val taskId = repository.createTask("旧标题", inboxId)
        advanceClock()
        repository.updateTask(taskId, title = "新标题", notes = null, priority = Priority.NOT_IMPORTANT_NOT_URGENT, listId = inboxId)
        advanceClock()
        repository.completeTask(taskId)

        val events = db.taskEventDao().getForTask(taskId)

        assertEquals(listOf(TaskEventType.CREATED, TaskEventType.COMPLETED), events.map { it.eventType })
        assertEquals("旧标题", events.first().titleSnapshot)
        assertEquals("新标题", events.last().titleSnapshot)
    }

    // ---------------------------------------------------------- subtasks

    @Test
    fun completingAParentDoesNotCompleteItsSubtasks() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val firstSubtask = repository.addSubtask(parentId, "子任务 1")
        val secondSubtask = repository.addSubtask(parentId, "子任务 2")

        assertTrue(repository.completeTask(parentId))

        assertNotNull(repository.getTask(parentId)?.completedAt)
        assertNull(repository.getTask(firstSubtask)?.completedAt)
        assertNull(repository.getTask(secondSubtask)?.completedAt)
        // The unfinished subtasks stay reachable from the parent.
        assertEquals(
            listOf(firstSubtask, secondSubtask),
            repository.observeSubtasks(parentId).first().map { it.id }
        )
    }

    @Test
    fun completingASubtaskDoesNotCompleteItsParent() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")

        assertTrue(repository.completeTask(subtaskId))

        assertNull(repository.getTask(parentId)?.completedAt)
        assertNotNull(repository.getTask(subtaskId)?.completedAt)
    }

    @Test
    fun subtasksOfSubtasksAreRefused() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")

        assertFailsWithIllegalArgument { repository.addSubtask(subtaskId, "二级子任务") }
    }

    @Test
    fun aSubtaskCannotBeCreatedUnderADeletedParent() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        advanceClock()
        repository.deleteTask(parentId)

        assertFailsWithIllegalArgument { repository.addSubtask(parentId, "子任务") }
    }

    @Test
    fun editingASubtaskKeepsItInTheListOfItsParent() = runBlocking {
        val otherListId = repository.createList("工作")
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")

        repository.updateTask(subtaskId, title = "子任务改", notes = null, priority = Priority.NOT_IMPORTANT_NOT_URGENT, listId = otherListId)

        assertEquals(inboxId, repository.getTask(subtaskId)?.listId)
        assertEquals("子任务改", repository.getTask(subtaskId)?.title)
    }

    @Test
    fun subtaskCountsAreReportedPerParent() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        repository.createTask("独立任务", inboxId)
        repository.addSubtask(parentId, "子任务 1")
        repository.addSubtask(parentId, "子任务 2")

        assertEquals(mapOf(parentId to 2), repository.observeSubtaskCounts().first())
    }

    @Test
    fun openTaskCountsPerListIgnoreCompletedDeletedAndSubtaskRows() = runBlocking {
        val workListId = repository.createList("工作")
        val firstId = repository.createTask("任务一", inboxId)
        val secondId = repository.createTask("任务二", inboxId)
        val subtaskId = repository.addSubtask(secondId, "子任务")
        val workId = repository.createTask("工作任务", workListId)

        // The subtask belongs to its parent's detail screen, so it is not counted.
        assertEquals(
            mapOf(inboxId to 2, workListId to 1),
            repository.observeOpenTaskCountsByList().first()
        )

        repository.completeTask(firstId)
        assertEquals(mapOf(inboxId to 1, workListId to 1), repository.observeOpenTaskCountsByList().first())

        repository.deleteTask(workId)
        assertEquals(mapOf(inboxId to 1), repository.observeOpenTaskCountsByList().first())

        repository.reopenTask(firstId)
        assertEquals(mapOf(inboxId to 2), repository.observeOpenTaskCountsByList().first())

        // Deleting the parent takes its subtask with it: the count drops by one,
        // because only the parent was ever counted.
        repository.deleteTask(secondId)
        assertNotNull(repository.getTask(subtaskId)?.deletedAt)
        assertEquals(mapOf(inboxId to 1), repository.observeOpenTaskCountsByList().first())
    }

    // -------------------------------------------------- delete / restore

    @Test
    fun deletingAParentSoftDeletesItsSubtasksAndRecordsEvents() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")
        advanceClock()

        val removedSubtasks = repository.deleteTask(parentId)

        assertEquals(1, removedSubtasks)
        assertNotNull(repository.getTask(parentId)?.deletedAt)
        assertNotNull(repository.getTask(subtaskId)?.deletedAt)

        val parentEvents = db.taskEventDao().getForTask(parentId)
        assertEquals(listOf(TaskEventType.CREATED, TaskEventType.DELETED), parentEvents.map { it.eventType })
        assertEquals("""{"subtaskIds":[$subtaskId]}""", parentEvents.last().detailsJson)

        val subtaskEvents = db.taskEventDao().getForTask(subtaskId)
        assertEquals(listOf(TaskEventType.CREATED, TaskEventType.DELETED), subtaskEvents.map { it.eventType })
    }

    @Test
    fun deletingAlreadyDeletedTaskIsANoOp() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        repository.deleteTask(taskId)
        advanceClock()

        assertEquals(0, repository.deleteTask(taskId))
        assertEquals(2, db.taskEventDao().getForTask(taskId).size)
    }

    @Test
    fun deletedTasksLeaveTheOpenAndCompletedViewsAndAppearInTheTrash() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")
        repository.completeTask(subtaskId)
        advanceClock()

        repository.deleteTask(parentId)

        assertTrue(repository.observeOpenTasksInList(inboxId).first().isEmpty())
        assertTrue(repository.observeCompletedTasks().first().isEmpty())
        assertEquals(
            setOf(parentId, subtaskId),
            repository.observeDeletedTasks().first().map { it.id }.toSet()
        )
    }

    @Test
    fun aDeletedTaskCannotBeCompletedAndKeepsItsHistory() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        advanceClock()
        repository.deleteTask(taskId)

        assertFalse(repository.completeTask(taskId))
        assertNull(repository.getTask(taskId)?.completedAt)
        // Deleting a task must not remove its events.
        assertEquals(
            listOf(TaskEventType.CREATED, TaskEventType.DELETED),
            db.taskEventDao().getForTask(taskId).map { it.eventType }
        )
    }

    @Test
    fun restoringAParentBringsBackTheSubtasksDeletedWithIt() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")
        advanceClock()
        repository.deleteTask(parentId)

        val restored = repository.restoreTask(parentId)

        assertEquals(2, restored)
        assertNull(repository.getTask(parentId)?.deletedAt)
        assertNull(repository.getTask(subtaskId)?.deletedAt)
        assertTrue(repository.observeDeletedTasks().first().isEmpty())
        assertEquals(
            listOf(TaskEventType.CREATED, TaskEventType.DELETED, TaskEventType.RESTORED),
            db.taskEventDao().getForTask(subtaskId).map { it.eventType }
        )
    }

    @Test
    fun restoringASubtaskAlsoRestoresItsDeletedParentButNotItsSiblings() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val firstSubtask = repository.addSubtask(parentId, "子任务 1")
        val secondSubtask = repository.addSubtask(parentId, "子任务 2")
        advanceClock()
        repository.deleteTask(parentId)

        val restored = repository.restoreTask(firstSubtask)

        assertEquals(2, restored)
        assertNull(repository.getTask(parentId)?.deletedAt)
        assertNull(repository.getTask(firstSubtask)?.deletedAt)
        assertNotNull(repository.getTask(secondSubtask)?.deletedAt)
    }

    @Test
    fun restoringAParentDoesNotReviveSubtasksDeletedOnTheirOwn() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val deletedOnItsOwn = repository.addSubtask(parentId, "先被单独删除的子任务")
        val cascaded = repository.addSubtask(parentId, "随父任务删除的子任务")
        advanceClock()
        repository.deleteTask(deletedOnItsOwn)
        advanceClock()

        repository.deleteTask(parentId)
        val restored = repository.restoreTask(parentId)

        // The parent and the subtask that went down with it come back...
        assertEquals(2, restored)
        assertNull(repository.getTask(parentId)?.deletedAt)
        assertNull(repository.getTask(cascaded)?.deletedAt)
        // ...but the one the user deleted on its own stays in the trash.
        assertNotNull(repository.getTask(deletedOnItsOwn)?.deletedAt)
        assertEquals(
            listOf(deletedOnItsOwn),
            repository.observeDeletedTasks().first().map { it.id }
        )
    }

    @Test
    fun eachDeleteOnlyBringsBackTheSubtasksOfThatDelete() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val first = repository.addSubtask(parentId, "子任务 1")
        val second = repository.addSubtask(parentId, "子任务 2")

        advanceClock()
        repository.deleteTask(parentId)
        assertEquals(3, repository.restoreTask(parentId))

        // A later delete round covers only the subtasks still live at that time.
        advanceClock()
        repository.deleteTask(second)
        advanceClock()
        repository.deleteTask(parentId)

        assertEquals(2, repository.restoreTask(parentId))
        assertNull(repository.getTask(first)?.deletedAt)
        assertNotNull(repository.getTask(second)?.deletedAt)
    }

    @Test
    fun restoringATaskThatIsNotDeletedDoesNothing() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)

        assertEquals(0, repository.restoreTask(taskId))
        assertEquals(1, db.taskEventDao().getForTask(taskId).size)
    }

    @Test
    fun restoringADeletedSubtaskAloneDoesNotTouchALivingParent() = runBlocking {
        val parentId = repository.createTask("父任务", inboxId)
        val subtaskId = repository.addSubtask(parentId, "子任务")
        advanceClock()
        repository.deleteTask(subtaskId)

        assertEquals(1, repository.restoreTask(subtaskId))
        assertNull(repository.getTask(subtaskId)?.deletedAt)
        assertNull(repository.getTask(parentId)?.deletedAt)
        assertEquals(listOf(subtaskId), repository.observeSubtasks(parentId).first().map { it.id })
    }

    private fun advanceClock(millis: Long = 1_000L) {
        clock += millis
    }
    // ------------------------------------------------------------- 标签

    @Test
    fun tagsCanBeCreatedRenamedAndDeleted() = runBlocking {
        val tagId = repository.createTag("等回复", 0xFFFF0000.toInt())
        repository.updateTag(tagId, "已回复", 0xFF00FF00.toInt())

        val tag = repository.observeTags().first().single()
        assertEquals("已回复", tag.name)
        assertEquals(0xFF00FF00.toInt(), tag.color)

        repository.deleteTag(tagId)
        assertTrue(repository.observeTags().first().isEmpty())
    }

    @Test
    fun settingTheTagsOfATaskReplacesTheWholeSet() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        val urgent = repository.createTag("紧急", 0xFFFF0000.toInt())
        val home = repository.createTag("家里", 0xFF0000FF.toInt())

        repository.setTaskTags(taskId, listOf(urgent, home))
        assertEquals(setOf(urgent, home), repository.observeTagsOf(taskId).first().map { it.id }.toSet())

        repository.setTaskTags(taskId, listOf(home))
        assertEquals(listOf(home), repository.observeTagsOf(taskId).first().map { it.id })
    }

    @Test
    fun deletingATagClearsItsLinksAndLeavesTheTaskAlone() = runBlocking {
        val taskId = repository.createTask("任务", inboxId)
        val tagId = repository.createTag("临时", 0xFFFF0000.toInt())
        repository.setTaskTags(taskId, listOf(tagId))

        repository.deleteTag(tagId)

        assertTrue(repository.observeTagsOf(taskId).first().isEmpty())
        assertNotNull(repository.getTask(taskId))
    }

    @Test
    fun tagsOfEveryTaskAreGroupedForTheListCards() = runBlocking {
        val first = repository.createTask("任务一", inboxId)
        val second = repository.createTask("任务二", inboxId)
        val tagId = repository.createTag("标签", 0xFFFF0000.toInt())
        repository.setTaskTags(first, listOf(tagId))

        val byTask = repository.observeTagsByTask().first()

        assertEquals(listOf(tagId), byTask[first]?.map { it.id })
        assertNull(byTask[second])
    }

    private fun assertFailsWithIllegalArgument(block: suspend () -> Unit) {
        var caught: Throwable? = null
        runBlocking {
            try {
                block()
            } catch (error: Throwable) {
                caught = error
            }
        }
        assertTrue(
            "期望 IllegalArgumentException，实际是 $caught",
            caught is IllegalArgumentException
        )
    }
}
