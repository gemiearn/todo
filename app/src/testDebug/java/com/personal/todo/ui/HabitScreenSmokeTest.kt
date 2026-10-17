package com.personal.todo.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TodoDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            habits = HabitRepository(db, zone = ZoneId.systemDefault())
            store = ViewModelStore()
        }
    }

    /** 和真机上一样从 [ViewModelStore] 里取：这样 [tearDown] 才有办法清掉它的作用域。 */
    private fun habitViewModel(): HabitViewModel =
        ViewModelProvider(store, HabitViewModel.factory(habits))[HabitViewModel::class.java]

    @After
    fun tearDown() {
        // 先 store.clear()（取消 view model 作用域，房间的查询订阅随之结束），再关库：
        // 页面这会儿还订阅着 HabitViewModel 里的查询，直接关库会让房间把异常抛进那个作用域，
        // 被 kotlinx-coroutines-test 算成未捕获异常，冒到**别的测试类**里去（实测会连带让
        // 四象限的冒烟断言失败）。走 ViewModelStore 是为了拿回「清掉作用域」这个正规入口。
        store.clear()
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
        val viewModel = habitViewModel()
        val yesterday = viewModel.today.minusDays(1)
        launchHabits(viewModel)

        compose.waitUntilNodeCount(hasText("今天 0/5 次"), 1, TimeoutMillis)
        compose.onNodeWithTag(habitRowTag(habitId)).performClick()
        compose.waitUntilNodeCount(hasText("今天 1/5 次"), 1, TimeoutMillis)

        // 昨天那一格点两下：数字只长在昨天那一格上，今天还是 1。
        compose.onNodeWithTag(habitDayCellTag(habitId, yesterday)).performClick()
        compose.onNodeWithTag(habitDayCellTag(habitId, yesterday)).performClick()
        compose.waitUntilNodeCount(hasText("今天 1/5 次"), 1, TimeoutMillis)
        assertEquals(1, runBlocking { db.habitDao().getCheckIn(habitId, viewModel.today)?.count })
        assertEquals(2, runBlocking { db.habitDao().getCheckIn(habitId, yesterday)?.count })
    }

    @Test
    fun anEmptyHabitListSaysSo() {
        launchHabits(habitViewModel())

        compose.onNodeWithText("还没有习惯", substring = true).assertExists()
    }

    @Test
    fun theNewHabitDialogComposes() {
        launchHabits(habitViewModel())

        compose.onNodeWithTag(HabitNewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新建习惯").assertExists()
        compose.onNodeWithText("每天目标").assertExists()
        compose.onNodeWithText("每次增加").assertExists()
        compose.onNodeWithTag(HabitUnitFieldTag).assertExists()
    }

    @Test
    fun aHabitCanBeCreatedFromTheSheetWithItsTarget() {
        val viewModel = habitViewModel()
        launchHabits(viewModel)

        compose.onNodeWithTag(HabitNewTag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(HabitNameFieldTag).performTextInput("喝水")
        compose.waitForIdle()
        // 名字确实落进了弹层状态（不然保存按钮是禁用的，点了也不会动）。
        compose.onNodeWithText("喝水").assertExists()

        // 把「每天目标」从默认的 1 改成 5（输入框可以直接敲；按钮才要用语义点击）。
        compose.onNodeWithTag(HabitTargetFieldTag).performTextClearance()
        compose.onNodeWithTag(HabitTargetFieldTag).performTextInput("5")
        compose.waitForIdle()
        compose.onNodeWithText("保存").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.waitUntilNodeCount(hasText("今天 0/5 次"), 1, TimeoutMillis)
    }
}
