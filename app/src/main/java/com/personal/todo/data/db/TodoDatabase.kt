package com.personal.todo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single local database of the app.
 *
 * Version 1 is the first schema (阶段 1: local task core). Every later schema
 * change must add a real migration plus a migration test - destructive
 * migrations are forbidden by the spec (section 7.2), because silently wiping
 * the user's tasks is never acceptable.
 */
@Database(
    entities = [TaskList::class, Task::class, TaskEvent::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {

    abstract fun taskListDao(): TaskListDao

    abstract fun taskDao(): TaskDao

    abstract fun taskEventDao(): TaskEventDao

    companion object {
        const val DATABASE_NAME = "todo.db"
    }
}
