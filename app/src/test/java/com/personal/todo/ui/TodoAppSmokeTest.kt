package com.personal.todo.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TodoAppSmokeTest {

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
}
