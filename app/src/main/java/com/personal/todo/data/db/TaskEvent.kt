package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.todo.model.TaskEventType

/**
 * Append-only history of the key operations on a task (spec section 7).
 *
 * [titleSnapshot] keeps the title as it was when the event happened, so the
 * history stays readable even after the task is renamed or soft deleted.
 * The foreign key uses RESTRICT: tasks are never physically deleted, and this
 * guarantees an event can never be dropped together with its task.
 */
@Entity(
    tableName = "task_events",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("taskId")]
)
data class TaskEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val eventAt: Long,
    val eventType: TaskEventType,
    val titleSnapshot: String,
    val detailsJson: String? = null
)
