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
 */
@Database(
    entities = [
        TaskList::class,
        Task::class,
        TaskEvent::class,
        FocusSession::class,
        ActiveTimer::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskListDao(): TaskListDao

    abstract fun taskDao(): TaskDao

    abstract fun taskEventDao(): TaskEventDao

    abstract fun focusDao(): FocusDao

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

        /** Every migration the app ships, in order. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
