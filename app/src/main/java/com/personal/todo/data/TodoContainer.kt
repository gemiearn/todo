package com.personal.todo.data

import android.content.Context
import com.personal.todo.R
import com.personal.todo.data.db.DatabaseProvider

/**
 * Minimal manual wiring for the app. A full DI framework is explicitly not
 * wanted for a single module local app (spec section 7.2), so this is the only
 * place that knows how the repositories are built.
 *
 * Both repositories deliberately share the one database: a focus session and a
 * task therefore live in the same file and the same transaction scope.
 */
object TodoContainer {

    @Volatile
    private var repository: TaskRepository? = null

    @Volatile
    private var focus: FocusRepository? = null

    @Volatile
    private var habits: HabitRepository? = null

    fun repository(context: Context): TaskRepository {
        repository?.let { return it }
        synchronized(this) {
            repository?.let { return it }
            val appContext = context.applicationContext
            val created = TaskRepository(
                db = DatabaseProvider.get(appContext),
                inboxName = appContext.getString(R.string.list_inbox_name)
            )
            repository = created
            return created
        }
    }

    fun focusRepository(context: Context): FocusRepository {
        focus?.let { return it }
        synchronized(this) {
            focus?.let { return it }
            val created = FocusRepository(db = DatabaseProvider.get(context.applicationContext))
            focus = created
            return created
        }
    }

    fun habitRepository(context: Context): HabitRepository {
        habits?.let { return it }
        synchronized(this) {
            habits?.let { return it }
            val created = HabitRepository(db = DatabaseProvider.get(context.applicationContext))
            habits = created
            return created
        }
    }
}
