package com.personal.todo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.personal.todo.data.ColorStyle
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.FocusSettingsStore
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.viewmodel.FocusViewModel
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var focus: FocusRepository
    private lateinit var focusSettings: FocusSettingsStore
    private var listId = 0L

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            focus = FocusRepository(db)
            focusSettings = FocusSettingsStore(RuntimeEnvironment.getApplication())
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
                    focusViewModel = FocusViewModel(focus, tasks, focusSettings),
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

    /**
     * 跨期任务要在计划日到截止日之间的每一天都有小圆点，而不是只有截止那天有。
     * 这是用户在 0.2.0 验收过的行为，日历页重写之后必须仍然成立。
     */
    @Test
    fun aSpanningTaskDotsEveryDayBetweenItsEnds() {
        val today = tasks.today()
        val month = YearMonth.from(today)
        // Kept inside the month the grid opens on, so the whole span is on screen.
        val planned = maxOf(today.minusDays(3), month.atDay(1))
        val due = minOf(today.plusDays(3), month.atEndOfMonth())
        val spanId = runBlocking {
            tasks.createTask("跨期任务", tasks.ensureInbox(), plannedDate = planned, dueDate = due)
        }

        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        var day = planned
        while (!day.isAfter(due)) {
            compose.onNodeWithTag(dayDotTag(day, spanId), useUnmergedTree = true).assertExists()
            day = day.plusDays(1)
        }
        compose.onNodeWithTag(dayDotTag(planned.minusDays(1), spanId), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    /**
     * 跨月的区间不能在本月页面被「截断」：上个月末尾的那几个格子（月历第一行的
     * 前置日期）也要带点，否则一条 10/28–11/2 的待办看起来就像只在尾部出现过。
     */
    @Test
    fun aSpanAcrossTheMonthEdgeKeepsItsDots() {
        val month = YearMonth.from(tasks.today())
        val firstOfMonth = month.atDay(1)
        val firstCell = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
        val due = firstOfMonth.plusDays(2)
        val spanId = runBlocking {
            tasks.createTask("跨月任务", tasks.ensureInbox(), plannedDate = firstCell, dueDate = due)
        }

        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(dayDotTag(firstCell, spanId), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(dayDotTag(firstOfMonth, spanId), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(dayDotTag(due, spanId), useUnmergedTree = true).assertExists()
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

    /**
     * A task that runs from one date to another is on every day between them, so its
     * card names both ends - a card that only named the due date hid the start, and
     * the task looked like it began on its deadline.
     */
    @Test
    fun aTaskCardNamesBothEndsOfItsRange() {
        val today = tasks.today()
        val spanning = Task(
            id = 1L,
            title = "跨期任务",
            listId = 1L,
            createdAt = 0L,
            updatedAt = 0L,
            plannedDate = today.minusDays(9),
            dueDate = today.plusDays(9)
        )

        compose.setContent {
            TodoPomodoroTheme {
                TaskCard(
                    task = spanning,
                    subtaskCount = 0,
                    checked = false,
                    onCheckedChange = {},
                    onClick = {},
                    nowMillis = System.currentTimeMillis(),
                    zone = ZoneId.systemDefault()
                )
            }
        }

        compose.onNodeWithText("跨期任务").assertExists()
        // The dash only shows up when the card names two dates.
        compose.onNodeWithText("–", substring = true).assertExists()
    }

    /** The tab that was added for the pomodoro: it renders without the shell's search chrome. */
    @Test
    fun theFocusTabComposes() {
        launch(HomeTab.Focus.route, HomeTab.Focus.route)

        compose.onNodeWithText("开始专注").assertExists()
        compose.onNodeWithText("先选一个要专注的任务").assertExists()
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

    /**
     * 换一套色系之后四象限页照样画得出来：卡片底是那一套象限色的淡版，角标文字按底色
     * 亮度自动换成深色（马卡龙是浅色，白字会糊在上面）。
     */
    @Test
    fun theQuadrantPageComposesUnderTheMacaronColorStyle() {
        compose.setContent {
            TodoPomodoroTheme(colorStyle = ColorStyle.MACARON) {
                QuadrantScreen(
                    openTasks = flowOf(emptyList<Task>()),
                    completedTasks = flowOf(emptyList<Task>()),
                    onOpenTask = {}
                )
            }
        }

        assertSomewhereOnScreen("重要且紧急")
        assertSomewhereOnScreen("不重要不紧急")
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

    /** 列表顶栏的筛选按钮：点开「显示范围」面板，三个维度都在。 */
    @Test
    fun theRangeFilterPanelOpensFromTheListTopBar() {
        launch("inbox", "inbox")

        compose.onNodeWithContentDescription("筛选").performClick()
        compose.waitForIdle()

        assertSomewhereOnScreen("显示范围")
        assertSomewhereOnScreen("清单")
        assertSomewhereOnScreen("优先级")
        assertSomewhereOnScreen("标签")
    }

    /** 日历 ⋮ 面板：同一个筛选面板下面再接一段「显示设置」。 */
    @Test
    fun theCalendarDisplayOptionsOpenFromTheMenu() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(CalendarDisplayOptionsTag).performClick()
        compose.waitForIdle()

        assertSomewhereOnScreen("显示范围")
        assertSomewhereOnScreen("显示设置")
        assertSomewhereOnScreen("显示已完成")
    }

    /** 周视图有一对前后箭头，点一下会挪到下一周。 */
    @Test
    fun theWeekViewHasStepArrows() {
        launch(HomeTab.Calendar.route, HomeTab.Calendar.route)

        compose.onNodeWithTag(CalendarViewSwitchTag).performClick()
        compose.onNodeWithTag(calendarViewOptionTag(CalendarView.WEEK)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(CalendarStepPreviousTag).assertExists()
        compose.onNodeWithTag(CalendarStepNextTag).performClick()
        compose.waitForIdle()
        // 挪到下一周之后，周视图仍然在，只是标题换成了另一周。
        compose.onNodeWithTag(calendarBodyTag(CalendarView.WEEK)).assertExists()
    }
}
