package com.personal.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.ui.theme.TodoPomodoroTheme
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Composes the app shell itself, route by route.
 *
 * The shell is where the tabs meet the screens, and it is the part no logic test
 * can reach: every root route is composed once here, so a screen that crashes on
 * the way in fails in this test with its stack trace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MainScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TodoDatabase
    private lateinit var tasks: TaskRepository
    private var listId = 0L

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            FocusRepository(db)
            val inbox = tasks.ensureInbox()
            tasks.createTask("写季度报告", inbox, plannedDate = tasks.today())
            listId = tasks.createList("工作")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun launch(route: String, kind: String, argument: Long? = null) {
        val nav = NavStack(mutableStateListOf(route))
        compose.setContent {
            TodoPomodoroTheme {
                // The selected day is live, the way the shell keeps it: a view that
                // silently ignores the day it is given hides whole classes of bug.
                var selectedDate by remember { mutableStateOf(tasks.today()) }
                MainScreen(
                    repository = tasks,
                    nav = nav,
                    route = route,
                    kind = kind,
                    argument = argument,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it }
                )
            }
        }
    }

    /**
     * A screen only has to render; the same word usually appears in both the top
     * bar and the drawer, so presence is what matters here, not uniqueness.
     */
    private fun assertSomewhereOnScreen(text: String) {
        compose.onAllNodesWithText(text, substring = true).onFirst().assertExists()
    }

    @Test
    fun theTodayTabComposes() {
        launch(HomeTab.Today.route, HomeTab.Today.route)

        assertSomewhereOnScreen("今天")
    }

    @Test
    fun theCalendarTabComposes() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        // The calendar page replaces the top bar with the month and year header.
        assertSomewhereOnScreen("年")
    }

    /**
     * 年 / 月 / 周 / 三日 are four ways to lay out the same rows. Each one has to
     * compose, and the switch has to come back to the month it started on.
     */
    @Test
    fun theCalendarSwitchesThroughEveryView() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(calendarBodyTag(CalendarView.MONTH)).assertExists()

        listOf(
            CalendarView.WEEK,
            CalendarView.THREE_DAY,
            CalendarView.YEAR,
            CalendarView.MONTH
        ).forEach { view ->
            compose.onNodeWithTag(CalendarViewSwitchTag).performClick()
            compose.onNodeWithTag(calendarViewOptionTag(view)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(calendarBodyTag(view)).assertExists()
        }
    }

    /**
     * 三日 is anchored on the window it was opened with: picking the last day of it
     * must select that day, not slide the three days out from under the finger.
     */
    @Test
    fun theThreeDayWindowStaysPutWhenADayIsPicked() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(CalendarViewSwitchTag).performClick()
        compose.onNodeWithTag(calendarViewOptionTag(CalendarView.THREE_DAY)).performClick()
        compose.waitForIdle()

        val today = tasks.today()
        compose.onNodeWithTag(calendarDayColumnTag(today)).assertExists()

        compose.onNodeWithTag(calendarDayColumnTag(today.plusDays(2))).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(calendarDayColumnTag(today)).assertExists()
        compose.onNodeWithTag(calendarDayColumnTag(today.plusDays(1))).assertExists()
    }

    /** The 时间线 toggle swaps the day panel of the month view for the time axis. */
    @Test
    fun theTimelineToggleSwitchesTheDayPanel() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(DayTimelineTag).assertDoesNotExist()

        compose.onNodeWithTag(CalendarTimelineTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(DayTimelineTag).assertExists()
        // The task of setUp has no planned time, so it sits in the 全天 block.
        assertSomewhereOnScreen("全天")

        compose.onNodeWithTag(CalendarTimelineTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(DayTimelineTag).assertDoesNotExist()
    }

    /** An empty day reads the way the reference screenshot has it. */
    @Test
    fun theEmptyTimelineSaysThereIsNothingToDo() {
        compose.setContent {
            TodoPomodoroTheme {
                DayTimelineScreen(
                    tasks = emptyList(),
                    subtaskCounts = emptyMap(),
                    zone = ZoneId.systemDefault(),
                    onOpenTask = {},
                    onComplete = {},
                    onReopen = {},
                    onDelete = {}
                )
            }
        }

        assertSomewhereOnScreen("你这一天没有任务")
        assertSomewhereOnScreen("放松一下吧")
    }

    @Test
    fun theQuadrantTabComposes() {
        launch(HomeTab.Quadrant.route, HomeTab.Quadrant.route)

        assertSomewhereOnScreen("四象限")
    }

    /** The tab that crashed on the phone: 更多. */
    @Test
    fun theMoreTabComposes() {
        launch(HomeTab.More.route, HomeTab.More.route)

        assertSomewhereOnScreen("更多")
        assertSomewhereOnScreen("崩溃日志")
    }

    @Test
    fun aUserListComposes() {
        launch("list/$listId", "list", listId)

        assertSomewhereOnScreen("工作")
    }

    @Test
    fun theTrashComposes() {
        launch("trash", "trash")

        assertSomewhereOnScreen("回收站")
    }

    @Test
    fun theCompletedViewComposes() {
        launch("completed", "completed")

        assertSomewhereOnScreen("已完成")
    }

    @Test
    fun theFutureViewComposes() {
        launch("future", "future")

        assertSomewhereOnScreen("未来")
    }

    @Test
    fun theOverdueViewComposes() {
        launch("overdue", "overdue")

        assertSomewhereOnScreen("逾期")
    }
}
