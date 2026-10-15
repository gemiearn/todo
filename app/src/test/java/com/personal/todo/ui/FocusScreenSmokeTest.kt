package com.personal.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TaskList
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.FocusMode
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.viewmodel.FocusViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Smoke tests that really compose the focus screen on the JVM.
 *
 * A crash can live purely in the composition - a screen that no logic test ever
 * composes cannot fail there. These tests open the screen in each of its states:
 * idle, running, and every timer mode, and they drive the one rule the screen has
 * about starting: a session always runs on a task the user picked.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FocusScreenSmokeTest {

    private val TimeoutMillis = 5_000L

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TodoDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var focus: FocusRepository
    private lateinit var lists: List<TaskList>

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            focus = FocusRepository(db)
            tasks.createTask("写季度报告", tasks.ensureInbox())
            val work = tasks.createList("工作")
            tasks.createTask("整理周报", work)
            lists = tasks.observeLists().first()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** The view model lives outside the composition, as the app creates it. */
    private fun launchFocus(viewModel: FocusViewModel = FocusViewModel(focus, tasks)) {
        compose.setContent {
            TodoPomodoroTheme {
                FocusScreen(viewModel = viewModel, lists = lists, onBack = {})
            }
        }
    }

    /** Picking a task from the picker; it is the only way a session can start. */
    private fun pickTask(title: String) {
        compose.onNodeWithTag(FocusTaskRowTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(title).performClick()
        compose.waitForIdle()
    }

    @Test
    fun theIdleScreenComposes() {
        launchFocus()

        compose.onNodeWithText("开始专注").assertExists()
    }

    @Test
    fun everyTimerModeComposes() {
        launchFocus()

        listOf(FocusMode.STOPWATCH, FocusMode.COUNTDOWN, FocusMode.POMODORO).forEach { mode ->
            compose.onNodeWithTag(focusModeTag(mode)).performClick()
            compose.waitForIdle()
        }

        compose.onNodeWithText("开始专注").assertExists()
    }

    /** 计时必须绑任务: the screen asks for one before anything can start. */
    @Test
    fun aSessionAsksForATaskFirst() {
        launchFocus()

        compose.onNodeWithText("先选一个要专注的任务").assertExists()

        pickTask("写季度报告")

        compose.onNodeWithText("先选一个要专注的任务").assertDoesNotExist()
        compose.onNodeWithText("写季度报告").assertExists()
    }

    /** The picker groups the tasks by list and offers no unattached way out. */
    @Test
    fun thePickerGroupsByListAndOffersNoUnattachedSession() {
        launchFocus()

        compose.onNodeWithTag(FocusTaskRowTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("未关联任务").assertDoesNotExist()
        compose.onNodeWithText("收件箱").assertExists()
        compose.onNodeWithText("工作").assertExists()
        compose.onNodeWithText("整理周报").assertExists()
    }

    /**
     * 倒计时：四个快捷档之外还有一条 5–300 分钟（最多 5 小时）的滑条，圆环两旁的 ±
     * 钮按 5 分钟一档改时长，改过的值真的进了这次计时。
     */
    @Test
    fun theCountdownLengthCanBeSetByHand() {
        launchFocus()
        compose.onNodeWithTag(focusModeTag(FocusMode.COUNTDOWN)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(FocusMinutesSliderTag).assertExists()
        compose.onNodeWithText("15:00").assertExists()

        compose.onNodeWithTag(FocusPlusTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("20:00").assertExists()

        compose.onNodeWithTag(FocusMinusTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("15:00").assertExists()

        compose.onNodeWithTag(FocusPlusTag).performClick()
        compose.waitForIdle()

        pickTask("写季度报告")
        compose.onNodeWithTag(FocusStartTag).performClick()
        compose.waitUntilNodeCount(hasText("专注中"), 1, TimeoutMillis)

        assertEquals(1_200, runBlocking { focus.observeActiveTimer().first()?.plannedSeconds })
    }

    /** 番茄钟的 25 分钟是规格里的默认值，滑条因此只在倒计时出现。 */
    @Test
    fun onlyTheCountdownHasALengthSlider() {
        launchFocus()

        compose.onNodeWithTag(FocusMinutesSliderTag).assertDoesNotExist()
        // 圆环里是番茄钟的长度，设置状态也看得见。
        compose.onNodeWithText("25:00").assertExists()

        compose.onNodeWithTag(focusModeTag(FocusMode.COUNTDOWN)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(FocusMinutesSliderTag).assertExists()
    }

    @Test
    fun theRunningTimerComposesAndCanBeStoppedEarly() {
        launchFocus()
        pickTask("写季度报告")

        compose.onNodeWithText("开始专注").performClick()
        // Starting is asynchronous (it writes the session), so the running panel
        // appears a moment later - wait for it instead of for an idle frame.
        compose.waitUntilNodeCount(hasText("专注中"), 1, TimeoutMillis)

        compose.onNodeWithText("提前结束").performClick()
        compose.waitUntilNodeCount(hasText("开始专注"), 1, TimeoutMillis)
        compose.onNodeWithText("中断", substring = true).assertExists()
    }

    @Test
    fun aFinishedSessionShowsUpInTheHistory() {
        val viewModel = FocusViewModel(focus, tasks)
        runBlocking {
            focus.startTimer(FocusMode.POMODORO, 1_500)
            focus.abandonTimer()
        }
        launchFocus(viewModel)

        compose.onNodeWithText("已完成", substring = true).assertDoesNotExist()
        compose.onNodeWithText("中断", substring = true).assertExists()
        // The history is grouped by the day it happened.
        compose.onNodeWithText("今天").assertExists()
    }
}
