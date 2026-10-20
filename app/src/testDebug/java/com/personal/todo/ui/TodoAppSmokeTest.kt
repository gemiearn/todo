package com.personal.todo.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.HabitRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.ui.theme.TodoPomodoroTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
    private lateinit var habits: HabitRepository

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            focus = FocusRepository(db)
            habits = HabitRepository(db)
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
                TodoApp(repository = tasks, focusRepository = focus, habitRepository = habits)
            }
        }
    }

    /** 逐帧盯切页那一瞬间：看多少帧、每帧多少毫秒。 */
    private companion object {
        const val FramesToWatch = 30
        const val FrameMillis = 16L
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
     * 有记录时统计页把概览和五张卡都画出来：概览、专注详情、专注趋势、
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
        compose.onNodeWithText("专注概览").assertExists()
        compose.onNodeWithText("今日番茄").assertExists()
        compose.onNodeWithText("累计专注").assertExists()
        // 概览卡在 0，后面依次是详情 / 趋势 / 时间线 / 最佳时间 / 年度；趋势及之后的
        // 卡片在视口之外，列表要滚过去才会组合它们。
        compose.onNodeWithTag(FocusStatsTag).performScrollToIndex(2)
        compose.onNodeWithText("专注趋势").assertExists()
        compose.onNodeWithTag(FocusStatsTag).performScrollToIndex(4)
        compose.onNodeWithText("最佳专注时间").assertExists()
        compose.onNodeWithTag(FocusStatsTag).performScrollToIndex(5)
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
     * 逐条专注记录在它自己的那一页，入口在专注页左滑面板里：面板只放一行「专注记录」，
     * 点它才推进记录页（记录行只留开始时刻与结果，不写计时模式）。
     */
    @Test
    fun theRecordListLivesOnItsOwnPageReachedFromTheFocusDrawer() {
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

        // 专注页：拉开左滑面板，里面是一行能跳过去的「专注记录」。抽屉是 ModalDrawerSheet，
        // 坐标点击落不到它的行上（与 ModalBottomSheet 同一个坑），所以走语义动作。
        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("打开清单列表").performClick()
        repeat(20) { compose.waitForIdle() }
        compose.onNodeWithText("专注记录").assertExists()
        compose.onNodeWithText("专注记录").performSemanticsAction(SemanticsActions.OnClick)
        repeat(20) { compose.waitForIdle() }

        // 记录页：那条记录整行都在，写的是「开始–结束 · 结果」，没有计时模式。
        compose.waitUntilNodeCount(hasText("写季度报告"), 1, TimeoutMillis)
        compose.onNodeWithTag(FocusRecordsTag).assertExists()
        compose.onNodeWithText("已完成", substring = true).assertExists()
        compose.onNodeWithText("–", substring = true).assertExists()
        compose.onNodeWithText("番茄钟", substring = true).assertDoesNotExist()
    }

    /** 记录可以删：⋮ → 删除 → 确认之后它就离开历史，页面回到空态。 */
    @Test
    fun aRecordCanBeDeletedFromItsOwnPage() {
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
        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("打开清单列表").performClick()
        repeat(20) { compose.waitForIdle() }
        compose.onNodeWithText("专注记录").performSemanticsAction(SemanticsActions.OnClick)
        repeat(20) { compose.waitForIdle() }
        compose.waitUntilNodeCount(hasText("写季度报告"), 1, TimeoutMillis)

        // 记录行最右那个 ⋮ 是「修改 / 删除」；弹出菜单与对话框里的按钮同样走语义动作。
        compose.onNodeWithContentDescription("更多操作").performClick()
        repeat(20) { compose.waitForIdle() }
        compose.onNodeWithText("删除").performSemanticsAction(SemanticsActions.OnClick)
        repeat(20) { compose.waitForIdle() }

        // 确认框里再点一次「删除」。
        compose.onNodeWithText("删除这条专注记录？").assertExists()
        compose.onNodeWithText("删除").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilNodeCount(hasText("还没有专注记录"), 1, TimeoutMillis)
    }

    /**
     * 任务卡右侧那个 ▶ 要点得动、并且**把任务带进专注页**（用户 2026-10-19 报「任务点击专注
     * 图标不会自动绑定任务」）：到专注页时任务已经选好，不再是「先选一个要专注的任务」。
     */
    @Test
    fun theQuickFocusButtonCarriesTheTaskIntoTheFocusPage() {
        val taskId = runBlocking {
            tasks.createTask("今天的专注对象", tasks.ensureInbox(), plannedDate = tasks.today())
        }

        launchApp()
        compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(quickFocusTag(taskId)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("今天的专注对象").assertExists()
        compose.onNodeWithText("先选一个要专注的任务").assertDoesNotExist()
    }

    /**
     * 任务详情页的「专注小结」要真显示这条任务上的专注（用户 2026-10-19 报「任务里面的专注
     * 统计为空，哪怕绑定的专注已经完成」）。
     */
    @Test
    fun theTaskDetailShowsTheFocusTotalsOfItsSessions() {
        val taskId = runBlocking {
            tasks.createTask("今天的专注对象", tasks.ensureInbox(), plannedDate = tasks.today())
        }
        runBlocking {
            focus.startTimer(
                mode = FocusMode.POMODORO,
                plannedSeconds = 1_500,
                taskId = taskId,
                taskTitleSnapshot = "今天的专注对象",
                listNameSnapshot = "收件箱"
            )
            focus.finishTimer()
        }

        launchApp()
        compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("今天的专注对象").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("专注小结").assertExists()
        compose.onNodeWithText("专注 1 次", substring = true).assertExists()
        compose.onNodeWithText("还没有专注记录").assertDoesNotExist()
    }

    /**
     * 拉开左滑面板之后切页签，面板必须收回去（用户 2026-10-19 反复报的一条）。
     *
     * 壳层是同一条组合，`drawerState` 会跨页留着：用户在某页把它拉出来、切到别的页，
     * 再切回有抽屉的页时就「自己开着」。这里把三条路都钉住：今天→专注、再回今天、
     * 以及绕一圈「今天→四象限→今天」（四象限那页连抽屉都不给），最后再确认抽屉没被弄坏。
     */
    @Test
    fun theDrawerIsClosedAgainAfterSwitchingTabs() {
        launchApp()

        // 今天：拉开面板，它是打开的（里面的「收件箱」看得见）。
        openDrawer()
        compose.onNodeWithText("收件箱").assertIsDisplayed()

        // 切到专注：面板必须收回去（那张面板上是「专注记录」那一行）。
        compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        settleDrawer()
        compose.onNodeWithText("专注记录").assertIsNotDisplayed()

        // 再回今天：同样不能是开着的。
        compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
        settleDrawer()
        compose.onNodeWithText("收件箱").assertIsNotDisplayed()

        // 今天 → 四象限 → 今天：绕一圈（四象限那页连抽屉都不给）也不许把它带回来。
        compose.onNodeWithTag(tabTag(HomeTab.Quadrant)).performClick()
        settleDrawer()
        compose.onNodeWithTag(tabTag(HomeTab.Today)).performClick()
        settleDrawer()
        compose.onNodeWithText("收件箱").assertIsNotDisplayed()

        // 抽屉本身没被弄坏：还能照常拉开。
        openDrawer()
        compose.onNodeWithText("收件箱").assertIsDisplayed()
    }

    /**
     * 切页那一瞬间**不允许出现任何一帧「抽屉还开着」**（用户 2026-10-19 报的「切到专注页
     * 会闪一下」）。
     *
     * 关掉自动推进时钟、逐帧检查：`waitForIdle` 只看「稳没稳」，不推进时钟，所以平时那些
     * 断言只能看到「最终」状态——闪一下正是被这一步吃掉的。
     */
    @Test
    fun noFrameShowsAnOpenDrawerAfterSwitchingTabs() {
        launchApp()
        openDrawer()
        compose.onNodeWithText("收件箱").assertIsDisplayed()

        assertNoDrawerFrameDuring("专注记录") {
            compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        }
    }

    /**
     * 从推入页返回、再切页，同样不许闪出开着的面板。
     *
     * 这一路是上一版的漏网之鱼：抽屉里点「习惯」会 `push` 一页，MainScreen 整个离开组合，
     * 那一句 `scope.launch { close() }` 被取消——抽屉就停在半开、甚至整个开着回来。
     */
    @Test
    fun noFrameShowsAnOpenDrawerAfterARoundTripThroughAPushedPage() {
        launchApp()
        openDrawer()
        compose.onNodeWithText("习惯").performSemanticsAction(SemanticsActions.OnClick)
        repeat(20) { compose.waitForIdle() }
        compose.onNodeWithText("还没有习惯", substring = true).assertExists()

        assertNoDrawerFrameDuring("收件箱") {
            compose.onNodeWithContentDescription("返回").performClick()
        }
        assertNoDrawerFrameDuring("专注记录") {
            compose.onNodeWithTag(tabTag(HomeTab.Focus)).performClick()
        }
    }

    /** [action] 之后的每一帧都不许看得见 [drawerRowText]（那是面板里的一行）。 */
    private fun assertNoDrawerFrameDuring(drawerRowText: String, action: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            action()
            repeat(FramesToWatch) { frame ->
                compose.mainClock.advanceTimeBy(FrameMillis)
                compose.waitForIdle()
                assertFalse(
                    "第 $frame 帧里看到了开着的面板（$drawerRowText）",
                    runCatching { compose.onNodeWithText(drawerRowText).assertIsDisplayed() }.isSuccess
                )
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    /** 点顶栏那个菜单按钮把抽屉拉出来，并等它滑到位。 */
    private fun openDrawer() {
        compose.onNodeWithContentDescription("打开清单列表")
            .performSemanticsAction(SemanticsActions.OnClick)
        settleDrawer()
    }

    /**
     * 让抽屉滑入 / 滑出的动画真正走完。
     *
     * ⚠️ 只反复 `waitForIdle()` **不推进组合时钟**——抽屉是 `animateTo` 出来的，停在半路
     * 既不是「显示」也不是「不显示」，断言会假失败/假通过。
     */
    private fun settleDrawer() {
        repeat(10) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(500)
        }
        compose.waitForIdle()
    }

    /**
     * 四象限是「决定优先级」的地方，不是「完成任务」的地方：任务照旧画得出来，但页面上
     * 一个能打勾的控件都没有。完成要回列表或详情页去做。
     */
    @Test
    fun theQuadrantPageHasNoCheckboxToTick() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.Quadrant)).performClick()
        compose.waitForIdle()

        // 先确认四象限这一页真的在屏幕上，再看任务有没有画出来：列表是懒的，第一条
        // 不一定第一帧就在（之前写死 assertExists 会偶发地什么都没找到）。
        compose.onNodeWithText("重要且紧急").assertExists()
        compose.waitUntilNodeCount(hasText("写季度报告"), 1, TimeoutMillis)
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    /**
     * 习惯页的入口在任务列表顶栏那个「清单 / 习惯」开关上（用户 2026-10-18 把入口从
     * 抽屉和更多页搬到了这里），点「习惯」那一格就切过去。
     *
     * 页面自己怎么打卡、怎么新建，都在 HabitScreenSmokeTest 里钉；这里只管「入口通不通」。
     * 收尾时点「清单」退回去：习惯页的查询订阅随之下线，别把活着的订阅留给下一条测试
     * （实测会把后面那条四象限的冒烟断言带崩）。
     */
    @Test
    fun theHabitPageOpensFromTheListsSwitch() {
        launchApp()

        compose.onNodeWithTag("$ListsHabitsSwitchTag-habits").performClick()
        compose.waitForIdle()

        // 一个习惯都还没有时，页面直接说实话；右上角就是新建入口。
        compose.onNodeWithText("还没有习惯", substring = true).assertExists()
        compose.onNodeWithTag(HabitNewTag).assertExists()

        // 同一枚开关点「清单」就回到任务列表：那一页的顶栏上又是这个开关。
        compose.onNodeWithTag("$ListsHabitsSwitchTag-lists").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("$ListsHabitsSwitchTag-habits").assertExists()
    }
}
