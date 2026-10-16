package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.model.FocusTiming
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The focus timer against a real (in memory) Room database, with both clocks
 * injected: the rules of spec section 5 are checked here, including what happens
 * to a session that survived the process that started it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusRepositoryTest {

    private lateinit var db: TodoDatabase
    private lateinit var focus: FocusRepository

    private var wallClock = 1_000_000L
    private var monotonic = 10_000L
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        focus = FocusRepository(db, now = { wallClock }, elapsed = { monotonic }, zone = shanghai)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Moves both clocks by [seconds]; the wall clock normally moves with it. */
    private fun advance(seconds: Int, wallSeconds: Int = seconds) {
        monotonic += seconds * 1_000L
        wallClock += wallSeconds * 1_000L
    }

    private suspend fun activeTimer() = focus.observeActiveTimer().first()

    private suspend fun onlySession() = requireNotNull(db.focusDao().getSessions().singleOrNull())

    // ------------------------------------------------------------- 基本规则

    @Test
    fun aSecondSessionIsRefusedWhileOneIsAlreadyRunning() = runBlocking {
        assertTrue(focus.startTimer(FocusMode.POMODORO, 1_500))
        advance(60)

        assertFalse("已经在计时时不能再开第二个", focus.startTimer(FocusMode.STOPWATCH, 0))

        val timer = requireNotNull(activeTimer())
        assertEquals("原来那个计时器不能被替换掉", FocusMode.POMODORO, timer.mode)
    }

    @Test
    fun aTimedSessionIsRecordedAsCompletedWhenItReachesItsTarget() = runBlocking {
        focus.startTimer(FocusMode.POMODORO, 1_500)
        advance(1_500)

        assertTrue(
            FocusTiming.reachedTarget(
                focus.stateOf(requireNotNull(activeTimer())),
                monotonic
            )
        )
        assertTrue(focus.finishTimer())

        val session = onlySession()
        assertEquals(FocusStatus.COMPLETED, session.status)
        assertEquals(1_500, session.actualFocusSeconds)
        assertEquals(1_500, session.plannedSeconds)
        assertNull("结束后活动计时器必须被清掉", activeTimer())
    }

    @Test
    fun pausingStopsTheClockAndThePausedSpanIsNeverCounted() = runBlocking {
        focus.startTimer(FocusMode.STOPWATCH, 0)
        advance(600)

        assertTrue(focus.pauseTimer())
        advance(3_600)
        assertTrue(focus.resumeTimer())
        advance(300)
        assertTrue(focus.finishTimer())

        assertEquals("暂停的一小时不算专注", 900, onlySession().actualFocusSeconds)
    }

    @Test
    fun stoppingEarlyIsRecordedAsInterruptedWithItsRealSeconds() = runBlocking {
        focus.startTimer(FocusMode.POMODORO, 1_500)
        advance(240)

        assertTrue(focus.abandonTimer())

        val session = onlySession()
        assertEquals(FocusStatus.INTERRUPTED, session.status)
        assertEquals(240, session.actualFocusSeconds)
        assertEquals("目标时长仍然记着，方便区分没做满的番茄", 1_500, session.plannedSeconds)
    }

    @Test
    fun aStopwatchHasNoTargetAndCanRunForHours() = runBlocking {
        assertTrue(focus.startTimer(FocusMode.STOPWATCH, 25 * 60))
        assertEquals("正向计时没有目标时长", 0, requireNotNull(activeTimer()).plannedSeconds)

        advance(3 * 3_600)
        assertTrue(focus.finishTimer())

        val session = onlySession()
        assertEquals(FocusStatus.COMPLETED, session.status)
        assertEquals(10_800, session.actualFocusSeconds)
    }

    @Test
    fun endingASessionThatIsNotThereChangesNothing() = runBlocking {
        assertFalse(focus.finishTimer())
        assertFalse(focus.abandonTimer())
        assertTrue(db.focusDao().getSessions().isEmpty())
    }

    // ------------------------------------------------------- 进程中途消失

    @Test
    fun aRecoveredTimerComesBackPausedAndDropsTheUnverifiedSpan() = runBlocking {
        focus.startTimer(FocusMode.POMODORO, 1_500)
        advance(600)

        assertTrue("计时器是上一个进程留下的，需要恢复一次", focus.recoverTimer())

        val recovered = requireNotNull(activeTimer())
        assertFalse("恢复后先暂停，由用户决定何时继续", recovered.running)
        assertEquals("进程消失期间的时间不作数", 0, recovered.accumulatedFocusSeconds)

        assertFalse("同一个进程里只恢复一次", focus.recoverTimer())
        assertTrue(focus.resumeTimer())
        advance(300)
        assertTrue(focus.finishTimer())

        assertEquals(300, onlySession().actualFocusSeconds)
    }

    @Test
    fun theCheckpointTakenOnLeavingTheForegroundSurvivesAProcessDeath() = runBlocking {
        focus.startTimer(FocusMode.POMODORO, 1_500)
        advance(600)

        assertTrue(focus.checkpointTimer())
        val banked = requireNotNull(activeTimer())
        assertEquals(600, banked.accumulatedFocusSeconds)
        assertTrue("落盘之后仍在计时", banked.running)

        assertTrue(focus.recoverTimer())
        val recovered = requireNotNull(activeTimer())
        assertFalse(recovered.running)
        assertEquals(600, recovered.accumulatedFocusSeconds)

        assertTrue(focus.finishTimer())
        assertEquals("已经验证过的专注时间不能丢", 600, onlySession().actualFocusSeconds)
    }

    @Test
    fun aRebootedMonotonicClockDoesNotInventFocusTime() = runBlocking {
        focus.startTimer(FocusMode.STOPWATCH, 0)
        advance(600)
        focus.checkpointTimer()

        // The device reboots: the monotonic clock starts over from a small value.
        monotonic = 500L

        assertTrue(focus.finishTimer())
        assertEquals(
            "重启后那段无法验证的时间不能被算成专注",
            600,
            onlySession().actualFocusSeconds
        )
    }

    // ------------------------------------------------------------- 任务关联

    @Test
    fun deletingATaskKeepsItsFocusHistory() = runBlocking {
        val tasks = TaskRepository(db, "收件箱", now = { wallClock }, zone = shanghai)
        val taskId = tasks.createTask("写报告", tasks.ensureInbox())

        focus.startTimer(
            mode = FocusMode.POMODORO,
            plannedSeconds = 1_500,
            taskId = taskId,
            taskTitleSnapshot = "写报告"
        )
        advance(1_500)
        assertTrue(focus.finishTimer())

        tasks.deleteTask(taskId)

        val session = onlySession()
        assertEquals("软删除任务不会动专注记录", taskId, session.taskId)
        assertEquals("快照让历史仍然可读", "写报告", session.taskTitleSnapshot)
    }

    /** 开始时任务身上的标签随 session 一起存下来，统计页才筛得动。 */
    @Test
    fun theTagSnapshotOfTheLinkedTaskTravelsWithTheSession() = runBlocking {
        val tasks = TaskRepository(db, "收件箱", now = { wallClock }, zone = shanghai)
        val taskId = tasks.createTask("写报告", tasks.ensureInbox())

        focus.startTimer(
            mode = FocusMode.POMODORO,
            plannedSeconds = 1_500,
            taskId = taskId,
            taskTitleSnapshot = "写报告",
            listNameSnapshot = "工作",
            tagNamesSnapshot = FocusTagSnapshot.encode(listOf("等回复", "重要"))
        )
        advance(1_500)
        assertTrue(focus.finishTimer())

        val session = onlySession()
        assertEquals(listOf("等回复", "重要"), FocusTagSnapshot.names(session.tagNamesSnapshot))
        assertTrue(FocusTagSnapshot.matches(session.tagNamesSnapshot, "等回复"))
    }
}
