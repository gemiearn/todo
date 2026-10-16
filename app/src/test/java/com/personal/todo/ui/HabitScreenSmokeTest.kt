package com.personal.todo.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.room.Room
import com.personal.todo.data.HabitRepository
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.viewmodel.HabitViewModel
import java.time.ZoneId
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
 * 习惯页本身：一行习惯 + 最近 7 天的格子。
 *
 * 页面只画「今天做了没」，所以这里钉住两件事：进度文案跟着次数走，以及「点一下 +1」真的
 * 落到了那一天——日期一律按本地日期算，跨天的加减都记在各自那一天上。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HabitScreenSmokeTest {

    private val TimeoutMillis = 5_000L

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TodoDatabase
    private lateinit var habits: HabitRepository

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            habits = HabitRepository(db, zone = ZoneId.systemDefault())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun launchHabits(viewModel: HabitViewModel) {
        compose.setContent {
            TodoPomodoroTheme {
                HabitScreen(viewModel = viewModel, onBack = {})
            }
        }
    }

    @Test
    fun aHabitRowChecksInAndOutOnTheDayItIsPressed() {
        val habitId = runBlocking { habits.createHabit("喝水", 5) }
        val viewModel = HabitViewModel(habits)
        val yesterday = viewModel.today.minusDays(1)
        launchHabits(viewModel)

        compose.waitUntilNodeCount(hasText("今天 0/5"), 1, TimeoutMillis)
        compose.onNodeWithTag(habitRowTag(habitId)).performClick()
        compose.waitUntilNodeCount(hasText("今天 1/5"), 1, TimeoutMillis)

        // 昨天那一格点两下：数字只长在昨天那一格上，今天还是 1。
        compose.onNodeWithTag(habitDayCellTag(habitId, yesterday)).performClick()
        compose.onNodeWithTag(habitDayCellTag(habitId, yesterday)).performClick()
        compose.waitUntilNodeCount(hasText("今天 1/5"), 1, TimeoutMillis)
        assertEquals(1, runBlocking { db.habitDao().getCheckIn(habitId, viewModel.today)?.count })
        assertEquals(2, runBlocking { db.habitDao().getCheckIn(habitId, yesterday)?.count })
    }

    @Test
    fun anEmptyHabitListSaysSo() {
        launchHabits(HabitViewModel(habits))

        compose.onNodeWithText("还没有习惯", substring = true).assertExists()
    }

    @Test
    fun theNewHabitDialogComposes() {
        launchHabits(HabitViewModel(habits))

        compose.onNodeWithTag(HabitNewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新建习惯").assertExists()
        compose.onNodeWithText("每天次数").assertExists()
    }

    @Test
    fun aHabitCanBeCreatedFromTheSheetWithItsTarget() {
        val viewModel = HabitViewModel(habits)
        launchHabits(viewModel)

        compose.onNodeWithTag(HabitNewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(HabitNameFieldTag).performTextInput("喝水")
        compose.waitForIdle()
        // 名字确实落进了弹层状态（不然保存按钮是禁用的，点了也不会动）。
        compose.onNodeWithText("喝水").assertExists()

        // 弹层里的控件要用语义点击：这套 Robolectric 环境里，坐标点击落不到弹层上。
        compose.onNodeWithText("+").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("+").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("3").assertExists()

        compose.onNodeWithText("保存").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.waitUntilNodeCount(hasText("今天 0/3"), 1, TimeoutMillis)
    }
}
