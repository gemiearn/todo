package com.personal.todo.data

import android.content.Context
import com.personal.todo.R
import com.personal.todo.data.db.DatabaseProvider

/**
 * Minimal manual wiring for the app. A full DI framework is explicitly not
 * wanted for a single module local app (spec section 7.2), so this is the only
 * place that knows how the repository is built.
 */
object TodoContainer {

    @Volatile
    private var repository: TaskRepository? = null

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
}
