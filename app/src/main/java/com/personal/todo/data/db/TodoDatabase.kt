package com.personal.todo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The single local database of the app.
 *
 * Version 1 was the first schema (阶段 1: local task core). Every later schema
 * change must add a real migration plus a migration test - destructive
 * migrations are forbidden by the spec (section 7.2), because silently wiping
 * the user's tasks is never acceptable.
 *
 * Version 2 adds the dates of section 4.1: `plannedDate`, `dueDate` and
 * `dueTime`. All three are nullable, so an existing task simply has no dates
 * after the upgrade - nothing is guessed or back-filled.
 *
 * Version 3 adds the focus timer of section 5: `focus_sessions` (the finished
 * history, one row per session) and `active_timer` (the single recoverable row
 * of a running session). Both are new tables, so no existing row is touched.
 *
 * Version 4 adds `tasks.plannedTime` (the moment work is meant to start, which
 * the day timeline orders by) and the tag tables `tags` / `task_tags`. The new
 * column is nullable and the two tables are new, so again nothing is rewritten.
 *
 * Version 5 adds `active_timer.phase`, so a pomodoro can carry the break that
 * follows it (说明文档 5.1). The column is `NOT NULL DEFAULT 'FOCUS'`: a timer
 * stored before it existed was a focus block, which is exactly that default.
 *
 * Version 6 adds `focus_sessions.listNameSnapshot` and
 * `active_timer.listNameSnapshot`: the 清单 a session's task was in. Both are
 * nullable, so sessions (and a running timer) recorded before the upgrade simply
 * have no list and are reported as 未关联 - nothing is guessed.
 *
 * Version 7 changes what `tasks.priority` holds, not its shape: 优先级 is now the
 * four quadrants (重要 / 紧急 are two dimensions the user sets), so the old three
 * values NORMAL / HIGH / URGENT are rewritten into the four quadrant names. No
 * column is added or dropped, so every other table is untouched.
 *
 * Version 8 adds `tasks.repeatRule` and `tasks.recurrenceSourceId`: a repeating
 * task keeps its rule, and every occurrence it produces is a task of its own
 * (spec section 4.5), pointing back at its source. Both are nullable, so every
 * task that already exists simply does not repeat.
 */
@Database(
    entities = [
        TaskList::class,
        Task::class,
        TaskEvent::class,
        FocusSession::class,
        ActiveTimer::class,
        Tag::class,
        TaskTag::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskListDao(): TaskListDao

    abstract fun taskDao(): TaskDao

    abstract fun taskEventDao(): TaskEventDao

    abstract fun focusDao(): FocusDao

    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "todo.db"

        /**
         * v1 -> v2: pure local dates as `YYYY-MM-DD` text and an optional local
         * time as `HH:mm` text. Only nullable columns are added, so existing
         * rows keep their data and stay valid.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `plannedDate` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `dueDate` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `dueTime` TEXT")
            }
        }

        /**
         * v2 -> v3: the focus timer tables. They are created empty; the task,
         * list and event tables are not rewritten, so nothing can be lost.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER, " +
                        "`taskTitleSnapshot` TEXT, " +
                        "`mode` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER NOT NULL, " +
                        "`plannedSeconds` INTEGER NOT NULL, " +
                        "`actualFocusSeconds` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`note` TEXT, " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` " +
                        "ON `focus_sessions` (`taskId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` " +
                        "ON `focus_sessions` (`startedAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `active_timer` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`mode` TEXT NOT NULL, " +
                        "`taskId` INTEGER, " +
                        "`taskTitleSnapshot` TEXT, " +
                        "`plannedSeconds` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`lastResumeAtElapsed` INTEGER NOT NULL, " +
                        "`accumulatedFocusSeconds` INTEGER NOT NULL, " +
                        "`running` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * v3 -> v4: the planned time of a task, plus tags. `plannedTime` is a
         * nullable `HH:mm` text column like `dueTime`, and the two tag tables
         * start empty - no existing task, list, event or focus session is
         * rewritten, so nothing can be lost on the upgrade.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `plannedTime` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_tags` (" +
                        "`taskId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`taskId`, `tagId`), " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` " +
                        "ON `task_tags` (`tagId`)"
                )
            }
        }

        /**
         * v4 -> v5: which phase the single active timer is counting. SQLite needs a
         * default when a NOT NULL column is added to a table that may already hold a
         * row, and that row is a focus block - so `FOCUS` is both the requirement and
         * the truth. Nothing else on the table is rewritten.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `active_timer` ADD COLUMN `phase` TEXT NOT NULL DEFAULT 'FOCUS'"
                )
            }
        }

        /**
         * v5 -> v6: the 清单 a focus session's task was in. Nullable on both
         * tables, so every existing session and a timer that is running right
         * now upgrade as "no list" - which is exactly what they have.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_sessions` ADD COLUMN `listNameSnapshot` TEXT")
                db.execSQL("ALTER TABLE `active_timer` ADD COLUMN `listNameSnapshot` TEXT")
            }
        }

        /**
         * v6 -> v7: 优先级变成四象限。表结构一点没动，只把已经存下来的三个旧值改写成
         * 四个象限名——归一化之后，读取端的 [com.personal.todo.model.Priority.fromStored]
         * 就不需要再认识旧名字了。
         *
         * 映射：NORMAL → 不重要不紧急、HIGH → 重要不紧急、URGENT → 重要且紧急（原来的
         * 「高」和「紧急」都算重要，所以两档都落在「重要」里）。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `tasks` SET `priority` = 'IMPORTANT_URGENT' WHERE `priority` = 'URGENT'")
                db.execSQL("UPDATE `tasks` SET `priority` = 'IMPORTANT_NOT_URGENT' WHERE `priority` = 'HIGH'")
                db.execSQL("UPDATE `tasks` SET `priority` = 'NOT_IMPORTANT_NOT_URGENT' WHERE `priority` = 'NORMAL'")
            }
        }

        /**
         * v7 -> v8: 重复任务。规则与「这一行是从哪条重复任务生成的」都是可空列，所以
         * 已经存在的任务升级之后就是「不重复」——这正是它们的情况。新增的那个索引是
         * 给生成器去重用的（同一源 + 同一天只能有一条）。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatRule` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrenceSourceId` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_recurrenceSourceId` " +
                        "ON `tasks` (`recurrenceSourceId`)"
                )
            }
        }

        /** Every migration the app ships, in order. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )
    }
}
