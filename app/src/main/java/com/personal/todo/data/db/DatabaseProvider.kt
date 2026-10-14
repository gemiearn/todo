package com.personal.todo.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.todo.R

/**
 * Creates and caches the app database.
 *
 * The inbox row is inserted from the Room `onCreate` callback, so it exists
 * before the very first query is executed - no startup race, no extra
 * "seed on first launch" logic in the UI layer.
 */
object DatabaseProvider {

    @Volatile
    private var instance: TodoDatabase? = null

    fun get(context: Context): TodoDatabase =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    /** Builds the database exactly as the app does, without caching it. */
    internal fun create(context: Context): TodoDatabase =
        Room.databaseBuilder(context, TodoDatabase::class.java, TodoDatabase.DATABASE_NAME)
            .addMigrations(*TodoDatabase.ALL_MIGRATIONS)
            .addCallback(SeedInboxCallback(context.getString(R.string.list_inbox_name)))
            .build()

    private class SeedInboxCallback(private val inboxName: String) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO task_lists (name, isInbox, sortOrder, createdAt, updatedAt) VALUES (?, 1, 0, ?, ?)",
                arrayOf<Any?>(inboxName, now, now)
            )
        }
    }
}
