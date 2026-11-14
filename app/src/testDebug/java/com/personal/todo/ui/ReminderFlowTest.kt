package com.personal.todo.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.TaskRepository
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.ReminderKind
import com.personal.todo.notification.ReminderNotifications
import com.personal.todo.notification.ReminderScheduler
import com.personal.todo.notification.ReminderDiagnostics
import com.personal.todo.ui.theme.TodoPomodoroTheme
import com.personal.todo.viewmodel.TaskEditorViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 门禁回归：实际点击「今天 → 时间 12:45 → 准时」，不绕过日期弹层设置提醒。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1100dp")
class ReminderFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TodoDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var scheduler: ReminderScheduler
    private lateinit var editor: TaskEditorViewModel
    private val viewModels = ViewModelStore()
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2030, 11, 14)
    private var clock by mutableLongStateOf(day.atTime(12, 43).atZone(zone).toInstant().toEpochMilli())

    @Before
    fun setUp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Explicitly reproduce ordinary alarms being delayed by Android.
        ReminderSettingsStore(context).writeExactAlarm(false)
        db = Room.inMemoryDatabaseBuilder(context, TodoDatabase::class.java)
            .allowMainThreadQueries().build()
        scheduler = ReminderScheduler(context, database = { db }, now = { clock }, bootCount = { 1 })
        tasks = TaskRepository(db, "收件箱", now = { clock }, reminders = scheduler)
        editor = TaskEditorViewModel(tasks, null, tasks.ensureInbox())
        viewModels.put("editor", editor)
        ReminderNotifications.ensureChannel(context)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        db.close()
    }

    @Test
    fun todayTimeAndOnTimeReminderAreSavedAndNotifyTwoMinutesLater() {
        var savedTask by mutableStateOf<Task?>(null)
        compose.setContent {
            TodoPomodoroTheme {
                val task = savedTask
                if (task == null) {
                    NewTaskSheet(editor, onSaved = {
                        savedTask = runBlocking { db.taskDao().getPendingReminders().single() }
                    }, onDismiss = {})
                } else {
                    TaskCard(task, 0, false, {}, {}, clock, zone)
                }
            }
        }
        compose.onNodeWithTag(EditorTitleTag).performTextInput("两分钟后提醒")
        // 「一页填写」：快捷日期、时间、提醒都在同一页，且都是就地改完就生效。
        compose.onNodeWithText("今天").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilNodeCount(hasText("时间"), 1, 15_000L)
        compose.onNodeWithText("时间").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("输入时分").performSemanticsAction(SemanticsActions.OnClick)
        val timeFields = compose.onAllNodes(hasSetTextAction() and
            hasAnyAncestor(hasTestTag("sheet-time-input")))
        timeFields.onFirst().performTextReplacement("12")
        timeFields.onLast().performTextReplacement("45")
        compose.onNodeWithText("确定").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("提醒").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("准时").performSemanticsAction(SemanticsActions.OnClick)
        // 提醒选完就直接写进表单，摘要行立刻写出算出来的时刻——不再有「确认日期和提醒」那一层。
        compose.onNodeWithText("提醒：准时", substring = true).assertExists()
        compose.runOnIdle {
            assertEquals("两分钟后提醒", editor.title)
            assertEquals("The entry page must keep the picked day", day, editor.plannedDate)
            assertEquals("The time dialog must keep the picked time", LocalTime.of(12, 45), editor.plannedTime)
            assertEquals(ReminderKind.ON_TIME, editor.reminderKind)
        }
        compose.onNodeWithTag(EditorSaveTag).performSemanticsAction(SemanticsActions.OnClick)
        // Room resumes the view model on Android's main looper. waitUntil only
        // advances Compose frames when its condition does not query UI nodes;
        // drain the main queue as well so the actual save callback can run.
        val saveDeadline = System.currentTimeMillis() + 15_000L
        while (savedTask == null && !editor.saveFailed) {
            if (System.currentTimeMillis() >= saveDeadline) {
                fail("Save callback timed out: saving=${editor.saving.value}, saved=${editor.saved.value}, titleError=${editor.titleHasError}")
            }
            compose.waitForIdle()
        }
        assertFalse("Editor failed to save the reminder", editor.saveFailed)

        val task = requireNotNull(savedTask)
        val expected = day.atTime(12, 45).atZone(zone).toInstant().toEpochMilli()
        assertEquals(day, task.plannedDate)
        assertEquals(LocalTime.of(12, 45), task.plannedTime)
        assertEquals(ReminderKind.ON_TIME, task.reminderKind)
        assertEquals(expected, task.reminderAt)
        compose.onNodeWithText("12:45 提醒").assertExists()
        val context = RuntimeEnvironment.getApplication()
        val alarms = context.getSystemService(AlarmManager::class.java)
        assertEquals(expected, shadowOf(alarms).scheduledAlarms.single().triggerAtTime)

        compose.runOnIdle { clock = expected }
        // Android has not dispatched its ordinary alarm; foreground delivery must still notify.
        runBlocking { scheduler.dispatchDue() }
        val notifications = context.getSystemService(NotificationManager::class.java)
        assertNotNull(shadowOf(notifications).getNotification(task.id.toInt()))
        assertTrue(ReminderDiagnostics.report(context).contains("应用前台 任务#${task.id} 通知请求成功=true"))
        assertEquals("应用前台", ReminderSettingsStore(context).deliverySource(task.id, expected))
        compose.onNodeWithText("12:45 · 提醒时间已到").assertExists()
        runBlocking { scheduler.dispatch(expected) }
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }
}
