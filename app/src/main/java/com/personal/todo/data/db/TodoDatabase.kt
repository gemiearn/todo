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
 */
@Database(
    entities = [TaskList::class, Task::class, TaskEvent::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskListDao(): TaskListDao

    abstract fun taskDao(): TaskDao

    abstract fun taskEventDao(): TaskEventDao

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

        /** Every migration the app ships, in order. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
