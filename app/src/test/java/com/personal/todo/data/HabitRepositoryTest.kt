package com.personal.todo.data

import androidx.room.Room
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
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
 * 习惯与打卡规则，跑在真的（内存）Room 上。
 *
 * 这里钉住的是「一天一次数」这件事：加一次、减一次、到目标就停、减到 0 就把记录删掉，
 * 以及每天各算各的。日期一律用本地日期，所以换时区不会把打卡挪到隔壁那天。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitRepositoryTest {

    private lateinit var db: TodoDatabase
    private lateinit var habits: HabitRepository
    private val wallClock = 1_000_000_000L
    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        habits = HabitRepository(db, now = { wallClock }, zone = shanghai)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val today: LocalDate get() = Schedule.todayOf(wallClock, shanghai)

    @Test
    fun aHabitStartsAtZeroAndFillsUpToItsTarget() = runBlocking {
        val habitId = habits.createHabit("喝水", 5)

        assertEquals(0, count(habitId, today))
        repeat(5) { habits.increment(habitId, today) }
        assertEquals(5, count(habitId, today))

        habits.increment(habitId, today)
        assertEquals("到目标就不再往上加", 5, count(habitId, today))
    }

    @Test
    fun decrementTakesOneBackAndZeroRemovesTheRow() = runBlocking {
        val habitId = habits.createHabit("喝水", 5)
        repeat(3) { habits.increment(habitId, today) }

        habits.decrement(habitId, today)
        assertEquals(2, count(habitId, today))

        habits.decrement(habitId, today)
        habits.decrement(habitId, today)
        assertEquals(0, count(habitId, today))
        assertNull("没记录＝没打卡，不留 0 值行", db.habitDao().getCheckIn(habitId, today))

        habits.decrement(habitId, today)
        assertEquals("减过头也不会变成负数", 0, count(habitId, today))
    }

    @Test
    fun eachDayKeepsItsOwnCount() = runBlocking {
        val habitId = habits.createHabit("喝水", 5)
        val yesterday = today.minusDays(1)

        habits.increment(habitId, today)
        habits.increment(habitId, yesterday)
        habits.increment(habitId, yesterday)

        assertEquals(1, count(habitId, today))
        assertEquals("昨天那天自己数自己的", 2, count(habitId, yesterday))
    }

    @Test
    fun theWindowOnlyReturnsTheDaysItAsksFor() = runBlocking {
        val habitId = habits.createHabit("喝水", 1)
        habits.increment(habitId, today)
        habits.increment(habitId, today.minusDays(9))

        val rows = habits.observeCheckIns(today.minusDays(6), today).first()
        assertEquals("窗口外的记录不该露出来", 1, rows.size)
        assertEquals(today, rows.single().date)
    }

    @Test
    fun deletingAHabitTakesItsCheckInsWithIt() = runBlocking {
        val habitId = habits.createHabit("喝水", 3)
        habits.increment(habitId, today)

        habits.deleteHabit(habitId)

        assertTrue(db.habitDao().observeHabits().first().isEmpty())
        assertTrue("打卡记录跟着习惯一起清掉", habits.observeCheckIns(today.minusDays(6), today).first().isEmpty())
    }

    @Test
    fun habitsKeepTheirOrderAndTheTargetIsAtLeastOne() = runBlocking {
        val first = habits.createHabit("喝水", 5)
        val second = habits.createHabit("读书", 0)

        val list = db.habitDao().observeHabits().first()
        assertEquals("按建的时候的顺序排", listOf(first, second), list.map { it.id })
        assertEquals("次数至少 1", 1, list.last().dailyTarget)
    }

    @Test
    fun aHabitKeepsItsIconAndClearingItStoresNull() = runBlocking {
        val habitId = habits.createHabit("喝水", 5, icon = "💧")
        assertEquals("💧", db.habitDao().getById(habitId)?.icon)

        habits.updateHabit(habitId, "喝水", 5, icon = "")
        assertNull("选「无」就是把这一列清掉", db.habitDao().getById(habitId)?.icon)

        // 改名 / 改目标量时把图标一起带上，图标不会被顺手抹掉。
        habits.updateHabit(habitId, "喝水", 8, "杯", 2, "💧")
        val habit = requireNotNull(db.habitDao().getById(habitId))
        assertEquals("💧", habit.icon)
        assertEquals(8, habit.dailyTarget)
        assertEquals("杯", habit.unitLabel)
        assertEquals(2, habit.stepAmount)
    }

    /**
     * 关联任务自动打卡（v14，用户口径「只要计时了就打卡」）：只有**关联到这条任务**并且
     * **开关是开的**那些习惯才记，次数与手点一下同一个口径（夹在目标以内）。
     */
    @Test
    fun focusingTheLinkedTaskChecksInOnlyTheHabitsWithTheSwitchOn() = runBlocking {
        val taskId = 7L
        val on = habits.createHabit("写周报", 3, linkedTaskId = taskId, autoCheckInOnFocus = true)
        val off = habits.createHabit("喝水", 3, linkedTaskId = taskId)
        val other = habits.createHabit("读书", 3, linkedTaskId = 9L, autoCheckInOnFocus = true)

        habits.autoCheckInForTask(taskId, today)
        assertEquals(1, count(on, today))
        assertEquals("开关没开的习惯不动", 0, count(off, today))
        assertEquals("关联的是别的任务，不动", 0, count(other, today))

        // 起三次计时就记三次——但到目标为止，和手点一样。
        repeat(5) { habits.autoCheckInForTask(taskId, today) }
        assertEquals(3, count(on, today))
    }

    @Test
    fun turningTheSwitchOffOrClearingTheLinkStopsTheAutoCheckIn() = runBlocking {
        val habitId = habits.createHabit("写周报", 3, linkedTaskId = 7L, autoCheckInOnFocus = true)

        habits.updateHabit(habitId, "写周报", 3, linkedTaskId = 7L, autoCheckInOnFocus = false)
        habits.autoCheckInForTask(7L, today)
        assertEquals("开关关了就不再自动打卡", 0, count(habitId, today))

        habits.updateHabit(habitId, "写周报", 3, linkedTaskId = null, autoCheckInOnFocus = true)
        assertNull("没有关联任务，链接就是空的", db.habitDao().getById(habitId)?.linkedTaskId)
        assertEquals(
            "链接没了，「开关」也不会留着一个开着的值",
            false,
            db.habitDao().getById(habitId)?.autoCheckInOnFocus
        )
        habits.autoCheckInForTask(7L, today)
        assertEquals(0, count(habitId, today))
    }

    private suspend fun count(habitId: Long, date: LocalDate): Int =
        db.habitDao().getCheckIn(habitId, date)?.count ?: 0

    /**
     * 「今天」跟着钟走：习惯页一直开着跨过午夜时，最右那一格与打卡落点必须自己翻到新的一天，
     * 而不是停在「这一份建立的那一天」。tick 间隔调成 1 毫秒，跨天只等一次 tick。
     */
    @Test
    fun todayFollowsAMidnightRollover() = runBlocking {
        var clock = millisOf(LocalDateTime.of(2026, 10, 14, 23, 59))
        val ticking = HabitRepository(db, now = { clock }, zone = shanghai, todayRefreshMillis = 1)

        val emitted = mutableListOf<LocalDate>()
        val collector = launch { ticking.observeToday().collect { emitted += it } }
        try {
            withTimeout(5_000) { while (emitted.isEmpty()) delay(5) }
            assertEquals(LocalDate.of(2026, 10, 14), emitted.last())

            // 跨过午夜：不重启进程、不重建流。
            clock = millisOf(LocalDateTime.of(2026, 10, 15, 0, 0, 30))
            withTimeout(5_000) { while (emitted.last() != LocalDate.of(2026, 10, 15)) delay(5) }
            assertEquals(LocalDate.of(2026, 10, 15), emitted.last())
        } finally {
            collector.cancel()
        }
    }

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(shanghai).toInstant().toEpochMilli()
}