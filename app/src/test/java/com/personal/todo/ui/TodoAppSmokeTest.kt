package com.personal.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.ui.theme.TodoPomodoroTheme
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
 * Switches the bottom bar inside one composition, which is the thing that used
 * to crash the app on the phone:
 *
 * ```
 * java.lang.ClassCastException: androidx.lifecycle.LifecycleRegistry cannot be
 * cast to androidx.compose.runtime.saveable.SaveableHolder
 *     at ...RememberSaveableKt.rememberSaveable
 *     at ...TodoAppKt.MainScreen
 * ```
 *
 * The shell must therefore keep the same composition call sequence for every
 * route; a @Composable call that is skipped on one route and taken on another
 * shifts the slot table and the next `rememberSaveable` reads the wrong slot.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TodoAppSmokeTest {

    private val TimeoutMillis = 5_000L

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TodoDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var focus: FocusRepository

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            focus = FocusRepository(db)
            tasks.createTask("写季度报告", tasks.ensureInbox(), plannedDate = tasks.today())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun launchApp() {
        compose.setContent {
            TodoPomodoroTheme {
                TodoApp(repository = tasks, focusRepository = focus)
            }
        }
    }

    /**
     * 今天 is the `else` branch of the shell's route switch and 更多 has a branch
     * of its own: going back and forth between them is exactly the transition
     * that broke the slot table.
     */
    @Test
    fun switchingBetweenTheTabsKeepsTheShellStable() {
        launchApp()

        val order = listOf(
            HomeTab.More,
            HomeTab.Today,
            HomeTab.More,
            HomeTab.Quadrant,
            HomeTab.Today,
            HomeTab.Calendar,
            HomeTab.More,
            HomeTab.Today
        )
        order.forEach { tab ->
            compose.onNodeWithTag(tabTag(tab)).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * The month grid used to jump five years ahead on every visit to the tab: the
     * pager was scrolled to a page that had been counted twice, and the settle then
     * dragged the selected day along with it. Opening the tab again has to land on
     * the selected month, with that day's task under the grid.
     */
    @Test
    fun theCalendarComesBackToTheSelectedMonth() {
        launchApp()
        val yearLabel = "${tasks.today().year} 年"

        repeat(2) {
            compose.onNodeWithTag(tabTag(HomeTab.Calendar)).performClick()
            compose.waitForIdle()

            compose.onNodeWithText(yearLabel, substring = true).assertExists()
            compose.onNodeWithText("写季度报告").assertExists()

            compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * 专注 is a root of the bottom bar, not a pushed page. Reached either way it keeps
     * the tab strip, so there is always a way out - pushed, its back arrow had nothing
     * to pop when it was the stack root, and the tab strip disappeared entirely.
     */
    @Test
    fun theFocusPageKeepsTheTabStripWhicheverWayItIsOpened() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("开始专注").assertExists()
        compose.onNodeWithText("先选一个要专注的任务").assertExists()
        compose.onNodeWithTag(tabTag(HomeTab.Today)).assertExists()

        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注（番茄钟）").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("开始专注").assertExists()
        compose.onNodeWithTag(tabTag(HomeTab.Today)).assertExists()
    }

    /** 统计 is its own page, pushed with a back arrow, reached from 更多 or from 专注. */
    @Test
    fun theStatisticsPageOpensFromTheMoreTab() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注统计").performClick()
        compose.waitForIdle()

        // 还没有专注记录时，它老老实实说没有。
        compose.onNodeWithText("还没有专注记录", substring = true).assertExists()
    }

    /**
     * 有记录时统计页把五张卡都画出来，顺序照参考图来：专注详情、专注趋势、
     * 专注时间线、最佳专注时间、年度热力图。
     */
    @Test
    fun theStatisticsPageDrawsAllItsCardsOnceThereIsData() {
        runBlocking {
            val taskId = tasks.createTask("写季度报告", tasks.ensureInbox(), plannedDate = tasks.today())
            focus.startTimer(
                mode = FocusMode.POMODORO,
                plannedSeconds = 1_500,
                taskId = taskId,
                taskTitleSnapshot = "写季度报告",
                listNameSnapshot = "收件箱"
            )
            focus.finishTimer()
        }

        launchApp()
        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注统计").performClick()
        compose.waitForIdle()

        // sessions 是 Room 的 Flow，进页面之后才第一次发值：等那张卡真的出现。
        compose.waitUntilNodeCount(hasText("专注详情"), 1, TimeoutMillis)
        compose.onNodeWithText("专注趋势").assertExists()
        // 后两张在视口之外，列表要滚过去才会组合它们。
        compose.onNodeWithTag(FocusStatsTag).performScrollToIndex(3)
        compose.onNodeWithText("最佳专注时间").assertExists()
        compose.onNodeWithTag(FocusStatsTag).performScrollToIndex(4)
        compose.onNodeWithText("年度热力图").assertExists()
    }

    @Test
    fun openingTheMoreTabRepeatedlyKeepsTheShellStable() {
        launchApp()

        repeat(3) {
            compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
            compose.waitForIdle()
        }
    }

    /**
     * The editor used to open only once: the route reused its view model from the
     * view model store, still holding the "saved" flag, so the second visit popped
     * itself on the first frame - the screen only flashed on the phone.
     */
    @Test
    fun theEditorStaysOpenWhenItIsOpenedASecondTime() {
        launchApp()

        compose.onNodeWithContentDescription("新建任务").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTitleTag).performTextInput("买牛奶")
        compose.onNodeWithText("保存").performClick()
        // Back on the list again: the editor has no new-task button of its own.
        compose.waitUntilNodeCount(hasContentDescription("新建任务"), 1, TimeoutMillis)

        compose.onNodeWithContentDescription("新建任务").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("保存").assertExists()
    }

    /** 统计入口在专注页右上角的图标里，点它就进统计页。 */
    @Test
    fun theStatsEntrySitsInTheFocusTopBar() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(FocusStatsEntryTag).assertExists()
        compose.onNodeWithTag(FocusStatsEntryTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(FocusStatsTag).assertExists()
    }

    /** 设置页是推入页：从更多进去，主题三选都在。 */
    @Test
    fun theSettingsPageOpensFromTheMoreTab() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SettingsScreenTag).assertExists()
        compose.onNodeWithText("主题").assertExists()
        compose.onNodeWithText("跟随系统").assertExists()
        compose.onNodeWithText("浅色").assertExists()
        compose.onNodeWithText("深色").assertExists()
        compose.onNodeWithText("色系").assertExists()
        compose.onNodeWithText("马卡龙").assertExists()
        compose.onNodeWithText("莫兰迪").assertExists()
    }

    /** 专注详情的第四个分段「自定义」会亮出起止日两个选择器。 */
    @Test
    fun theStatsDetailCardOffersACustomRange() {
        runBlocking {
            val taskId = tasks.createTask("写季度报告", tasks.ensureInbox(), plannedDate = tasks.today())
            focus.startTimer(
                mode = FocusMode.POMODORO,
                plannedSeconds = 1_500,
                taskId = taskId,
                taskTitleSnapshot = "写季度报告",
                listNameSnapshot = "收件箱"
            )
            focus.finishTimer()
        }

        launchApp()
        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注统计").performClick()
        compose.waitForIdle()

        compose.waitUntilNodeCount(hasText("专注详情"), 1, TimeoutMillis)
        compose.onNodeWithText("自定义").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("起始日期").assertExists()
        compose.onNodeWithText("结束日期").assertExists()
    }

    /**
     * 统计页顶上多了一行「按标签筛」：选一个标签，整页只算那一段记录。选了没有记录的
     * 标签时，页面会明说「这个标签下还没有专注记录」，而不是装作从没专注过。
     */
    @Test
    fun theStatsPageCanBeFilteredByTag() {
        runBlocking {
            val taskId = tasks.createTask("写季度报告", tasks.ensureInbox(), plannedDate = tasks.today())
            val workTag = tasks.createTag("工作", 0xFF3D7BF7.toInt())
            tasks.createTag("个人", 0xFF3DBE7B.toInt())
            tasks.setTaskTags(taskId, listOf(workTag))
            focus.startTimer(
                mode = FocusMode.POMODORO,
                plannedSeconds = 1_500,
                taskId = taskId,
                taskTitleSnapshot = "写季度报告",
                listNameSnapshot = "收件箱",
                tagNamesSnapshot = FocusTagSnapshot.encode(listOf("工作"))
            )
            focus.finishTimer()
        }

        launchApp()
        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注统计").performClick()
        compose.waitForIdle()

        compose.waitUntilNodeCount(hasText("专注详情"), 1, TimeoutMillis)
        compose.onNodeWithTag(FocusStatsTagFilterTag).assertExists()

        // 选一个没有记录的标签：整页被筛空，只剩那句实话。
        compose.onNodeWithText("个人").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("这个标签下还没有专注记录").assertExists()

        // 回到「全部」：详情卡又回来了。
        compose.onNodeWithText("全部").performClick()
        compose.waitForIdle()
        compose.waitUntilNodeCount(hasText("专注详情"), 1, TimeoutMillis)
    }

    /**
     * 逐条专注记录只在统计页底部出现：专注页不再自己堆历史；记录行也不再写计时模式
     * （正计时 / 倒计时 / 番茄钟），只留开始时刻与结果。
     */
    @Test
    fun theRecordListLivesOnTheStatsPageWithoutTheTimerMode() {
        runBlocking {
            val taskId = tasks.createTask("写季度报告", tasks.ensureInbox(), plannedDate = tasks.today())
            focus.startTimer(
                mode = FocusMode.POMODORO,
                plannedSeconds = 1_500,
                taskId = taskId,
                taskTitleSnapshot = "写季度报告",
                listNameSnapshot = "收件箱"
            )
            focus.finishTimer()
        }

        launchApp()

        // 专注页上没有那一段记录。
        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注记录").assertDoesNotExist()

        // 记录在统计页底部，滚过去看它。
        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注统计").performClick()
        compose.waitForIdle()
        compose.waitUntilNodeCount(hasText("专注详情"), 1, TimeoutMillis)

        compose.onNodeWithTag(FocusStatsTag).performScrollToNode(hasText("专注记录"))
        compose.onNodeWithText("专注记录").assertExists()
        compose.onNodeWithTag(FocusStatsTag).performScrollToNode(hasText("写季度报告"))
        compose.onNodeWithText("写季度报告").assertExists()
        compose.onNodeWithText("已完成", substring = true).assertExists()
        compose.onNodeWithText("番茄钟", substring = true).assertDoesNotExist()
    }
}
