package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.RepeatKind
import com.personal.todo.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
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
 * 重复任务的生成器：规则只留在带规则那条任务上，算出来的每一次都落成**独立任务**
 * （说明文档 4.5），所以每次完成、改期、删除都只影响那一次。
 *
 * 补生成的窗口是「今天 ~ 今天 + 14 天」，过去的日期不回溯。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurrenceGenerationTest {

    private lateinit var db: TodoDatabase
    private lateinit var repository: TaskRepository
    private var clock = 1_000L
    private var inboxId = 0L

    /** 2026-10-15，星期五。 */
    private val today = LocalDate.of(2026, 10, 15)

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

    private suspend fun dailyTask(
        title: String = "每天跑步",
        plannedDate: LocalDate? = today,
        dueDate: LocalDate? = null,
        plannedTime: LocalTime? = LocalTime.of(7, 0),
        rule: RepeatRule = RepeatRule(RepeatKind.DAILY)
    ): Long = repository.createTask(
        title = title,
        listId = inboxId,
        plannedDate = plannedDate,
        plannedTime = plannedTime,
        dueDate = dueDate,
        repeatRule = rule
    )

    @Test
    fun everyComingDayGetsItsOwnTask() = runBlocking {
        val sourceId = dailyTask()
        assertEquals("源自己占今天那一格", 14, repository.materializeRecurrences(today))

        val occurrences = db.taskDao().getOccurrencesOf(sourceId)
        assertEquals(14, occurrences.size)
        assertTrue("只往后生成", occurrences.all { (it.plannedDate ?: today).isAfter(today) })
        assertTrue("实例自己没有规则", occurrences.all { it.repeatRule == null })
        assertEquals("生成它的那条任务记在 sourceId 上", sourceId, occurrences.first().recurrenceSourceId)
        assertEquals("标题、时刻都一起带过来", "每天跑步", occurrences.first().title)
        assertEquals(LocalTime.of(7, 0), occurrences.first().plannedTime)
        assertNull("实例不是子任务", occurrences.first().parentTaskId)
    }

    @Test
    fun runningItAgainChangesNothing() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today)
        assertEquals("幂等：再跑一次一条都不多", 0, repository.materializeRecurrences(today))
        assertEquals(14, db.taskDao().getOccurrencesOf(sourceId).size)
    }

    @Test
    fun aPastAnchorIsNotBackfilled() = runBlocking {
        val sourceId = dailyTask(plannedDate = today.minusDays(3))
        assertEquals("前面那三天不补", 15, repository.materializeRecurrences(today))
        val dates = db.taskDao().getOccurrencesOf(sourceId).mapNotNull { it.plannedDate }
        assertTrue(dates.all { !it.isBefore(today) })
    }

    @Test
    fun aDoneOrTrashedOccurrenceIsNeverBroughtBack() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today)
        val occurrences = db.taskDao().getOccurrencesOf(sourceId)
        repository.completeTask(occurrences.first().id)
        db.taskDao().markDeleted(occurrences[1].id, clock)

        assertEquals(0, repository.materializeRecurrences(today))
        assertEquals("已经做过和已经删掉的那两次都还在原来的状态", 14, db.taskDao().getOccurrencesOf(sourceId).size)
    }

    @Test
    fun theSpanOfTheSourceIsCarriedOver() = runBlocking {
        // 「每周一 9:00-11:00」这种：源自己跨一天，实例也各自跨一天
        val sourceId = dailyTask(dueDate = today.plusDays(1))
        repository.materializeRecurrences(today)

        val first = db.taskDao().getOccurrencesOf(sourceId).minByOrNull { it.plannedDate ?: today }
        assertEquals(today.plusDays(1), first?.plannedDate)
        assertEquals(today.plusDays(2), first?.dueDate)
    }

    @Test
    fun weekendsOnlyGenerateSaturdayAndSunday() = runBlocking {
        val sourceId = dailyTask(rule = RepeatRule(RepeatKind.WEEKENDS))
        repository.materializeRecurrences(today)

        val dates = db.taskDao().getOccurrencesOf(sourceId).mapNotNull { it.plannedDate }
        assertEquals("两周里正好四个周末日", 4, dates.size)
        assertTrue("只落在周六周日", dates.all { it.dayOfWeek.value >= 6 })
    }

    @Test
    fun aSourceInTheTrashStopsProducing() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today)
        db.taskDao().markDeleted(sourceId, clock)

        assertEquals("源进了回收站就不再产生新的那几次", 0, repository.materializeRecurrences(today))
    }
}
