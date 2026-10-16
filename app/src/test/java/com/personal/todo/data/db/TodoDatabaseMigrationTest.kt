package com.personal.todo.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.personal.todo.data.FocusRepository
import com.personal.todo.data.HabitRepository
import com.personal.todo.model.FocusMode
import com.personal.todo.model.FocusStatus
import com.personal.todo.model.FocusTagSnapshot
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatKind
import com.personal.todo.model.RepeatRule
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
            assertEquals("「HIGH」这一路迁移下来会变成「重要不紧急」", Priority.IMPORTANT_NOT_URGENT, task.priority)
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
     * v5 -> v6: the 清单 a focus session's task was in. Both new columns are
     * nullable, so a session recorded before the upgrade comes back with no list
     * - which is exactly what it has, and the statistics page reports it as
     * 未关联 - and a timer running right now upgrades just as harmlessly.
     */
    @Test
    fun migratingFromVersion5KeepsTheSessionsAndLeavesTheListEmpty() = runBlocking {
        val name = "migration-5-to-6.db"
        context.deleteDatabase(name)
        createVersion5Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val session = db.focusDao().getSessions().single()
            assertEquals("迁移前的任务", session.taskTitleSnapshot)
            assertEquals(1_500, session.actualFocusSeconds)
            assertNull("旧记录没有清单快照，不会被猜出来", session.listNameSnapshot)

            // 迁移之后记下来的专注才带着当时的清单名。
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
                    taskId = 1L,
                    taskTitleSnapshot = "迁移前的任务",
                    listNameSnapshot = "收件箱"
                )
            )
            assertTrue(focus.abandonTimer())

            val written = requireNotNull(db.focusDao().getSessions().maxByOrNull { it.id })
            assertEquals("收件箱", written.listNameSnapshot)
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database exactly as version 5 would have left it: the
     * DDL of `schemas/.../5.json`, one task and its finished 番茄钟.
     */
    private fun createVersion5Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V5_TASK_LISTS)
            sqlite.execSQL(V5_TASKS)
            sqlite.execSQL(V5_TASK_EVENTS)
            sqlite.execSQL(V5_FOCUS_SESSIONS)
            sqlite.execSQL(V5_ACTIVE_TIMER)
            sqlite.execSQL(V5_TAGS)
            sqlite.execSQL(V5_TASK_TAGS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V5_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                    "sortOrder, createdAt, updatedAt, plannedDate, plannedTime, dueDate, dueTime) " +
                    "VALUES ('迁移前的任务', NULL, 1, NULL, 'NORMAL', NULL, NULL, 0, 1000, 1000, " +
                    "NULL, NULL, NULL, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO task_events (taskId, eventAt, eventType, titleSnapshot, detailsJson) " +
                    "VALUES (1, 1000, 'CREATED', '迁移前的任务', NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO focus_sessions (taskId, taskTitleSnapshot, mode, startedAt, endedAt, " +
                    "plannedSeconds, actualFocusSeconds, status, note) " +
                    "VALUES (1, '迁移前的任务', 'POMODORO', 1000, 2000, 1500, 1500, 'COMPLETED', NULL)"
            )
            sqlite.version = 5
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    /**
     * v6 -> v7: 优先级从三档变成四象限。表结构一个字段都没动，只是把旧值重写掉了，
     * 所以这里验的是映射本身。
     */
    @Test
    fun migratingFromVersion6RewritesTheThreeOldPrioritiesIntoQuadrants() = runBlocking {
        val name = "migration-6-to-7.db"
        context.deleteDatabase(name)
        createVersion6Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            assertEquals(
                Priority.NOT_IMPORTANT_NOT_URGENT,
                requireNotNull(db.taskDao().getById(1L)).priority
            )
            assertEquals(
                Priority.IMPORTANT_NOT_URGENT,
                requireNotNull(db.taskDao().getById(2L)).priority
            )
            assertEquals(
                Priority.IMPORTANT_URGENT,
                requireNotNull(db.taskDao().getById(3L)).priority
            )
            assertEquals("标题与其它字段一点没被动", "任务0", db.taskDao().getById(1L)?.title)
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database exactly as version 6 would have left it: the
     * DDL of `schemas/.../6.json` and three tasks carrying the old priorities.
     */
    private fun createVersion6Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V5_TASK_LISTS)
            sqlite.execSQL(V5_TASKS)
            sqlite.execSQL(V5_TASK_EVENTS)
            sqlite.execSQL(V6_FOCUS_SESSIONS)
            sqlite.execSQL(V6_ACTIVE_TIMER)
            sqlite.execSQL(V5_TAGS)
            sqlite.execSQL(V5_TASK_TAGS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V6_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            listOf("NORMAL", "HIGH", "URGENT").forEachIndexed { index, priority ->
                sqlite.execSQL(
                    "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                        "sortOrder, createdAt, updatedAt, plannedDate, plannedTime, dueDate, dueTime) " +
                        "VALUES (?, NULL, 1, NULL, ?, NULL, NULL, 0, 1000, 1000, NULL, NULL, NULL, NULL)",
                    arrayOf<Any?>("任务$index", priority)
                )
            }
            sqlite.version = 6
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    /**
     * v7 -> v8: 重复任务。两个新列都可空，所以已经存在的任务升级之后就是「不重复」；
     * 升完之后带规则的重复任务能正常写进去、读出来。
     */
    @Test
    fun migratingFromVersion7AddsRepeatsAndKeepsEveryTask() = runBlocking {
        val name = "migration-7-to-8.db"
        context.deleteDatabase(name)
        createVersion7Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val task = requireNotNull(db.taskDao().getById(1L))
            assertEquals("迁移前的任务", task.title)
            assertEquals(Priority.IMPORTANT_NOT_URGENT, task.priority)
            assertNull("老任务不会凭空长出规则", task.repeatRule)
            assertNull(task.recurrenceSourceId)

            db.taskDao().update(
                task.copy(
                    plannedDate = LocalDate.of(2026, 10, 15),
                    repeatRule = RepeatRule(RepeatKind.DAILY)
                )
            )
            assertEquals(
                RepeatRule(RepeatKind.DAILY),
                db.taskDao().getById(1L)?.repeatRule
            )
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database exactly as version 7 would have left it: the
     * same DDL as version 6 (that upgrade only rewrote values), one task.
     */
    private fun createVersion7Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V5_TASK_LISTS)
            sqlite.execSQL(V5_TASKS)
            sqlite.execSQL(V5_TASK_EVENTS)
            sqlite.execSQL(V6_FOCUS_SESSIONS)
            sqlite.execSQL(V6_ACTIVE_TIMER)
            sqlite.execSQL(V5_TAGS)
            sqlite.execSQL(V5_TASK_TAGS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            // 版本 7 的表结构和版本 6 完全一样，所以 identity hash 也是同一个。
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V6_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                    "sortOrder, createdAt, updatedAt, plannedDate, plannedTime, dueDate, dueTime) " +
                    "VALUES ('迁移前的任务', NULL, 1, NULL, 'IMPORTANT_NOT_URGENT', NULL, NULL, 0, 1000, 1000, " +
                    "NULL, NULL, NULL, NULL)"
            )
            sqlite.version = 7
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    /**
     * v8 -> v9: 专注记录里带上当时任务的标签。两张表上都可空，所以升级前记下的 session
     * 回来就是「没有标签」；升完之后新记的专注才带着标签快照。
     */
    @Test
    fun migratingFromVersion8KeepsSessionsAndLeavesTheTagSnapshotEmpty() = runBlocking {
        val name = "migration-8-to-9.db"
        context.deleteDatabase(name)
        createVersion8Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val session = db.focusDao().getSessions().single()
            assertEquals("迁移前的任务", session.taskTitleSnapshot)
            assertEquals("收件箱", session.listNameSnapshot)
            assertNull("旧记录没有标签快照，不会被猜出来", session.tagNamesSnapshot)
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
                    taskId = 1L,
                    taskTitleSnapshot = "迁移前的任务",
                    listNameSnapshot = "收件箱",
                    tagNamesSnapshot = FocusTagSnapshot.encode(listOf("工作", "重要"))
                )
            )
            assertTrue(focus.abandonTimer())

            val written = requireNotNull(db.focusDao().getSessions().maxByOrNull { it.id })
            assertTrue(FocusTagSnapshot.matches(written.tagNamesSnapshot, "重要"))
            assertTrue("没有打的标签不在快照里", !FocusTagSnapshot.matches(written.tagNamesSnapshot, "个人"))
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database exactly as version 8 would have left it: the
     * v6 DDL for the focus tables plus the two repeat columns of v8 on `tasks`,
     * one task and its finished 番茄钟.
     */
    private fun createVersion8Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V5_TASK_LISTS)
            sqlite.execSQL(V8_TASKS)
            sqlite.execSQL(V5_TASK_EVENTS)
            sqlite.execSQL(V6_FOCUS_SESSIONS)
            sqlite.execSQL(V6_ACTIVE_TIMER)
            sqlite.execSQL(V5_TAGS)
            sqlite.execSQL(V5_TASK_TAGS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            sqlite.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tasks_recurrenceSourceId` ON `tasks` (`recurrenceSourceId`)"
            )
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V8_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                    "sortOrder, createdAt, updatedAt, plannedDate, plannedTime, dueDate, dueTime, " +
                    "repeatRule, recurrenceSourceId) " +
                    "VALUES ('迁移前的任务', NULL, 1, NULL, 'IMPORTANT_NOT_URGENT', NULL, NULL, 0, 1000, 1000, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO task_events (taskId, eventAt, eventType, titleSnapshot, detailsJson) " +
                    "VALUES (1, 1000, 'CREATED', '迁移前的任务', NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO focus_sessions (taskId, taskTitleSnapshot, listNameSnapshot, mode, startedAt, " +
                    "endedAt, plannedSeconds, actualFocusSeconds, status, note) " +
                    "VALUES (1, '迁移前的任务', '收件箱', 'POMODORO', 1000, 2000, 1500, 1500, 'COMPLETED', NULL)"
            )
            sqlite.version = 8
        } finally {
            sqlite.close()
        }
        assertTrue(file.exists())
    }

    /**
     * v9 -> v10: 习惯的两张表。它们从空的开始，所以升级前记下的任务与专注记录一个都没被动
     * 过；升完之后新习惯与打卡记录都能正常写读。
     */
    @Test
    fun migratingFromVersion9AddsTheHabitTablesAndKeepsEverything() = runBlocking {
        val name = "migration-9-to-10.db"
        context.deleteDatabase(name)
        createVersion9Database(name)

        val db = Room.databaseBuilder(context, TodoDatabase::class.java, name)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val session = db.focusDao().getSessions().single()
            assertEquals("迁移前的任务", session.taskTitleSnapshot)
            assertEquals("收件箱", session.listNameSnapshot)

            assertTrue("习惯表是新建的，里面是空的", db.habitDao().observeHabits().first().isEmpty())

            val habits = HabitRepository(
                db = db,
                now = { 2_000L },
                zone = ZoneId.of("Asia/Shanghai")
            )
            val habitId = habits.createHabit("喝水", 5)
            habits.increment(habitId, LocalDate.of(2026, 10, 16))

            val habit = requireNotNull(db.habitDao().getById(habitId))
            assertEquals("喝水", habit.name)
            assertEquals(5, habit.dailyTarget)
            assertEquals(
                "打卡按本地日期存下来",
                1,
                db.habitDao().getCheckIn(habitId, LocalDate.of(2026, 10, 16))?.count
            )
        } finally {
            db.close()
        }
    }

    /**
     * Builds a file backed database exactly as version 9 would have left it: the
     * v8 DDL plus `tagNamesSnapshot` on both focus tables, one task and its
     * finished 番茄钟.
     */
    private fun createVersion9Database(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sqlite.execSQL(V5_TASK_LISTS)
            sqlite.execSQL(V8_TASKS)
            sqlite.execSQL(V5_TASK_EVENTS)
            sqlite.execSQL(V9_FOCUS_SESSIONS)
            sqlite.execSQL(V9_ACTIVE_TIMER)
            sqlite.execSQL(V5_TAGS)
            sqlite.execSQL(V5_TASK_TAGS)
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_events_taskId` ON `task_events` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)")
            sqlite.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tasks_recurrenceSourceId` ON `tasks` (`recurrenceSourceId`)"
            )
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqlite.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$V9_IDENTITY_HASH')")
            sqlite.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('收件箱', 1, 0, 1000, 1000)"
            )
            sqlite.execSQL(
                "INSERT INTO tasks (title, notes, listId, parentTaskId, priority, completedAt, deletedAt, " +
                    "sortOrder, createdAt, updatedAt, plannedDate, plannedTime, dueDate, dueTime, " +
                    "repeatRule, recurrenceSourceId) " +
                    "VALUES ('迁移前的任务', NULL, 1, NULL, 'IMPORTANT_NOT_URGENT', NULL, NULL, 0, 1000, 1000, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO task_events (taskId, eventAt, eventType, titleSnapshot, detailsJson) " +
                    "VALUES (1, 1000, 'CREATED', '迁移前的任务', NULL)"
            )
            sqlite.execSQL(
                "INSERT INTO focus_sessions (taskId, taskTitleSnapshot, listNameSnapshot, tagNamesSnapshot, " +
                    "mode, startedAt, endedAt, plannedSeconds, actualFocusSeconds, status, note) " +
                    "VALUES (1, '迁移前的任务', '收件箱', NULL, 'POMODORO', 1000, 2000, 1500, 1500, 'COMPLETED', NULL)"
            )
            sqlite.version = 9
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

        /** The identity hash `schemas/.../5.json` records for version 5. */
        const val V5_IDENTITY_HASH = "d6700e6737c6411d1ed3b24cb122374f"

        const val V5_TASK_LISTS = V1_TASK_LISTS

        const val V5_TASK_EVENTS = V1_TASK_EVENTS

        /** The tasks table as of version 5: v1 plus 计划日期 / 计划时间 / 截止日期 / 截止时间. */
        const val V5_TASKS =
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `notes` TEXT, `listId` INTEGER NOT NULL, `parentTaskId` INTEGER, " +
                "`priority` TEXT NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`plannedDate` TEXT, `plannedTime` TEXT, `dueDate` TEXT, `dueTime` TEXT, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        const val V5_FOCUS_SESSIONS =
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER, `taskTitleSnapshot` TEXT, `mode` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER NOT NULL, `plannedSeconds` INTEGER NOT NULL, " +
                "`actualFocusSeconds` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        const val V5_ACTIVE_TIMER =
            "CREATE TABLE IF NOT EXISTS `active_timer` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                "`phase` TEXT NOT NULL DEFAULT 'FOCUS', `taskId` INTEGER, `taskTitleSnapshot` TEXT, " +
                "`plannedSeconds` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`lastResumeAtElapsed` INTEGER NOT NULL, `accumulatedFocusSeconds` INTEGER NOT NULL, " +
                "`running` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        /** The identity hash `schemas/.../6.json` records for version 6. */
        const val V6_IDENTITY_HASH = "2cde2772d9ed663cb919cf565ce4c92f"

        const val V6_FOCUS_SESSIONS =
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER, `taskTitleSnapshot` TEXT, `listNameSnapshot` TEXT, `mode` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `plannedSeconds` INTEGER NOT NULL, " +
                "`actualFocusSeconds` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        const val V6_ACTIVE_TIMER =
            "CREATE TABLE IF NOT EXISTS `active_timer` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                "`phase` TEXT NOT NULL DEFAULT 'FOCUS', `taskId` INTEGER, `taskTitleSnapshot` TEXT, " +
                "`listNameSnapshot` TEXT, `plannedSeconds` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`lastResumeAtElapsed` INTEGER NOT NULL, `accumulatedFocusSeconds` INTEGER NOT NULL, " +
                "`running` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        /** The identity hash `schemas/.../8.json` records for version 8. */
        const val V8_IDENTITY_HASH = "b5a0b666f0d6ed8a8aeba1adc5cb4eda"

        /** The tasks table as of version 8: v5 plus 重复规则与「从哪条重复任务生成」。 */
        const val V8_TASKS =
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `notes` TEXT, `listId` INTEGER NOT NULL, `parentTaskId` INTEGER, " +
                "`priority` TEXT NOT NULL, `completedAt` INTEGER, `deletedAt` INTEGER, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`plannedDate` TEXT, `plannedTime` TEXT, `dueDate` TEXT, `dueTime` TEXT, " +
                "`repeatRule` TEXT, `recurrenceSourceId` INTEGER, " +
                "FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        /** The identity hash `schemas/.../9.json` records for version 9. */
        const val V9_IDENTITY_HASH = "75bbb228c11e8fe6f01f3a1fc1ff6c60"

        /** 专注记录表到版本 9：v6 的那张再加上标签快照列。 */
        const val V9_FOCUS_SESSIONS =
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER, `taskTitleSnapshot` TEXT, `listNameSnapshot` TEXT, `tagNamesSnapshot` TEXT, " +
                "`mode` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
                "`plannedSeconds` INTEGER NOT NULL, `actualFocusSeconds` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `note` TEXT, " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

        const val V9_ACTIVE_TIMER =
            "CREATE TABLE IF NOT EXISTS `active_timer` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                "`phase` TEXT NOT NULL DEFAULT 'FOCUS', `taskId` INTEGER, `taskTitleSnapshot` TEXT, " +
                "`listNameSnapshot` TEXT, `tagNamesSnapshot` TEXT, `plannedSeconds` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `lastResumeAtElapsed` INTEGER NOT NULL, " +
                "`accumulatedFocusSeconds` INTEGER NOT NULL, `running` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val V5_TAGS =
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

        const val V5_TASK_TAGS =
            "CREATE TABLE IF NOT EXISTS `task_tags` (`taskId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`taskId`, `tagId`), " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
    }
}
