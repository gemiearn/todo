package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.RepeatKind
import com.personal.todo.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * 重复任务的生成器：规则只留在带规则那条任务上，算出来的每一次都落成**独立任务**
 * （说明文档 4.5），所以每次完成、改期、删除都只影响那一次。
 *
 * 除了最后那条验默认窗口的测试，其余都显式传一个两周的小窗口，断言才好读；生产上默认
 * 铺满一年，好让「每个工作日」一直延续下去。
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

    /** 测试里用的那段窗口：今天起两周。 */
    private val window = 14

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
        assertEquals("源自己占今天那一格", 14, repository.materializeRecurrences(today, window))

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
        repository.materializeRecurrences(today, window)
        assertEquals("幂等：再跑一次一条都不多", 0, repository.materializeRecurrences(today, window))
        assertEquals(14, db.taskDao().getOccurrencesOf(sourceId).size)
    }

    /**
     * 实例是一张独立的任务行，标签存在 task_tags 里，所以生成时必须把源任务的标签连线一起
     * 复制过去；否则按标签筛「今天 / 未来 / 日历」时这些实例根本不会出现，专注开始的标签
     * 快照也会是空的。
     */
    @Test
    fun generatedOccurrencesInheritTheSourceTags() = runBlocking {
        val morning = repository.createTag("晨间", 0xFF00FF00.toInt())
        val sourceId = dailyTask()
        repository.setTaskTags(sourceId, listOf(morning))

        repository.materializeRecurrences(today, window)

        val occurrences = db.taskDao().getOccurrencesOf(sourceId)
        assertTrue("确实生成了实例", occurrences.isNotEmpty())
        occurrences.forEach { occurrence ->
            assertEquals(
                "每条实例都跟着源任务带上同一个标签",
                listOf(morning),
                repository.observeTagsOf(occurrence.id).first().map { it.id }
            )
        }
    }

    @Test
    fun aPastAnchorIsNotBackfilled() = runBlocking {
        val sourceId = dailyTask(plannedDate = today.minusDays(3))
        assertEquals("前面那三天不补", 15, repository.materializeRecurrences(today, window))
        val dates = db.taskDao().getOccurrencesOf(sourceId).mapNotNull { it.plannedDate }
        assertTrue(dates.all { !it.isBefore(today) })
    }

    @Test
    fun aDoneOrTrashedOccurrenceIsNeverBroughtBack() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrences = db.taskDao().getOccurrencesOf(sourceId)
        repository.completeTask(occurrences.first().id)
        db.taskDao().markDeleted(occurrences[1].id, clock)

        assertEquals(0, repository.materializeRecurrences(today, window))
        assertEquals("已经做过和已经删掉的那两次都还在原来的状态", 14, db.taskDao().getOccurrencesOf(sourceId).size)
    }

    @Test
    fun theSpanOfTheSourceIsCarriedOver() = runBlocking {
        // 「每周一 9:00-11:00」这种：源自己跨一天，实例也各自跨一天
        val sourceId = dailyTask(dueDate = today.plusDays(1))
        repository.materializeRecurrences(today, window)

        val first = db.taskDao().getOccurrencesOf(sourceId).minByOrNull { it.plannedDate ?: today }
        assertEquals(today.plusDays(1), first?.plannedDate)
        assertEquals(today.plusDays(2), first?.dueDate)
    }

    @Test
    fun weekendsOnlyGenerateSaturdayAndSunday() = runBlocking {
        val sourceId = dailyTask(rule = RepeatRule(RepeatKind.WEEKENDS))
        repository.materializeRecurrences(today, window)

        val dates = db.taskDao().getOccurrencesOf(sourceId).mapNotNull { it.plannedDate }
        assertEquals("两周里正好四个周末日", 4, dates.size)
        assertTrue("只落在周六周日", dates.all { it.dayOfWeek.value >= 6 })
    }

    @Test
    fun aSourceInTheTrashStopsProducing() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        db.taskDao().markDeleted(sourceId, clock)

        assertEquals("源进了回收站就不再产生新的那几次", 0, repository.materializeRecurrences(today, window))
    }

    /**
     * 默认窗口是**一年**：「每个工作日一直延续」要的就是这个，而不是两周就断掉。
     * 生成器每次都从「今天」起算，所以冷启动重跑时这个窗口会随日期自然往前滑。
     */
    @Test
    fun theDefaultWindowKeepsEveryDayComingForAYear() = runBlocking {
        val sourceId = dailyTask()
        assertEquals(365, repository.materializeRecurrences(today))

        val dates = db.taskDao().getOccurrencesOf(sourceId).mapNotNull { it.plannedDate }
        assertEquals("一年里每天一次（源自己占第一天）", 365, dates.size)
        assertEquals(today.plusDays(365), dates.max())
    }

    // ------------------------------------------------ 删整条系列（2026-10-25）

    /**
     * 「删除整条系列」要把规则源和它生成过的每一次一起收进回收站，而且**从此不再长出来**：
     * 只删当前这一次的话，第二天生成器又把下一次补上，用户会觉得根本删不掉。
     */
    @Test
    fun deletingTheSeriesTakesTheSourceAndEveryOccurrence() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val before = db.taskDao().getSeriesRows(sourceId).size
        assertEquals("源 + 14 次", 15, before)

        val removed = repository.deleteSeries(sourceId)

        assertEquals(15, removed)
        assertTrue(
            "整串一行都不剩（在打开态里）",
            db.taskDao().getSeriesRows(sourceId).all { it.deletedAt != null }
        )
        assertEquals("再跑生成器也补不出来", 0, repository.materializeRecurrences(today.plusDays(1), window))
    }

    /** 从**某一实例**发起整串删除，也一样把源一起带走：那才是「以后都不再重复了」。 */
    @Test
    fun deletingTheSeriesFromOneOccurrenceAlsoTakesTheSource() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrence = db.taskDao().getOccurrencesOf(sourceId).first()

        repository.deleteSeries(occurrence.id)

        assertNotNull("源也被删了", repository.getTask(sourceId)?.deletedAt)
        assertTrue(
            "整串一行都不剩（在打开态里）",
            db.taskDao().getSeriesRows(sourceId).all { it.deletedAt != null }
        )
    }

    /**
     * 撤销整串删除：随那次一起进回收站的那些行原样回来，而**先前单独删掉**的那一次不动——
     * 撤销的是刚才那一下，不是回收站里的全部。
     */
    @Test
    fun restoringTheSeriesBringsBackExactlyWhatThatDeleteTook() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrences = db.taskDao().getOccurrencesOf(sourceId)
        val deletedOnItsOwn = occurrences.first().id
        repository.deleteTask(deletedOnItsOwn)
        advanceClock()

        repository.deleteSeries(sourceId)
        val restored = repository.restoreSeries(sourceId)

        assertEquals("源 + 其余 13 次回来，单独删的那次不回来", 14, restored)
        assertNull(repository.getTask(sourceId)?.deletedAt)
        assertNotNull("单独删的那次仍在回收站里", repository.getTask(deletedOnItsOwn)?.deletedAt)
        assertNull(repository.getTask(occurrences[1].id)?.deletedAt)
    }

    /** 撤销之后系列照旧会继续生成：源回来了，规则也还在。 */
    @Test
    fun aRestoredSeriesKeepsGenerating() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        repository.deleteSeries(sourceId)
        repository.restoreSeries(sourceId)

        val created = repository.materializeRecurrences(today.plusDays(20), window)

        assertTrue("源回来了，后面那几天会接着生成", created > 0)
    }

    /**
     * 回收站里点「恢复」一条整串删除的实例，回来的也是整串：用户的心里那件事还在重复，
     * 只恢复一条的话明天照样是孤零零的一条。
     */
    @Test
    fun restoringOneOccurrenceOfASeriesDeleteRestoresTheWholeSeries() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrence = db.taskDao().getOccurrencesOf(sourceId).first()
        repository.deleteSeries(sourceId)

        val restored = repository.restoreTask(occurrence.id)

        assertEquals("回收站里点一条就恢复整串", 15, restored)
        assertNull(repository.getTask(sourceId)?.deletedAt)
        assertNull(repository.getTask(occurrence.id)?.deletedAt)
    }

    /** 只删这一次（普通删除）不该碰后面的每一次。 */
    @Test
    fun deletingOneOccurrenceLeavesTheRestOfTheSeriesAlone() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrence = db.taskDao().getOccurrencesOf(sourceId).first()

        repository.deleteTask(occurrence.id)

        assertNull("源还好好的", repository.getTask(sourceId)?.deletedAt)
        assertEquals(
            "其余实例都还在",
            14,
            db.taskDao().getSeriesRows(sourceId).count { it.deletedAt == null }
        )
    }

    /** 源已经被删掉的那串不再算「还在重复」：详情页据此不给「删除整条系列」。 */
    @Test
    fun aSeriesWhoseSourceIsGoneIsNoLongerLive() = runBlocking {
        val sourceId = dailyTask()
        repository.materializeRecurrences(today, window)
        val occurrence = db.taskDao().getOccurrencesOf(sourceId).first()

        assertTrue(repository.isInLiveSeries(occurrence.id))
        repository.deleteTask(sourceId)
        assertTrue("源没了，残留的实例不再属于一串还在重复的任务", !repository.isInLiveSeries(occurrence.id))
    }

    private fun advanceClock(millis: Long = 1_000L) {
        clock += millis
    }
}
