package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A task list (清单). The inbox is the built-in list that always exists and
 * can never be deleted (spec section 7.3).
 */
@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isInbox: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
