package com.personal.todo.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.Priority
import com.personal.todo.model.TaskEventType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v1 -> v2 migration test (spec section 7.2: a schema change without a tested
 * migration is forbidden, and destructive migrations are not allowed).
 *
 * The v1 database is created with the exact DDL of `schemas/.../1.json`, rows
 * are inserted, and the file is then opened by the app's own builder. Adding
 * only nullable columns must keep every existing row readable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoDatabaseMigrationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun migratingFromVersion1KeepsTasksAndLeavesTheNewDatesEmpty() = runBlocking {
        val name = "migration-1-to-2.db"
        context.deleteDatabase(name)
        createVersion1Database(name, taskTitle = "迁移前的任务", priority = "HIGH")

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val task = requireNotNull(db.taskDao().getById(1L))
            assertEquals("迁移前的任务", task.title)
            assertEquals(Priority.HIGH, task.priority)
            assertNull("旧任务在升级后不应被猜测出日期", task.plannedDate)
            assertNull(task.dueDate)
            assertNull(task.dueTime)
            assertNull("计划时间同样是空的，不会被猜", task.plannedTime)

            assertEquals(
                listOf(TaskEventType.CREATED),
                db.taskEventDao().getForTask(1L).map { it.eventType }
            )
            assertEquals("收件箱", db.taskListDao().getById(1L)?.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun theNewDateColumnsCanBeUsedAfterTheMigration() = runBlocking {
        val name = "migration-write-2.db"
        context.deleteDatabase(name)
        createVersion1Database(name, taskTitle = "旧任务", priority = "NORMAL")

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE tasks SET plannedDate = '2026-10-14', dueDate = '2026-10-15', dueTime = '09:30' WHERE id = 1"
            )

            val task = requireNotNull(db.taskDao().getById(1L))
            assertEquals(LocalDate.of(2026, 10, 14), task.plannedDate)
            assertEquals(LocalDate.of(2026, 10, 15), task.dueDate)
            assertEquals(LocalTime.of(9, 30), task.dueTime)
        } finally {
            db.close()
        }
    }

    /**
     * v2 -> v4 in one go: the focus tables of v3 and the planned-time column plus
     * the tag tables of v4 all arrive while the tasks, their dates and their
     * events stay exactly as they were. This is the chain an install that jumps
     * several versions at once runs.
     */
    @Test
    fun migratingFromVersion2KeepsTheTaskDataAndAddsTheFocusAndTagTables() = runBlocking {
        val name = "migration-2-to-3.db"
        context.deleteDatabase(name)
        createVersion2Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val task = requireNotNull(db.taskDao().getById(1L))
            assertEquals("迁移前的任务", task.title)
            assertEquals(LocalDate.of(2026, 10, 14), task.plannedDate)
            assertEquals(LocalDate.of(2026, 10, 15), task.dueDate)
            assertEquals(LocalTime.of(9, 30), task.dueTime)
            assertNull("旧任务的计划时间是空的", task.plannedTime)
            assertEquals(
                listOf(TaskEventType.CREATED),
                db.taskEventDao().getForTask(1L).map { it.eventType }
            )

            // The focus tables are usable immediately, and a session can even be
            // linked to a task that existed before the upgrade.
            assertTrue(db.focusDao().getSessions().isEmpty())
            assertNull(db.focusDao().getActiveTimer())

            val focus = FocusRepository(
                db = db,
                now = { 2_000L },
                elapsed = { 5_000L },
                zone = ZoneId.of("Asia/Shanghai")
            )
            assertTrue(
                focus.startTimer(
                    mode = FocusMode.POMODORO,
                    plannedSeconds = 1_500,
                    taskId = task.id,
                    taskTitleSnapshot = task.title
                )
            )
            assertTrue(focus.abandonTimer())

            val session = requireNotNull(db.focusDao().getSessions().singleOrNull())
            assertEquals(FocusMode.POMODORO, session.mode)
            assertEquals(FocusStatus.INTERRUPTED, session.status)
            assertEquals("迁移前的任务", session.taskTitleSnapshot)
            assertEquals(task.id, session.taskId)

            // The tag tables are new and work right away: a pre-existing task can
            // be tagged, and the link is readable through the join.
            assertTrue(db.tagDao().observeTags().first().isEmpty())
            val tagId = db.tagDao().insert(
                Tag(name = "等回复", color = 0xFF3D7BF7.toInt(), createdAt = 3_000L, updatedAt = 3_000L)
            )
            db.tagDao().link(TaskTag(taskId = task.id, tagId = tagId))
            assertEquals(
                listOf("等回复"),
                db.tagDao().observeTagsOf(task.id).first().map { it.name }
            )
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database as version 2 would have left it: the version
     * 1 tables plus the three date columns the first migration adds.
     */
    private fun createVersion2Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V1_TASK_LISTS)
            sqlite.execSQL(V2_TASKS)
            sqlite.execSQL(V1_TASK_EVENTS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                    "sortOrder, createdAt, updatedAt, plannedDate, dueDate, dueTime) " +
                    "VALUES ('迁移前的任务', NULL, 1, NULL, 'NORMAL', NULL, NULL, 0, 1000, 1000, " +
                    "'2026-10-14', '2026-10-15', '09:30')"
            )
            sqlite.execSQL(
                "INSERT INTO task_events (taskId, eventAt, eventType, titleSnapshot, detailsJson) " +
                    "VALUES (1, 1000, 'CREATED', '迁移前的任务', NULL)"
            )
            sqlite.version = 2
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    /**
     * Builds a file backed database exactly as version 1 of the app would have:
     * same DDL, same indices, same room_master_table identity hash.
     */
    private fun createVersion1Database(name: String, taskTitle: String, priority: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V1_TASK_LISTS)
            sqlite.execSQL(V1_TASKS)
            sqlite.execSQL(V1_TASK_EVENTS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V1_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, sortOrder, createdAt, updatedAt) " +
                    "VALUES (?, NULL, 1, NULL, ?, NULL, NULL, 0, 1000, 1000)",
                arrayOf<Any?>(taskTitle, priority)
            )
            sqlite.execSQL(
                "INSERT INTO task_events (taskId, eventAt, eventType, titleSnapshot, detailsJson) VALUES (1, 1000, 'CREATED', ?, NULL)",
                arrayOf<Any?>(taskTitle)
            )
            sqlite.version = 1
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    private companion object {
        const val V1_IDENTITY_HASH = "70541762c5a90c64b8db8c601824df55"

        const val V1_TASK_LISTS =
            "CREATE TABLE IF NOT EXISTS `task_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `isInbox` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

        const val V1_TASKS =
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `notes` TEXT, `listId` INTEGER NOT NULL, `parentTaskId` INTEGER, " +
                "`priority` TEXT NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        const val V1_TASK_EVENTS =
            "CREATE TABLE IF NOT EXISTS `task_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER NOT NULL, `eventAt` INTEGER NOT NULL, `eventType` TEXT NOT NULL, " +
                "`titleSnapshot` TEXT NOT NULL, `detailsJson` TEXT, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        /** The tasks table as the v1 -> v2 migration leaves it. */
        const val V2_TASKS =
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `notes` TEXT, `listId` INTEGER NOT NULL, `parentTaskId` INTEGER, " +
                "`priority` TEXT NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`plannedDate` TEXT, `dueDate` TEXT, `dueTime` TEXT, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
    }
}
