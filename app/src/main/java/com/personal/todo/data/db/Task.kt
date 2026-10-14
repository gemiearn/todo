package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.todo.model.Priority

/**
 * A single task row (spec section 7.3).
 *
 * State is expressed with two nullable timestamps: [completedAt] non-null means
 * completed, [deletedAt] non-null means soft deleted. Rows are never physically
 * deleted, which keeps history (tasks and events) readable after a delete.
 *
 * [parentTaskId] is the one-level subtask link. It is not nullable for
 * sub-subtasks: the UI and [com.personal.todo.data.TaskRepository] both refuse
 * to create a subtask of a subtask.
 *
 * Dates (planned date / due date) belong to phase 2 and reminder time to phase
 * 4; they are deliberately not part of schema version 1 yet, so those phases
 * add them through a real Room migration with a migration test.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("listId"), Index("parentTaskId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val listId: Long,
    val parentTaskId: Long? = null,
    val priority: Priority = Priority.NORMAL,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    /** Kept for manual ordering; phase 1 sorts by creation time. */
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
