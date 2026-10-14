package com.personal.todo.data

import android.content.Context
import androidx.room.Room
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.data.db.TodoDatabase
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Database level tests: reopening the real database file (the "app restart"
 * acceptance criterion of phase 1) and the foreign keys that protect task
 * history.
 *
 * These use a file backed database instead of an in memory one, because the
 * foreign key behaviour of the shipped app must be verified.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskPersistenceTest {

    private fun openDatabase(context: Context, name: String): TodoDatabase =
        Room.databaseBuilder(context, TodoDatabase::class.java, name).build()

    @Test
    fun tasksAndEventsSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val first = openDatabase(context, "restart-test.db")
        val firstRepository = TaskRepository(first, inboxName = "收件箱", now = { 5_000L })
        val inboxId = firstRepository.ensureInbox()
        val listId = firstRepository.createList("工作")
        val taskId = firstRepository.createTask("跨重启任务", listId, notes = "备注", priority = Priority.URGENT)
        firstRepository.completeTask(taskId)
        first.close()

        val second = openDatabase(context, "restart-test.db")
        try {
            val repository = TaskRepository(second, inboxName = "收件箱")

            val task = requireNotNull(repository.getTask(taskId))
            assertEquals("跨重启任务", task.title)
            assertEquals("备注", task.notes)
            assertEquals(Priority.URGENT, task.priority)
            assertEquals(listId, task.listId)
            assertNotNull(task.completedAt)

            assertEquals(listOf("收件箱", "工作"), repository.observeLists().first().map { it.name })
            assertEquals(listOf(taskId), repository.observeCompletedTasks().first().map { it.id })
            assertEquals(
                listOf(TaskEventType.CREATED, TaskEventType.COMPLETED),
                second.taskEventDao().getForTask(taskId).map { it.eventType }
            )
        } finally {
            second.close()
        }
    }

    @Test
    fun theInboxExistsBeforeTheFirstQuery() = runBlocking {
        // Uses the same builder as the app (seed callback included).
        val db = DatabaseProvider.create(RuntimeEnvironment.getApplication())
        try {
            val inbox = requireNotNull(db.taskListDao().getInbox())

            assertTrue(inbox.isInbox)
            assertEquals("收件箱", inbox.name)
            assertEquals(1, db.taskListDao().observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun aTaskWithHistoryCannotBePhysicallyDeleted() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = openDatabase(context, "fk-task-test.db")
        try {
            val repository = TaskRepository(db, inboxName = "收件箱")
            val inboxId = repository.ensureInbox()
            val taskId = repository.createTask("有历史的任务", inboxId)

            assertPhysicalDeleteIsRejected(db, "DELETE FROM tasks WHERE id = ?", taskId)

            assertNotNull(repository.getTask(taskId))
        } finally {
            db.close()
        }
    }

    @Test
    fun aListThatStillHasTasksCannotBePhysicallyDeleted() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = openDatabase(context, "fk-list-test.db")
        try {
            val repository = TaskRepository(db, inboxName = "收件箱")
            val listId = repository.createList("工作")
            repository.createTask("任务", listId)

            assertPhysicalDeleteIsRejected(db, "DELETE FROM task_lists WHERE id = ?", listId)

            assertNotNull(db.taskListDao().getById(listId))
        } finally {
            db.close()
        }
    }

    private fun assertPhysicalDeleteIsRejected(db: TodoDatabase, sql: String, id: Long) {
        val rejected = try {
            db.openHelper.writableDatabase.execSQL(sql, arrayOf<Any?>(id))
            false
        } catch (error: Exception) {
            true
        }
        assertTrue("物理删除本应被外键约束拒绝，但成功了", rejected)
    }
}
