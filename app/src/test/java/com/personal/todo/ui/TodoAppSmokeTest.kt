package com.personal.todo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.TodoDatabase
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
     * 专注（番茄钟） is reached from 更多. This path had no test at all, and the entry
     * used to be called plain 专注, which is a name nobody starts a timer from.
     */
    @Test
    fun theFocusPageOpensFromTheMoreTab() {
        launchApp()

        compose.onNodeWithTag(tabTag(HomeTab.More)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("专注（番茄钟）").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("开始专注").assertExists()
        compose.onNodeWithText("先选一个要专注的任务").assertExists()
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
}
