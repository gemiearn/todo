package com.personal.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.viewmodel.FocusViewModel
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
 * Smoke tests that really compose the focus screen on the JVM.
 *
 * A crash can live purely in the composition - a screen that no logic test ever
 * composes cannot fail there. These tests open the screen in each of its states:
 * idle, running, and every timer mode.
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

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            tasks = TaskRepository(db, "收件箱")
            focus = FocusRepository(db)
            tasks.createTask("写季度报告", tasks.ensureInbox())
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
                FocusScreen(viewModel = viewModel, onBack = {})
            }
        }
    }

    @Test
    fun theIdleScreenComposes() {
        launchFocus()

        compose.onNodeWithText("开始专注").assertExists()
    }

    @Test
    fun everyTimerModeComposes() {
        launchFocus()

        listOf("正向计时", "倒计时", "番茄钟").forEach { mode ->
            compose.onNodeWithText(mode).performClick()
            compose.waitForIdle()
        }

        compose.onNodeWithText("开始专注").assertExists()
    }

    @Test
    fun theRunningTimerComposesAndCanBeStoppedEarly() {
        launchFocus()

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
            focus.startTimer(com.personal.todo.model.FocusMode.POMODORO, 1_500)
            focus.abandonTimer()
        }
        launchFocus(viewModel)

        compose.onNodeWithText("已完成", substring = true).assertDoesNotExist()
        compose.onNodeWithText("中断", substring = true).assertExists()
    }
}
