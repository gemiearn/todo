package com.personal.todo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.todo.model.Priority
import com.personal.todo.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime

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
 * Dates follow spec section 7.3 and are deliberately stored in a way that
 * cannot drift across time zones: [plannedDate] and [dueDate] are pure local
 * dates kept as `YYYY-MM-DD` text, and [dueTime] is a local time kept as
 * `HH:mm` text. A null [dueTime] means "due at the end of [dueDate]". Reminder
 * time stays in phase 4 and will be added by a later migration.
 *
 * [repeatRule] makes this row the *source* of a repeating series: the rule lives
 * here and nowhere else, and every occurrence it produces is a separate task
 * (spec section 4.5). [recurrenceSourceId] is that link seen from the generated
 * occurrence's side; it carries no foreign key, because a source that was soft
 * deleted must not take the occurrences that already exist with it.
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
    indices = [Index("listId"), Index("parentTaskId"), Index("recurrenceSourceId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val listId: Long,
    val parentTaskId: Long? = null,
    val priority: Priority = Priority.DEFAULT,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    /** Kept for manual ordering; phase 1 sorts by creation time. */
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** Local date the user plans to work on it; purely informational. */
    val plannedDate: LocalDate? = null,
    /**
     * Local time the user plans to start it. Together with [plannedDate] it is
     * what the day timeline orders by - the moment the work is meant to begin,
     * which is a different question from the deadline below.
     */
    val plannedTime: LocalTime? = null,
    /** Local date it is due; the only field that can make a task overdue. */
    val dueDate: LocalDate? = null,
    /** Local time it is due; null means "at the end of [dueDate]". */
    val dueTime: LocalTime? = null,
    /** 重复规则。有它，这一行就是「带规则的那条任务」，规则只存在这里。 */
    val repeatRule: RepeatRule? = null,
    /** 生成这条任务的那条重复任务的 id；它就是某一次，不是源。 */
    val recurrenceSourceId: Long? = null
)
