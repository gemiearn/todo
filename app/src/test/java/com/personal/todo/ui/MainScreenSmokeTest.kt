package com.personal.todo.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
                MainScreen(
                    repository = tasks,
                    nav = nav,
                    route = route,
                    kind = kind,
                    argument = argument,
                    selectedDate = tasks.today(),
                    onSelectDate = {}
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
